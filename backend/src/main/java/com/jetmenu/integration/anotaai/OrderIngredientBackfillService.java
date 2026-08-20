package com.jetmenu.integration.anotaai;

import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientCreatedEvent;
import com.jetmenu.ingredient.IngredientNameNormalizer;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.integration.RawJsonResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderCalculations;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderItemExtraIngredient;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.product.OrderCostCalculatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class OrderIngredientBackfillService {

    private static final Logger log = LoggerFactory.getLogger(OrderIngredientBackfillService.class);

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    private final MerchantRepository merchantRepository;
    private final IngredientRepository ingredientRepository;
    private final OrderRepository orderRepository;
    private final AnotaAIClient anotaAIClient;
    private final OrderCostCalculatorService costCalculatorService;

    public OrderIngredientBackfillService(MerchantRepository merchantRepository,
                                          IngredientRepository ingredientRepository,
                                          OrderRepository orderRepository,
                                          AnotaAIClient anotaAIClient,
                                          OrderCostCalculatorService costCalculatorService) {
        this.merchantRepository = merchantRepository;
        this.ingredientRepository = ingredientRepository;
        this.orderRepository = orderRepository;
        this.anotaAIClient = anotaAIClient;
        this.costCalculatorService = costCalculatorService;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIngredientCreated(IngredientCreatedEvent event) {
        Merchant merchant = merchantRepository.findById(event.merchantId()).orElse(null);
        if (merchant == null) return;

        String apiKey = merchant.getAnotaAiApiKey();
        if (apiKey == null || apiKey.isBlank()) return;

        Ingredient ingredient = ingredientRepository.findById(event.ingredientId()).orElse(null);
        if (ingredient == null) return;

        LocalDate today = LocalDate.now(BRAZIL_ZONE);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        List<Order> orders = orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                event.merchantId(), OrderOrigin.ANOTA_AI, startOfDay, endOfDay);

        log.info("[Backfill] ingrediente='{}' — {} pedidos hoje para verificar", ingredient.getName(), orders.size());

        for (Order order : orders) {
            try {
                backfillOrder(order, ingredient, apiKey, event.canonicalName());
            } catch (Exception e) {
                log.error("[Backfill] erro ao processar pedido externalId={}: {}",
                        order.getExternalOrderId(), e.getMessage(), e);
            }
        }
    }

    private void backfillOrder(Order order, Ingredient ingredient, String apiKey, String canonicalName) {
        if (order.getExternalOrderId() == null) return;

        RawJsonResponse<AnotaAIOrderDetailResponse> detailResponse =
                anotaAIClient.getOrderDetail(apiKey, order.getExternalOrderId());
        if (detailResponse == null || detailResponse.body() == null) return;
        AnotaAIOrderDetailResponse response = detailResponse.body();
        if (response.getInfo() == null) return;

        List<AnotaAIOrderDetailResponse.AnotaAIOrderItem> remoteItems = response.getInfo().getItems();
        if (remoteItems == null) return;

        boolean changed = false;
        for (AnotaAIOrderDetailResponse.AnotaAIOrderItem remoteItem : remoteItems) {
            OrderItem localItem = findMatchingLocalItem(order, remoteItem.getInternalId());
            if (localItem == null) continue;

            if (remoteItem.getSubItems() == null) continue;

            for (AnotaAIOrderDetailResponse.AnotaAISubItem subItem : remoteItem.getSubItems()) {
                if (subItem.getName() == null) continue;
                String subCanonical = IngredientNameNormalizer.normalize(subItem.getName());
                if (!subCanonical.equals(canonicalName)) continue;
                if (alreadyHasExtra(localItem, ingredient.getId())) continue;

                BigDecimal perUnit = ingredient.getDefaultQuantity() != null
                        ? ingredient.getDefaultQuantity()
                        : BigDecimal.ONE;
                BigDecimal qty = perUnit.multiply(BigDecimal.valueOf(subItem.getQuantity()));

                // Mesmo payload da importação: o valor pago vem literalmente do subItem.
                // Sem isto, um adicional pago só apareceria com valor quando resolvido na
                // importação — e ficaria sem valor quando o ingrediente é cadastrado depois.
                OrderItemExtraIngredient extra = OrderItemExtraIngredient.builder()
                        .orderItem(localItem)
                        .ingredient(ingredient)
                        .quantity(qty)
                        .costPerUnit(ingredient.getCostPerUnit())
                        .salePricePerUnit(BigDecimal.valueOf(subItem.getPrice()))
                        .salePriceTotal(BigDecimal.valueOf(subItem.getTotal()))
                        .ingredientName(ingredient.getName())
                        .ingredientUnit(ingredient.getUnit())
                        .build();
                localItem.getExtraIngredients().add(extra);
                changed = true;
                log.info("[Backfill] extra adicionado: pedido={} item={} ingrediente='{}'",
                        order.getExternalOrderId(), localItem.getId(), ingredient.getName());
            }
        }

        if (changed) {
            // Mesma regra de OrderService.update(): uma correção manual do custo
            // (valuesOverriddenAt != null) é uma afirmação explícita do lojista e não pode ser
            // desfeita por um recálculo automático. Sem esta guarda o pedido acabaria exibindo
            // "Ajustado manualmente" com um custo que o lojista nunca digitou. O extra continua
            // sendo adicionado ao item; só o custo do pedido é preservado.
            if (order.getValuesOverriddenAt() == null) {
                order.setTotalCost(costCalculatorService.computeOrderTotalCost(order));
            }
            order.setEstimatedProfit(OrderCalculations.calculateEstimatedProfit(order));
            orderRepository.save(order);
        }
    }

    private OrderItem findMatchingLocalItem(Order order, String remoteInternalId) {
        if (remoteInternalId == null || remoteInternalId.isBlank() || order.getItems() == null) return null;
        return order.getItems().stream()
                .filter(item -> item.getProduct() != null
                        && remoteInternalId.equals(item.getProduct().getExternalId()))
                .findFirst()
                .orElse(null);
    }

    private boolean alreadyHasExtra(OrderItem item, java.util.UUID ingredientId) {
        if (item.getExtraIngredients() == null) return false;
        return item.getExtraIngredients().stream()
                .anyMatch(e -> e.getIngredient() != null
                        && ingredientId.equals(e.getIngredient().getId()));
    }
}
