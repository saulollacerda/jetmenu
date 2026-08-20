package com.jetmenu.integration.anotaai;

import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientCreatedEvent;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.integration.RawJsonResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderItemExtraIngredient;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import com.jetmenu.product.OrderCostCalculatorService;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderIngredientBackfillService")
class OrderIngredientBackfillServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private IngredientRepository ingredientRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private AnotaAIClient anotaAIClient;
    @Mock private OrderCostCalculatorService costCalculatorService;

    @InjectMocks
    private OrderIngredientBackfillService backfillService;

    private UUID merchantId;
    private UUID ingredientId;
    private IngredientCreatedEvent event;
    private Merchant merchantWithApiKey;
    private Ingredient ingredient;
    private static final String CANONICAL_NAME = "acai zero";
    private static final String API_KEY = "Bearer test-api-key";
    private static final String EXTERNAL_PRODUCT_ID = "anotaai-product-ext-id";

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        ingredientId = UUID.randomUUID();
        event = new IngredientCreatedEvent(merchantId, ingredientId, CANONICAL_NAME);

        merchantWithApiKey = Merchant.builder()
                .id(merchantId)
                .build();
        merchantWithApiKey.setAnotaAiApiKey(API_KEY);

        ingredient = Ingredient.builder()
                .id(ingredientId)
                .name("Açaí Zero")
                .canonicalName(CANONICAL_NAME)
                .unit("g")
                .costPerUnit(new BigDecimal("0.05"))
                .defaultQuantity(new BigDecimal("100"))
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Product buildProduct(String externalId) {
        return Product.builder()
                .id(UUID.randomUUID())
                .externalId(externalId)
                .name("Açaí 500ml")
                .price(new BigDecimal("25.00"))
                .status(ProductStatus.ACTIVE)
                .merchant(merchantWithApiKey)
                .build();
    }

    private OrderItem buildItem(Product product, BigDecimal unitCost) {
        return OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(1)
                .unitPrice(new BigDecimal("25.00"))
                .unitCost(unitCost)
                .extraIngredients(new ArrayList<>())
                .build();
    }

    private Order buildOrder(String externalOrderId, List<OrderItem> items) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .merchant(merchantWithApiKey)
                .externalOrderId(externalOrderId)
                .origin(OrderOrigin.ANOTA_AI)
                .status(OrderStatus.PAID)
                .totalValue(new BigDecimal("25.00"))
                .estimatedProfit(new BigDecimal("10.00"))
                .totalCost(new BigDecimal("15.00"))
                .dateTime(LocalDate.now().atTime(12, 0))
                .items(items)
                .build();
        items.forEach(i -> i.setOrder(order));
        return order;
    }

    private RawJsonResponse<AnotaAIOrderDetailResponse> raw(AnotaAIOrderDetailResponse response) {
        return new RawJsonResponse<>(response, "{\"fixture\":\"raw\"}");
    }

    private AnotaAIOrderDetailResponse buildDetailResponse(String productExternalId, String subItemName,
                                                           int subItemQty, double price, double total) {
        AnotaAIOrderDetailResponse response = buildDetailResponse(productExternalId, subItemName, subItemQty);
        AnotaAIOrderDetailResponse.AnotaAISubItem subItem =
                response.getInfo().getItems().get(0).getSubItems().get(0);
        subItem.setPrice(price);
        subItem.setTotal(total);
        return response;
    }

    private AnotaAIOrderDetailResponse buildDetailResponse(String productExternalId, String subItemName, int subItemQty) {
        AnotaAIOrderDetailResponse.AnotaAISubItem subItem = new AnotaAIOrderDetailResponse.AnotaAISubItem();
        subItem.setName(subItemName);
        subItem.setQuantity(subItemQty);

        AnotaAIOrderDetailResponse.AnotaAIOrderItem item = new AnotaAIOrderDetailResponse.AnotaAIOrderItem();
        item.setInternalId(productExternalId);
        item.setSubItems(List.of(subItem));

        AnotaAIOrderDetailResponse.OrderDetail detail = new AnotaAIOrderDetailResponse.OrderDetail();
        detail.setItems(List.of(item));

        AnotaAIOrderDetailResponse response = new AnotaAIOrderDetailResponse();
        response.setInfo(detail);
        return response;
    }

    // -------------------------------------------------------------------------
    // Guard clauses — merchant sem apiKey
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("quando merchant não tem apiKey")
    class MerchantWithoutApiKey {

        @Test
        @DisplayName("não deve chamar AnotaAIClient quando apiKey é null")
        void whenMerchantHasNoApiKey_shouldSkipBackfill() {
            Merchant merchantNoKey = Merchant.builder().id(merchantId).build();
            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantNoKey));
            lenient().when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));

            backfillService.onIngredientCreated(event);

            verify(anotaAIClient, never()).getOrderDetail(any(), any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("não deve chamar AnotaAIClient quando apiKey é string vazia")
        void whenMerchantHasBlankApiKey_shouldSkipBackfill() {
            Merchant merchantBlankKey = Merchant.builder().id(merchantId).build();
            merchantBlankKey.setAnotaAiApiKey("   ");
            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantBlankKey));
            lenient().when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));

            backfillService.onIngredientCreated(event);

            verify(anotaAIClient, never()).getOrderDetail(any(), any());
            verify(orderRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // Guard clauses — sem pedidos hoje
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("quando não há pedidos hoje")
    class NoTodayOrders {

        @Test
        @DisplayName("não deve chamar AnotaAIClient quando lista de pedidos é vazia")
        void whenNoTodayOrders_shouldDoNothing() {
            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
            given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
            given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                    eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                    .willReturn(List.of());

            backfillService.onIngredientCreated(event);

            verify(anotaAIClient, never()).getOrderDetail(any(), any());
        }

        @Test
        @DisplayName("não deve chamar getOrderDetail quando order.externalOrderId é null")
        void whenOrderHasNoExternalId_shouldSkipOrder() {
            Product product = buildProduct(EXTERNAL_PRODUCT_ID);
            OrderItem item = buildItem(product, new BigDecimal("10.00"));
            Order order = buildOrder(null, new ArrayList<>(List.of(item)));

            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
            given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
            given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                    eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                    .willReturn(List.of(order));

            backfillService.onIngredientCreated(event);

            verify(anotaAIClient, never()).getOrderDetail(any(), any());
        }
    }

    // -------------------------------------------------------------------------
    // Guard clauses — AnotaAI retorna resposta inválida
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("quando AnotaAI retorna resposta inválida")
    class InvalidAnotaAIResponse {

        @BeforeEach
        void setUpOrderAndMerchant() {
            Product product = buildProduct(EXTERNAL_PRODUCT_ID);
            OrderItem item = buildItem(product, new BigDecimal("10.00"));
            Order order = buildOrder("ext-order-123", new ArrayList<>(List.of(item)));

            lenient().when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchantWithApiKey));
            lenient().when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
            lenient().when(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                    eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                    .thenReturn(List.of(order));
        }

        @Test
        @DisplayName("não deve adicionar extra quando getOrderDetail retorna null")
        void whenAnotaAIReturnsNullResponse_shouldSkipOrder() {
            given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-123")).willReturn(null);

            backfillService.onIngredientCreated(event);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("não deve adicionar extra quando response.getInfo() é null")
        void whenAnotaAIResponseHasNullInfo_shouldSkipOrder() {
            AnotaAIOrderDetailResponse response = new AnotaAIOrderDetailResponse();
            response.setInfo(null);
            given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-123")).willReturn(raw(response));

            backfillService.onIngredientCreated(event);

            verify(orderRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // Happy path — subItem casa com ingrediente
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("quando subItem casa com o ingrediente criado")
    class SubItemMatchesIngredient {

        private Order order;
        private OrderItem item;

        @BeforeEach
        void setUpMatchingScenario() {
            Product product = buildProduct(EXTERNAL_PRODUCT_ID);
            item = buildItem(product, new BigDecimal("10.00"));
            order = buildOrder("ext-order-123", new ArrayList<>(List.of(item)));

            given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
            given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
            given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                    eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                    .willReturn(List.of(order));
            given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-123"))
                    .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Açaí Zero", 1)));
        }

        @Test
        @DisplayName("deve criar OrderItemExtraIngredient com quantidade = defaultQuantity × subItem.quantity")
        void whenSubItemMatchesIngredient_shouldAddExtraAndSave() {
            given(costCalculatorService.computeOrderTotalCost(order))
                    .willReturn(new BigDecimal("15.00"));

            backfillService.onIngredientCreated(event);

            assertThat(item.getExtraIngredients()).hasSize(1);
            OrderItemExtraIngredient extra = item.getExtraIngredients().get(0);
            assertThat(extra.getIngredient()).isEqualTo(ingredient);
            assertThat(extra.getCostPerUnit()).isEqualByComparingTo(ingredient.getCostPerUnit());
            // quantity = defaultQuantity(100) × subItem.quantity(1) = 100
            assertThat(extra.getQuantity()).isEqualByComparingTo(new BigDecimal("100"));
            assertThat(extra.getIngredientName()).isEqualTo("Açaí Zero");
            assertThat(extra.getIngredientUnit()).isEqualTo("g");

            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("deve recalcular totalCost usando OrderCostCalculatorService")
        void whenSubItemMatchesIngredient_shouldRecalculateTotalCost() {
            given(costCalculatorService.computeOrderTotalCost(order)).willReturn(new BigDecimal("20.00"));

            backfillService.onIngredientCreated(event);

            assertThat(order.getTotalCost()).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("deve recalcular estimatedProfit = totalValue − deliveryFee − totalCost")
        void whenSubItemMatchesIngredient_shouldRecalculateEstimatedProfit() {
            // totalValue=25, deliveryFee=null (zero), totalCost=20 → profit=5
            given(costCalculatorService.computeOrderTotalCost(order)).willReturn(new BigDecimal("20.00"));

            backfillService.onIngredientCreated(event);

            assertThat(order.getEstimatedProfit()).isEqualByComparingTo(new BigDecimal("5.00"));
        }

        @Test
        @DisplayName("não deve sobrescrever o custo corrigido manualmente pelo lojista")
        void whenOrderHasManualOverride_shouldNotOverwriteTotalCost() {
            // O lojista corrigiu o custo à mão (override ativo). O backfill continua
            // adicionando o extra, mas o custo manual é a palavra final: sobrescrevê-lo
            // deixaria o pedido dizendo "Ajustado manualmente" com um valor que o lojista
            // nunca digitou — e com o snapshot original apontando para outro número ainda.
            order.setTotalCost(new BigDecimal("9.00"));
            order.setOriginalTotalValue(new BigDecimal("25.00"));
            order.setOriginalTotalCost(new BigDecimal("15.00"));
            order.setOriginalEstimatedProfit(new BigDecimal("10.00"));
            order.setValuesOverriddenAt(LocalDateTime.now().minusHours(1));

            backfillService.onIngredientCreated(event);

            // O extra entra normalmente — só o custo é preservado.
            assertThat(item.getExtraIngredients()).hasSize(1);
            assertThat(order.getTotalCost()).isEqualByComparingTo(new BigDecimal("9.00"));
            verify(costCalculatorService, never()).computeOrderTotalCost(order);
            // Lucro recalculado a partir do custo manual: 25 − 0 − 9 = 16.
            assertThat(order.getEstimatedProfit()).isEqualByComparingTo(new BigDecimal("16.00"));
            // O snapshot original permanece intacto.
            assertThat(order.getOriginalTotalCost()).isEqualByComparingTo(new BigDecimal("15.00"));
        }
    }

    // -------------------------------------------------------------------------
    // Idempotência — extra já existe
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("não deve duplicar extra quando OrderItem já tem extra para o mesmo ingrediente")
    void whenExtraAlreadyExistsForIngredient_shouldNotDuplicate() {
        Product product = buildProduct(EXTERNAL_PRODUCT_ID);
        OrderItem item = buildItem(product, new BigDecimal("10.00"));

        OrderItemExtraIngredient existingExtra = OrderItemExtraIngredient.builder()
                .id(UUID.randomUUID())
                .ingredient(ingredient)
                .quantity(new BigDecimal("100"))
                .costPerUnit(new BigDecimal("0.05"))
                .ingredientName("Açaí Zero")
                .ingredientUnit("g")
                .orderItem(item)
                .build();
        item.setExtraIngredients(new ArrayList<>(List.of(existingExtra)));

        Order order = buildOrder("ext-order-123", new ArrayList<>(List.of(item)));

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
        given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
        given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                .willReturn(List.of(order));
        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-123"))
                .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Açaí Zero", 1)));

        backfillService.onIngredientCreated(event);

        assertThat(item.getExtraIngredients()).hasSize(1);
        verify(orderRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Valor pago pelo cliente
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deve gravar o valor pago do payload no extra criado pelo backfill")
    void whenBackfillingPricedSubItem_shouldPersistSalePrice() {
        Product product = buildProduct(EXTERNAL_PRODUCT_ID);
        OrderItem item = buildItem(product, new BigDecimal("10.00"));
        Order order = buildOrder("ext-order-123", new ArrayList<>(List.of(item)));

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
        given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
        given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                .willReturn(List.of(order));
        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-123"))
                .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Açaí Zero", 1, 5.0, 5.0)));

        backfillService.onIngredientCreated(event);

        assertThat(item.getExtraIngredients()).hasSize(1);
        OrderItemExtraIngredient extra = item.getExtraIngredients().get(0);

        // O extra criado pelo backfill deve carregar o preço igual ao criado na importação:
        // é o mesmo payload, e sem isso o adicional apareceria sem valor pago no detalhe.
        assertThat(extra.getSalePriceTotal()).isEqualByComparingTo("5.00");
        assertThat(extra.getSalePricePerUnit()).isEqualByComparingTo("5.00");
        // Custo continua vindo do catálogo local, e a gramatura continua sendo gramatura.
        assertThat(extra.getCostPerUnit()).isEqualByComparingTo("0.05");
        assertThat(extra.getQuantity()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("backfill de complemento base (preço 0) deve gravar zero, não nulo")
    void whenBackfillingZeroPricedSubItem_shouldPersistZero() {
        Product product = buildProduct(EXTERNAL_PRODUCT_ID);
        OrderItem item = buildItem(product, new BigDecimal("10.00"));
        Order order = buildOrder("ext-order-123", new ArrayList<>(List.of(item)));

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
        given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
        given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                .willReturn(List.of(order));
        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-123"))
                .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Açaí Zero", 1, 0.0, 0.0)));

        backfillService.onIngredientCreated(event);

        OrderItemExtraIngredient extra = item.getExtraIngredients().get(0);
        assertThat(extra.getSalePriceTotal()).isEqualByComparingTo("0.00");
    }

    // -------------------------------------------------------------------------
    // Nenhum match de nome
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("não deve adicionar extra quando subItem não casa com o canonical do ingrediente")
    void whenSubItemNameDoesNotMatchCanonical_shouldIgnore() {
        Product product = buildProduct(EXTERNAL_PRODUCT_ID);
        OrderItem item = buildItem(product, new BigDecimal("10.00"));
        Order order = buildOrder("ext-order-123", new ArrayList<>(List.of(item)));

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
        given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
        given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                .willReturn(List.of(order));
        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-123"))
                .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Banana", 1)));

        backfillService.onIngredientCreated(event);

        assertThat(item.getExtraIngredients()).isEmpty();
        verify(orderRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Múltiplos itens — só o que casa
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deve adicionar extra somente no item cujo externalId casa com internalId do AnotaAI")
    void whenMultipleItemsInOrder_shouldOnlyBackfillMatchingItem() {
        Product matchingProduct = buildProduct(EXTERNAL_PRODUCT_ID);
        Product otherProduct = buildProduct("other-ext-id");

        OrderItem matchingItem = buildItem(matchingProduct, new BigDecimal("10.00"));
        OrderItem otherItem = buildItem(otherProduct, new BigDecimal("8.00"));
        Order order = buildOrder("ext-order-123", new ArrayList<>(List.of(matchingItem, otherItem)));

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
        given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
        given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                .willReturn(List.of(order));
        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-123"))
                .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Açaí Zero", 1)));
        given(costCalculatorService.computeOrderTotalCost(order)).willReturn(new BigDecimal("18.00"));

        backfillService.onIngredientCreated(event);

        assertThat(matchingItem.getExtraIngredients()).hasSize(1);
        assertThat(otherItem.getExtraIngredients()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Múltiplos pedidos
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deve fazer backfill em todos os pedidos com subItem que casa")
    void whenMultipleOrdersMatch_shouldBackfillAll() {
        Product p1 = buildProduct(EXTERNAL_PRODUCT_ID);
        Product p2 = buildProduct(EXTERNAL_PRODUCT_ID);

        OrderItem item1 = buildItem(p1, new BigDecimal("10.00"));
        OrderItem item2 = buildItem(p2, new BigDecimal("10.00"));

        Order order1 = buildOrder("ext-order-1", new ArrayList<>(List.of(item1)));
        Order order2 = buildOrder("ext-order-2", new ArrayList<>(List.of(item2)));

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
        given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
        given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                .willReturn(List.of(order1, order2));
        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-1"))
                .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Açaí Zero", 1)));
        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-2"))
                .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Açaí Zero", 1)));
        given(costCalculatorService.computeOrderTotalCost(any())).willReturn(new BigDecimal("15.00"));

        backfillService.onIngredientCreated(event);

        verify(orderRepository, times(2)).save(any());
    }

    // -------------------------------------------------------------------------
    // defaultQuantity null → fallback 1
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deve usar quantity=1 como fallback quando ingredient.defaultQuantity é null")
    void whenIngredientDefaultQuantityIsNull_shouldUseOne() {
        ingredient = Ingredient.builder()
                .id(ingredientId)
                .name("Açaí Zero")
                .canonicalName(CANONICAL_NAME)
                .unit("g")
                .costPerUnit(new BigDecimal("0.05"))
                .defaultQuantity(null)
                .build();

        Product product = buildProduct(EXTERNAL_PRODUCT_ID);
        OrderItem item = buildItem(product, new BigDecimal("10.00"));
        Order order = buildOrder("ext-order-123", new ArrayList<>(List.of(item)));

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
        given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
        given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                .willReturn(List.of(order));
        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-123"))
                .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Açaí Zero", 3)));
        given(costCalculatorService.computeOrderTotalCost(order)).willReturn(new BigDecimal("15.00"));

        backfillService.onIngredientCreated(event);

        OrderItemExtraIngredient extra = item.getExtraIngredients().get(0);
        // defaultQuantity null → 1; subItem.quantity = 3 → quantity = 1 × 3 = 3
        assertThat(extra.getQuantity()).isEqualByComparingTo(new BigDecimal("3"));
    }

    // -------------------------------------------------------------------------
    // internalId não casa com nenhum item local
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("não deve adicionar extra quando internalId do AnotaAI não corresponde a nenhum produto local")
    void whenAnotaAIItemInternalIdDoesNotMatchAnyLocalItem_shouldSkip() {
        Product product = buildProduct("completely-different-external-id");
        OrderItem item = buildItem(product, new BigDecimal("10.00"));
        Order order = buildOrder("ext-order-123", new ArrayList<>(List.of(item)));

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
        given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
        given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                .willReturn(List.of(order));
        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-123"))
                .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Açaí Zero", 1)));

        backfillService.onIngredientCreated(event);

        assertThat(item.getExtraIngredients()).isEmpty();
        verify(orderRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Resiliência — exceção em um pedido não impede os demais
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deve continuar com os demais pedidos quando getOrderDetail lança exceção em um")
    void whenExceptionOnOneOrder_shouldContinueWithOthers() {
        Product p1 = buildProduct(EXTERNAL_PRODUCT_ID);
        Product p2 = buildProduct(EXTERNAL_PRODUCT_ID);

        OrderItem item1 = buildItem(p1, new BigDecimal("10.00"));
        OrderItem item2 = buildItem(p2, new BigDecimal("10.00"));

        Order order1 = buildOrder("ext-order-fail", new ArrayList<>(List.of(item1)));
        Order order2 = buildOrder("ext-order-ok", new ArrayList<>(List.of(item2)));

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchantWithApiKey));
        given(ingredientRepository.findById(ingredientId)).willReturn(Optional.of(ingredient));
        given(orderRepository.findByMerchantIdAndOriginAndDateTimeBetween(
                eq(merchantId), eq(OrderOrigin.ANOTA_AI), any(), any()))
                .willReturn(List.of(order1, order2));

        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-fail"))
                .willThrow(new RuntimeException("AnotaAI offline"));
        given(anotaAIClient.getOrderDetail(API_KEY, "ext-order-ok"))
                .willReturn(raw(buildDetailResponse(EXTERNAL_PRODUCT_ID, "Açaí Zero", 1)));
        given(costCalculatorService.computeOrderTotalCost(order2)).willReturn(new BigDecimal("15.00"));

        backfillService.onIngredientCreated(event);

        // O segundo pedido deve ter sido processado mesmo com a falha no primeiro
        assertThat(item2.getExtraIngredients()).hasSize(1);
        verify(orderRepository, times(1)).save(order2);
    }

    @Test
    @DisplayName("onIngredientCreated deve escutar AFTER_COMMIT para evitar race com o commit do ingrediente")
    void onIngredientCreated_shouldListenAfterCommit() throws NoSuchMethodException {
        Method method = OrderIngredientBackfillService.class
                .getMethod("onIngredientCreated", IngredientCreatedEvent.class);

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation)
                .as("backfill deve usar @TransactionalEventListener para rodar só após o commit")
                .isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
