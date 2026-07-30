package com.jetmenu.integration.ifood.services;

import com.jetmenu.customer.Customer;
import com.jetmenu.customer.CustomerRepository;
import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientNameNormalizer;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.integration.ifood.dto.IfoodOrderDetailResponse;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadService;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.notification.NotificationService;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderCalculations;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderItemExtraIngredient;
import com.jetmenu.order.OrderItemUnmatchedSubItem;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderPaymentMethod;
import com.jetmenu.order.OrderTiming;
import com.jetmenu.order.OrderType;
import com.jetmenu.order.ResolvedSubItems;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import com.jetmenu.product.Include;
import com.jetmenu.product.IncludeKind;
import com.jetmenu.product.IncludeRepository;
import com.jetmenu.product.OrderCostCalculatorService;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductCostCalculator;
import com.jetmenu.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Importa pedidos do iFood a partir dos eventos do ciclo de vida (CONFIRMED, CONCLUDED,
 * CANCELLED), persistindo {@link Order}, {@link OrderItem}s e {@link OrderItemExtraIngredient}s,
 * e aplica as transições de status em pedidos já importados. Espelha o fluxo do
 * AnotaAIOrderImportService: produtos resolvidos por externalCode com fallback de nome
 * canônico, complementos (options) resolvidos contra o catálogo de ingredientes por nome
 * canônico.
 *
 * <p>Regras de transição: CANCELLED sempre vence (inclusive sobre PAID) e nunca é revertido;
 * eventos repetidos são idempotentes.
 */
@Service
public class IfoodOrderImportService {

    private static final Logger log = LoggerFactory.getLogger(IfoodOrderImportService.class);

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String FOOD_CATEGORY = "FOOD";

    /** Sponsor names in {@code benefits[].sponsorshipValues[].name}. */
    private static final String SPONSOR_IFOOD = "IFOOD";
    private static final String SPONSOR_MERCHANT = "MERCHANT";

    private final MerchantRepository merchantRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final IngredientRepository ingredientRepository;
    private final IncludeRepository includeRepository;
    private final NotificationService notificationService;
    private final OrderCostCalculatorService orderCostCalculatorService;
    private final ExternalOrderRawPayloadService rawPayloadService;
    private final com.jetmenu.order.OrderFichaService orderFichaService;

    public IfoodOrderImportService(MerchantRepository merchantRepository,
                                   OrderRepository orderRepository,
                                   CustomerRepository customerRepository,
                                   ProductRepository productRepository,
                                   IngredientRepository ingredientRepository,
                                   IncludeRepository includeRepository,
                                   NotificationService notificationService,
                                   OrderCostCalculatorService orderCostCalculatorService,
                                   ExternalOrderRawPayloadService rawPayloadService,
                                   com.jetmenu.order.OrderFichaService orderFichaService) {
        this.merchantRepository = merchantRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.ingredientRepository = ingredientRepository;
        this.includeRepository = includeRepository;
        this.notificationService = notificationService;
        this.orderCostCalculatorService = orderCostCalculatorService;
        this.rawPayloadService = rawPayloadService;
        this.orderFichaService = orderFichaService;
    }

