package com.jetmenu.integration.anotaai;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.ingredient.IngredientStatus;
import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.integration.RawJsonResponse;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayload;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadRepository;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.notification.Notification;
import com.jetmenu.notification.NotificationRepository;
import com.jetmenu.notification.NotificationStatus;
import com.jetmenu.notification.NotificationType;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderItemExtraIngredient;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.product.Include;
import com.jetmenu.product.IncludeKind;
import com.jetmenu.product.IncludeRepository;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Cobertura end-to-end de {@link AnotaAISyncService} contra Postgres real.
 *
 * <p>O único bean mockado é {@link AnotaAIClient} (não queremos hit em API externa).
 * Tudo o mais — repositórios, NotificationService, OrderCostCalculatorService —
 * roda real, ligado ao Postgres de teste.
 *
 * <p>Por que esta classe importa: o bug do {@code owner_id} estourou justamente
 * neste fluxo, no momento em que {@code NotificationService.createMissingIngredient}
 * foi acionado dentro do importOrder.
 */
@DisplayName("AnotaAISyncService — integração com Postgres")
class AnotaAISyncServiceIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    private AnotaAIClient anotaAIClient;

    @Autowired private AnotaAISyncService syncService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private IncludeRepository includeRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ExternalOrderRawPayloadRepository rawPayloadRepository;
    @Autowired private com.jetmenu.order.OrderFichaLineRepository orderFichaLineRepository;

    private Merchant merchant;
    private String apiKey;

    @BeforeEach
    void setupMerchantWithApiKey() {
        merchant = createMerchant();
        apiKey = "test-key-" + merchant.getId();
        merchant.setAnotaAiApiKey(apiKey);
        merchantRepository.save(merchant);
    }

    @Test
    @DisplayName("syncOrders — Include INGREDIENT específico sobrescreve defaultQuantity global ao criar extra")
    void syncOrders_shouldPersistFullOrderWithExtrasAndCost() {
        // Setup: produto Açaí 330ml com Include INGREDIENT "leite ninho" qty=40g.
        // Ingrediente global tem defaultQuantity=20g.
        // Pedido tem subItem "leite ninho" qty=3.
        // Regra: Include INGREDIENT específico sobrescreve global → qty = 40×3 = 120g.
        Category category = persistCategory("Açaí");
        Product acai330 = persistProduct("Açaí 330ml", "anota-acai-330", category,
                new BigDecimal("15.00"));
        Include leiteNinhoInclude = Include.builder()
                .product(acai330)
                .name("leite ninho")
                .cost(new BigDecimal("0.05"))
                .quantity(new BigDecimal("40"))
                .kind(IncludeKind.INGREDIENT)
                .build();
        includeRepository.save(leiteNinhoInclude);
        persistIngredient("leite ninho", "g",
                new BigDecimal("0.05"), new BigDecimal("20"));

        given(anotaAIClient.getOrderList(apiKey)).willReturn(orderListWith("ord-int-1"));
        AnotaAIOrderDetailResponse detail = AnotaAIFixtures.load(
                "order_detail_acai_330_three_leite_ninho.json", AnotaAIOrderDetailResponse.class);
        detail.getInfo().setId("ord-int-1");
        detail.getInfo().getItems().get(0).setInternalId("anota-acai-330");
        given(anotaAIClient.getOrderDetail(apiKey, "ord-int-1")).willReturn(raw(detail));

        // Act
        AnotaAISyncResult result = syncService.syncOrders(merchant.getId());

        // Assert — resultado de retorno
        assertThat(result.getOrdersImported()).isEqualTo(1);
        assertThat(result.getOrdersSkipped()).isZero();
        assertThat(result.getErrors()).isEmpty();

        // Assert — persistência real no banco
        Optional<Order> saved = orderRepository.findByExternalOrderIdAndMerchantId(
                "ord-int-1", merchant.getId());
        assertThat(saved).isPresent();
        Order order = saved.get();
        assertThat(order.getOrigin()).isEqualTo(OrderOrigin.ANOTA_AI);
        assertThat(order.getItems()).hasSize(1);

        var item = order.getItems().get(0);
        assertThat(item.getProduct().getId()).isEqualTo(acai330.getId());
        assertThat(item.getQuantity()).isEqualTo(1);
        assertThat(item.getExtraIngredients()).hasSize(1);
        // qty = Include.quantity (40g) × subItem.quantity (3) = 120g
        assertThat(item.getExtraIngredients().get(0).getQuantity()).isEqualByComparingTo("120");
        // totalCost = 120g × 0.05 = 6.00
        assertThat(order.getTotalCost()).isEqualByComparingTo("6.00");

        // Assert — payload bruto persistido em jsonb para auditoria
        List<ExternalOrderRawPayload> payloads = rawPayloadRepository.findAll();
        assertThat(payloads).hasSize(1);
        ExternalOrderRawPayload payload = payloads.get(0);
        assertThat(payload.getMerchantId()).isEqualTo(merchant.getId());
        assertThat(payload.getOrigin()).isEqualTo(OrderOrigin.ANOTA_AI);
        assertThat(payload.getExternalOrderId()).isEqualTo("ord-int-1");
        assertThat(payload.getPayload()).contains("integration-raw-fixture");
        assertThat(payload.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("syncOrders deve criar Notification de MISSING_INGREDIENT quando subItem não casa com ingrediente — END-TO-END catch do bug do owner_id")
    void syncOrders_shouldPersistMissingIngredientNotification() {
        // Setup: produto existe, mas ingrediente "leite ninho" NÃO está cadastrado
        Category category = persistCategory("Açaí");
        Product acai = persistProduct("Açaí 500ml", "anota-acai-500", category,
                new BigDecimal("20.00"));

        given(anotaAIClient.getOrderList(apiKey)).willReturn(orderListWith("ord-int-missing"));
        AnotaAIOrderDetailResponse detail = AnotaAIFixtures.load(
                "order_detail_with_subitems.json", AnotaAIOrderDetailResponse.class);
        detail.getInfo().setId("ord-int-missing");
        detail.getInfo().getItems().get(0).setInternalId("anota-acai-500");
        given(anotaAIClient.getOrderDetail(apiKey, "ord-int-missing")).willReturn(raw(detail));

        // Act
        AnotaAISyncResult result = syncService.syncOrders(merchant.getId());

        // Assert — pedido importado, mas sem o extra
        assertThat(result.getOrdersImported()).isEqualTo(1);
        assertThat(result.getMissingIngredientNames()).contains("Açaí Premium");

        // Assert — notificação persistida no banco (este é o exato INSERT que estourava com owner_id)
        List<Notification> all = notificationRepository.findAll();
        assertThat(all).hasSize(1);
        Notification n = all.get(0);
        assertThat(n.getMerchant().getId()).isEqualTo(merchant.getId());
        assertThat(n.getType()).isEqualTo(NotificationType.MISSING_INGREDIENT);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(n.getReferenceDisplay()).isEqualTo("Açaí Premium");
        assertThat(n.getReferenceData()).isEqualTo("acai premium");
    }

    @Test
    @DisplayName("syncOrders deve criar um extra separado por subItem — mesmo nome gera múltiplos extras")
    void syncOrders_shouldCreateSeparateExtraPerSubItemEndToEnd() {
        // Fixture: leite ninho ×2, chocoball ×2, morango ×1 → 5 extras distintos
        Category category = persistCategory("Açaí");
        Product acai = persistProduct("Açaí 330ml", "anota-acai-dup", category,
                new BigDecimal("15.00"));
        Ingredient leiteNinho = persistIngredient("leite ninho", "un",
                new BigDecimal("0.0533"), new BigDecimal("20"));
        Ingredient chocoball = persistIngredient("chocoball", "un",
                new BigDecimal("0.066"), new BigDecimal("20"));
        Ingredient morango = persistIngredient("morango", "un",
                new BigDecimal("0.01"), new BigDecimal("1"));

        given(anotaAIClient.getOrderList(apiKey)).willReturn(orderListWith("ord-int-dup"));
        AnotaAIOrderDetailResponse detail = AnotaAIFixtures.load(
                "order_detail_duplicate_subitems.json", AnotaAIOrderDetailResponse.class);
        detail.getInfo().setId("ord-int-dup");
        detail.getInfo().getItems().get(0).setInternalId("anota-acai-dup");
        given(anotaAIClient.getOrderDetail(apiKey, "ord-int-dup")).willReturn(raw(detail));

        syncService.syncOrders(merchant.getId());

        Order order = orderRepository.findByExternalOrderIdAndMerchantId(
                "ord-int-dup", merchant.getId()).orElseThrow();
        var extras = order.getItems().get(0).getExtraIngredients();

        // 5 extras: leite ninho ×2, chocoball ×2, morango ×1
        assertThat(extras).hasSize(5);

        var leiteExtras = extras.stream()
                .filter(e -> e.getIngredient().getId().equals(leiteNinho.getId())).toList();
        assertThat(leiteExtras).hasSize(2);
        leiteExtras.forEach(e -> assertThat(e.getQuantity()).isEqualByComparingTo("20"));

        var chocoExtras = extras.stream()
                .filter(e -> e.getIngredient().getId().equals(chocoball.getId())).toList();
        assertThat(chocoExtras).hasSize(2);
        chocoExtras.forEach(e -> assertThat(e.getQuantity()).isEqualByComparingTo("20"));

        var morangoExtras = extras.stream()
                .filter(e -> e.getIngredient().getId().equals(morango.getId())).toList();
        assertThat(morangoExtras).hasSize(1);
        assertThat(morangoExtras.get(0).getQuantity()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("syncOrders deve pular pedido já importado (idempotência)")
    void syncOrders_shouldSkipAlreadyImportedOrder() {
        Category category = persistCategory("Açaí");
        Product acai = persistProduct("Açaí 500ml", "anota-idem", category,
                new BigDecimal("20.00"));

        given(anotaAIClient.getOrderList(apiKey)).willReturn(orderListWith("ord-idem"));
        AnotaAIOrderDetailResponse detail = AnotaAIFixtures.load(
                "order_detail_simple.json", AnotaAIOrderDetailResponse.class);
        detail.getInfo().setId("ord-idem");
        detail.getInfo().getItems().get(0).setInternalId("anota-idem");
        given(anotaAIClient.getOrderDetail(apiKey, "ord-idem")).willReturn(raw(detail));

        AnotaAISyncResult first = syncService.syncOrders(merchant.getId());
        AnotaAISyncResult second = syncService.syncOrders(merchant.getId());

        assertThat(first.getOrdersImported()).isEqualTo(1);
        assertThat(second.getOrdersImported()).isZero();
        assertThat(second.getOrdersSkipped()).isEqualTo(1);
        // Apenas 1 pedido persistido — não duplicou
        assertThat(orderRepository.count()).isEqualTo(1);
        // Payload bruto também não duplica: pedido pulado não busca detalhe
        assertThat(rawPayloadRepository.count()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private RawJsonResponse<AnotaAIOrderDetailResponse> raw(AnotaAIOrderDetailResponse detail) {
        // Precisa ser JSON válido: a coluna payload é jsonb no Postgres
        return new RawJsonResponse<>(detail, "{\"fixture\":\"integration-raw-fixture\"}");
    }

    private AnotaAIOrderListResponse orderListWith(String orderId) {
        AnotaAIOrderListResponse response = AnotaAIFixtures.load(
                "order_list_template.json", AnotaAIOrderListResponse.class);
        var summary = response.getInfo().getDocs().get(0);
        summary.setId(orderId);
        summary.setSalesChannel("anotaai");
        return response;
    }

    private Category persistCategory(String name) {
        return categoryRepository.save(Category.builder()
                .merchant(merchant)
                .name(name)
                .build());
    }

    private Product persistProduct(String name, String externalId, Category category, BigDecimal price) {
        return productRepository.save(Product.builder()
                .merchant(merchant)
                .name(name)
                .externalId(externalId)
                .category(category)
                .price(price)
                .status(ProductStatus.ACTIVE)
                .build());
    }

    private Ingredient persistIngredient(String name, String unit, BigDecimal cost, BigDecimal defaultQty) {
        return ingredientRepository.save(Ingredient.builder()
                .merchant(merchant)
                .name(name)
                .canonicalName(name) // já normalizado nos casos do teste
                .unit(unit)
                .costPerUnit(cost)
                .defaultQuantity(defaultQty)
                .status(IngredientStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("syncOrders — ficha do pedido entra UMA vez no pedido importado (Anota.AI)")
    void syncOrders_shouldApplyOrderFichaOncePerImportedOrder() {
        Category category = persistCategory("Açaí");
        Product acai = persistProduct("Açaí 500ml", "anota-ficha", category,
                new BigDecimal("20.00"));
        // Ficha do pedido do lojista: 1 sacola a 0.50 por pedido
        Ingredient sacola = persistIngredient("sacola", "un",
                new BigDecimal("0.50"), BigDecimal.ONE);
        orderFichaLineRepository.save(com.jetmenu.order.OrderFichaLine.builder()
                .merchant(merchant).ingredient(sacola).quantity(BigDecimal.ONE).sortOrder(0).build());

        given(anotaAIClient.getOrderList(apiKey)).willReturn(orderListWith("ord-ficha"));
        AnotaAIOrderDetailResponse detail = AnotaAIFixtures.load(
                "order_detail_simple.json", AnotaAIOrderDetailResponse.class);
        detail.getInfo().setId("ord-ficha");
        detail.getInfo().getItems().get(0).setInternalId("anota-ficha");
        given(anotaAIClient.getOrderDetail(apiKey, "ord-ficha")).willReturn(raw(detail));

        syncService.syncOrders(merchant.getId());

        Order order = orderRepository.findByExternalOrderIdAndMerchantId(
                "ord-ficha", merchant.getId()).orElseThrow();

        // snapshot gravado no pedido, uma linha só
        assertThat(order.getOrderFicha()).hasSize(1);
        var line = order.getOrderFicha().get(0);
        assertThat(line.getIngredientName()).isEqualTo("sacola");
        assertThat(line.getIngredientUnit()).isEqualTo("un");
        assertThat(line.getCostPerUnit()).isEqualByComparingTo("0.50");
        // o produto não tem PACKAGING, então todo o custo do pedido vem da ficha do pedido
        assertThat(order.getTotalCost()).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("syncOrders — lojista sem ficha do pedido: custo inalterado (no-op)")
    void syncOrders_withoutOrderFichaKeepsCostUnchanged() {
        Category category = persistCategory("Açaí");
        persistProduct("Açaí 500ml", "anota-noficha", category, new BigDecimal("20.00"));

        given(anotaAIClient.getOrderList(apiKey)).willReturn(orderListWith("ord-noficha"));
        AnotaAIOrderDetailResponse detail = AnotaAIFixtures.load(
                "order_detail_simple.json", AnotaAIOrderDetailResponse.class);
        detail.getInfo().setId("ord-noficha");
        detail.getInfo().getItems().get(0).setInternalId("anota-noficha");
        given(anotaAIClient.getOrderDetail(apiKey, "ord-noficha")).willReturn(raw(detail));

        syncService.syncOrders(merchant.getId());

        Order order = orderRepository.findByExternalOrderIdAndMerchantId(
                "ord-noficha", merchant.getId()).orElseThrow();
        assertThat(order.getOrderFicha()).isEmpty();
        assertThat(order.getTotalCost()).isEqualByComparingTo("0.00");
    }
}
