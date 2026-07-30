package com.jetmenu.integration.anotaai;

import com.jetmenu.category.CatalogOrigin;
import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.customer.Customer;
import com.jetmenu.customer.CustomerRepository;
import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.ingredient.IngredientStatus;
import com.jetmenu.integration.RawJsonResponse;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadService;
import com.jetmenu.notification.NotificationService;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import com.jetmenu.fee.FeeRepository;
import com.jetmenu.product.Product;
import com.jetmenu.product.Include;
import com.jetmenu.product.IncludeKind;
import com.jetmenu.product.IncludeRepository;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnotaAISyncService")
class AnotaAISyncServiceTest {

    @Mock private AnotaAIClient anotaAIClient;
    @Mock private MerchantRepository merchantRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private FeeRepository feeRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private IngredientRepository ingredientRepository;
    @Mock private IncludeRepository includeRepository;
    @Mock private NotificationService notificationService;
    @Mock private com.jetmenu.product.OrderCostCalculatorService orderCostCalculatorService;
    @Mock private ExternalOrderRawPayloadService rawPayloadService;
    @Mock private com.jetmenu.order.OrderFichaService orderFichaService;

    @InjectMocks
    private AnotaAISyncService syncService;

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        merchant = Merchant.builder()
                .id(merchantId)
                .build();
        merchant.setAnotaAiApiKey("test-api-key");
        // Default: cálculo de custo retorna ZERO — testes que verificam profit/cost específico sobrescrevem
        org.mockito.Mockito.lenient()
                .when(orderCostCalculatorService.computeOrderTotalCost(any(Order.class)))
                .thenReturn(BigDecimal.ZERO);
        // FK lookup para builder de entidades — retorna proxy com o id passado
        org.mockito.Mockito.lenient()
                .when(merchantRepository.getReferenceById(any(UUID.class)))
                .thenAnswer(inv -> Merchant.builder().id(inv.getArgument(0)).build());
    }

    // -------------------------------------------------------------------------
    // syncCatalog — apenas categorias e produtos
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("syncCatalog deve criar categorias e produtos novos")
    void syncCatalog_shouldCreateCategoriesAndProducts() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getCatalog("test-api-key")).willReturn(buildCatalog());
        given(categoryRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId)))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId)))
                .willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(inv -> { Category c = inv.getArgument(0); c.setId(UUID.randomUUID()); return c; });

        AnotaAISyncResult result = syncService.syncCatalog(merchantId);

        assertThat(result.getCategoriesCreated()).isEqualTo(1);
        assertThat(result.getCategoriesUpdated()).isZero();
        assertThat(result.getProductsCreated()).isEqualTo(2);
        assertThat(result.getProductsUpdated()).isZero();
        verify(categoryRepository, times(1)).save(any(Category.class));
        verify(productRepository, times(2)).save(any(Product.class));
    }

    @Test
    @DisplayName("syncCatalog deve marcar categorias e produtos criados com origin ANOTA_AI")
    void syncCatalog_shouldStampAnotaAiOrigin() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getCatalog("test-api-key")).willReturn(buildCatalog());
        given(categoryRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId)))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId)))
                .willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(inv -> { Category c = inv.getArgument(0); c.setId(UUID.randomUUID()); return c; });

        syncService.syncCatalog(merchantId);

        verify(categoryRepository).save(argThat(c -> c.getOrigin() == CatalogOrigin.ANOTA_AI));
        verify(productRepository, times(2)).save(argThat(p -> p.getOrigin() == CatalogOrigin.ANOTA_AI));
    }

    @Test
    @DisplayName("syncCatalog deve atualizar produtos existentes")
    void syncCatalog_shouldUpdateExistingProducts() {
        Category existingCategory = Category.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Old Name").externalId("cat-1").build();
        Product existingProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Old Product")
                .price(new BigDecimal("5.00")).status(ProductStatus.ACTIVE).externalId("item-1").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getCatalog("test-api-key")).willReturn(buildCatalog());
        given(categoryRepository.findByExternalIdAndMerchantId("cat-1", merchantId))
                .willReturn(Optional.of(existingCategory));
        given(productRepository.findByExternalIdAndMerchantId("item-1", merchantId))
                .willReturn(Optional.of(existingProduct));
        given(productRepository.findByExternalIdAndMerchantId("item-2", merchantId))
                .willReturn(Optional.empty());

        AnotaAISyncResult result = syncService.syncCatalog(merchantId);

        assertThat(result.getCategoriesUpdated()).isEqualTo(1);
        assertThat(result.getCategoriesCreated()).isZero();
        assertThat(result.getProductsUpdated()).isEqualTo(1);
        assertThat(result.getProductsCreated()).isEqualTo(1);
        assertThat(existingCategory.getName()).isEqualTo("Bebidas");
        assertThat(existingProduct.getPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("syncCatalog NÃO deve tocar em ingredientes — usuário cadastra manualmente")
    void syncCatalog_shouldNotTouchIngredients() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getCatalog("test-api-key")).willReturn(buildCatalogWithAdditionals());
        given(categoryRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId)))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId)))
                .willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(inv -> { Category c = inv.getArgument(0); c.setId(UUID.randomUUID()); return c; });

        syncService.syncCatalog(merchantId);

        // Nada de ingredientes ou categorias de ingrediente é criado no sync de cardápio
        verify(ingredientRepository, never()).save(any(Ingredient.class));
        // Categorias is_additional=true são puladas no Pass único; só cat-1 (is_additional=false) gera Category
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    @DisplayName("syncCatalog(merchantId, true) deve apagar Includes dos produtos vindos do Anota.AI antes de re-importar")
    void syncCatalog_withClearRecipes_shouldDeleteExistingIncludes() {
        Product existingProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Old Product")
                .price(new BigDecimal("5.00")).status(ProductStatus.ACTIVE).externalId("item-1").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getCatalog("test-api-key")).willReturn(buildCatalog());
        given(categoryRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId)))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("item-1", merchantId))
                .willReturn(Optional.of(existingProduct));
        given(productRepository.findByExternalIdAndMerchantId("item-2", merchantId))
                .willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(inv -> { Category c = inv.getArgument(0); c.setId(UUID.randomUUID()); return c; });
        given(productRepository.save(any(Product.class)))
                .willAnswer(inv -> { Product p = inv.getArgument(0); if (p.getId() == null) p.setId(UUID.randomUUID()); return p; });

        syncService.syncCatalog(merchantId, true);

        verify(includeRepository).deleteAllByProductIdAndProductMerchantId(existingProduct.getId(), merchantId);
    }

    @Test
    @DisplayName("syncCatalog(merchantId, false) NÃO deve apagar Includes existentes")
    void syncCatalog_withoutClearRecipes_shouldNotDeleteIncludes() {
        Product existingProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Old Product")
                .price(new BigDecimal("5.00")).status(ProductStatus.ACTIVE).externalId("item-1").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getCatalog("test-api-key")).willReturn(buildCatalog());
        given(categoryRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId)))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("item-1", merchantId))
                .willReturn(Optional.of(existingProduct));
        given(productRepository.findByExternalIdAndMerchantId("item-2", merchantId))
                .willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(inv -> { Category c = inv.getArgument(0); c.setId(UUID.randomUUID()); return c; });
        given(productRepository.save(any(Product.class)))
                .willAnswer(inv -> { Product p = inv.getArgument(0); if (p.getId() == null) p.setId(UUID.randomUUID()); return p; });

        syncService.syncCatalog(merchantId, false);

        verify(includeRepository, never()).deleteAllByProductIdAndProductMerchantId(any(), any());
    }

    @Test
    @DisplayName("syncCatalog(merchantId) — overload legado equivale a clearRecipes=false")
    void syncCatalog_legacyOverload_shouldNotClearRecipes() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getCatalog("test-api-key")).willReturn(buildCatalog());
        given(categoryRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId)))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId)))
                .willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(inv -> { Category c = inv.getArgument(0); c.setId(UUID.randomUUID()); return c; });

        syncService.syncCatalog(merchantId);

        verify(includeRepository, never()).deleteAllByProductIdAndProductMerchantId(any(), any());
    }

    // -------------------------------------------------------------------------
    // syncOrders
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("syncOrders deve importar pedidos novos")
    void syncOrders_shouldImportNewOrders() {
        Customer existingCustomer = Customer.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Teste").phone("43123456789").build();
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Refrigerante 1L")
                .status(ProductStatus.ACTIVE).externalId("65d4a428f784bb001956f919").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-1")).willReturn(raw(buildOrderDetail("order-1")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(existingCustomer));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("65d4a428f784bb001956f919", merchantId))
                .willReturn(Optional.of(mappedProduct));

        AnotaAISyncResult result = syncService.syncOrders(merchantId);

        assertThat(result.getOrdersImported()).isEqualTo(1);
        assertThat(result.getOrdersSkipped()).isZero();

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getOrigin()).isEqualTo(OrderOrigin.ANOTA_AI);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getExternalOrderId()).isEqualTo("order-1");
        assertThat(saved.getCustomer()).isEqualTo(existingCustomer);
        // total=10, deliveryFee=0 → totalValue = 10
        assertThat(saved.getTotalValue()).isEqualByComparingTo("10.00");
        assertThat(saved.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("syncOrders deve calcular estimatedProfit como sum(item.profit) — sem subtrair fees")
    void syncOrders_shouldComputeEstimatedProfitWithoutFee() {
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Refrigerante 1L")
                .status(ProductStatus.ACTIVE).externalId("65d4a428f784bb001956f919").build();
        Ingredient costIng = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Cost").unit("un")
                .costPerUnit(new BigDecimal("4.00")).status(IngredientStatus.ACTIVE).build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-1")).willReturn(raw(buildOrderDetail("order-1")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("65d4a428f784bb001956f919", merchantId))
                .willReturn(Optional.of(mappedProduct));
        given(includeRepository.findByProductIdAndProductMerchantId(mappedProduct.getId(), merchantId))
                .willReturn(List.of(Include.builder().product(mappedProduct).name(costIng.getName()).cost(costIng.getCostPerUnit()).quantity(BigDecimal.ONE).build()));
        given(orderCostCalculatorService.computeOrderTotalCost(any(Order.class)))
                .willReturn(new BigDecimal("4.00"));

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getEstimatedProfit()).isEqualByComparingTo("6.00");
        assertThat(orderCaptor.getValue().getTotalCost()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("syncOrders deve ignorar pedidos com salesChannel=ifood — apenas Anota.AI é importado")
    void syncOrders_shouldSkipIfoodOrders() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key"))
                .willReturn(buildOrderListWithSalesChannel("order-ifood", "ifood"));

        AnotaAISyncResult result = syncService.syncOrders(merchantId);

        assertThat(result.getOrdersImported()).isZero();
        assertThat(result.getOrdersSkipped()).isEqualTo(1);
        verify(anotaAIClient, never()).getOrderDetail(anyString(), anyString());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncOrders NÃO deve salvar pedido existente quando origin já está correta")
    void syncOrders_shouldNotSaveExistingOrderWhenOriginIsAlreadyCorrect() {
        Order existingOrder = Order.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .externalOrderId("order-anota")
                .origin(OrderOrigin.ANOTA_AI)
                .build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key"))
                .willReturn(buildOrderListWithSalesChannel("order-anota", "anotaai"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-anota", merchantId)).willReturn(true);

        syncService.syncOrders(merchantId);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("syncOrders deve manter origin=ANOTA_AI para outros salesChannel")
    void syncOrders_shouldDefaultToAnotaAiOriginForNonIfoodChannels() {
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Refrigerante 1L")
                .status(ProductStatus.ACTIVE).externalId("65d4a428f784bb001956f919").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key"))
                .willReturn(buildOrderListWithSalesChannel("order-whats", "anotaai"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-whats", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-whats"))
                .willReturn(raw(buildOrderDetail("order-whats")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(productRepository.findByExternalIdAndMerchantId("65d4a428f784bb001956f919", merchantId))
                .willReturn(Optional.of(mappedProduct));

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrigin()).isEqualTo(OrderOrigin.ANOTA_AI);
    }

    @Test
    @DisplayName("importOrder deve usar detail.total como totalValue — deliveryFee já está inclusa no total da Anota.AI")
    void importOrder_shouldPersistDeliveryFeeAndUseItInProfit() {
        // detail.total para Anota.AI já inclui a taxa de entrega (confirmado: item.total + deliveryFee = detail.total).
        // Não somar deliveryFee novamente — evita inflação do totalValue.
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 500ml")
                .status(ProductStatus.ACTIVE).externalId("65d4a428f784bb001956f919").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-1"))
                .willReturn(raw(buildOrderDetailWithDeliveryFee("order-1", 25.80, 6.00)));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("65d4a428f784bb001956f919", merchantId))
                .willReturn(Optional.of(mappedProduct));
        given(includeRepository.findByProductIdAndProductMerchantId(mappedProduct.getId(), merchantId))
                .willReturn(List.of());
        given(orderCostCalculatorService.computeOrderTotalCost(any(Order.class)))
                .willReturn(BigDecimal.ZERO);

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getDeliveryFee()).isEqualByComparingTo("6.00");
        // totalValue = detail.total (25.80) — deliveryFee já está inclusa, não somar novamente
        assertThat(saved.getTotalValue()).isEqualByComparingTo("25.80");
        // estimatedProfit = (totalValue − deliveryFee) − totalCost = (25.80 − 6.00) − 0 = 19.80
        assertThat(saved.getEstimatedProfit()).isEqualByComparingTo("19.80");
        assertThat(saved.getTotalCost()).isEqualByComparingTo("0");
        // sem additionalFees no payload, serviceFee fica nulo — comportamento inalterado
        assertThat(saved.getServiceFee()).isNull();
    }

    @Test
    @DisplayName("importOrder deve persistir a taxa de serviço (additionalFees) e excluí-la do lucro")
    void importOrder_shouldPersistServiceFeeAndExcludeFromProfit() {
        // Pedidos do canal iFood via Anota.AI trazem additionalFees (ex.: RESTAURANT_SERVICE_FEE,
        // "Taxa de serviço"). Esse valor está incluso no detail.total mas é repasse ao iFood —
        // não é receita do restaurante e deve ser excluído do lucro.
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 500ml")
                .status(ProductStatus.ACTIVE).externalId("65d4a428f784bb001956f919").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-1"))
                .willReturn(raw(buildOrderDetailWithServiceFee("order-1", 26.79, 6.00, 0.99)));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("65d4a428f784bb001956f919", merchantId))
                .willReturn(Optional.of(mappedProduct));
        given(includeRepository.findByProductIdAndProductMerchantId(mappedProduct.getId(), merchantId))
                .willReturn(List.of());
        given(orderCostCalculatorService.computeOrderTotalCost(any(Order.class)))
                .willReturn(BigDecimal.ZERO);

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getServiceFee()).isEqualByComparingTo("0.99");
        assertThat(saved.getDeliveryFee()).isEqualByComparingTo("6.00");
        assertThat(saved.getTotalValue()).isEqualByComparingTo("26.79");
        // estimatedProfit = totalValue − deliveryFee − serviceFee − totalCost
        //                 = 26.79 − 6.00 − 0.99 − 0 = 19.80
        assertThat(saved.getEstimatedProfit()).isEqualByComparingTo("19.80");
    }

    @Test
    @DisplayName("syncOrders deve pular pedidos já importados")
    void syncOrders_shouldSkipAlreadyImportedOrders() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-1", merchantId)).willReturn(true);

        AnotaAISyncResult result = syncService.syncOrders(merchantId);

        assertThat(result.getOrdersImported()).isZero();
        assertThat(result.getOrdersSkipped()).isEqualTo(1);
        verify(anotaAIClient, never()).getOrderDetail(anyString(), anyString());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncOrders deve criar cliente novo se não existir")
    void syncOrders_shouldCreateCustomerIfNotFound() {
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Refrigerante 1L")
                .status(ProductStatus.ACTIVE).externalId("65d4a428f784bb001956f919").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-1")).willReturn(raw(buildOrderDetail("order-1")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId)).willReturn(Optional.empty());
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(inv -> { Customer c = inv.getArgument(0); c.setId(UUID.randomUUID()); return c; });
        given(productRepository.findByExternalIdAndMerchantId("65d4a428f784bb001956f919", merchantId))
                .willReturn(Optional.of(mappedProduct));

        AnotaAISyncResult result = syncService.syncOrders(merchantId);

        assertThat(result.getOrdersImported()).isEqualTo(1);
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer created = customerCaptor.getValue();
        assertThat(created.getName()).isEqualTo("Teste");
        assertThat(created.getPhone()).isEqualTo("43123456789");
        assertThat(created.getMerchant().getId()).isEqualTo(merchantId);
    }

    @Test
    @DisplayName("syncOrders deve lançar exceção se usuário não tiver API key")
    void syncOrders_shouldFailIfNoApiKey() {
        Merchant userWithoutKey = Merchant.builder().id(merchantId).build();
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(userWithoutKey));

        assertThatThrownBy(() -> syncService.syncOrders(merchantId))
                .isInstanceOf(AnotaAIIntegrationException.class);
    }

    // -------------------------------------------------------------------------
    // syncOrders — match de extras por nome canônico (refactor central)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("syncOrders deve gravar no item a margem ideal do produto (snapshot na importação)")
    void syncOrders_shouldSnapshotProductTargetMarginPctOnItem() {
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 500ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id")
                .targetMarginPct(new BigDecimal("48.00")).build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-2"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-2", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-2"))
                .willReturn(raw(buildOrderDetailWithSubItems("order-2")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID())
                        .merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(mappedProduct));

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getItems().get(0).getTargetMarginPct())
                .isEqualByComparingTo(new BigDecimal("48.00"));
    }

    @Test
    @DisplayName("syncOrders deve resolver extra ingredient por canonical name (normalizado)")
    void syncOrders_shouldMatchExtraIngredientByCanonicalName() {
        Ingredient acaiPremium = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí Premium").unit("ml")
                .costPerUnit(new BigDecimal("0.05")).defaultQuantity(new BigDecimal("500"))
                .status(IngredientStatus.ACTIVE).build();
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 500ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-2"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-2", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-2"))
                .willReturn(raw(buildOrderDetailWithSubItems("order-2")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(mappedProduct));
        // subItem.name = "Açaí Premium" → canonical "acai premium"
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("acai premium", merchantId))
                .willReturn(Optional.of(acaiPremium));

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getExtraIngredients()).hasSize(1);
        var extra = saved.getItems().get(0).getExtraIngredients().get(0);
        assertThat(extra.getIngredient()).isEqualTo(acaiPremium);
        // quantity = subItem.quantity (1) × ingredient.defaultQuantity (500) = 500
        assertThat(extra.getQuantity()).isEqualByComparingTo("500");
        assertThat(extra.getCostPerUnit()).isEqualByComparingTo("0.05");
        assertThat(extra.getIngredientName()).isEqualTo("Açaí Premium");
        assertThat(extra.getIngredientUnit()).isEqualTo("ml");
        // Nenhuma notificação foi criada — ingrediente foi encontrado
        verify(notificationService, never()).createMissingIngredient(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("syncOrders deve respeitar subItem.quantity ao montar OrderItemExtraIngredient")
    void syncOrders_shouldRespectSubItemQuantityForExtra() {
        Ingredient leiteNinho = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("leite ninho").unit("g")
                .costPerUnit(new BigDecimal("0.0533"))
                .defaultQuantity(new BigDecimal("20"))
                .status(IngredientStatus.ACTIVE).build();
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 330ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-q2"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-q2", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-q2"))
                .willReturn(raw(AnotaAIFixtures.load("order_detail_with_subitem_quantity_two.json",
                        AnotaAIOrderDetailResponse.class)));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(mappedProduct));
        // subItem.name = "leite ninho" → canonical "leite ninho"
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("leite ninho", merchantId))
                .willReturn(Optional.of(leiteNinho));

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        var extra = orderCaptor.getValue().getItems().get(0).getExtraIngredients().get(0);
        // quantity = subItem.quantity (2) × ingredient.defaultQuantity (20) = 40 g
        assertThat(extra.getQuantity()).isEqualByComparingTo("40");
        assertThat(extra.getCostPerUnit()).isEqualByComparingTo("0.0533");
    }

    @Test
    @DisplayName("syncOrders deve criar notificação e pular extra quando ingrediente não está cadastrado")
    void syncOrders_shouldCreateMissingIngredientNotificationWhenNotFound() {
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 500ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-3"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-3", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-3"))
                .willReturn(raw(buildOrderDetailWithSubItems("order-3")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(mappedProduct));
        // Ingrediente "Açaí Premium" não encontrado
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("acai premium", merchantId))
                .willReturn(Optional.empty());

        AnotaAISyncResult result = syncService.syncOrders(merchantId);

        // Pedido foi importado, mas sem o extra
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getItems().get(0).getExtraIngredients()).isEmpty();

        // Notificação criada
        verify(notificationService).createMissingIngredient("Açaí Premium", "acai premium", merchantId);
        // Nome aparece no resultado
        assertThat(result.getMissingIngredientNames()).containsExactly("Açaí Premium");
    }

    @Test
    @DisplayName("syncOrders deve ouvinte notificar uma única vez para o mesmo nome quando aparece em múltiplos pedidos do mesmo sync")
    void syncOrders_shouldReportDistinctMissingNamesAcrossOrders() {
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 500ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        // 2 pedidos com o mesmo subItem "Açaí Premium" faltando
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderListWith2Orders("ord-A", "ord-B"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "ord-A"))
                .willReturn(raw(buildOrderDetailWithSubItems("ord-A")));
        given(anotaAIClient.getOrderDetail("test-api-key", "ord-B"))
                .willReturn(raw(buildOrderDetailWithSubItems("ord-B")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(mappedProduct));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("acai premium", merchantId))
                .willReturn(Optional.empty());

        AnotaAISyncResult result = syncService.syncOrders(merchantId);

        // Nome único no resultado (não duplica)
        assertThat(result.getMissingIngredientNames()).containsExactly("Açaí Premium");
        // Notificação foi chamada (a dedupe acontece no NotificationService, então pode ser chamada 2x)
        verify(notificationService, times(2))
                .createMissingIngredient("Açaí Premium", "acai premium", merchantId);
    }

    // -------------------------------------------------------------------------
    // syncOrders — catalog sync on product miss
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("syncOrders deve acionar syncCatalog quando produto não é encontrado e importar pedido após retry")
    void syncOrders_shouldTriggerCatalogSyncAndRetryWhenProductNotFound() {
        Product foundAfterSync = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 500ml")
                .status(ProductStatus.ACTIVE).externalId("65d4a428f784bb001956f919").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-1")).willReturn(raw(buildOrderDetail("order-1")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));

        // Primeira busca: produto não encontrado; segunda (após syncCatalog): encontrado
        given(productRepository.findByExternalIdAndMerchantId("65d4a428f784bb001956f919", merchantId))
                .willReturn(Optional.empty(), Optional.of(foundAfterSync));

        // Setup para o syncCatalog chamado internamente
        given(anotaAIClient.getCatalog("test-api-key")).willReturn(buildCatalog());
        given(categoryRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId))).willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(inv -> { Category c = inv.getArgument(0); c.setId(UUID.randomUUID()); return c; });
        given(productRepository.findByExternalIdAndMerchantId(eq("item-1"), eq(merchantId))).willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId(eq("item-2"), eq(merchantId))).willReturn(Optional.empty());

        AnotaAISyncResult result = syncService.syncOrders(merchantId);

        verify(anotaAIClient).getCatalog("test-api-key");
        assertThat(result.getOrdersImported()).isEqualTo(1);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("syncOrders deve acionar syncCatalog apenas uma vez por execução mesmo com múltiplos produtos não encontrados")
    void syncOrders_shouldTriggerCatalogSyncOnlyOncePerRun() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderListWith2Orders("ord-A", "ord-B"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "ord-A")).willReturn(raw(buildOrderDetail("ord-A")));
        given(anotaAIClient.getOrderDetail("test-api-key", "ord-B")).willReturn(raw(buildOrderDetail("ord-B")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));

        // Produto nunca encontrado — mesmo após syncCatalog
        given(productRepository.findByExternalIdAndMerchantId(eq("65d4a428f784bb001956f919"), eq(merchantId)))
                .willReturn(Optional.empty());

        // Setup para o syncCatalog
        given(anotaAIClient.getCatalog("test-api-key")).willReturn(buildCatalog());
        given(categoryRepository.findByExternalIdAndMerchantId(anyString(), eq(merchantId))).willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(inv -> { Category c = inv.getArgument(0); c.setId(UUID.randomUUID()); return c; });
        given(productRepository.findByExternalIdAndMerchantId(eq("item-1"), eq(merchantId))).willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId(eq("item-2"), eq(merchantId))).willReturn(Optional.empty());

        syncService.syncOrders(merchantId);

        // getCatalog acionado exatamente uma vez para os dois pedidos
        verify(anotaAIClient, times(1)).getCatalog("test-api-key");
    }

    // -------------------------------------------------------------------------
    // syncOrders — deduplicação de subItems com mesmo nome canônico
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("syncOrders deve criar um extra separado para cada subItem, mesmo que tenham o mesmo nome")
    void syncOrders_shouldCreateSeparateExtraForEachSubItem() {
        // Cenário: leite ninho aparece 2x, chocoball aparece 2x, morango aparece 1x.
        // Esperado: 5 extras distintos — um por subItem, sem deduplicação.
        Ingredient leiteNinho = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build())
                .name("leite ninho").unit("un")
                .costPerUnit(new BigDecimal("0.0533"))
                .defaultQuantity(new BigDecimal("20"))
                .status(IngredientStatus.ACTIVE).build();
        Ingredient chocoball = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build())
                .name("chocoball").unit("un")
                .costPerUnit(new BigDecimal("0.066"))
                .defaultQuantity(new BigDecimal("20"))
                .status(IngredientStatus.ACTIVE).build();
        Ingredient morango = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build())
                .name("morango").unit("un")
                .costPerUnit(new BigDecimal("0.01"))
                .defaultQuantity(new BigDecimal("1"))
                .status(IngredientStatus.ACTIVE).build();
        Product acai330 = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 330ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-dedup-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-dedup-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-dedup-1"))
                .willReturn(raw(AnotaAIFixtures.load("order_detail_duplicate_subitems.json",
                        AnotaAIOrderDetailResponse.class)));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID())
                        .merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(acai330));
        given(includeRepository.findByProductIdAndProductMerchantId(acai330.getId(), merchantId))
                .willReturn(List.of());
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("leite ninho", merchantId))
                .willReturn(Optional.of(leiteNinho));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("chocoball", merchantId))
                .willReturn(Optional.of(chocoball));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("morango", merchantId))
                .willReturn(Optional.of(morango));

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        var extras = orderCaptor.getValue().getItems().get(0).getExtraIngredients();

        // 5 extras — um por subItem (leite ninho ×2, chocoball ×2, morango ×1)
        assertThat(extras).hasSize(5);

        var leiteNinhoExtras = extras.stream()
                .filter(e -> e.getIngredientName().equals("leite ninho")).toList();
        var chocoballExtras = extras.stream()
                .filter(e -> e.getIngredientName().equals("chocoball")).toList();
        var morangoExtras = extras.stream()
                .filter(e -> e.getIngredientName().equals("morango")).toList();

        assertThat(leiteNinhoExtras).hasSize(2);
        leiteNinhoExtras.forEach(e -> assertThat(e.getQuantity()).isEqualByComparingTo("20"));
        assertThat(chocoballExtras).hasSize(2);
        chocoballExtras.forEach(e -> assertThat(e.getQuantity()).isEqualByComparingTo("20"));
        assertThat(morangoExtras).hasSize(1);
        assertThat(morangoExtras.get(0).getQuantity()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("syncOrders deve criar extras separados para subItems com mesmo nome em case diferente")
    void syncOrders_shouldCreateSeparateExtrasForCaseInsensitiveSameNameSubItems() {
        // Cenário: "Leite Ninho" e "leite ninho" → mesmo ingrediente, mas subItems diferentes
        // Esperado: 2 extras distintos, cada um com defaultQuantity (20g)
        Ingredient leiteNinho = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build())
                .name("leite ninho").unit("un")
                .costPerUnit(new BigDecimal("0.0533"))
                .defaultQuantity(new BigDecimal("20"))
                .status(IngredientStatus.ACTIVE).build();
        Product acai330 = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 330ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();

        AnotaAIOrderDetailResponse response = AnotaAIFixtures.load("order_detail_duplicate_subitems.json",
                AnotaAIOrderDetailResponse.class);
        response.getInfo().getItems().get(0).getSubItems().get(2).setName("Leite Ninho");
        response.getInfo().setId("order-dedup-2");

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-dedup-2"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-dedup-2", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-dedup-2")).willReturn(raw(response));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID())
                        .merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(acai330));
        given(includeRepository.findByProductIdAndProductMerchantId(acai330.getId(), merchantId))
                .willReturn(List.of());
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("leite ninho", merchantId))
                .willReturn(Optional.of(leiteNinho));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("chocoball", merchantId))
                .willReturn(Optional.empty());
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("morango", merchantId))
                .willReturn(Optional.empty());

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        var extras = orderCaptor.getValue().getItems().get(0).getExtraIngredients();

        var leiteNinhoExtras = extras.stream()
                .filter(e -> e.getIngredientName().equals("leite ninho")).toList();
        // 2 subItems distintos → 2 extras, cada um com 20g
        assertThat(leiteNinhoExtras).hasSize(2);
        leiteNinhoExtras.forEach(e -> assertThat(e.getQuantity()).isEqualByComparingTo("20"));
    }

    // -------------------------------------------------------------------------
    // syncOrders — quantidade específica por produto vs default global
    // (Include na ficha técnica do produto sobrescreve Ingredient.defaultQuantity)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("syncOrders NÃO deve criar extra quando subItem casa com Include do produto (regra: Include é autoritativo)")
    void syncOrders_shouldNotCreateExtraWhenSubItemMatchesProductInclude() {
        // Cenário: Açaí 330ml tem Include "leite ninho" 40g na ficha técnica.
        // Pedido tem subItem "leite ninho" qty=2.
        // Regra atual: Include é autoritativo — o subItem é pulado, nenhum extra é criado.
        // O leite ninho aparece apenas como insumo da ficha técnica.
        Ingredient leiteNinhoGlobal = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build())
                .name("leite ninho").unit("g")
                .costPerUnit(new BigDecimal("0.0533"))
                .defaultQuantity(new BigDecimal("20"))
                .status(IngredientStatus.ACTIVE).build();
        Product acai330 = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 330ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();
        Include leiteNinhoNoAcai330 = Include.builder()
                .id(UUID.randomUUID()).product(acai330)
                .name("leite ninho")
                .cost(new BigDecimal("0.0533"))
                .quantity(new BigDecimal("40"))
                .build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-pps-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-pps-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-pps-1"))
                .willReturn(raw(AnotaAIFixtures.load("order_detail_with_subitem_quantity_two.json",
                        AnotaAIOrderDetailResponse.class)));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(acai330));
        given(includeRepository.findByProductIdAndProductMerchantId(acai330.getId(), merchantId))
                .willReturn(List.of(leiteNinhoNoAcai330));
        // ingredientRepository não é consultado: subItem é pulado antes pelo match com Include

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        // Include é autoritativo: nenhum extra criado
        assertThat(orderCaptor.getValue().getItems().get(0).getExtraIngredients()).isEmpty();
    }

    @Test
    @DisplayName("syncOrders deve usar a quantidade do Include do produto quando houver Include INGREDIENT correspondente ao subItem")
    void syncOrders_shouldUseProductIncludeQuantityWhenIngredientIncludeMatchesSubItem() {
        // Cenário: produto "Açaí 500ml" tem Include INGREDIENT "Açaí Premium" com qty=250g.
        // Ingrediente "Açaí Premium" tem defaultQuantity=100g (qty global).
        // Pedido tem subItem "Açaí Premium" qty=1.
        // Esperado: extra criado com qty=250g (Include específico), não 100g (default global).
        Ingredient acaiPremium = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build())
                .name("Açaí Premium").unit("g")
                .costPerUnit(new BigDecimal("0.05"))
                .defaultQuantity(new BigDecimal("100"))
                .status(IngredientStatus.ACTIVE).build();
        Product acai500 = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 500ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();
        Include acaiPremiumInclude = Include.builder()
                .id(UUID.randomUUID()).product(acai500)
                .name("Açaí Premium")
                .cost(new BigDecimal("0.05"))
                .quantity(new BigDecimal("250"))
                .kind(IncludeKind.INGREDIENT)
                .build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-piq-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-piq-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-piq-1"))
                .willReturn(raw(buildOrderDetailWithSubItems("order-piq-1")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(acai500));
        given(includeRepository.findByProductIdAndProductMerchantId(acai500.getId(), merchantId))
                .willReturn(List.of(acaiPremiumInclude));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("acai premium", merchantId))
                .willReturn(Optional.of(acaiPremium));

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        var extra = orderCaptor.getValue().getItems().get(0).getExtraIngredients().get(0);
        // qty = Include.quantity (250g), NÃO ingredient.defaultQuantity (100g)
        assertThat(extra.getQuantity()).isEqualByComparingTo("250");
        assertThat(extra.getCostPerUnit()).isEqualByComparingTo("0.05");
    }

    @Test
    @DisplayName("syncOrders deve fazer fallback para Ingredient.defaultQuantity quando não houver Include específico no produto")
    void syncOrders_shouldFallbackToGlobalIngredientDefaultWhenNoProductSpecificIncludeMatches() {
        // Cenário: produto não tem Include com nome "leite ninho" — só tem "copo".
        // Esperado: usar o defaultQuantity global (20g) × subItem.quantity (2) = 40g.
        Ingredient leiteNinhoGlobal = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build())
                .name("leite ninho").unit("g")
                .costPerUnit(new BigDecimal("0.0533"))
                .defaultQuantity(new BigDecimal("20"))
                .status(IngredientStatus.ACTIVE).build();
        Product acai330 = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 330ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();
        Include outroInclude = Include.builder()
                .id(UUID.randomUUID()).product(acai330)
                .name("copo")
                .cost(new BigDecimal("0.50"))
                .quantity(BigDecimal.ONE)
                .build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-pps-2"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-pps-2", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-pps-2"))
                .willReturn(raw(AnotaAIFixtures.load("order_detail_with_subitem_quantity_two.json",
                        AnotaAIOrderDetailResponse.class)));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(acai330));
        given(includeRepository.findByProductIdAndProductMerchantId(acai330.getId(), merchantId))
                .willReturn(List.of(outroInclude));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("leite ninho", merchantId))
                .willReturn(Optional.of(leiteNinhoGlobal));

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        var extra = orderCaptor.getValue().getItems().get(0).getExtraIngredients().get(0);
        // 20 (default global) × 2 (subItem.quantity) = 40g
        assertThat(extra.getQuantity()).isEqualByComparingTo("40");
        assertThat(extra.getCostPerUnit()).isEqualByComparingTo("0.0533");
    }

    @Test
    @DisplayName("syncOrders — quando ingrediente está no Include, subItems não duplicam o custo, mesmo com qty>1")
    void syncOrders_shouldNotDuplicateIngredientWhenItIsAlreadyInProductRecipe() {
        // Regra atual (Include autoritativo): se "leite ninho" já está na ficha técnica do
        // produto via Include, subItems com mesmo nome são pulados — não viram extras.
        // Isso evita double-counting (custo aparecia tanto no Include quanto no Extra).
        // O custo do leite ninho vem 100% da ficha técnica (40g × 0,05 = 2,00 por produto).
        Ingredient leiteNinhoGlobal = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build())
                .name("leite ninho").unit("g")
                .costPerUnit(new BigDecimal("0.05"))
                .defaultQuantity(new BigDecimal("20"))
                .status(IngredientStatus.ACTIVE).build();
        Product acai330 = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 330ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();
        Include leiteNinhoNoAcai330 = Include.builder()
                .id(UUID.randomUUID()).product(acai330)
                .name("leite ninho")
                .cost(new BigDecimal("0.05"))
                .quantity(new BigDecimal("40"))
                .build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-pps-3"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-pps-3", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-pps-3"))
                .willReturn(raw(AnotaAIFixtures.load("order_detail_acai_330_three_leite_ninho.json",
                        AnotaAIOrderDetailResponse.class)));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(acai330));
        given(includeRepository.findByProductIdAndProductMerchantId(acai330.getId(), merchantId))
                .willReturn(List.of(leiteNinhoNoAcai330));
        // ingredientRepository não é consultado: subItem é pulado antes pelo match com Include

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        // Nenhum extra: leite ninho está na ficha técnica e não é duplicado
        assertThat(orderCaptor.getValue().getItems().get(0).getExtraIngredients()).isEmpty();
    }

    @Test
    @DisplayName("syncOrders — match case-insensitive entre Include.name e subItem (canonical) também pula extra")
    void syncOrders_shouldSkipExtraWhenIncludeNameMatchesSubItemCaseInsensitively() {
        // Cenário: Include.name = "Leite Ninho" (case mixed); subItem.name = "leite ninho".
        // O match via canonical name deve detectar a equivalência e pular o extra,
        // mesmo com a diferença de capitalização.
        Ingredient leiteNinhoGlobal = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build())
                .name("leite ninho").unit("g")
                .costPerUnit(new BigDecimal("0.0533"))
                .defaultQuantity(new BigDecimal("20"))
                .status(IngredientStatus.ACTIVE).build();
        Product acai330 = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 330ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();
        Include leiteNinhoNoAcai330 = Include.builder()
                .id(UUID.randomUUID()).product(acai330)
                .name("Leite Ninho") // case e capitalização diferentes do canonical "leite ninho"
                .cost(new BigDecimal("0.08"))
                .quantity(new BigDecimal("40"))
                .build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-pps-4"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-pps-4", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-pps-4"))
                .willReturn(raw(AnotaAIFixtures.load("order_detail_with_subitem_quantity_two.json",
                        AnotaAIOrderDetailResponse.class)));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(acai330));
        given(includeRepository.findByProductIdAndProductMerchantId(acai330.getId(), merchantId))
                .willReturn(List.of(leiteNinhoNoAcai330));
        // ingredientRepository não é consultado: subItem é pulado pelo match canonical com Include

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        // Match canonical funciona com case diferente: nenhum extra
        assertThat(orderCaptor.getValue().getItems().get(0).getExtraIngredients()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // syncOrders — PACKAGING autoritativo (não cria extra quando subItem casa com embalagem)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("syncOrders NÃO deve criar OrderItemExtraIngredient quando subItem casa com Include PACKAGING")
    void syncOrders_shouldSkipExtraWhenSubItemMatchesInclude() {
        // Cenário: Açaí 500ml tem Include PACKAGING "Copo 500ml" (embalagem, sempre presente).
        // Pedido chega com subItem "Copo 500ml" qty=1.
        // Esperado: NENHUM OrderItemExtraIngredient criado — embalagem (PACKAGING) já está na
        // base do pedido. Apenas PACKAGING é autoritativo: um match com INGREDIENT viraria extra.
        Ingredient copo = Ingredient.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build())
                .name("Copo 500ml").unit("g")
                .costPerUnit(new BigDecimal("0.0217"))
                .defaultQuantity(new BigDecimal("100"))
                .status(IngredientStatus.ACTIVE).build();
        Product acai500 = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí 500ml")
                .status(ProductStatus.ACTIVE).externalId("product-internal-id").build();
        Include copoInclude = Include.builder()
                .id(UUID.randomUUID()).product(acai500)
                .name("Copo 500ml")
                .cost(new BigDecimal("0.0217"))
                .quantity(new BigDecimal("240"))
                .kind(IncludeKind.PACKAGING)
                .build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-skip-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-skip-1", merchantId)).willReturn(false);
        AnotaAIOrderDetailResponse detail = AnotaAIFixtures.load(
                "order_detail_with_subitem_quantity_two.json", AnotaAIOrderDetailResponse.class);
        // Reescreve o subItem para casar com o Include PACKAGING "Copo 500ml"
        detail.getInfo().getItems().get(0).getSubItems().get(0).setName("Copo 500ml");
        detail.getInfo().getItems().get(0).getSubItems().get(0).setQuantity(1);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-skip-1")).willReturn(raw(detail));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(feeRepository.findByNameIgnoreCaseAndMerchantId("money", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByExternalIdAndMerchantId("product-internal-id", merchantId))
                .willReturn(Optional.of(acai500));
        given(includeRepository.findByProductIdAndProductMerchantId(acai500.getId(), merchantId))
                .willReturn(List.of(copoInclude));
        // Stub lenient: o Ingredient existe no merchant, mas a busca não será feita
        // porque o subItem casa com Include PACKAGING e é pulado antes.
        org.mockito.Mockito.lenient()
                .when(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("copo 500ml", merchantId))
                .thenReturn(Optional.of(copo));

        syncService.syncOrders(merchantId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        var item = orderCaptor.getValue().getItems().get(0);
        assertThat(item.getExtraIngredients())
                .as("subItem que casa com Include PACKAGING não deve criar OrderItemExtraIngredient")
                .isEmpty();
        // Nenhuma notificação de ingrediente ausente: o ingrediente existe, só não vira extra
        verify(notificationService, org.mockito.Mockito.never())
                .createMissingIngredient(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // syncOrders — payload bruto para auditoria
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("syncOrders deve salvar o payload bruto do pedido importado para auditoria")
    void syncOrders_shouldSaveRawPayloadForImportedOrder() {
        Product mappedProduct = Product.builder()
                .id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Refrigerante 1L")
                .status(ProductStatus.ACTIVE).externalId("65d4a428f784bb001956f919").build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-1")).willReturn(raw(buildOrderDetail("order-1")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willReturn(Optional.of(Customer.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).build()));
        given(productRepository.findByExternalIdAndMerchantId("65d4a428f784bb001956f919", merchantId))
                .willReturn(Optional.of(mappedProduct));

        syncService.syncOrders(merchantId);

        verify(rawPayloadService).save(merchantId, OrderOrigin.ANOTA_AI, "order-1", RAW_JSON);
    }

    @Test
    @DisplayName("syncOrders NÃO deve salvar payload bruto quando o import do pedido falha")
    void syncOrders_shouldNotSaveRawPayloadWhenImportFails() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(anotaAIClient.getOrderList("test-api-key")).willReturn(buildOrderList("order-1"));
        given(orderRepository.existsByExternalOrderIdAndMerchantId("order-1", merchantId)).willReturn(false);
        given(anotaAIClient.getOrderDetail("test-api-key", "order-1")).willReturn(raw(buildOrderDetail("order-1")));
        given(customerRepository.findByPhoneAndMerchantId("43123456789", merchantId))
                .willThrow(new RuntimeException("boom"));

        AnotaAISyncResult result = syncService.syncOrders(merchantId);

        assertThat(result.getErrors()).isNotEmpty();
        verify(rawPayloadService, never()).save(any(), any(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static final String RAW_JSON = "{\"fixture\":\"raw\"}";

    private RawJsonResponse<AnotaAIOrderDetailResponse> raw(AnotaAIOrderDetailResponse response) {
        return new RawJsonResponse<>(response, RAW_JSON);
    }

    private AnotaAICatalogResponse buildCatalog() {
        return AnotaAIFixtures.load("catalog_minimal.json", AnotaAICatalogResponse.class);
    }

    private AnotaAICatalogResponse buildCatalogWithAdditionals() {
        return AnotaAIFixtures.load("catalog_with_additionals.json", AnotaAICatalogResponse.class);
    }

    private AnotaAIOrderListResponse buildOrderList(String orderId) {
        return buildOrderListWithSalesChannel(orderId, "anotaai");
    }

    private AnotaAIOrderListResponse buildOrderListWithSalesChannel(String orderId, String salesChannel) {
        AnotaAIOrderListResponse response = AnotaAIFixtures.load("order_list_template.json",
                AnotaAIOrderListResponse.class);
        AnotaAIOrderListResponse.OrderSummary summary = response.getInfo().getDocs().get(0);
        summary.setId(orderId);
        summary.setSalesChannel(salesChannel);
        return response;
    }

    private AnotaAIOrderListResponse buildOrderListWith2Orders(String idA, String idB) {
        AnotaAIOrderListResponse response = AnotaAIFixtures.load("order_list_template.json",
                AnotaAIOrderListResponse.class);
        AnotaAIOrderListResponse.OrderSummary first = response.getInfo().getDocs().get(0);
        first.setId(idA);
        first.setSalesChannel("anotaai");
        AnotaAIOrderListResponse.OrderSummary second = new AnotaAIOrderListResponse.OrderSummary();
        second.setId(idB);
        second.setSalesChannel("anotaai");
        second.setFrom(first.getFrom());
        second.setUpdatedAt(first.getUpdatedAt());
        response.getInfo().getDocs().add(second);
        return response;
    }

    private AnotaAIOrderDetailResponse buildOrderDetailWithSubItems(String orderId) {
        AnotaAIOrderDetailResponse response = AnotaAIFixtures.load("order_detail_with_subitems.json",
                AnotaAIOrderDetailResponse.class);
        response.getInfo().setId(orderId);
        return response;
    }

    private AnotaAIOrderDetailResponse buildOrderDetailWithDeliveryFee(String orderId, double total, double deliveryFee) {
        AnotaAIOrderDetailResponse response = buildOrderDetail(orderId);
        response.getInfo().setTotal(total);
        response.getInfo().setDeliveryFee(deliveryFee);
        return response;
    }

    private AnotaAIOrderDetailResponse buildOrderDetailWithServiceFee(String orderId, double total,
                                                                      double deliveryFee, double serviceFeeValue) {
        AnotaAIOrderDetailResponse response = buildOrderDetailWithDeliveryFee(orderId, total, deliveryFee);
        AnotaAIOrderDetailResponse.AdditionalFee fee = new AnotaAIOrderDetailResponse.AdditionalFee();
        fee.setType("RESTAURANT_SERVICE_FEE_INADIMPLENCIA");
        fee.setDescription("Taxa de serviço");
        fee.setValue(serviceFeeValue);
        response.getInfo().setAdditionalFees(List.of(fee));
        return response;
    }

    private AnotaAIOrderDetailResponse buildOrderDetail(String orderId) {
        AnotaAIOrderDetailResponse response = AnotaAIFixtures.load("order_detail_simple.json",
                AnotaAIOrderDetailResponse.class);
        response.getInfo().setId(orderId);
        return response;
    }
}