    /**
     * @param status status inicial derivado do evento que originou o import
     *               (CONFIRMED → PENDING, CONCLUDED → PAID, CANCELLED → CANCELLED)
     * @param rawJson corpo JSON bruto retornado pelo iFood, persistido para auditoria
     * @return {@code true} se o pedido foi importado; {@code false} se foi pulado
     *         (categoria não-FOOD, pedido de teste, merchant desconhecido ou duplicado).
     */
    @Transactional
    public boolean importOrder(IfoodOrderDetailResponse detail, OrderStatus status, String rawJson) {
        if (!FOOD_CATEGORY.equalsIgnoreCase(detail.getCategory())) {
            log.info("[iFood] pedido {} ignorado — category='{}'", detail.getId(), detail.getCategory());
            return false;
        }
        if (detail.isTest()) {
            log.info("[iFood] pedido {} é de teste — importando com status TEST", detail.getId());
            status = OrderStatus.TEST;
        }
        if (detail.getMerchant() == null || detail.getMerchant().getId() == null) {
            log.warn("[iFood] pedido {} ignorado — payload sem merchant.id", detail.getId());
            return false;
        }

        Optional<Merchant> merchantOpt = merchantRepository.findByIfoodMerchantId(detail.getMerchant().getId());
        if (merchantOpt.isEmpty()) {
            log.warn("[iFood] pedido {} ignorado — merchant iFood '{}' não autorizado/desconhecido",
                    detail.getId(), detail.getMerchant().getId());
            return false;
        }
        UUID merchantId = merchantOpt.get().getId();

        if (orderRepository.existsByExternalOrderIdAndMerchantId(detail.getId(), merchantId)) {
            log.info("[iFood] pedido {} já importado — ignorando", detail.getId());
            return false;
        }

        Customer customer = resolveCustomer(detail.getCustomer(), merchantId);
        List<OrderItem> items = buildItems(detail, merchantId);

        IfoodOrderDetailResponse.Total total = detail.getTotal();
        BigDecimal totalValue = total != null && total.getOrderAmount() != null
                ? total.getOrderAmount() : BigDecimal.ZERO;
        BigDecimal deliveryFee = total != null ? total.getDeliveryFee() : null;

        Order order = Order.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .dateTime(parseCreatedAt(detail.getCreatedAt()))
                .customer(customer)
                .fee(null)
                .status(status)
                .totalValue(totalValue)
                .deliveryFee(deliveryFee)
                .origin(OrderOrigin.IFOOD)
                .externalOrderId(detail.getId())
                .extraInfo(detail.getExtraInfo())
                .items(items)
                .build();

        items.forEach(item -> item.setOrder(order));
        applyOrderDetails(order, detail);

        // Ficha do pedido: insumos cobrados UMA vez por pedido (sacola, guardanapo),
        // independentemente da quantidade de itens. Vazia = custo inalterado.
        List<com.jetmenu.order.OrderFichaIngredient> orderFicha =
                orderFichaService.buildSnapshot(merchantId);
        orderFicha.forEach(line -> line.setOrder(order));
        order.setOrderFicha(orderFicha);

        BigDecimal totalCost = orderCostCalculatorService.computeOrderTotalCost(order);
        order.setTotalCost(totalCost);
        order.setEstimatedProfit(OrderCalculations.calculateEstimatedProfit(order));

        orderRepository.save(order);
        rawPayloadService.save(merchantId, OrderOrigin.IFOOD, detail.getId(), rawJson);
        return true;
    }

