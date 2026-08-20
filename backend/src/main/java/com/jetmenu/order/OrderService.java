package com.jetmenu.order;

import com.jetmenu.customer.Customer;
import com.jetmenu.customer.CustomerRepository;
import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientNameNormalizer;
import com.jetmenu.ingredient.IngredientNotFoundException;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.fee.Fee;
import com.jetmenu.fee.FeeRepository;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.product.Product;
import com.jetmenu.product.OrderCostCalculatorService;
import com.jetmenu.product.ProductCostCalculator;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.Include;
import com.jetmenu.product.IncludeKind;
import com.jetmenu.product.IncludeRepository;
import com.jetmenu.product.IncludeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    // Pedidos manuais usam a hora de Brasília — não dependemos do timezone do servidor
    // (em prod/Railway é UTC), que adiantaria o horário em 3h.
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final IngredientRepository ingredientRepository;
    private final FeeRepository feeRepository;
    private final MerchantRepository merchantRepository;
    private final IncludeRepository includeRepository;
    private final OrderCostCalculatorService orderCostCalculatorService;
    private final OrderFichaService orderFichaService;

    public OrderService(OrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        ProductRepository productRepository,
                        IngredientRepository ingredientRepository,
                        FeeRepository feeRepository,
                        MerchantRepository merchantRepository,
                        IncludeRepository includeRepository,
                        OrderCostCalculatorService orderCostCalculatorService,
                        OrderFichaService orderFichaService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.ingredientRepository = ingredientRepository;
        this.feeRepository = feeRepository;
        this.merchantRepository = merchantRepository;
        this.includeRepository = includeRepository;
        this.orderCostCalculatorService = orderCostCalculatorService;
        this.orderFichaService = orderFichaService;
    }

    @Transactional
    public OrderResponse create(UUID merchantId, OrderRequest request) {
        Customer customer = resolveCustomer(merchantId, request);

        Fee fee = resolveFee(request.getFeeId(), merchantId);

        List<OrderItem> items = buildItems(merchantId, request.getItems());

        BigDecimal totalValue = calculateTotalValue(items);

        Order order = Order.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .dateTime(LocalDateTime.now(BRAZIL_ZONE))
                .customer(customer)
                .fee(fee)
                .status(OrderStatus.PAID)
                .totalValue(totalValue)
                .origin(request.getOrigin() != null ? request.getOrigin() : OrderOrigin.JETMENU)
                .items(items)
                .build();

        items.forEach(item -> item.setOrder(order));

        // Ficha do pedido: insumos cobrados UMA vez, fora do laço dos itens.
        attachOrderFicha(order, merchantId);

        // Cálculo via service (requer order.items e order.orderFicha setados)
        BigDecimal totalCost = orderCostCalculatorService.computeOrderTotalCost(order);
        order.setTotalCost(totalCost);
        order.setEstimatedProfit(OrderCalculations.calculateEstimatedProfit(order));

        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    /**
     * Copia a ficha do pedido do lojista para o pedido (snapshot). Sem ficha configurada
     * a lista fica vazia e o custo não muda — no-op para quem não usa a funcionalidade.
     */
    private void attachOrderFicha(Order order, UUID merchantId) {
        List<OrderFichaIngredient> ficha = orderFichaService.buildSnapshot(merchantId);
        ficha.forEach(line -> line.setOrder(order));
        order.setOrderFicha(ficha);
    }

    public OrderResponse findById(UUID merchantId, UUID id) {

        Order order = orderRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(UUID merchantId, String search, OrderStatus status, Pageable pageable) {
        String term = search == null ? "" : search;
        Page<Order> page = status == null
                ? orderRepository.findPageByMerchantIdAndCustomerNameContaining(merchantId, term, pageable)
                : orderRepository.findPageByMerchantIdAndStatusAndCustomerNameContaining(merchantId, status, term, pageable);

        List<OrderItem> pageItems = page.getContent().stream()
                .filter(o -> o.getItems() != null)
                .flatMap(o -> o.getItems().stream())
                .toList();

        Set<UUID> productIds = pageItems.stream()
                .map(i -> i.getProduct().getId())
                .collect(Collectors.toSet());
        Map<UUID, List<Include>> includesByProduct = productIds.isEmpty() ? Map.of() :
                includeRepository.findAllByProductIdInAndProductMerchantId(productIds, merchantId)
                        .stream()
                        .collect(Collectors.groupingBy(inc -> inc.getProduct().getId()));

        // Resolved ONCE for the whole page. Per order, every one carrying unmatched
        // subItems cost its own query (N+1): a page of 20 imported orders fired 20 extra
        // SELECTs just to decide which "register ingredient" buttons to hide.
        Set<String> registeredCanonicalNames = registeredCanonicalNamesFor(pageItems, merchantId);

        return page.map(o -> toResponse(o, includesByProduct, registeredCanonicalNames));
    }

    @Transactional(readOnly = true)
    public java.util.Map<OrderStatus, Long> statusCounts(UUID merchantId, LocalDateTime start, LocalDateTime end, String search) {
        String term = search == null ? "" : search;
        java.util.Map<OrderStatus, Long> counts = new java.util.EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : orderRepository.countByStatusForMerchant(merchantId, start, end, term)) {
            counts.put((OrderStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional
    public OrderResponse update(UUID merchantId, UUID id, OrderRequest request) {

        Order order = orderRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new OrderNotFoundException(id));

        Customer customer = resolveCustomer(merchantId, request);

        Fee fee = resolveFee(request.getFeeId(), merchantId);

        List<OrderItem> newItems = buildItems(merchantId, request.getItems());
        carryOverExtraSalePrices(order, newItems);
        carryOverItemObservations(order, newItems);

        BigDecimal totalValue = calculateTotalValue(newItems, order.getDeliveryFee(), order.getServiceFee());

        order.setCustomer(customer);
        order.setFee(fee);
        // Uma correção manual do valor final/custo (override, ver updateValues/restoreValues) é
        // uma afirmação explícita do lojista sobre o que o pedido realmente valeu — editar os
        // itens depois não pode desfazê-la silenciosamente. Enquanto o override estiver ativo
        // (valuesOverriddenAt != null), o totalValue recalculado a partir dos itens é
        // descartado e o valor manual gravado no pedido é preservado.
        if (order.getValuesOverriddenAt() == null) {
            order.setTotalValue(totalValue);
        }
        if (request.getStatus() != null) {
            order.setStatus(request.getStatus());
        }
        if (request.getOrigin() != null) {
            order.setOrigin(request.getOrigin());
        }
        newItems.forEach(item -> item.setOrder(order));
        if (order.getItems() == null) {
            order.setItems(new ArrayList<>());
        }
        order.getItems().clear();
        order.getItems().addAll(newItems);

        // A ficha do pedido NÃO é re-snapshotada aqui: o pedido conserva a ficha com que
        // foi criado. Editar um pedido antigo não pode importar a ficha de hoje.

        // Cálculo via service (requer order.items atualizado)
        BigDecimal totalCost = orderCostCalculatorService.computeOrderTotalCost(order);
        // Mesma regra do override acima: custo manual não é sobrescrito pelo recálculo dos itens.
        if (order.getValuesOverriddenAt() == null) {
            order.setTotalCost(totalCost);
        }
        order.setEstimatedProfit(OrderCalculations.calculateEstimatedProfit(order));

        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    /**
     * Correção manual do valor final ({@code totalValue}) e do custo total ({@code totalCost})
     * do pedido. Na primeira chamada (sem override ativo) tira um snapshot dos valores
     * calculados pelo sistema antes de aplicar os novos — o snapshot é a referência do que o
     * pedido "deveria" valer, e nunca é sobrescrito por correções seguintes.
     */
    @Transactional
    public OrderResponse updateValues(UUID merchantId, UUID id, OrderValuesRequest request) {
        Order order = orderRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (order.getValuesOverriddenAt() == null) {
            snapshotOriginalValues(order);
        }

        order.setTotalValue(request.getTotalValue());
        order.setTotalCost(request.getTotalCost());
        order.setEstimatedProfit(OrderCalculations.calculateEstimatedProfit(order));

        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    /**
     * Grava em {@code originalTotalValue}/{@code originalTotalCost}/{@code originalEstimatedProfit}
     * os valores efetivos do pedido ANTES da primeira correção manual, resolvendo
     * {@code totalCost} da mesma forma que {@link #toResponse}: usa o valor persistido, ou o
     * fallback legado ({@code OrderCalculations.calculateTotalCost(items) + orderFichaCost})
     * quando ele é nulo — assim um pedido legado também ganha um baseline com sentido.
     */
    private void snapshotOriginalValues(Order order) {
        List<OrderItem> items = order.getItems() != null ? order.getItems() : List.of();
        BigDecimal orderFichaCost = orderCostCalculatorService.computeOrderFichaCost(order);
        if (orderFichaCost == null) orderFichaCost = BigDecimal.ZERO;
        BigDecimal resolvedTotalCost = order.getTotalCost() != null
                ? order.getTotalCost()
                : OrderCalculations.calculateTotalCost(items).add(orderFichaCost);

        Fee fee = order.getFee();
        BigDecimal resolvedEstimatedProfit = OrderCalculations.calculateEstimatedProfit(
                order.getTotalValue(), order.getDeliveryFee(), order.getServiceFee(), resolvedTotalCost,
                fee != null ? fee.getFeeRate() : null);

        order.setOriginalTotalValue(order.getTotalValue());
        order.setOriginalTotalCost(resolvedTotalCost);
        order.setOriginalEstimatedProfit(resolvedEstimatedProfit);
        order.setValuesOverriddenAt(LocalDateTime.now(BRAZIL_ZONE));
    }

    /**
     * Restaura {@code totalValue}/{@code totalCost} do snapshot original e apaga a correção
     * manual. Pedido nunca corrigido manualmente ({@code valuesOverriddenAt == null}) é um
     * no-op: nada é gravado.
     */
    @Transactional
    public OrderResponse restoreValues(UUID merchantId, UUID id) {
        Order order = orderRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (order.getValuesOverriddenAt() == null) {
            return toResponse(order);
        }

        order.setTotalValue(order.getOriginalTotalValue());
        order.setTotalCost(order.getOriginalTotalCost());
        order.setEstimatedProfit(OrderCalculations.calculateEstimatedProfit(order));
        order.setOriginalTotalValue(null);
        order.setOriginalTotalCost(null);
        order.setOriginalEstimatedProfit(null);
        order.setValuesOverriddenAt(null);

        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID merchantId, UUID id) {
        if (!orderRepository.existsByIdAndMerchantId(id, merchantId)) {
            throw new OrderNotFoundException(id);
        }
        orderRepository.deleteByIdAndMerchantId(id, merchantId);
    }

    private List<OrderItem> buildItems(UUID merchantId, List<OrderItemRequest> itemRequests) {
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest itemRequest : itemRequests) {
            Product product = productRepository.findByIdAndMerchantId(itemRequest.getProductId(), merchantId)
                    .orElseThrow(() -> new OrderNotFoundException(
                            "Produto com ID " + itemRequest.getProductId() + " não encontrado"));

            // Pedido manual: a ficha técnica completa acompanha o produto por padrão;
            // o operador desmarca os insumos que ficaram de fora (excludedIncludeIds).
            Set<UUID> excludedIncludeIds = itemRequest.getExcludedIncludeIds() != null
                    ? new java.util.HashSet<>(itemRequest.getExcludedIncludeIds())
                    : new java.util.HashSet<>();
            BigDecimal unitCost = ProductCostCalculator.computeSelectedCost(
                    includeRepository.findByProductIdAndProductMerchantId(product.getId(), merchantId),
                    excludedIncludeIds);

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .unitCost(unitCost)
                    // Snapshot da margem ideal: o item conserva o alvo vigente no produto
                    // quando o pedido foi feito, mesmo que o produto mude depois.
                    .targetMarginPct(product.getTargetMarginPct())
                    .excludedIncludeIds(excludedIncludeIds)
                    .build();

            List<OrderItemExtraIngredient> extraIngredients = buildExtraIngredients(merchantId, itemRequest);
            extraIngredients.forEach(extra -> extra.setOrderItem(item));
            item.setExtraIngredients(extraIngredients);

            items.add(item);
        }
        return items;
    }

    private List<OrderItemExtraIngredient> buildExtraIngredients(UUID merchantId, OrderItemRequest itemRequest) {
        List<OrderItemExtraIngredient> extraIngredients = new ArrayList<>();
        if (itemRequest.getExtraIngredients() == null || itemRequest.getExtraIngredients().isEmpty()) {
            return extraIngredients;
        }

        for (OrderItemExtraIngredientRequest extraRequest : itemRequest.getExtraIngredients()) {
            Ingredient ingredient = ingredientRepository.findByIdAndMerchantId(extraRequest.getIngredientId(), merchantId)
                    .orElseThrow(() -> new IngredientNotFoundException(extraRequest.getIngredientId()));

            OrderItemExtraIngredient extra = OrderItemExtraIngredient.builder()
                    .ingredient(ingredient)
                    .quantity(extraRequest.getQuantity())
                    .costPerUnit(ingredient.getCostPerUnit())
                    .ingredientName(ingredient.getName())
                    .ingredientUnit(ingredient.getUnit())
                    .build();

            extraIngredients.add(extra);
        }

        return extraIngredients;
    }

    /**
     * Resolve o cliente do pedido. Com {@code customerId}, busca o cliente existente
     * (404 quando não encontrado). Com apenas {@code customerName} — fluxo rápido da
     * UI — reutiliza o primeiro cliente do lojista cujo nome case no formato canônico
     * (sem caixa/acentos) ou cria um novo somente com o nome.
     */
    private Customer resolveCustomer(UUID merchantId, OrderRequest request) {
        if (request.getCustomerId() != null) {
            return customerRepository.findByIdAndMerchantId(request.getCustomerId(), merchantId)
                    .orElseThrow(() -> new OrderNotFoundException(
                            "Cliente com ID " + request.getCustomerId() + " não encontrado"));
        }

        String canonicalName = IngredientNameNormalizer.normalize(request.getCustomerName());
        return customerRepository.findAllByMerchantId(merchantId).stream()
                .filter(c -> IngredientNameNormalizer.normalize(c.getName()).equals(canonicalName))
                .findFirst()
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .merchant(merchantRepository.getReferenceById(merchantId))
                        .name(request.getCustomerName().trim())
                        .build()));
    }

    private Fee resolveFee(UUID feeId, UUID merchantId) {
        if (feeId == null) return null;
        return feeRepository.findByIdAndMerchantId(feeId, merchantId)
                .orElseThrow(() -> new OrderNotFoundException(
                        "Taxa com ID " + feeId + " não encontrada"));
    }

    private BigDecimal calculateTotalValue(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Total do pedido = itens + adicionais pagos + taxa de entrega + taxa de serviço.
     * <p>
     * As duas taxas só existem em pedido importado, onde o total do parceiro já as inclui.
     * Somá-las de volta ao recalcular é o que mantém o total igual ao que o cliente pagou —
     * sem isso a edição derrubava o total para a soma dos itens, e como o lucro desconta
     * ambas as taxas ({@link OrderCalculations#calculateEstimatedProfit}), a diferença era
     * cobrada duas vezes. Em pedido manual as taxas são nulas e o total segue a soma dos itens.
     */
    private BigDecimal calculateTotalValue(List<OrderItem> items, BigDecimal deliveryFee, BigDecimal serviceFee) {
        return calculateTotalValue(items)
                .add(sumExtraSalePrices(items))
                .add(deliveryFee != null ? deliveryFee : BigDecimal.ZERO)
                .add(serviceFee != null ? serviceFee : BigDecimal.ZERO);
    }

    /**
     * Receita dos adicionais pagos. Em pedido importado o {@code unitPrice} do item não inclui
     * os subItems cobrados — o valor deles vive em {@code salePriceTotal}. Adicional de pedido
     * manual não tem preço de venda ({@code null}) e não entra na conta.
     */
    private BigDecimal sumExtraSalePrices(List<OrderItem> items) {
        return items.stream()
                .filter(item -> item.getExtraIngredients() != null)
                .flatMap(item -> item.getExtraIngredients().stream())
                .map(OrderItemExtraIngredient::getSalePriceTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Copia o preço de venda dos adicionais do pedido atual para os adicionais reconstruídos
     * a partir do request, casando por ingrediente. O request da UI não carrega preço de venda
     * (adicional de pedido manual não tem), então sem esse repasse uma edição apagaria a receita
     * dos adicionais que vieram do parceiro.
     */
    private void carryOverExtraSalePrices(Order order, List<OrderItem> newItems) {
        if (order.getItems() == null) return;
        Map<UUID, BigDecimal> salePriceByIngredient = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            if (item.getExtraIngredients() == null) continue;
            for (OrderItemExtraIngredient extra : item.getExtraIngredients()) {
                if (extra.getSalePriceTotal() == null || extra.getIngredient() == null) continue;
                salePriceByIngredient.merge(extra.getIngredient().getId(), extra.getSalePriceTotal(),
                        BigDecimal::add);
            }
        }
        if (salePriceByIngredient.isEmpty()) return;
        for (OrderItem item : newItems) {
            if (item.getExtraIngredients() == null) continue;
            for (OrderItemExtraIngredient extra : item.getExtraIngredients()) {
                BigDecimal salePrice = salePriceByIngredient.remove(extra.getIngredient().getId());
                if (salePrice != null) {
                    extra.setSalePriceTotal(salePrice);
                }
            }
        }
    }

    /**
     * Copia a observação importada de cada item ("sem cebola") para os itens reconstruídos a
     * partir do request, casando por produto. O request da UI não carrega observação, então
     * sem esse repasse uma edição apagaria a instrução do cliente — que a homologação do
     * iFood exige exibir na comanda.
     */
    private void carryOverItemObservations(Order order, List<OrderItem> newItems) {
        if (order.getItems() == null) return;
        Map<UUID, String> observationsByProduct = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            if (item.getObservations() == null || item.getProduct() == null) continue;
            observationsByProduct.putIfAbsent(item.getProduct().getId(), item.getObservations());
        }
        if (observationsByProduct.isEmpty()) return;
        for (OrderItem item : newItems) {
            if (item.getObservations() != null || item.getProduct() == null) continue;
            item.setObservations(observationsByProduct.get(item.getProduct().getId()));
        }
    }

    private OrderResponse toResponse(Order order) {
        return toResponse(order, null, null);
    }

    /**
     * @param includesByProduct            includes already loaded for the page, or {@code null}
     *                                     to fetch them per product (single order).
     * @param pageRegisteredCanonicalNames canonical names already resolved for the page, or
     *                                     {@code null} to resolve them from this order alone.
     */
    private OrderResponse toResponse(Order order, Map<UUID, List<Include>> includesByProduct,
                                     Set<String> pageRegisteredCanonicalNames) {
        List<OrderItem> items = order.getItems() != null ? order.getItems() : List.of();
        UUID orderMerchantId = order.getMerchant().getId();
        Set<String> registeredCanonicalNames = pageRegisteredCanonicalNames != null
                ? pageRegisteredCanonicalNames
                : registeredCanonicalNamesFor(items, orderMerchantId);
        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> toItemResponse(item, orderMerchantId, includesByProduct, order.getOrigin(),
                        registeredCanonicalNames))
                .toList();

        List<OrderFichaIngredientResponse> orderFichaResponses = toOrderFichaResponses(order);
        BigDecimal orderFichaCost = orderCostCalculatorService.computeOrderFichaCost(order);
        if (orderFichaCost == null) orderFichaCost = BigDecimal.ZERO;

        Fee fee = order.getFee();
        // totalCost: snapshot persistido tem prioridade; pedidos antigos (null) caem no fallback legado.
        // O fallback soma a ficha do pedido para não perder essa parcela caso o snapshot de
        // totalCost falte — pedidos pré-V17 não têm ficha, então continuam idênticos.
        BigDecimal totalCost = order.getTotalCost() != null
                ? order.getTotalCost()
                : OrderCalculations.calculateTotalCost(items).add(orderFichaCost);
        // Lucro: recalcula sempre a partir dos valores do pedido para refletir a fórmula atual,
        // usando o totalCost resolvido acima (snapshot ou fallback) e a taxa de meio de pagamento.
        BigDecimal estimatedProfit = OrderCalculations.calculateEstimatedProfit(
                order.getTotalValue(), order.getDeliveryFee(), order.getServiceFee(), totalCost,
                fee != null ? fee.getFeeRate() : null);

        return OrderResponse.builder()
                .id(order.getId())
                .dateTime(order.getDateTime())
                .customerId(order.getCustomer().getId())
                .customerName(order.getCustomer().getName())
                .status(order.getStatus())
                .totalValue(order.getTotalValue())
                .estimatedProfit(estimatedProfit)
                .deliveryFee(order.getDeliveryFee())
                .serviceFee(order.getServiceFee())
                .totalCost(totalCost)
                .feeId(fee != null ? fee.getId() : null)
                .feeName(fee != null ? fee.getName() : null)
                .feeRate(fee != null ? fee.getFeeRate() : null)
                .items(itemResponses)
                .origin(order.getOrigin())
                .marginPct(computeMarginPct(estimatedProfit, order.getTotalValue(),
                        order.getDeliveryFee(), order.getServiceFee()))
                .orderFicha(orderFichaResponses)
                .orderFichaCost(orderFichaCost)
                // Dados descritivos do marketplace (homologação iFood): nulos em pedidos
                // manuais e em pedidos importados antes da V27.
                .displayId(order.getDisplayId())
                .orderType(order.getOrderType())
                .orderTiming(order.getOrderTiming())
                .customerDocument(order.getCustomerDocument())
                .paymentPrepaidAmount(order.getPaymentPrepaidAmount())
                .paymentPendingAmount(order.getPaymentPendingAmount())
                .paymentMethods(toPaymentMethodResponses(order))
                .discountTotal(order.getDiscountTotal())
                .discountIfoodValue(order.getDiscountIfoodValue())
                .discountMerchantValue(order.getDiscountMerchantValue())
                .deliveryMode(order.getDeliveryMode())
                .deliveredBy(order.getDeliveredBy())
                .deliveryDateTime(order.getDeliveryDateTime())
                .deliveryObservations(order.getDeliveryObservations())
                .pickupCode(order.getPickupCode())
                .takeoutMode(order.getTakeoutMode())
                .takeoutDateTime(order.getTakeoutDateTime())
                // Snapshot da correção manual de totalValue/totalCost. Nulos em todo pedido
                // que nunca foi corrigido manualmente pelo lojista.
                .originalTotalValue(order.getOriginalTotalValue())
                .originalTotalCost(order.getOriginalTotalCost())
                .originalEstimatedProfit(order.getOriginalEstimatedProfit())
                .valuesOverriddenAt(order.getValuesOverriddenAt())
                .build();
    }

    /**
     * Meios de pagamento do pedido. O troco ({@code changeAmount = changeFor − value}) é
     * calculado aqui para que toda tela mostre o mesmo valor — exigência da homologação do
     * módulo Order do iFood.
     */
    private List<OrderPaymentMethodResponse> toPaymentMethodResponses(Order order) {
        if (order.getPaymentMethods() == null) {
            return List.of();
        }
        return order.getPaymentMethods().stream()
                .map(method -> OrderPaymentMethodResponse.builder()
                        .id(method.getId())
                        .method(method.getMethod())
                        .type(method.getType())
                        .cardBrand(method.getCardBrand())
                        .value(method.getValue())
                        .currency(method.getCurrency())
                        .changeFor(method.getChangeFor())
                        .changeAmount(computeChangeAmount(method))
                        .build())
                .toList();
    }

    /** Troco devido ao cliente. Null quando não há {@code changeFor} (pagamento sem troco). */
    private BigDecimal computeChangeAmount(OrderPaymentMethod method) {
        BigDecimal changeFor = method.getChangeFor();
        if (changeFor == null) {
            return null;
        }
        BigDecimal paid = method.getValue() != null ? method.getValue() : BigDecimal.ZERO;
        BigDecimal change = changeFor.subtract(paid);
        return change.signum() < 0 ? BigDecimal.ZERO : change;
    }

    /**
     * Linhas da ficha do pedido gravadas NO pedido. Sempre do snapshot — nunca da
     * configuração atual do lojista —, para que o detalhe mostre o que foi cobrado.
     */
    private List<OrderFichaIngredientResponse> toOrderFichaResponses(Order order) {
        if (order.getOrderFicha() == null) {
            return List.of();
        }
        return order.getOrderFicha().stream()
                .map(line -> {
                    BigDecimal quantity = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
                    BigDecimal costPerUnit = line.getCostPerUnit() != null ? line.getCostPerUnit() : BigDecimal.ZERO;
                    return OrderFichaIngredientResponse.builder()
                            .id(line.getId())
                            .ingredientId(line.getIngredient() != null ? line.getIngredient().getId() : null)
                            .ingredientName(line.getIngredientName())
                            .ingredientUnit(line.getIngredientUnit())
                            .quantity(quantity)
                            .costPerUnit(costPerUnit)
                            .totalCost(quantity.multiply(costPerUnit))
                            .build();
                })
                .toList();
    }

    /**
     * Margem (%) do pedido sobre o subtotal dos produtos ({@code totalValue − deliveryFee −
     * serviceFee}), mesma base usada no cálculo do lucro. A taxa de entrega e a taxa de serviço
     * são excluídas do denominador porque já estão excluídas do numerador.
     */
    private BigDecimal computeMarginPct(BigDecimal profit, BigDecimal totalValue,
                                        BigDecimal deliveryFee, BigDecimal serviceFee) {
        BigDecimal base = OrderCalculations.calculateProductsSubtotal(totalValue, deliveryFee, serviceFee);
        if (base.signum() == 0 || profit == null) {
            return null;
        }
        return profit
                .divide(base, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Canonical names of unmatched subItems that ALREADY exist as an ingredient of the
     * merchant. A single query covers every item received — a single order or a whole page —
     * and drives which "register ingredient" buttons are no longer needed (the ingredient
     * has been created). Empty when there are no unmatched subItems, skipping the query.
     */
    private Set<String> registeredCanonicalNamesFor(List<OrderItem> items, UUID merchantId) {
        Set<String> canonicalNames = items.stream()
                .filter(item -> item.getUnmatchedSubItems() != null)
                .flatMap(item -> item.getUnmatchedSubItems().stream())
                .map(OrderItemUnmatchedSubItem::getCanonicalName)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (canonicalNames.isEmpty()) {
            return Set.of();
        }
        return new java.util.HashSet<>(
                ingredientRepository.findExistingCanonicalNames(merchantId, canonicalNames));
    }

    private OrderItemResponse toItemResponse(OrderItem item, UUID merchantId, Map<UUID, List<Include>> includesByProduct,
                                             OrderOrigin origin, Set<String> registeredCanonicalNames) {
        List<OrderItemExtraIngredientResponse> extraResponses = item.getExtraIngredients() != null
                ? item.getExtraIngredients().stream().map(extra -> {
                    BigDecimal totalCost = extra.getQuantity()
                            .multiply(extra.getCostPerUnit())
                            .multiply(BigDecimal.valueOf(item.getQuantity()));

                    // Preço pago é repassado como foi gravado, sem multiplicar por
                    // item.quantity: o total do pedido já embute os valores dos subItems.
                    return OrderItemExtraIngredientResponse.builder()
                            .id(extra.getId())
                            .ingredientId(extra.getIngredient().getId())
                            .ingredientName(extra.getIngredientName())
                            .ingredientUnit(extra.getIngredientUnit())
                            .quantity(extra.getQuantity())
                            .costPerUnit(extra.getCostPerUnit())
                            .totalCost(totalCost)
                            .salePricePerUnit(extra.getSalePricePerUnit())
                            .salePriceTotal(extra.getSalePriceTotal())
                            .build();
                }).toList()
                : List.of();

        // Insumos = Includes da ficha técnica do produto (snapshot atual).
        // Pedido manual (JETMENU/legado sem origem): PACKAGING + legados sem kind menos
        // os insumos desmarcados pelo operador (excludedIncludeIds). INGREDIENT nunca é
        // puxado — só conta quando pedido como extra.
        // Pedido importado (AnotaAI/iFood): apenas PACKAGING — ingredientes escolhidos
        // chegam via extraIngredients (subItems).
        List<Include> productIncludes = includesByProduct != null
                ? includesByProduct.getOrDefault(item.getProduct().getId(), List.of())
                : includeRepository.findByProductIdAndProductMerchantId(item.getProduct().getId(), merchantId);
        Set<UUID> excludedIncludeIds = item.getExcludedIncludeIds() != null
                ? item.getExcludedIncludeIds()
                : Set.of();
        boolean manualOrder = origin == null || origin == OrderOrigin.JETMENU;
        List<IncludeResponse> insumos = productIncludes.stream()
                .filter(inc -> manualOrder
                        ? inc.getKind() != IncludeKind.INGREDIENT
                        : inc.getKind() == IncludeKind.PACKAGING)
                .filter(inc -> inc.getId() == null || !excludedIncludeIds.contains(inc.getId()))
                .map(this::toIncludeResponse)
                .toList();

        // SubItems não-casados: expostos apenas enquanto não houver um ingrediente com o
        // mesmo nome canônico. Assim que o lojista cadastra o ingrediente, o registro é
        // filtrado e o botão de cadastro some no próximo carregamento do pedido.
        List<OrderItemUnmatchedSubItemResponse> unmatchedResponses = item.getUnmatchedSubItems() != null
                ? item.getUnmatchedSubItems().stream()
                    .filter(u -> u.getCanonicalName() == null
                            || !registeredCanonicalNames.contains(u.getCanonicalName()))
                    .map(u -> OrderItemUnmatchedSubItemResponse.builder()
                            .id(u.getId())
                            .rawName(u.getRawName())
                            .quantity(u.getQuantity())
                            .salePricePerUnit(u.getSalePricePerUnit())
                            .salePriceTotal(u.getSalePriceTotal())
                            .build())
                    .toList()
                : List.of();

        // Modelo aditivo: ficha técnica (item.unitCost = mandatory base) + extras.
        BigDecimal unitCost = orderCostCalculatorService.computeItemUnitCost(item, merchantId);
        if (unitCost == null) unitCost = BigDecimal.ZERO;
        BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(item.getQuantity()));

        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .unitCost(unitCost)
                .totalCost(totalCost)
                .targetMarginPct(item.getTargetMarginPct())
                // Margem realizada no item, sobre o valor cobrado (produto + adicionais pagos)
                // e o custo total — os dois lados precisam contar os extras, senão o custo do
                // adicional é descontado de uma receita que não o inclui.
                .marginPct(ProductCostCalculator.computeMarginPct(
                        OrderCalculations.calculateItemChargedValue(item), totalCost))
                .observations(item.getObservations())
                .insumos(insumos)
                .extraIngredients(extraResponses)
                .unmatchedSubItems(unmatchedResponses)
                .excludedIncludeIds(List.copyOf(excludedIncludeIds))
                .build();
    }

    private IncludeResponse toIncludeResponse(Include include) {
        BigDecimal cost = include.getCost() != null ? include.getCost() : BigDecimal.ZERO;
        BigDecimal quantity = include.getQuantity() != null ? include.getQuantity() : BigDecimal.ONE;
        return IncludeResponse.builder()
                .id(include.getId())
                .productId(include.getProduct().getId())
                .name(include.getName())
                .cost(cost)
                .quantity(quantity)
                .totalCost(cost.multiply(quantity))
                .build();
    }
}



