package com.jetmenu.integration.anotaai.services;

import com.jetmenu.customer.Customer;
import com.jetmenu.fee.Fee;
import com.jetmenu.fee.FeeRepository;
import com.jetmenu.integration.anotaai.AnotaAIOrderDetailResponse;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderCalculations;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderItemExtraIngredient;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import com.jetmenu.product.Include;
import com.jetmenu.product.IncludeRepository;
import com.jetmenu.product.OrderCostCalculatorService;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductCostCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Importa um pedido individual do Anota.AI, persistindo {@link Order}, {@link OrderItem}s
 * e {@link OrderItemExtraIngredient}s. Delega as resoluções de cliente, produto e extras
 * para os resolvers especializados.
 */
public class AnotaAIOrderImportService {

    private static final Logger log = LoggerFactory.getLogger(AnotaAIOrderImportService.class);

    // Anota.AI/iFood enviam createdAt em UTC. Convertemos sempre para o fuso de
    // Brasília — não dependemos do timezone do servidor (em prod/Railway é UTC).
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    private final MerchantRepository merchantRepository;
    private final OrderRepository orderRepository;
    private final FeeRepository feeRepository;
    private final IncludeRepository includeRepository;
    private final OrderCostCalculatorService orderCostCalculatorService;
    private final AnotaAICustomerResolver customerResolver;
    private final AnotaAIProductResolver productResolver;
    private final AnotaAIExtraIngredientResolver extraIngredientResolver;
    private final com.jetmenu.order.OrderFichaService orderFichaService;

    public AnotaAIOrderImportService(MerchantRepository merchantRepository,
                                      OrderRepository orderRepository,
                                      FeeRepository feeRepository,
                                      IncludeRepository includeRepository,
                                      OrderCostCalculatorService orderCostCalculatorService,
                                      AnotaAICustomerResolver customerResolver,
                                      AnotaAIProductResolver productResolver,
                                      AnotaAIExtraIngredientResolver extraIngredientResolver,
                                      com.jetmenu.order.OrderFichaService orderFichaService) {
        this.merchantRepository = merchantRepository;
        this.orderRepository = orderRepository;
        this.feeRepository = feeRepository;
        this.includeRepository = includeRepository;
        this.orderCostCalculatorService = orderCostCalculatorService;
        this.customerResolver = customerResolver;
        this.productResolver = productResolver;
        this.extraIngredientResolver = extraIngredientResolver;
        this.orderFichaService = orderFichaService;
    }

    public void importOrder(AnotaAIOrderDetailResponse.OrderDetail detail,
                             UUID merchantId,
                             OrderOrigin origin,
                             Set<String> missingIngredientNames,
                             Runnable onCatalogSyncRequested,
                             boolean[] catalogSynced) {
        Customer customer = customerResolver.resolve(detail.getCustomer(), merchantId);
        Fee fee = resolveFee(detail.getPayments(), merchantId);

        List<OrderItem> items = new ArrayList<>();
        if (detail.getItems() != null) {
            for (AnotaAIOrderDetailResponse.AnotaAIOrderItem remoteItem : detail.getItems()) {
                Optional<Product> productOpt = productResolver.resolve(remoteItem, merchantId);

                if (productOpt.isEmpty()) {
                    if (!catalogSynced[0]) {
                        log.info("[Anota.AI] produto '{}' / '{}' não encontrado — sincronizando catálogo e tentando novamente",
                                remoteItem.getInternalId(), remoteItem.getName());
                        onCatalogSyncRequested.run();
                        catalogSynced[0] = true;
                        productOpt = productResolver.resolve(remoteItem, merchantId);
                    }
                    if (productOpt.isEmpty()) {
                        log.warn("[Anota.AI] pulando item — produto não encontrado: internalId='{}' name='{}'",
                                remoteItem.getInternalId(), remoteItem.getName());
                        continue;
                    }
                }

                Product product = productOpt.get();
                List<Include> productIncludes =
                        includeRepository.findByProductIdAndProductMerchantId(product.getId(), merchantId);
                BigDecimal unitCost = ProductCostCalculator.computeOrderBaseCost(productIncludes);
                OrderItem item = OrderItem.builder()
                        .product(product)
                        .quantity(remoteItem.getQuantity())
                        .unitPrice(BigDecimal.valueOf(remoteItem.getPrice()))
                        .unitCost(unitCost)
                        // Snapshot da margem ideal vigente no produto no momento da importação.
                        .targetMarginPct(product.getTargetMarginPct())
                        .build();
                com.jetmenu.order.ResolvedSubItems resolved = extraIngredientResolver.resolve(
                        remoteItem.getSubItems(), productIncludes, merchantId, missingIngredientNames);
                resolved.extras().forEach(extra -> extra.setOrderItem(item));
                item.setExtraIngredients(resolved.extras());
                resolved.unmatched().forEach(unmatched -> unmatched.setOrderItem(item));
                item.setUnmatchedSubItems(resolved.unmatched());
                items.add(item);
            }
        }

        BigDecimal deliveryFee = BigDecimal.valueOf(detail.getDeliveryFee());
        // detail.total da Anota.AI já inclui a taxa de entrega (item.total + deliveryFee = detail.total).
        // Usar diretamente — somar deliveryFee inflaria o totalValue em duplicidade.
        BigDecimal totalValue = BigDecimal.valueOf(detail.getTotal());
        // Taxa de serviço repassada ao iFood (additionalFees). Está inclusa no total, mas não é
        // receita do restaurante — persistida para ser deduzida do lucro/margem.
        BigDecimal serviceFee = sumAdditionalFees(detail.getAdditionalFees());

        Order order = Order.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .dateTime(parseCreatedAt(detail.getCreatedAt()))
                .customer(customer)
                .fee(fee)
                .status(OrderStatus.PAID)
                .totalValue(totalValue)
                .deliveryFee(deliveryFee)
                .serviceFee(serviceFee)
                .origin(origin)
                .externalOrderId(detail.getId())
                .items(items)
                .build();

        items.forEach(item -> item.setOrder(order));

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
    }

    LocalDateTime parseCreatedAt(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return LocalDateTime.now(BRAZIL_ZONE);
        }
        try {
            return OffsetDateTime.parse(createdAt)
                    .atZoneSameInstant(BRAZIL_ZONE)
                    .toLocalDateTime();
        } catch (RuntimeException e) {
            log.warn("[Anota.AI] createdAt inválido '{}', usando hora atual", createdAt);
            return LocalDateTime.now(BRAZIL_ZONE);
        }
    }

    /**
     * Soma as taxas adicionais do pedido (additionalFees) — taxas de serviço repassadas ao
     * iFood. Retorna {@code null} quando não há taxas, mantendo a coluna nula em pedidos sem
     * taxa de serviço (canal Anota.AI puro e pedidos anteriores). Todas as additionalFees
     * fazem parte do total mas são repasse, portanto excluídas do lucro do restaurante.
     */
    private BigDecimal sumAdditionalFees(List<AnotaAIOrderDetailResponse.AdditionalFee> additionalFees) {
        if (additionalFees == null || additionalFees.isEmpty()) return null;
        return additionalFees.stream()
                .map(fee -> BigDecimal.valueOf(fee.getValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Fee resolveFee(List<AnotaAIOrderDetailResponse.AnotaAIPayment> payments, UUID merchantId) {
        if (payments == null || payments.isEmpty()) return null;
        String name = payments.get(0).getName();
        if (name == null || name.isBlank()) return null;
        return feeRepository.findByNameIgnoreCaseAndMerchantId(name, merchantId).orElse(null);
    }
}
