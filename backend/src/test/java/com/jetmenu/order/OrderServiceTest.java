package com.jetmenu.order;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;

import com.jetmenu.customer.Customer;
import com.jetmenu.customer.CustomerRepository;
import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientNotFoundException;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.ingredient.IngredientStatus;
import com.jetmenu.fee.Fee;
import com.jetmenu.fee.FeeRepository;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import com.jetmenu.product.Include;
import com.jetmenu.product.IncludeKind;
import com.jetmenu.product.IncludeRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private FeeRepository feeRepository;

    @Mock
    private IncludeRepository includeRepository;

    @Mock
    private com.jetmenu.product.OrderCostCalculatorService orderCostCalculatorService;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private OrderFichaService orderFichaService;

    @InjectMocks
    private OrderService orderService;

    private UUID orderId;
    private UUID merchantId;
    private UUID customerId;
    private UUID productId;
    private UUID ingredientId;
    private Customer customer;
    private Product product;
    private Ingredient ingredient;
    private Order order;
    private OrderRequest orderRequest;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        lenient().when(merchantRepository.getReferenceById(any())).thenReturn(Merchant.builder().id(merchantId).build());
        customerId = UUID.randomUUID();
        productId = UUID.randomUUID();
        ingredientId = UUID.randomUUID();

        customer = Customer.builder()
                .id(customerId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Cliente Teste")
                .phone("11999999999")
                .email("cliente@email.com")
                .build();

        product = Product.builder()
                .id(productId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Hambúrguer")
                .price(new BigDecimal("30.00"))
                .status(ProductStatus.ACTIVE)
                .build();

        ingredient = Ingredient.builder()
                .id(ingredientId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Bacon")
                .unit("g")
                .costPerUnit(new BigDecimal("0.10"))
                .status(IngredientStatus.ACTIVE)
                .build();

        // Mock receita do produto: 1 include (insumo/embalagem) × custo 12.00 = unitCost 12.00
        Include defaultRecipe = Include.builder()
                .product(product).name("CustoBase")
                .cost(new BigDecimal("12.00"))
                .quantity(BigDecimal.ONE)
                .kind(IncludeKind.PACKAGING).build();
        lenient().when(includeRepository.findByProductIdAndProductMerchantId(productId, merchantId))
                .thenReturn(List.of(defaultRecipe));

        // Cálculo padrão dos testes simples: 2 items × 12.00 base = 24.00.
        // Testes com extras sobrescrevem este mock.
        lenient().when(orderCostCalculatorService.computeOrderTotalCost(any(Order.class)))
                .thenReturn(new BigDecimal("24.00"));

        OrderItemRequest itemRequest = OrderItemRequest.builder()
                .productId(productId)
                .quantity(2)
                .build();

        orderRequest = OrderRequest.builder()
                .customerId(customerId)
                .items(List.of(itemRequest))
                .build();

        OrderItem orderItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(2)
                .unitPrice(new BigDecimal("30.00"))
                .unitCost(new BigDecimal("12.00"))
                .build();

        order = Order.builder()
                .id(orderId)
                .merchant(Merchant.builder().id(merchantId).build())
                .dateTime(LocalDateTime.now())
                .customer(customer)
                .status(OrderStatus.PAID)
                .totalValue(new BigDecimal("60.00"))
                .estimatedProfit(new BigDecimal("36.00"))
                .items(new ArrayList<>(List.of(orderItem)))
                .build();
    }

    // -------------------------------------------------------------------------
    // create()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("deve criar pedido com dados válidos e retornar OrderResponse")
        void shouldCreateOrderAndReturnOrderResponse() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            OrderResponse result = orderService.create(merchantId, orderRequest);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(orderId);
            assertThat(result.getCustomerId()).isEqualTo(customerId);
            assertThat(result.getCustomerName()).isEqualTo("Cliente Teste");
            then(orderRepository).should().save(any(Order.class));
        }

        @Test
        @DisplayName("deve usar o canal (origin) informado no request")
        void shouldUseOriginFromRequest() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            OrderRequest withOrigin = OrderRequest.builder()
                    .customerId(customerId)
                    .origin(OrderOrigin.IFOOD)
                    .items(orderRequest.getItems())
                    .build();

            orderService.create(merchantId, withOrigin);

            then(orderRepository).should().save(argThat(saved -> saved.getOrigin() == OrderOrigin.IFOOD));
        }

        @Test
        @DisplayName("deve usar JETMENU como canal padrão quando origin não é informado")
        void shouldDefaultOriginToMenubank() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.create(merchantId, orderRequest);

            then(orderRepository).should().save(argThat(saved -> saved.getOrigin() == OrderOrigin.JETMENU));
        }

        @Test
        @DisplayName("deve definir status como PAID (concluído) por padrão")
        void shouldSetStatusToPaidByDefault() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.create(merchantId, orderRequest);

            then(orderRepository).should().save(argThat(savedOrder -> savedOrder.getStatus() == OrderStatus.PAID));
        }

        @Test
        @DisplayName("deve calcular totalValue a partir dos itens (quantidade × preço)")
        void shouldCalculateTotalValueFromItems() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.create(merchantId, orderRequest);

            // 2 × R$30.00 = R$60.00
            then(orderRepository).should().save(argThat(savedOrder ->
                    savedOrder.getTotalValue().compareTo(new BigDecimal("60.00")) == 0
            ));
        }

        @Test
        @DisplayName("deve calcular estimatedProfit (totalValue − custo total dos itens)")
        void shouldCalculateEstimatedProfitFromItems() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.create(merchantId, orderRequest);

            // totalValue(60.00) − totalCost(2 × 12.00 = 24.00) = 36.00
            then(orderRepository).should().save(argThat(savedOrder ->
                    savedOrder.getEstimatedProfit().compareTo(new BigDecimal("36.00")) == 0
            ));
        }

        @Test
        @DisplayName("deve definir unitPrice do item com o preço atual do produto")
        void shouldSetItemUnitPriceFromProductPrice() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.create(merchantId, orderRequest);

            then(orderRepository).should().save(argThat(savedOrder ->
                    savedOrder.getItems().get(0).getUnitPrice()
                            .compareTo(new BigDecimal("30.00")) == 0
            ));
        }

        @Test
        @DisplayName("deve definir dateTime no momento da criação")
        void shouldSetDateTimeOnCreation() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            LocalDateTime before = LocalDateTime.now();
            orderService.create(merchantId, orderRequest);
            LocalDateTime after = LocalDateTime.now();

            then(orderRepository).should().save(argThat(savedOrder ->
                    !savedOrder.getDateTime().isBefore(before) &&
                    !savedOrder.getDateTime().isAfter(after)
            ));
        }

        @Test
        @DisplayName("deve lançar OrderNotFoundException quando cliente não encontrado")
        void shouldThrowWhenCustomerNotFound() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.create(merchantId, orderRequest))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("Cliente");

            then(orderRepository).should(never()).save(any(Order.class));
        }

        @Test
        @DisplayName("deve computar unitCost só com insumos (PACKAGING e legados sem kind); INGREDIENT fica de fora")
        void shouldCountOnlyInsumosSkippingIngredientKind() {
            Include copo = Include.builder().id(UUID.randomUUID()).product(product)
                    .name("Copo").cost(new BigDecimal("0.30")).quantity(BigDecimal.ONE)
                    .kind(IncludeKind.PACKAGING).build();
            Include granola = Include.builder().id(UUID.randomUUID()).product(product)
                    .name("Granola").cost(new BigDecimal("0.05")).quantity(new BigDecimal("40"))
                    .kind(IncludeKind.INGREDIENT).build();
            Include acaiBase = Include.builder().id(UUID.randomUUID()).product(product)
                    .name("Açaí base").cost(new BigDecimal("0.10")).quantity(new BigDecimal("150"))
                    .kind(null).build();
            given(includeRepository.findByProductIdAndProductMerchantId(productId, merchantId))
                    .willReturn(List.of(copo, granola, acaiBase));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.create(merchantId, orderRequest);

            // 0.30 + (0.10×150) = 15.30 — Granola (INGREDIENT) só entraria como extra
            then(orderRepository).should().save(argThat(saved ->
                    saved.getItems().get(0).getUnitCost().compareTo(new BigDecimal("15.30")) == 0));
        }

        @Test
        @DisplayName("deve remover do unitCost os insumos desmarcados e persistir as exclusões")
        void shouldExcludeDeselectedInsumosFromUnitCostAndPersistExclusions() {
            UUID acaiBaseId = UUID.randomUUID();
            Include copo = Include.builder().id(UUID.randomUUID()).product(product)
                    .name("Copo").cost(new BigDecimal("0.30")).quantity(BigDecimal.ONE)
                    .kind(IncludeKind.PACKAGING).build();
            Include acaiBase = Include.builder().id(acaiBaseId).product(product)
                    .name("Açaí base").cost(new BigDecimal("0.10")).quantity(new BigDecimal("150"))
                    .kind(null).build();
            given(includeRepository.findByProductIdAndProductMerchantId(productId, merchantId))
                    .willReturn(List.of(copo, acaiBase));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            OrderRequest withExclusion = OrderRequest.builder()
                    .customerId(customerId)
                    .items(List.of(OrderItemRequest.builder()
                            .productId(productId)
                            .quantity(1)
                            .excludedIncludeIds(List.of(acaiBaseId))
                            .build()))
                    .build();

            orderService.create(merchantId, withExclusion);

            then(orderRepository).should().save(argThat(saved ->
                    saved.getItems().get(0).getUnitCost().compareTo(new BigDecimal("0.30")) == 0
                            && saved.getItems().get(0).getExcludedIncludeIds().contains(acaiBaseId)));
        }

        @Test
        @DisplayName("deve listar como insumos só PACKAGING e legados menos exclusões; INGREDIENT fica de fora")
        void shouldExposeInsumosMinusExclusionsSkippingIngredientInResponse() {
            UUID copoId = UUID.randomUUID();
            Include copo = Include.builder().id(copoId).product(product)
                    .name("Copo").cost(new BigDecimal("0.30")).quantity(BigDecimal.ONE)
                    .kind(IncludeKind.PACKAGING).build();
            Include granola = Include.builder().id(UUID.randomUUID()).product(product)
                    .name("Granola").cost(new BigDecimal("0.05")).quantity(new BigDecimal("40"))
                    .kind(IncludeKind.INGREDIENT).build();
            Include acaiBase = Include.builder().id(UUID.randomUUID()).product(product)
                    .name("Açaí base").cost(new BigDecimal("0.10")).quantity(new BigDecimal("150"))
                    .kind(null).build();
            given(includeRepository.findByProductIdAndProductMerchantId(productId, merchantId))
                    .willReturn(List.of(copo, granola, acaiBase));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));

            OrderItem itemWithExclusion = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .quantity(1)
                    .unitPrice(new BigDecimal("30.00"))
                    .unitCost(new BigDecimal("15.00"))
                    .excludedIncludeIds(new java.util.HashSet<>(java.util.Set.of(copoId)))
                    .build();
            Order savedOrder = Order.builder()
                    .id(orderId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .dateTime(LocalDateTime.now())
                    .customer(customer)
                    .status(OrderStatus.PAID)
                    .origin(OrderOrigin.JETMENU)
                    .totalValue(new BigDecimal("30.00"))
                    .items(new ArrayList<>(List.of(itemWithExclusion)))
                    .build();
            given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

            OrderResponse result = orderService.create(merchantId, orderRequest);

            assertThat(result.getItems().get(0).getInsumos())
                    .extracting(com.jetmenu.product.IncludeResponse::getName)
                    .containsExactly("Açaí base");
            assertThat(result.getItems().get(0).getExcludedIncludeIds()).containsExactly(copoId);
        }

        @Test
        @DisplayName("deve reutilizar cliente existente quando customerName casa ignorando caixa e acentos")
        void shouldReuseExistingCustomerWhenNameMatchesIgnoringCaseAndAccents() {
            Customer joao = Customer.builder()
                    .id(UUID.randomUUID())
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("João")
                    .build();
            given(customerRepository.findAllByMerchantId(merchantId)).willReturn(List.of(joao));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            OrderRequest byName = OrderRequest.builder()
                    .customerName("  JOAO  ")
                    .items(orderRequest.getItems())
                    .build();

            orderService.create(merchantId, byName);

            then(orderRepository).should().save(argThat(saved -> saved.getCustomer() == joao));
            then(customerRepository).should(never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("deve criar cliente com o nome informado quando não há match")
        void shouldCreateCustomerWhenNoNameMatch() {
            given(customerRepository.findAllByMerchantId(merchantId)).willReturn(List.of(customer));
            given(customerRepository.save(any(Customer.class)))
                    .willAnswer(inv -> inv.getArgument(0));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            OrderRequest byName = OrderRequest.builder()
                    .customerName("  Maria Clara ")
                    .items(orderRequest.getItems())
                    .build();

            orderService.create(merchantId, byName);

            org.mockito.ArgumentCaptor<Customer> captor =
                    org.mockito.ArgumentCaptor.forClass(Customer.class);
            then(customerRepository).should().save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Maria Clara");
            assertThat(captor.getValue().getMerchant()).isNotNull();
        }

        @Test
        @DisplayName("deve priorizar customerId quando customerId e customerName são informados")
        void shouldPreferCustomerIdWhenBothProvided() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            OrderRequest both = OrderRequest.builder()
                    .customerId(customerId)
                    .customerName("Outro Nome")
                    .items(orderRequest.getItems())
                    .build();

            orderService.create(merchantId, both);

            then(customerRepository).should(never()).findAllByMerchantId(any());
            then(customerRepository).should(never()).save(any(Customer.class));
            then(orderRepository).should().save(argThat(saved -> saved.getCustomer() == customer));
        }

        @Test
        @DisplayName("deve usar o primeiro match quando existem clientes homônimos")
        void shouldReuseFirstMatchWhenDuplicateNamesExist() {
            Customer first = Customer.builder()
                    .id(UUID.randomUUID())
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("Ana")
                    .build();
            Customer second = Customer.builder()
                    .id(UUID.randomUUID())
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("ANA")
                    .build();
            given(customerRepository.findAllByMerchantId(merchantId)).willReturn(List.of(first, second));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            OrderRequest byName = OrderRequest.builder()
                    .customerName("ana")
                    .items(orderRequest.getItems())
                    .build();

            orderService.create(merchantId, byName);

            then(orderRepository).should().save(argThat(saved -> saved.getCustomer() == first));
            then(customerRepository).should(never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("deve lançar OrderNotFoundException quando produto não encontrado")
        void shouldThrowWhenProductNotFound() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.create(merchantId, orderRequest))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("Produto");

            then(orderRepository).should(never()).save(any(Order.class));
        }

        @Test
        @DisplayName("deve mapear corretamente os itens na resposta")
        void shouldMapItemsCorrectlyInResponse() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            OrderResponse result = orderService.create(merchantId, orderRequest);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getProductName()).isEqualTo("Hambúrguer");
            assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
            assertThat(result.getItems().get(0).getUnitPrice())
                    .isEqualByComparingTo(new BigDecimal("30.00"));
        }

        @Test
        @DisplayName("deve incluir custo de ingredientes extras no cálculo de estimatedProfit")
        void shouldIncludeExtraIngredientsCostWhenCalculatingEstimatedProfit() {
            OrderItemExtraIngredientRequest extra = OrderItemExtraIngredientRequest.builder()
                    .ingredientId(ingredientId)
                    // quantidade extra por unidade do produto
                    .quantity(new BigDecimal("50"))
                    .build();

            OrderItemRequest itemWithExtra = OrderItemRequest.builder()
                    .productId(productId)
                    .quantity(2)
                    .extraIngredients(List.of(extra))
                    .build();

            OrderRequest requestWithExtra = OrderRequest.builder()
                    .customerId(customerId)
                    .items(List.of(itemWithExtra))
                    .build();

            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));
            // Sobrescreve cálculo padrão com o valor esperado para este caso: base (24) + extras (10) = 34
            given(orderCostCalculatorService.computeOrderTotalCost(any(Order.class)))
                    .willReturn(new BigDecimal("34.00"));

            // return a saved copy so OrderResponse uses the calculated values
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(orderId);
                saved.getItems().forEach(i -> i.setId(UUID.randomUUID()));
                return saved;
            });

            OrderResponse response = orderService.create(merchantId, requestWithExtra);

            // totalValue = 2 * 30.00 = 60.00
            // totalCost (mockado pelo OrderCostCalculatorService) = 34.00
            // estimatedProfit = 60.00 - 34.00 = 26.00
            assertThat(response.getEstimatedProfit()).isEqualByComparingTo(new BigDecimal("26.00"));
        }

        @Test
        @DisplayName("deve expor unitCost e totalCost por item na resposta")
        void shouldExposeUnitCostAndTotalCostPerItem() {
            OrderItemExtraIngredientRequest extra = OrderItemExtraIngredientRequest.builder()
                    .ingredientId(ingredientId)
                    .quantity(new BigDecimal("50"))
                    .build();

            OrderItemRequest itemWithExtra = OrderItemRequest.builder()
                    .productId(productId)
                    .quantity(2)
                    .extraIngredients(List.of(extra))
                    .build();

            OrderRequest requestWithExtra = OrderRequest.builder()
                    .customerId(customerId)
                    .items(List.of(itemWithExtra))
                    .build();

            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(orderId);
                saved.getItems().forEach(i -> i.setId(UUID.randomUUID()));
                return saved;
            });
            // unitCost vem do calculator (mandatory base + opcionais escolhidos = 12 + 5 = 17)
            given(orderCostCalculatorService.computeItemUnitCost(any(OrderItem.class), eq(merchantId)))
                    .willReturn(new BigDecimal("17.00"));

            OrderResponse response = orderService.create(merchantId, requestWithExtra);

            // unitCost = base mandatória (12) + opcional escolhido (50 × 0.10 = 5) = 17
            // totalCost = unitCost × quantity (2) = 34
            OrderItemResponse itemResponse = response.getItems().get(0);
            assertThat(itemResponse.getUnitCost()).isEqualByComparingTo(new BigDecimal("17.00"));
            assertThat(itemResponse.getTotalCost()).isEqualByComparingTo(new BigDecimal("34.00"));
        }

        @Test
        @DisplayName("deve suportar costPerUnit com 4 casas decimais sem perder precisão")
        void shouldSupportFourDecimalCostPerUnit() {
            Ingredient fineIngredient = Ingredient.builder()
                    .id(ingredientId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("Açúcar refinado")
                    .unit("g")
                    .costPerUnit(new BigDecimal("0.0035"))
                    .status(IngredientStatus.ACTIVE)
                    .build();

            OrderItemExtraIngredientRequest extra = OrderItemExtraIngredientRequest.builder()
                    .ingredientId(ingredientId)
                    .quantity(new BigDecimal("100"))
                    .build();

            OrderItemRequest itemWithExtra = OrderItemRequest.builder()
                    .productId(productId)
                    .quantity(1)
                    .extraIngredients(List.of(extra))
                    .build();

            OrderRequest requestWithExtra = OrderRequest.builder()
                    .customerId(customerId)
                    .items(List.of(itemWithExtra))
                    .build();

            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(fineIngredient));
            // totalCost esperado: base (12.00) + extra (100 * 0.0035 = 0.35) = 12.35
            given(orderCostCalculatorService.computeOrderTotalCost(any(Order.class)))
                    .willReturn(new BigDecimal("12.3500"));
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(orderId);
                saved.getItems().forEach(i -> i.setId(UUID.randomUUID()));
                return saved;
            });

            OrderResponse response = orderService.create(merchantId, requestWithExtra);

            // totalValue = 30.00; totalCost = 12.35; estimatedProfit = 17.65
            assertThat(response.getEstimatedProfit())
                    .isEqualByComparingTo(new BigDecimal("17.6500"));
        }

        @Test
        @DisplayName("deve lançar IngredientNotFoundException quando ingrediente extra não existe")
        void shouldThrowWhenExtraIngredientNotFound() {
            OrderItemExtraIngredientRequest extra = OrderItemExtraIngredientRequest.builder()
                    .ingredientId(ingredientId)
                    .quantity(new BigDecimal("1"))
                    .build();

            OrderItemRequest itemWithExtra = OrderItemRequest.builder()
                    .productId(productId)
                    .quantity(1)
                    .extraIngredients(List.of(extra))
                    .build();

            OrderRequest requestWithExtra = OrderRequest.builder()
                    .customerId(customerId)
                    .items(List.of(itemWithExtra))
                    .build();

            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.create(merchantId, requestWithExtra))
                    .isInstanceOf(IngredientNotFoundException.class)
                    .hasMessageContaining("Ingrediente");

            then(orderRepository).should(never()).save(any(Order.class));
        }
    }

    // -------------------------------------------------------------------------
    // findById()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("deve retornar OrderResponse quando pedido existe")
        void shouldReturnOrderResponseWhenExists() {
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));

            OrderResponse result = orderService.findById(merchantId, orderId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(orderId);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(result.getCustomerId()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("deve lançar OrderNotFoundException quando pedido não existe")
        void shouldThrowWhenOrderNotFound() {
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.findById(merchantId, orderId))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("deve expor o valor pago pelo cliente ao lado do custo de produção do extra")
        void shouldExposeSalePricePaidAlongsideProductionCost() {
            OrderItemExtraIngredient extra = OrderItemExtraIngredient.builder()
                    .id(UUID.randomUUID())
                    .ingredient(ingredient)
                    .quantity(new BigDecimal("100"))          // gramatura
                    .costPerUnit(new BigDecimal("0.10"))      // custo do catálogo local
                    .salePricePerUnit(new BigDecimal("1.5000"))
                    .salePriceTotal(new BigDecimal("1.5000")) // valor pago (Pistache R$ 1,50)
                    .ingredientName("Pistache")
                    .ingredientUnit("g")
                    .build();
            order.getItems().get(0).setExtraIngredients(new ArrayList<>(List.of(extra)));

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));

            OrderResponse result = orderService.findById(merchantId, orderId);

            OrderItemExtraIngredientResponse response =
                    result.getItems().get(0).getExtraIngredients().get(0);

            // Valor pago: repassado como veio do payload, sem multiplicar por item.quantity (2).
            assertThat(response.getSalePriceTotal()).isEqualByComparingTo("1.5000");
            assertThat(response.getSalePricePerUnit()).isEqualByComparingTo("1.5000");
            // Custo de produção: 100g × 0,10 × item.quantity (2) = 20,00 — número diferente do preço.
            assertThat(response.getTotalCost()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("extras sem preço (pedidos manuais/importados antes da V16) devem tolerar valor nulo")
        void shouldTolerateNullSalePriceForLegacyExtras() {
            OrderItemExtraIngredient legacyExtra = OrderItemExtraIngredient.builder()
                    .id(UUID.randomUUID())
                    .ingredient(ingredient)
                    .quantity(new BigDecimal("50"))
                    .costPerUnit(new BigDecimal("0.10"))
                    .ingredientName("Bacon")
                    .ingredientUnit("g")
                    .build();
            order.getItems().get(0).setExtraIngredients(new ArrayList<>(List.of(legacyExtra)));

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));

            OrderResponse result = orderService.findById(merchantId, orderId);

            OrderItemExtraIngredientResponse response =
                    result.getItems().get(0).getExtraIngredients().get(0);

            assertThat(response.getSalePriceTotal()).isNull();
            assertThat(response.getSalePricePerUnit()).isNull();
            // O custo continua sendo calculado normalmente.
            assertThat(response.getTotalCost()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("deve expor subItems não-casados enquanto não houver ingrediente cadastrado")
        void shouldExposeUnmatchedSubItemsWhenNoIngredientExists() {
            OrderItemUnmatchedSubItem unmatched = OrderItemUnmatchedSubItem.builder()
                    .id(UUID.randomUUID())
                    .rawName("Nutella")
                    .canonicalName("nutella")
                    .quantity(2)
                    .salePricePerUnit(new BigDecimal("3.00"))
                    .salePriceTotal(new BigDecimal("6.00"))
                    .build();
            order.getItems().get(0).setUnmatchedSubItems(new ArrayList<>(List.of(unmatched)));

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            // Nenhum ingrediente com o nome canônico existe ainda.
            given(ingredientRepository.findExistingCanonicalNames(eq(merchantId), anyCollection()))
                    .willReturn(List.of());

            OrderResponse result = orderService.findById(merchantId, orderId);

            List<OrderItemUnmatchedSubItemResponse> responses =
                    result.getItems().get(0).getUnmatchedSubItems();
            assertThat(responses).hasSize(1);
            OrderItemUnmatchedSubItemResponse response = responses.get(0);
            assertThat(response.getRawName()).isEqualTo("Nutella");
            assertThat(response.getQuantity()).isEqualTo(2);
            assertThat(response.getSalePriceTotal()).isEqualByComparingTo("6.00");
        }

        @Test
        @DisplayName("deve esconder o subItem não-casado (botão some) depois que o ingrediente é cadastrado")
        void shouldHideUnmatchedSubItemOnceIngredientIsRegistered() {
            OrderItemUnmatchedSubItem unmatched = OrderItemUnmatchedSubItem.builder()
                    .id(UUID.randomUUID())
                    .rawName("Nutella")
                    .canonicalName("nutella")
                    .quantity(1)
                    .build();
            order.getItems().get(0).setUnmatchedSubItems(new ArrayList<>(List.of(unmatched)));

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            // O ingrediente já existe: o nome canônico volta na consulta e o registro é filtrado.
            given(ingredientRepository.findExistingCanonicalNames(eq(merchantId), anyCollection()))
                    .willReturn(List.of("nutella"));

            OrderResponse result = orderService.findById(merchantId, orderId);

            assertThat(result.getItems().get(0).getUnmatchedSubItems()).isEmpty();
        }

        @Test
        @DisplayName("deve popular insumos do item com os Includes da ficha técnica do produto")
        void shouldPopulateItemInsumosFromProductIncludes() {
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));

            OrderResponse result = orderService.findById(merchantId, orderId);

            assertThat(result.getItems()).hasSize(1);
            OrderItemResponse item = result.getItems().get(0);
            assertThat(item.getInsumos())
                    .as("insumos devem refletir os Includes da ficha técnica do produto")
                    .hasSize(1);
            assertThat(item.getInsumos().get(0).getName()).isEqualTo("CustoBase");
            assertThat(item.getInsumos().get(0).getCost()).isEqualByComparingTo(new BigDecimal("12.00"));
            assertThat(item.getInsumos().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(item.getInsumos().get(0).getTotalCost()).isEqualByComparingTo(new BigDecimal("12.00"));
        }

        @Test
        @DisplayName("insumos NÃO devem incluir Includes do tipo INGREDIENT nem de kind nulo")
        void shouldExcludeIngredientKindIncludesFromInsumos() {
            Include packaging = Include.builder()
                    .product(product).name("Copo")
                    .cost(new BigDecimal("0.35")).quantity(BigDecimal.ONE)
                    .kind(IncludeKind.PACKAGING).build();
            Include specificIngredient = Include.builder()
                    .product(product).name("Creme de Ovomaltine")
                    .cost(new BigDecimal("0.80")).quantity(new BigDecimal("150"))
                    .kind(IncludeKind.INGREDIENT).build();
            Include legacyNullKind = Include.builder()
                    .product(product).name("Açaí Goat")
                    .cost(new BigDecimal("0.02")).quantity(new BigDecimal("150"))
                    .build();
            given(includeRepository.findByProductIdAndProductMerchantId(productId, merchantId))
                    .willReturn(List.of(packaging, specificIngredient, legacyNullKind));

            // Pedido importado: insumos continuam sendo apenas PACKAGING (ingredientes
            // escolhidos chegam via extras). Pedido manual mostra PACKAGING + legados —
            // coberto em create() shouldExposeInsumosMinusExclusionsSkippingIngredientInResponse.
            order.setOrigin(OrderOrigin.ANOTA_AI);
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));

            OrderResponse result = orderService.findById(merchantId, orderId);

            OrderItemResponse item = result.getItems().get(0);
            assertThat(item.getInsumos())
                    .as("em pedidos importados, apenas Includes PACKAGING entram em insumos")
                    .hasSize(1);
            assertThat(item.getInsumos().get(0).getName()).isEqualTo("Copo");
        }

        @Test
        @DisplayName("pedido manual deve listar PACKAGING e legados em insumos; INGREDIENT fica de fora")
        void shouldListInsumosSkippingIngredientForManualOrders() {
            Include packaging = Include.builder()
                    .id(UUID.randomUUID()).product(product).name("Copo")
                    .cost(new BigDecimal("0.35")).quantity(BigDecimal.ONE)
                    .kind(IncludeKind.PACKAGING).build();
            Include legacyNullKind = Include.builder()
                    .id(UUID.randomUUID()).product(product).name("Açaí Goat")
                    .cost(new BigDecimal("0.02")).quantity(new BigDecimal("150"))
                    .build();
            Include ingredient = Include.builder()
                    .id(UUID.randomUUID()).product(product).name("Creme de Ovomaltine")
                    .cost(new BigDecimal("0.80")).quantity(new BigDecimal("150"))
                    .kind(IncludeKind.INGREDIENT).build();
            given(includeRepository.findByProductIdAndProductMerchantId(productId, merchantId))
                    .willReturn(List.of(packaging, legacyNullKind, ingredient));

            order.setOrigin(OrderOrigin.JETMENU);
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));

            OrderResponse result = orderService.findById(merchantId, orderId);

            assertThat(result.getItems().get(0).getInsumos())
                    .extracting(com.jetmenu.product.IncludeResponse::getName)
                    .containsExactlyInAnyOrder("Copo", "Açaí Goat");
        }

        @Test
        @DisplayName("deve calcular estimatedProfit a partir do unitCost dos itens, ignorando valor armazenado")
        void shouldComputeEstimatedProfitFromItemCosts() {
            OrderItem itemWithZeroCost = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .quantity(1)
                    .unitPrice(new BigDecimal("22.49"))
                    .unitCost(BigDecimal.ZERO)
                    .build();
            Order orderWithStaleProfit = Order.builder()
                    .id(orderId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .dateTime(LocalDateTime.now())
                    .customer(customer)
                    .status(OrderStatus.PENDING)
                    .totalValue(new BigDecimal("22.49"))
                    .estimatedProfit(new BigDecimal("-42.01"))
                    .items(new ArrayList<>(List.of(itemWithZeroCost)))
                    .build();

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId))
                    .willReturn(Optional.of(orderWithStaleProfit));

            OrderResponse result = orderService.findById(merchantId, orderId);

            // 22.49 - (unitCost=0 × qty=1) - fee=0 = 22.49
            assertThat(result.getEstimatedProfit()).isEqualByComparingTo(new BigDecimal("22.49"));
        }

        @Test
        @DisplayName("deve retornar marginPct = estimatedProfit / totalValue * 100")
        void shouldReturnMarginPct() {
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));

            OrderResponse result = orderService.findById(merchantId, orderId);

            assertThat(result.getMarginPct()).isNotNull();
            // marginPct = estimatedProfit / totalValue * 100
            BigDecimal expected = result.getEstimatedProfit()
                    .divide(result.getTotalValue(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            assertThat(result.getMarginPct()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("deve retornar marginPct null quando totalValue é zero")
        void shouldReturnNullMarginWhenTotalValueIsZero() {
            Order zeroOrder = Order.builder()
                    .id(orderId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .dateTime(LocalDateTime.now())
                    .customer(customer)
                    .status(OrderStatus.PENDING)
                    .totalValue(BigDecimal.ZERO)
                    .estimatedProfit(BigDecimal.ZERO)
                    .items(new ArrayList<>())
                    .build();

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(zeroOrder));

            OrderResponse result = orderService.findById(merchantId, orderId);

            assertThat(result.getMarginPct()).isNull();
        }

        @Test
        @DisplayName("deve calcular marginPct sobre (totalValue − deliveryFee), ignorando a taxa de entrega")
        void shouldExcludeDeliveryFeeFromMarginBase() {
            // totalValue 60.00 (inclui 10.00 de entrega) | deliveryFee 10.00 | totalCost 20.00 | feeRate 2%
            // subtotal   = 60.00 - 10.00              = 50.00
            // feeAmount  = 50.00 × 2%                 =  1.00
            // profit     = 50.00 - 20.00 - 1.00       = 29.00
            // marginPct  = 29.00 / 50.00 × 100        = 58.00  (e NÃO 29.00 / 60.00 = 48.33)
            Order deliveryOrder = Order.builder()
                    .id(orderId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .dateTime(LocalDateTime.now())
                    .customer(customer)
                    .status(OrderStatus.PAID)
                    .totalValue(new BigDecimal("60.00"))
                    .deliveryFee(new BigDecimal("10.00"))
                    .totalCost(new BigDecimal("20.00"))
                    .fee(Fee.builder().name("Pix").feeRate(new BigDecimal("2.00")).build())
                    .items(new ArrayList<>())
                    .build();

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(deliveryOrder));

            OrderResponse result = orderService.findById(merchantId, orderId);

            assertThat(result.getEstimatedProfit()).isEqualByComparingTo(new BigDecimal("29.00"));
            assertThat(result.getMarginPct()).isEqualByComparingTo(new BigDecimal("58.00"));
        }

        @Test
        @DisplayName("deve manter marginPct inalterado em pedido sem taxa de entrega")
        void shouldKeepMarginUnchangedWhenThereIsNoDeliveryFee() {
            // Sem taxa de entrega o denominador continua sendo o totalValue integral.
            // profit    = 60.00 - 20.00       = 40.00
            // marginPct = 40.00 / 60.00 × 100 = 66.67
            Order noDeliveryOrder = Order.builder()
                    .id(orderId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .dateTime(LocalDateTime.now())
                    .customer(customer)
                    .status(OrderStatus.PAID)
                    .totalValue(new BigDecimal("60.00"))
                    .deliveryFee(null)
                    .totalCost(new BigDecimal("20.00"))
                    .items(new ArrayList<>())
                    .build();

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(noDeliveryOrder));

            OrderResponse result = orderService.findById(merchantId, orderId);

            assertThat(result.getEstimatedProfit()).isEqualByComparingTo(new BigDecimal("40.00"));
            assertThat(result.getMarginPct()).isEqualByComparingTo(new BigDecimal("66.67"));
        }

        @Test
        @DisplayName("deve retornar marginPct null quando totalValue é igual à taxa de entrega")
        void shouldReturnNullMarginWhenTotalValueEqualsDeliveryFee() {
            // subtotal = 15.00 - 15.00 = 0 -> denominador zero, sem divisão
            Order feeOnlyOrder = Order.builder()
                    .id(orderId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .dateTime(LocalDateTime.now())
                    .customer(customer)
                    .status(OrderStatus.PENDING)
                    .totalValue(new BigDecimal("15.00"))
                    .deliveryFee(new BigDecimal("15.00"))
                    .totalCost(new BigDecimal("5.00"))
                    .items(new ArrayList<>())
                    .build();

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(feeOnlyOrder));

            OrderResponse result = orderService.findById(merchantId, orderId);

            assertThat(result.getMarginPct()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // findAll()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findAll(search, pageable)")
    class FindAll {

        @Test
        @DisplayName("deve retornar página de pedidos filtrada por nome do cliente (contains, case-insensitive)")
        void shouldReturnPagedOrdersFilteredByCustomerName() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(orderRepository.findPageByMerchantIdAndCustomerNameContaining(merchantId, "client", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), pageable, 1));

            org.springframework.data.domain.Page<OrderResponse> result =
                    orderService.findAll(merchantId, "client", null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(orderId);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("deve tratar search nulo como string vazia")
        void shouldTreatNullSearchAsEmpty() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(orderRepository.findPageByMerchantIdAndCustomerNameContaining(merchantId, "", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));

            org.springframework.data.domain.Page<OrderResponse> result =
                    orderService.findAll(merchantId, null, null, pageable);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("deve filtrar por status quando informado")
        void shouldFilterByStatusWhenProvided() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(orderRepository.findPageByMerchantIdAndStatusAndCustomerNameContaining(
                    merchantId, OrderStatus.READY, "", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(order), pageable, 1));

            org.springframework.data.domain.Page<OrderResponse> result =
                    orderService.findAll(merchantId, null, OrderStatus.READY, pageable);

            assertThat(result.getContent()).hasSize(1);
            then(orderRepository).should(never())
                    .findPageByMerchantIdAndCustomerNameContaining(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("statusCounts(start, end, search)")
    class StatusCounts {

        @Test
        @DisplayName("deve retornar contagens por status, preenchendo zeros para valores ausentes")
        void shouldReturnCountsByStatusWithZeroFill() {
            given(orderRepository.countByStatusForMerchant(eq(merchantId), any(), any(), eq("")))
                    .willReturn(List.of(
                            new Object[]{OrderStatus.PENDING, 5L},
                            new Object[]{OrderStatus.READY, 2L}
                    ));

            java.util.Map<OrderStatus, Long> result = orderService.statusCounts(merchantId, null, null, null);

            assertThat(result).containsEntry(OrderStatus.PENDING, 5L);
            assertThat(result).containsEntry(OrderStatus.READY, 2L);
            assertThat(result).containsEntry(OrderStatus.DELIVERED, 0L);
            assertThat(result).containsEntry(OrderStatus.PAID, 0L);
            assertThat(result).containsEntry(OrderStatus.CANCELLED, 0L);
        }
    }

    // -------------------------------------------------------------------------
    // update()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("deve atualizar pedido existente e retornar OrderResponse atualizado")
        void shouldUpdateAndReturnUpdatedOrderResponse() {
            UUID newProductId = UUID.randomUUID();
            Product newProduct = Product.builder()
                    .id(newProductId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("Pizza")
                    .price(new BigDecimal("45.00"))
                    .status(ProductStatus.ACTIVE)
                    .build();

            OrderItemRequest newItemRequest = OrderItemRequest.builder()
                    .productId(newProductId)
                    .quantity(3)
                    .build();

            OrderRequest updateRequest = OrderRequest.builder()
                    .customerId(customerId)
                    .items(List.of(newItemRequest))
                    .build();

            OrderItem updatedItem = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .product(newProduct)
                    .quantity(3)
                    .unitPrice(new BigDecimal("45.00"))
                    .unitCost(new BigDecimal("18.00"))
                    .build();

            Order updatedOrder = Order.builder()
                    .id(orderId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .dateTime(order.getDateTime())
                    .customer(customer)
                    .status(OrderStatus.PENDING)
                    .totalValue(new BigDecimal("135.00"))
                    .estimatedProfit(new BigDecimal("81.00"))
                    .items(List.of(updatedItem))
                    .build();

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(newProductId, merchantId)).willReturn(Optional.of(newProduct));
            given(orderRepository.save(any(Order.class))).willReturn(updatedOrder);

            OrderResponse result = orderService.update(merchantId, orderId, updateRequest);

            assertThat(result.getTotalValue()).isEqualByComparingTo(new BigDecimal("135.00"));
            assertThat(result.getEstimatedProfit()).isEqualByComparingTo(new BigDecimal("81.00"));
            assertThat(result.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("deve recalcular totais ao atualizar pedido")
        void shouldRecalculateTotalsOnUpdate() {
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.update(merchantId, orderId, orderRequest);

            then(orderRepository).should().save(argThat(savedOrder ->
                    savedOrder.getTotalValue().compareTo(new BigDecimal("60.00")) == 0 &&
                    savedOrder.getEstimatedProfit().compareTo(new BigDecimal("36.00")) == 0 &&
                    savedOrder.getStatus() == OrderStatus.PAID
            ));
        }

        @Test
        @DisplayName("deve permitir alterar status do pedido quando status é informado")
        void shouldUpdateOrderStatusWhenProvided() {
            OrderRequest updateWithStatus = OrderRequest.builder()
                    .customerId(customerId)
                    .items(orderRequest.getItems())
                    .status(OrderStatus.CANCELLED)
                    .build();

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.update(merchantId, orderId, updateWithStatus);

            then(orderRepository).should().save(argThat(savedOrder -> savedOrder.getStatus() == OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("deve resolver cliente por customerName também na atualização")
        void shouldResolveCustomerByNameOnUpdate() {
            Customer joao = Customer.builder()
                    .id(UUID.randomUUID())
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("João")
                    .build();
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            given(customerRepository.findAllByMerchantId(merchantId)).willReturn(List.of(joao));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            OrderRequest byName = OrderRequest.builder()
                    .customerName("joao")
                    .items(orderRequest.getItems())
                    .build();

            orderService.update(merchantId, orderId, byName);

            then(orderRepository).should().save(argThat(saved -> saved.getCustomer() == joao));
            then(customerRepository).should(never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("deve manter frete e taxa de serviço no totalValue ao atualizar pedido importado")
        void shouldKeepDeliveryAndServiceFeeInTotalValueOnUpdate() {
            // Pedido importado da Anota.AI: info.total (64.99) = itens (60.00) + frete (4.00)
            // + taxa de serviço (0.99). Recalcular o total só pelos itens descartava as duas
            // taxas do total — mas elas continuavam sendo deduzidas do lucro (dedução dupla).
            order.setDeliveryFee(new BigDecimal("4.00"));
            order.setServiceFee(new BigDecimal("0.99"));
            order.setTotalValue(new BigDecimal("64.99"));
            order.setOrigin(OrderOrigin.ANOTA_AI);

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.update(merchantId, orderId, orderRequest);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            Order saved = captor.getValue();
            // 60.00 (itens) + 4.00 (frete) + 0.99 (taxa de serviço)
            assertThat(saved.getTotalValue()).isEqualByComparingTo("64.99");
            assertThat(saved.getDeliveryFee()).isEqualByComparingTo("4.00");
            assertThat(saved.getServiceFee()).isEqualByComparingTo("0.99");
            // lucro = (64.99 − 4.00 − 0.99) − 24.00 = 36.00
            assertThat(saved.getEstimatedProfit()).isEqualByComparingTo("36.00");
        }

        @Test
        @DisplayName("deve preservar o preço de venda dos extras importados ao atualizar")
        void shouldKeepImportedExtraSalePriceInTotalValueOnUpdate() {
            // Em pedido importado, o preço do item (unitPrice) NÃO inclui os subItems pagos —
            // a receita deles vive em OrderItemExtraIngredient.salePriceTotal. Sem preservá-la,
            // editar o pedido apagava o valor dos adicionais do total.
            OrderItemExtraIngredient importedExtra = OrderItemExtraIngredient.builder()
                    .ingredient(ingredient)
                    .quantity(new BigDecimal("10"))
                    .costPerUnit(new BigDecimal("0.10"))
                    .ingredientName("Bacon")
                    .ingredientUnit("g")
                    .salePriceTotal(new BigDecimal("3.00"))
                    .build();
            OrderItem importedItem = order.getItems().get(0);
            importedExtra.setOrderItem(importedItem);
            importedItem.setExtraIngredients(new ArrayList<>(List.of(importedExtra)));
            order.setOrigin(OrderOrigin.ANOTA_AI);
            order.setTotalValue(new BigDecimal("63.00"));

            OrderRequest requestWithExtra = OrderRequest.builder()
                    .customerId(customerId)
                    .items(List.of(OrderItemRequest.builder()
                            .productId(productId)
                            .quantity(2)
                            .extraIngredients(List.of(OrderItemExtraIngredientRequest.builder()
                                    .ingredientId(ingredientId)
                                    .quantity(new BigDecimal("10"))
                                    .build()))
                            .build()))
                    .build();

            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.update(merchantId, orderId, requestWithExtra);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            Order saved = captor.getValue();
            // 60.00 (itens) + 3.00 (adicional pago importado)
            assertThat(saved.getTotalValue()).isEqualByComparingTo("63.00");
            assertThat(saved.getItems().get(0).getExtraIngredients().get(0).getSalePriceTotal())
                    .isEqualByComparingTo("3.00");
        }

        @Test
        @DisplayName("pedido manual sem frete nem taxa mantém totalValue = soma dos itens")
        void shouldKeepItemsSumForManualOrderWithoutFees() {
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            orderService.update(merchantId, orderId, orderRequest);

            then(orderRepository).should().save(argThat(saved ->
                    saved.getTotalValue().compareTo(new BigDecimal("60.00")) == 0));
        }

        @Test
        @DisplayName("deve lançar OrderNotFoundException ao atualizar pedido inexistente")
        void shouldThrowWhenOrderNotFoundForUpdate() {
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.update(merchantId, orderId, orderRequest))
                    .isInstanceOf(OrderNotFoundException.class);

            then(orderRepository).should(never()).save(any(Order.class));
        }
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deve deletar pedido existente sem lançar exceção")
        void shouldDeleteExistingOrder() {
            given(orderRepository.existsByIdAndMerchantId(orderId, merchantId)).willReturn(true);
            willDoNothing().given(orderRepository).deleteByIdAndMerchantId(orderId, merchantId);

            assertThatNoException().isThrownBy(() -> orderService.delete(merchantId, orderId));

            then(orderRepository).should().deleteByIdAndMerchantId(orderId, merchantId);
        }

        @Test
        @DisplayName("deve lançar OrderNotFoundException ao deletar pedido inexistente")
        void shouldThrowWhenOrderNotFoundForDelete() {
            given(orderRepository.existsByIdAndMerchantId(orderId, merchantId)).willReturn(false);

            assertThatThrownBy(() -> orderService.delete(merchantId, orderId))
                    .isInstanceOf(OrderNotFoundException.class);

            then(orderRepository).should(never()).deleteByIdAndMerchantId(any(), any());
        }
    }

    // -------------------------------------------------------------------------
    // Ficha do pedido — insumos cobrados uma vez por pedido
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ficha do pedido")
    class OrderFicha {

        private OrderFichaIngredient sacolaSnapshot() {
            return OrderFichaIngredient.builder()
                    .ingredient(Ingredient.builder().id(UUID.randomUUID()).name("Sacola").build())
                    .quantity(BigDecimal.ONE)
                    .costPerUnit(new BigDecimal("0.50"))
                    .ingredientName("Sacola")
                    .ingredientUnit("un")
                    .build();
        }

        @Test
        @DisplayName("create pendura o snapshot da ficha do pedido no pedido")
        void createAttachesOrderFichaSnapshot() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderFichaService.buildSnapshot(merchantId))
                    .willReturn(new ArrayList<>(List.of(sacolaSnapshot())));
            given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

            orderService.create(merchantId, orderRequest);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            Order saved = captor.getValue();
            assertThat(saved.getOrderFicha()).hasSize(1);
            assertThat(saved.getOrderFicha().get(0).getIngredientName()).isEqualTo("Sacola");
            // a linha precisa apontar de volta para o pedido, senão o cascade não persiste
            assertThat(saved.getOrderFicha().get(0).getOrder()).isSameAs(saved);
        }

        @Test
        @DisplayName("lojista sem ficha do pedido: nenhuma linha é gravada — no-op")
        void createWithoutOrderFichaIsNoOp() {
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderFichaService.buildSnapshot(merchantId)).willReturn(new ArrayList<>());
            given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

            orderService.create(merchantId, orderRequest);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            assertThat(captor.getValue().getOrderFicha()).isEmpty();
        }

        @Test
        @DisplayName("update NÃO re-snapshota: o pedido mantém a ficha com que foi criado")
        void updateKeepsOriginalSnapshot() {
            OrderFichaIngredient original = sacolaSnapshot();
            order.setOrderFicha(new ArrayList<>(List.of(original)));
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

            orderService.update(merchantId, orderId, orderRequest);

            then(orderFichaService).should(never()).buildSnapshot(any());
            assertThat(order.getOrderFicha()).containsExactly(original);
        }

        @Test
        @DisplayName("toResponse expõe o custo e as linhas da ficha do pedido")
        void responseExposesOrderFicha() {
            order.setOrderFicha(new ArrayList<>(List.of(sacolaSnapshot())));
            order.setTotalCost(new BigDecimal("24.50"));
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            given(orderCostCalculatorService.computeOrderFichaCost(order)).willReturn(new BigDecimal("0.50"));

            OrderResponse response = orderService.findById(merchantId, orderId);

            assertThat(response.getOrderFichaCost()).isEqualByComparingTo("0.50");
            assertThat(response.getOrderFicha()).hasSize(1);
            assertThat(response.getOrderFicha().get(0).getIngredientName()).isEqualTo("Sacola");
            assertThat(response.getOrderFicha().get(0).getTotalCost()).isEqualByComparingTo("0.50");
        }

        @Test
        @DisplayName("pedido sem ficha: custo zero e lista vazia na resposta")
        void responseWithoutOrderFicha() {
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));

            OrderResponse response = orderService.findById(merchantId, orderId);

            assertThat(response.getOrderFichaCost()).isEqualByComparingTo("0");
            assertThat(response.getOrderFicha()).isEmpty();
        }
    }

    /**
     * Descriptive fields imported from iFood that the comanda must display
     * (homologation of the Order module).
     */
    @Nested
    @DisplayName("toResponse() — detalhes de pagamento, cupom e observações")
    class HomologationDetailsResponse {

        @Test
        @DisplayName("deve expor identificação, documento, pagamento, cupom e observações do pedido")
        void responseExposesImportedDetails() {
            order.setOrigin(OrderOrigin.IFOOD);
            order.setDisplayId("3421");
            order.setOrderType(OrderType.DELIVERY);
            order.setOrderTiming(OrderTiming.IMMEDIATE);
            order.setCustomerDocument("12345678909");
            order.setPaymentPrepaidAmount(new BigDecimal("10.00"));
            order.setPaymentPendingAmount(new BigDecimal("39.97"));
            order.setDiscountTotal(new BigDecimal("12.00"));
            order.setDiscountIfoodValue(new BigDecimal("8.00"));
            order.setDiscountMerchantValue(new BigDecimal("4.00"));
            order.setDeliveryMode("DEFAULT");
            order.setDeliveredBy("MERCHANT");
            order.setDeliveryDateTime(LocalDateTime.of(2026, 7, 1, 18, 40));
            order.setDeliveryObservations("Portão azul");
            order.setPickupCode("9182");
            order.setTakeoutMode("DEFAULT");
            order.setTakeoutDateTime(LocalDateTime.of(2026, 7, 1, 18, 30));
            order.setPaymentMethods(new ArrayList<>(List.of(
                    OrderPaymentMethod.builder()
                            .method("CASH").type("OFFLINE").currency("BRL")
                            .value(new BigDecimal("39.97"))
                            .changeFor(new BigDecimal("50.00"))
                            .build(),
                    OrderPaymentMethod.builder()
                            .method("CREDIT").type("ONLINE").currency("BRL")
                            .value(new BigDecimal("10.00"))
                            .cardBrand("VISA")
                            .build())));
            order.getItems().get(0).setObservations("sem granola");
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));

            OrderResponse response = orderService.findById(merchantId, orderId);

            assertThat(response.getDisplayId()).isEqualTo("3421");
            assertThat(response.getOrderType()).isEqualTo(OrderType.DELIVERY);
            assertThat(response.getOrderTiming()).isEqualTo(OrderTiming.IMMEDIATE);
            assertThat(response.getCustomerDocument()).isEqualTo("12345678909");
            assertThat(response.getPaymentPrepaidAmount()).isEqualByComparingTo("10.00");
            assertThat(response.getPaymentPendingAmount()).isEqualByComparingTo("39.97");
            assertThat(response.getDiscountTotal()).isEqualByComparingTo("12.00");
            assertThat(response.getDiscountIfoodValue()).isEqualByComparingTo("8.00");
            assertThat(response.getDiscountMerchantValue()).isEqualByComparingTo("4.00");
            assertThat(response.getDeliveryMode()).isEqualTo("DEFAULT");
            assertThat(response.getDeliveredBy()).isEqualTo("MERCHANT");
            assertThat(response.getDeliveryDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 18, 40));
            assertThat(response.getDeliveryObservations()).isEqualTo("Portão azul");
            assertThat(response.getPickupCode()).isEqualTo("9182");
            assertThat(response.getTakeoutMode()).isEqualTo("DEFAULT");
            assertThat(response.getTakeoutDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 18, 30));

            assertThat(response.getPaymentMethods()).hasSize(2);
            assertThat(response.getPaymentMethods().get(0).getMethod()).isEqualTo("CASH");
            assertThat(response.getPaymentMethods().get(0).getChangeFor()).isEqualByComparingTo("50.00");
            // troco = changeFor − value, calculado no backend para todo cliente mostrar o mesmo
            assertThat(response.getPaymentMethods().get(0).getChangeAmount()).isEqualByComparingTo("10.03");
            assertThat(response.getPaymentMethods().get(0).getCardBrand()).isNull();
            assertThat(response.getPaymentMethods().get(1).getCardBrand()).isEqualTo("VISA");
            assertThat(response.getPaymentMethods().get(1).getValue()).isEqualByComparingTo("10.00");
            assertThat(response.getPaymentMethods().get(1).getChangeAmount()).isNull();

            assertThat(response.getItems().get(0).getObservations()).isEqualTo("sem granola");
        }

        @Test
        @DisplayName("pedido antigo/manual: campos novos nulos e lista de pagamentos vazia")
        void responseForLegacyOrder() {
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));

            OrderResponse response = orderService.findById(merchantId, orderId);

            assertThat(response.getDisplayId()).isNull();
            assertThat(response.getOrderType()).isNull();
            assertThat(response.getOrderTiming()).isNull();
            assertThat(response.getCustomerDocument()).isNull();
            assertThat(response.getDiscountTotal()).isNull();
            assertThat(response.getPickupCode()).isNull();
            assertThat(response.getPaymentMethods()).isEmpty();
            assertThat(response.getItems().get(0).getObservations()).isNull();
        }

        @Test
        @DisplayName("editar o pedido não pode apagar a observação importada do item")
        void updateKeepsImportedItemObservations() {
            order.getItems().get(0).setObservations("sem granola");
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
            given(customerRepository.findByIdAndMerchantId(customerId, merchantId)).willReturn(Optional.of(customer));
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.update(merchantId, orderId, orderRequest);

            assertThat(response.getItems().get(0).getObservations()).isEqualTo("sem granola");
        }
    }
}