    /**
     * Solidifica um pedido já importado quando o CONCLUDED chega: PENDING → PAID.
     * Não reverte CANCELLED e é idempotente para pedidos já PAID.
     *
     * @return {@code true} se o pedido existe localmente (evento tratado);
     *         {@code false} para acionar o import completo como fallback.
     */
    @Transactional
    public boolean concludeOrder(String externalOrderId, String ifoodMerchantId) {
        Optional<Order> orderOpt = findExistingOrder(externalOrderId, ifoodMerchantId);
        if (orderOpt.isEmpty()) {
            return false;
        }

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("[iFood] CONCLUDED ignorado — pedido {} já está CANCELLED", externalOrderId);
            return true;
        }
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.TEST) {
            return true;
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        log.info("[iFood] pedido {} solidificado — status PAID", externalOrderId);
        return true;
    }

    /**
     * Cancela um pedido já importado e notifica o lojista ({@code ORDER_CANCELLED}).
     * CANCELLED vence sobre qualquer status (inclusive PAID) e a operação é idempotente:
     * pedido já cancelado não gera nova notificação.
     *
     * @return {@code true} se o pedido existe localmente (evento tratado);
     *         {@code false} para acionar o import completo como fallback.
     */
    @Transactional
    public boolean cancelOrder(String externalOrderId, String ifoodMerchantId) {
        Optional<Order> orderOpt = findExistingOrder(externalOrderId, ifoodMerchantId);
        if (orderOpt.isEmpty()) {
            return false;
        }

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.TEST) {
            return true;
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        notificationService.createOrderCancelled(externalOrderId, null, order.getMerchant().getId());
        log.info("[iFood] pedido {} cancelado — removido dos ganhos", externalOrderId);
        return true;
    }

    /**
     * Registra a solicitação de cancelamento feita pelo cliente (ou pela plataforma) e notifica
     * o lojista — que ainda precisa aceitar ou recusar via
     * {@code POST /api/integrations/ifood/orders/{id}/cancellation-request/{accept,deny}}.
     *
     * <p>O status do pedido <strong>não</strong> muda aqui: uma solicitação sem resposta não
     * pode tirar o pedido dos ganhos nem deixar o estado local dessincronizado do iFood. Só o
     * aceite (ou o evento CANCELLED que vem depois) cancela o pedido de fato.
     *
     * <p>Pedido já CANCELLED ou TEST não gera notificação — não há nada a responder.
     *
     * @return {@code true} se o pedido existe localmente (evento tratado);
     *         {@code false} para acionar o import completo como fallback.
     */
    @Transactional
    public boolean registerCancellationRequest(String externalOrderId, String ifoodMerchantId) {
        Optional<Order> orderOpt = findExistingOrder(externalOrderId, ifoodMerchantId);
        if (orderOpt.isEmpty()) {
            return false;
        }

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.TEST) {
            log.info("[iFood] solicitação de cancelamento do pedido {} ignorada — status {}",
                    externalOrderId, order.getStatus());
            return true;
        }

        notificationService.createOrderCancellationRequested(
                order.getId(), externalOrderId, order.getMerchant().getId());
        log.info("[iFood] cliente solicitou o cancelamento do pedido {} — lojista notificado",
                externalOrderId);
        return true;
    }

    private Optional<Order> findExistingOrder(String externalOrderId, String ifoodMerchantId) {
        return merchantRepository.findByIfoodMerchantId(ifoodMerchantId)
                .flatMap(merchant -> orderRepository.findByExternalOrderIdAndMerchantId(
                        externalOrderId, merchant.getId()));
    }

    /**
     * Copia os dados descritivos exigidos pela homologação do módulo Order do iFood:
     * identificação amigável, tipo/timing, CPF/CNPJ da nota, breakdown de pagamento
     * (bandeira do cartão e troco), cupom com o rateio entre iFood e Loja, observações de
     * entrega/retirada e código de coleta.
     *
     * <p>Tudo aqui é apenas para exibição — nenhum destes campos entra no cálculo de
     * {@code totalValue}, {@code deliveryFee}, {@code serviceFee}, {@code totalCost} ou
     * {@code estimatedProfit}. Em particular, o desconto é gravado para ser mostrado na
     * comanda, nunca deduzido de nenhum valor.
     */
    private void applyOrderDetails(Order order, IfoodOrderDetailResponse detail) {
        order.setDisplayId(detail.getDisplayId());
        order.setOrderType(OrderType.fromExternal(detail.getOrderType()));
        order.setOrderTiming(OrderTiming.fromExternal(detail.getOrderTiming()));
        if (detail.getCustomer() != null) {
            order.setCustomerDocument(detail.getCustomer().getDocumentNumber());
        }

        applyPayments(order, detail.getPayments());
        applyBenefits(order, detail.getBenefits());

        IfoodOrderDetailResponse.Delivery delivery = detail.getDelivery();
        if (delivery != null) {
            order.setDeliveryMode(delivery.getMode());
            order.setDeliveredBy(delivery.getDeliveredBy());
            order.setDeliveryDateTime(parseDateTime(delivery.getDeliveryDateTime()));
            order.setDeliveryObservations(delivery.getObservations());
            order.setPickupCode(delivery.getPickupCode());
        }

        IfoodOrderDetailResponse.Takeout takeout = detail.getTakeout();
        if (takeout != null) {
            order.setTakeoutMode(takeout.getMode());
            order.setTakeoutDateTime(parseDateTime(takeout.getTakeoutDateTime()));
        }
    }

    private void applyPayments(Order order, IfoodOrderDetailResponse.Payments payments) {
        List<OrderPaymentMethod> methods = new ArrayList<>();
        order.setPaymentMethods(methods);
        if (payments == null) return;

        order.setPaymentPrepaidAmount(payments.getPrepaid());
        order.setPaymentPendingAmount(payments.getPending());
        if (payments.getMethods() == null) return;

        for (IfoodOrderDetailResponse.PaymentMethod remoteMethod : payments.getMethods()) {
            if (remoteMethod == null) continue;
            methods.add(OrderPaymentMethod.builder()
                    .order(order)
                    .method(remoteMethod.getMethod())
                    .type(remoteMethod.getType())
                    .currency(remoteMethod.getCurrency())
                    .value(remoteMethod.getValue())
                    .cardBrand(remoteMethod.getCard() != null ? remoteMethod.getCard().getBrand() : null)
                    .changeFor(remoteMethod.getCash() != null ? remoteMethod.getCash().getChangeFor() : null)
                    .build());
        }
    }

    /**
     * Soma o valor dos cupons e o rateio por patrocinador. O iFood pode enviar vários
     * benefícios (carrinho, frete, item) e cada um com mais de um patrocinador; a comanda
     * precisa do total e de quanto cada lado bancou.
     */
    private void applyBenefits(Order order, List<IfoodOrderDetailResponse.Benefit> benefits) {
        if (benefits == null || benefits.isEmpty()) return;

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal ifoodShare = BigDecimal.ZERO;
        BigDecimal merchantShare = BigDecimal.ZERO;

        for (IfoodOrderDetailResponse.Benefit benefit : benefits) {
            if (benefit == null) continue;
            if (benefit.getValue() != null) {
                total = total.add(benefit.getValue());
            }
            if (benefit.getSponsorshipValues() == null) continue;
            for (IfoodOrderDetailResponse.SponsorshipValue sponsor : benefit.getSponsorshipValues()) {
                if (sponsor == null || sponsor.getValue() == null || sponsor.getName() == null) continue;
                if (SPONSOR_IFOOD.equalsIgnoreCase(sponsor.getName())) {
                    ifoodShare = ifoodShare.add(sponsor.getValue());
                } else if (SPONSOR_MERCHANT.equalsIgnoreCase(sponsor.getName())) {
                    merchantShare = merchantShare.add(sponsor.getValue());
                } else {
                    log.info("[iFood] patrocinador de cupom desconhecido '{}' — valor não rateado",
                            sponsor.getName());
                }
            }
        }

        order.setDiscountTotal(total);
        order.setDiscountIfoodValue(ifoodShare);
        order.setDiscountMerchantValue(merchantShare);
    }

    private List<OrderItem> buildItems(IfoodOrderDetailResponse detail, UUID merchantId) {
        List<OrderItem> items = new ArrayList<>();
        if (detail.getItems() == null) return items;

        for (IfoodOrderDetailResponse.Item remoteItem : detail.getItems()) {
            Optional<Product> productOpt = resolveProduct(remoteItem, merchantId);
            if (productOpt.isEmpty()) {
                log.warn("[iFood] pulando item — produto não encontrado: externalCode='{}' name='{}'",
                        remoteItem.getExternalCode(), remoteItem.getName());
                notifyMissingProduct(remoteItem.getName(), merchantId);
                continue;
            }

            Product product = productOpt.get();
            List<Include> productIncludes =
                    includeRepository.findByProductIdAndProductMerchantId(product.getId(), merchantId);
            BigDecimal unitCost = ProductCostCalculator.computeOrderBaseCost(productIncludes);

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(remoteItem.getQuantity() != null ? remoteItem.getQuantity().intValue() : 1)
                    .unitPrice(remoteItem.getUnitPrice() != null ? remoteItem.getUnitPrice() : BigDecimal.ZERO)
                    .unitCost(unitCost)
                    // Snapshot da margem ideal vigente no produto no momento da importação.
                    .targetMarginPct(product.getTargetMarginPct())
                    .observations(remoteItem.getObservations())
                    .build();

            ResolvedSubItems resolved =
                    resolveExtras(remoteItem.getOptions(), productIncludes, merchantId);
            resolved.extras().forEach(extra -> extra.setOrderItem(item));
            item.setExtraIngredients(resolved.extras());
            resolved.unmatched().forEach(unmatched -> unmatched.setOrderItem(item));
            item.setUnmatchedSubItems(resolved.unmatched());
            items.add(item);
        }
        return items;
    }

    /**
     * Resolve o produto primeiro por {@code externalCode} (código PDV) e, sem match,
     * por nome canônico — o iFood não garante código PDV configurado por item.
     */
    private Optional<Product> resolveProduct(IfoodOrderDetailResponse.Item remoteItem, UUID merchantId) {
        String externalCode = remoteItem.getExternalCode();
        if (externalCode != null && !externalCode.isBlank()) {
            Optional<Product> byCode = productRepository.findByExternalIdAndMerchantId(externalCode, merchantId);
            if (byCode.isPresent()) return byCode;
        }
        String canonical = IngredientNameNormalizer.normalize(remoteItem.getName());
        if (canonical.isEmpty()) return Optional.empty();
        return productRepository.findByCanonicalNameAndMerchantId(canonical, merchantId);
    }

    private void notifyMissingProduct(String rawName, UUID merchantId) {
        if (rawName == null || rawName.isBlank()) return;
        notificationService.createMissingProduct(
                rawName, IngredientNameNormalizer.normalize(rawName), merchantId);
    }

    /**
     * Espelha o AnotaAIExtraIngredientResolver: options que casam com um Include
     * PACKAGING já estão na base do produto e não viram extra; as demais são
     * resolvidas contra o catálogo de ingredientes por nome canônico. Sem match,
     * gera notificação MISSING_INGREDIENT e o complemento é pulado.
     */
    private ResolvedSubItems resolveExtras(List<IfoodOrderDetailResponse.Option> options,
                                           List<Include> productIncludes,
                                           UUID merchantId) {
        List<OrderItemExtraIngredient> extras = new ArrayList<>();
        List<OrderItemUnmatchedSubItem> unmatched = new ArrayList<>();
        if (options == null || options.isEmpty()) return new ResolvedSubItems(extras, unmatched);

        Set<String> notifiedMissing = new HashSet<>();

        for (IfoodOrderDetailResponse.Option option : options) {
            String rawName = option.getName();
            if (rawName == null || rawName.isBlank()) continue;
            String canonical = IngredientNameNormalizer.normalize(rawName);

            if (matchesPackagingInclude(productIncludes, canonical)) {
                continue;
            }

            Optional<Ingredient> match = ingredientRepository
                    .findFirstByCanonicalNameAndMerchantIdOrderByIdAsc(canonical, merchantId);
            if (match.isEmpty()) {
                if (notifiedMissing.add(canonical)) {
                    notificationService.createMissingIngredient(rawName, canonical, merchantId);
                }
                // Grava a option não-casada para aparecer no detalhe do pedido com um botão
                // de cadastro, em vez de sumir. Preço/qtd copiados literalmente do payload.
                int quantity = option.getQuantity() != null ? option.getQuantity().intValue() : 1;
                unmatched.add(OrderItemUnmatchedSubItem.builder()
                        .rawName(rawName)
                        .canonicalName(canonical)
                        .quantity(quantity)
                        .salePricePerUnit(option.getUnitPrice())
                        .salePriceTotal(option.getPrice())
                        .build());
                continue;
            }

            Ingredient ingredient = match.get();
            BigDecimal customerQuantity = option.getQuantity() != null ? option.getQuantity() : BigDecimal.ONE;
            BigDecimal perUnitQuantity = resolveQuantityForProduct(productIncludes, canonical, ingredient);

            extras.add(OrderItemExtraIngredient.builder()
                    .ingredient(ingredient)
                    .quantity(perUnitQuantity.multiply(customerQuantity))
                    .costPerUnit(ingredient.getCostPerUnit())
                    .ingredientName(ingredient.getName())
                    .ingredientUnit(ingredient.getUnit())
                    .build());
        }
        return new ResolvedSubItems(extras, unmatched);
    }

    private boolean matchesPackagingInclude(List<Include> productIncludes, String canonical) {
        if (productIncludes == null || productIncludes.isEmpty()) return false;
        return productIncludes.stream()
                .filter(inc -> inc.getKind() == IncludeKind.PACKAGING)
                .anyMatch(inc -> inc.getName() != null
                        && IngredientNameNormalizer.normalize(inc.getName()).equals(canonical));
    }

    private BigDecimal resolveQuantityForProduct(List<Include> productIncludes,
                                                 String canonical,
                                                 Ingredient ingredient) {
        if (productIncludes != null) {
            for (Include inc : productIncludes) {
                if (inc.getKind() == IncludeKind.PACKAGING) continue;
                if (inc.getName() == null || inc.getQuantity() == null) continue;
                if (IngredientNameNormalizer.normalize(inc.getName()).equals(canonical)) {
                    return inc.getQuantity();
                }
            }
        }
        return ingredient.getDefaultQuantity() != null ? ingredient.getDefaultQuantity() : BigDecimal.ONE;
    }

    /**
     * Resolve o cliente primeiro pelo {@code customer.id} do iFood ({@code externalId}) e só
     * depois pelo telefone. O telefone 0800 é o número fixo da central do iFood, compartilhado
     * por todos os clientes (o que distingue é o {@code localizer}, não persistido) — usá-lo
     * como chave fundiria clientes distintos, então ele nunca deduplica nem é persistido.
     */
    private Customer resolveCustomer(IfoodOrderDetailResponse.CustomerInfo remoteCustomer, UUID merchantId) {
        if (remoteCustomer == null) {
            return customerRepository.save(Customer.builder()
                    .merchant(merchantRepository.getReferenceById(merchantId))
                    .name("Cliente iFood")
                    .build());
        }

        String externalId = remoteCustomer.getId();
        if (externalId != null && !externalId.isBlank()) {
            Optional<Customer> byExternalId =
                    customerRepository.findByExternalIdAndMerchantId(externalId, merchantId);
            if (byExternalId.isPresent()) return byExternalId.get();
        }

        String phone = remoteCustomer.getPhone() != null ? remoteCustomer.getPhone().getNumber() : null;
        boolean proxyPhone = isIfoodProxyPhone(phone);
        if (phone != null && !phone.isBlank() && !proxyPhone) {
            Optional<Customer> existing = customerRepository.findByPhoneAndMerchantId(phone, merchantId);
            if (existing.isPresent()) return existing.get();
        }

        return customerRepository.save(Customer.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .name(remoteCustomer.getName() != null ? remoteCustomer.getName() : "Cliente iFood")
                .phone(proxyPhone ? null : phone)
                .externalId(externalId)
                .build());
    }

    private static boolean isIfoodProxyPhone(String phone) {
        if (phone == null) return false;
        return phone.replaceAll("\\D", "").startsWith("0800");
    }

    LocalDateTime parseCreatedAt(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return LocalDateTime.now(BRAZIL_ZONE);
        }
        LocalDateTime parsed = parseDateTime(createdAt);
        if (parsed == null) {
            log.warn("[iFood] createdAt inválido '{}', usando hora atual", createdAt);
            return LocalDateTime.now(BRAZIL_ZONE);
        }
        return parsed;
    }

    /**
     * Converte um timestamp UTC do iFood para a hora local de São Paulo. Diferente de
     * {@link #parseCreatedAt}, um valor ausente ou inválido resulta em {@code null} — estes
     * campos são opcionais e não devem inventar uma data.
     */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value)
                    .atZoneSameInstant(BRAZIL_ZONE)
                    .toLocalDateTime();
        } catch (RuntimeException e) {
            log.warn("[iFood] data/hora inválida '{}' — campo ignorado", value);
            return null;
        }
    }
}
