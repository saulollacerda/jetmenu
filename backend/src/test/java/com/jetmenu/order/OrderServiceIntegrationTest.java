package com.jetmenu.order;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.customer.Customer;
import com.jetmenu.customer.CustomerRepository;
import com.jetmenu.fee.Fee;
import com.jetmenu.fee.FeeRepository;
import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.ingredient.IngredientStatus;
import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
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
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderService — integração com Postgres")
class OrderServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private IncludeRepository includeRepository;
    @Autowired private FeeRepository feeRepository;
    @Autowired private OrderFichaLineRepository orderFichaLineRepository;
    @Autowired private OrderFichaService orderFichaService;

    private Merchant merchant;
    private Customer customer;
    private Product product;
    private Ingredient ingredient;

    @BeforeEach
    void setup() {
        merchant = createMerchantAndAuthenticate();
        Category category = categoryRepository.save(Category.builder()
                .merchant(merchant).name("Açaí").build());
        product = productRepository.save(Product.builder()
                .merchant(merchant).name("Açaí 500ml").price(new BigDecimal("20.00"))
                .status(ProductStatus.ACTIVE).category(category).build());
        customer = customerRepository.save(Customer.builder()
                .merchant(merchant).name("João").phone("11999990000").build());
        ingredient = ingredientRepository.save(Ingredient.builder()
                .merchant(merchant).name("leite ninho").canonicalName("leite ninho")
                .unit("g").costPerUnit(new BigDecimal("0.05"))
                .defaultQuantity(new BigDecimal("20"))
                .status(IngredientStatus.ACTIVE).build());
    }

    @Test
    @DisplayName("create deve persistir pedido com items, extras e calcular custo total")
    void create_shouldPersistOrderWithItemsAndExtras() {
        // PACKAGING "embalagem" 10un a 0.05 → 0.50 (na ficha, entra por padrão)
        includeRepository.save(Include.builder()
                .product(product).name("embalagem")
                .cost(new BigDecimal("0.05")).quantity(new BigDecimal("10"))
                .kind(IncludeKind.PACKAGING).build());
        // Pedido manual: os insumos (PACKAGING + legados sem kind) entram por padrão;
        // este insumo legado caro é DESMARCADO pelo operador (excludedIncludeIds) e sai do custo.
        Include deselected = includeRepository.save(Include.builder()
                .product(product).name("insumo legado desmarcado caro")
                .cost(new BigDecimal("99.00")).quantity(new BigDecimal("10"))
                .kind(null).build());
        // INGREDIENT não é puxado para o pedido manual: mesmo sem exclusão, fica fora do custo.
        includeRepository.save(Include.builder()
                .product(product).name("creme de ovomaltine")
                .cost(new BigDecimal("50.00")).quantity(new BigDecimal("10"))
                .kind(IncludeKind.INGREDIENT).build());

        OrderRequest request = OrderRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId())
                        .quantity(2)
                        .excludedIncludeIds(List.of(deselected.getId()))
                        .extraIngredients(List.of(OrderItemExtraIngredientRequest.builder()
                                .ingredientId(ingredient.getId())
                                .quantity(new BigDecimal("30")) // 30g de extra por unidade
                                .build()))
                        .build()))
                .build();

        OrderResponse response = orderService.create(merchant.getId(), request);

        // Assert response
        assertThat(response.getId()).isNotNull();
        assertThat(response.getTotalValue()).isEqualByComparingTo("40.00"); // 20 × 2

        // Assert persistência
        Order persisted = orderRepository.findByIdAndMerchantId(response.getId(), merchant.getId()).orElseThrow();
        assertThat(persisted.getOrigin()).isEqualTo(OrderOrigin.JETMENU);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(persisted.getItems()).hasSize(1);
        var item = persisted.getItems().get(0);
        assertThat(item.getExtraIngredients()).hasSize(1);
        assertThat(item.getExtraIngredients().get(0).getQuantity()).isEqualByComparingTo("30");
        // totalCost = (insumos 0.50 + extra 30×0.05=1.50) × 2 = 4.00
        // o insumo desmarcado (99×10) sai do custo e fica como exclusão;
        // o INGREDIENT (50×10) nunca entra — só contaria se pedido como extra
        assertThat(persisted.getTotalCost()).isEqualByComparingTo("4.00");
        assertThat(item.getExcludedIncludeIds()).containsExactly(deselected.getId());
    }

    @Test
    @DisplayName("create deve aceitar pedido sem extras")
    void create_shouldAcceptOrderWithoutExtras() {
        OrderRequest request = OrderRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(1).build()))
                .build();

        OrderResponse response = orderService.create(merchant.getId(), request);

        Order persisted = orderRepository.findByIdAndMerchantId(response.getId(), merchant.getId()).orElseThrow();
        assertThat(persisted.getItems().get(0).getExtraIngredients()).isEmpty();
    }

    @Test
    @DisplayName("create deve falhar com cliente de outro merchant (isolamento)")
    void create_shouldRejectCustomerFromAnotherMerchant() {
        Merchant other = createMerchant("Outro");
        Customer fromOther = customerRepository.save(Customer.builder()
                .merchant(other).name("Outro Cliente").phone("999").build());

        OrderRequest request = OrderRequest.builder()
                .customerId(fromOther.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(1).build()))
                .build();

        assertThatThrownBy(() -> orderService.create(merchant.getId(), request))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("findById deve retornar pedido do merchant autenticado")
    void findById_shouldReturnOrder() {
        OrderResponse created = orderService.create(merchant.getId(), simpleRequest());

        OrderResponse fetched = orderService.findById(merchant.getId(), created.getId());

        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getCustomerName()).isEqualTo("João");
    }

    @Test
    @DisplayName("findById deve falhar quando o pedido pertence a outro merchant")
    void findById_shouldNotLeakAcrossMerchants() {
        OrderResponse created = orderService.create(merchant.getId(), simpleRequest());

        // Autentica como outro merchant
        Merchant outro = createMerchant("Outro");

        assertThatThrownBy(() -> orderService.findById(outro.getId(), created.getId()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("update deve substituir items e recalcular custos")
    void update_shouldReplaceItemsAndRecalculateCost() {
        OrderResponse created = orderService.create(merchant.getId(), simpleRequest());

        OrderRequest updated = OrderRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(5).build()))
                .status(OrderStatus.PENDING)
                .build();

        OrderResponse response = orderService.update(merchant.getId(), created.getId(), updated);

        assertThat(response.getTotalValue()).isEqualByComparingTo("100.00"); // 20 × 5
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("update de pedido importado preserva frete e taxa de serviço no total")
    void update_shouldPreserveDeliveryAndServiceFeeOnImportedOrder() {
        // Cenário real (Anota.AI, pedido 6a5ed0bdbd4231d650a99e21): info.total = 46.98 =
        // itens 41.99 + frete 4.00 + taxa de serviço 0.99. Antes da correção, salvar o pedido
        // pela tela derrubava o total para 41.99 e o lucro perdia as taxas duas vezes.
        Order imported = orderRepository.save(Order.builder()
                .merchant(merchant)
                .dateTime(LocalDateTime.now())
                .customer(customer)
                .status(OrderStatus.PAID)
                .origin(OrderOrigin.ANOTA_AI)
                .externalOrderId("6a5ed0bdbd4231d650a99e21")
                .totalValue(new BigDecimal("46.98"))
                .deliveryFee(new BigDecimal("4.00"))
                .serviceFee(new BigDecimal("0.99"))
                .totalCost(BigDecimal.ZERO)
                .estimatedProfit(new BigDecimal("41.99"))
                .items(new ArrayList<>())
                .build());
        imported.getItems().add(OrderItem.builder()
                .order(imported).product(product).quantity(1)
                .unitPrice(new BigDecimal("41.99")).unitCost(BigDecimal.ZERO)
                .build());
        orderRepository.saveAndFlush(imported);

        OrderResponse response = orderService.update(merchant.getId(), imported.getId(),
                OrderRequest.builder()
                        .customerId(customer.getId())
                        .items(List.of(OrderItemRequest.builder()
                                .productId(product.getId()).quantity(1).build()))
                        .status(OrderStatus.PAID)
                        .build());

        // product.price = 20.00 (catálogo) + 4.00 de frete + 0.99 de taxa
        assertThat(response.getTotalValue()).isEqualByComparingTo("24.99");
        assertThat(response.getDeliveryFee()).isEqualByComparingTo("4.00");
        assertThat(response.getServiceFee()).isEqualByComparingTo("0.99");
    }

    @Test
    @DisplayName("updatedAt é gravado na criação e atualizado a cada edição")
    void updatedAt_shouldBeWrittenOnCreateAndBumpedOnUpdate() {
        OrderResponse created = orderService.create(merchant.getId(), simpleRequest());
        // O timestamp é escrito no flush; o teste roda numa transação só, então força o flush.
        orderRepository.flush();
        LocalDateTime afterCreate = orderRepository.findById(created.getId()).orElseThrow().getUpdatedAt();
        assertThat(afterCreate).isNotNull();

        orderService.update(merchant.getId(), created.getId(), OrderRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(3).build()))
                .build());
        orderRepository.flush();

        LocalDateTime afterUpdate = orderRepository.findById(created.getId()).orElseThrow().getUpdatedAt();
        assertThat(afterUpdate).isAfterOrEqualTo(afterCreate);
    }

    @Test
    @DisplayName("delete deve remover pedido e cascatear items + extras")
    void delete_shouldRemoveOrderAndCascadeItems() {
        OrderResponse created = orderService.create(merchant.getId(), simpleRequest());
        UUID orderId = created.getId();

        orderService.delete(merchant.getId(), orderId);

        assertThat(orderRepository.findById(orderId)).isEmpty();
    }

    @Test
    @DisplayName("findAll deve paginar pedidos filtrando por nome do cliente")
    void findAll_shouldPaginateWithCustomerSearch() {
        Customer maria = customerRepository.save(Customer.builder()
                .merchant(merchant).name("Maria").phone("22222222222").build());

        orderService.create(merchant.getId(), simpleRequest());
        orderService.create(merchant.getId(), OrderRequest.builder()
                .customerId(maria.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(1).build()))
                .build());

        var page = orderService.findAll(merchant.getId(), "Maria", null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getCustomerName()).isEqualTo("Maria");
    }

    @Test
    @DisplayName("create com fee deve persistir referência à taxa")
    void create_shouldPersistFeeWhenProvided() {
        Fee fee = feeRepository.save(Fee.builder()
                .merchant(merchant).name("Pix").feeRate(new BigDecimal("0.0099")).build());

        OrderRequest request = OrderRequest.builder()
                .customerId(customer.getId())
                .feeId(fee.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(1).build()))
                .build();

        OrderResponse response = orderService.create(merchant.getId(), request);

        Order persisted = orderRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getFee().getId()).isEqualTo(fee.getId());
    }

    @Test
    @DisplayName("create deve aceitar taxa >= 10% (ex.: iFood 12%) e deduzi-la do lucro")
    void create_shouldAcceptFeeRateOfTenPercentOrMore() {
        // 12% não cabe em numeric(5,4); precisa de precisão maior para persistir.
        Fee fee = feeRepository.save(Fee.builder()
                .merchant(merchant).name("iFood").feeRate(new BigDecimal("12.00")).build());

        OrderRequest request = OrderRequest.builder()
                .customerId(customer.getId())
                .feeId(fee.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(1).build()))
                .build();

        OrderResponse response = orderService.create(merchant.getId(), request);

        Order persisted = orderRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getFee().getFeeRate()).isEqualByComparingTo("12.00");
        // totalValue 20.00, sem custo; taxa = 20 × 12% = 2.40 → lucro = 17.60
        assertThat(persisted.getTotalValue()).isEqualByComparingTo("20.00");
        assertThat(persisted.getEstimatedProfit()).isEqualByComparingTo("17.60");
    }

    private OrderRequest simpleRequest() {
        return OrderRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(1).build()))
                .build();
    }

    // -------------------------------------------------------------------------
    // Ficha do pedido — insumos cobrados UMA vez por pedido
    // -------------------------------------------------------------------------

    /** Configura a ficha do pedido do lojista: 1 sacola (0.80) + 2 guardanapos (0.03). */
    private void givenOrderFichaConfigured() {
        Ingredient sacola = ingredientRepository.save(Ingredient.builder()
                .merchant(merchant).name("sacola").canonicalName("sacola")
                .unit("un").costPerUnit(new BigDecimal("0.80"))
                .status(IngredientStatus.ACTIVE).build());
        Ingredient guardanapo = ingredientRepository.save(Ingredient.builder()
                .merchant(merchant).name("guardanapo").canonicalName("guardanapo")
                .unit("un").costPerUnit(new BigDecimal("0.03"))
                .status(IngredientStatus.ACTIVE).build());
        orderFichaService.replace(merchant.getId(), OrderFichaRequest.builder()
                .lines(List.of(
                        OrderFichaLineRequest.builder().ingredientId(sacola.getId())
                                .quantity(BigDecimal.ONE).build(),
                        OrderFichaLineRequest.builder().ingredientId(guardanapo.getId())
                                .quantity(new BigDecimal("2")).build()))
                .build());
    }

    @Test
    @DisplayName("ficha do pedido é cobrada UMA vez num pedido com 3 itens e quantidade > 1")
    void orderFicha_isChargedExactlyOnceForMultiItemOrder() {
        // 3 produtos distintos, todos com PACKAGING de 1.00, pedidos em quantidades diferentes
        Product p2 = productRepository.save(Product.builder()
                .merchant(merchant).name("Açaí 300ml").price(new BigDecimal("15.00"))
                .status(ProductStatus.ACTIVE).build());
        Product p3 = productRepository.save(Product.builder()
                .merchant(merchant).name("Açaí 700ml").price(new BigDecimal("25.00"))
                .status(ProductStatus.ACTIVE).build());
        for (Product p : List.of(product, p2, p3)) {
            includeRepository.save(Include.builder()
                    .product(p).name("copo").cost(new BigDecimal("1.00")).quantity(BigDecimal.ONE)
                    .kind(IncludeKind.PACKAGING).build());
        }
        givenOrderFichaConfigured();

        // quantidades 2, 3 e 1 = 6 unidades no total
        OrderResponse response = orderService.create(merchant.getId(), OrderRequest.builder()
                .customerId(customer.getId())
                .items(List.of(
                        OrderItemRequest.builder().productId(product.getId()).quantity(2).build(),
                        OrderItemRequest.builder().productId(p2.getId()).quantity(3).build(),
                        OrderItemRequest.builder().productId(p3.getId()).quantity(1).build()))
                .build());

        Order persisted = orderRepository.findByIdAndMerchantId(response.getId(), merchant.getId()).orElseThrow();

        // copos: 6 unidades × 1.00 = 6.00 (por item, correto)
        // ficha do pedido: (1 × 0.80) + (2 × 0.03) = 0.86 — UMA vez, não 3× nem 6×
        assertThat(persisted.getTotalCost()).isEqualByComparingTo("6.86");
        assertThat(persisted.getOrderFicha()).hasSize(2);
        assertThat(response.getOrderFichaCost()).isEqualByComparingTo("0.86");
        assertThat(response.getOrderFicha()).extracting(OrderFichaIngredientResponse::getIngredientName)
                .containsExactly("sacola", "guardanapo");
    }

    @Test
    @DisplayName("lojista sem ficha do pedido: custo idêntico ao de antes — no-op estrito")
    void orderFicha_absentIsStrictNoOp() {
        includeRepository.save(Include.builder()
                .product(product).name("copo").cost(new BigDecimal("1.00")).quantity(BigDecimal.ONE)
                .kind(IncludeKind.PACKAGING).build());

        OrderResponse response = orderService.create(merchant.getId(), OrderRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(2).build()))
                .build());

        Order persisted = orderRepository.findByIdAndMerchantId(response.getId(), merchant.getId()).orElseThrow();
        assertThat(persisted.getTotalCost()).isEqualByComparingTo("2.00"); // 2 × 1.00, inalterado
        assertThat(persisted.getOrderFicha()).isEmpty();
        assertThat(response.getOrderFichaCost()).isEqualByComparingTo("0");
        assertThat(response.getOrderFicha()).isEmpty();
    }

    @Test
    @DisplayName("snapshot: alterar a ficha depois NÃO muda o custo de um pedido já criado")
    void orderFicha_snapshotIsFrozenAfterOrderCreation() {
        givenOrderFichaConfigured();

        OrderResponse created = orderService.create(merchant.getId(), OrderRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(1).build()))
                .build());
        assertThat(created.getOrderFichaCost()).isEqualByComparingTo("0.86");

        // o lojista troca a ficha inteira por um insumo muito mais caro
        Ingredient caixa = ingredientRepository.save(Ingredient.builder()
                .merchant(merchant).name("caixa térmica").canonicalName("caixa termica")
                .unit("un").costPerUnit(new BigDecimal("50.00"))
                .status(IngredientStatus.ACTIVE).build());
        orderFichaService.replace(merchant.getId(), OrderFichaRequest.builder()
                .lines(List.of(OrderFichaLineRequest.builder()
                        .ingredientId(caixa.getId()).quantity(BigDecimal.ONE).build()))
                .build());

        // pedido já fechado mantém o custo com que foi calculado
        Order persisted = orderRepository.findByIdAndMerchantId(created.getId(), merchant.getId()).orElseThrow();
        assertThat(persisted.getTotalCost()).isEqualByComparingTo(created.getTotalCost());
        assertThat(persisted.getOrderFicha()).hasSize(2);

        OrderResponse reloaded = orderService.findById(merchant.getId(), created.getId());
        assertThat(reloaded.getOrderFichaCost()).isEqualByComparingTo("0.86");
        assertThat(reloaded.getOrderFicha()).extracting(OrderFichaIngredientResponse::getIngredientName)
                .containsExactly("sacola", "guardanapo");
    }

    @Test
    @DisplayName("snapshot: reajustar o custo do ingrediente não muda o custo de pedidos passados")
    void orderFicha_snapshotSurvivesIngredientCostChange() {
        givenOrderFichaConfigured();

        OrderResponse created = orderService.create(merchant.getId(), OrderRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(1).build()))
                .build());

        Ingredient sacola = ingredientRepository.findAllByMerchantId(merchant.getId()).stream()
                .filter(i -> i.getName().equals("sacola")).findFirst().orElseThrow();
        sacola.setCostPerUnit(new BigDecimal("99.00"));
        ingredientRepository.save(sacola);

        OrderResponse reloaded = orderService.findById(merchant.getId(), created.getId());
        assertThat(reloaded.getOrderFichaCost()).isEqualByComparingTo("0.86");
    }

    @Test
    @DisplayName("ficha do pedido persiste as linhas em order_ficha_ingredients (cascade)")
    void orderFicha_snapshotRowsArePersisted() {
        givenOrderFichaConfigured();

        OrderResponse created = orderService.create(merchant.getId(), OrderRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product.getId()).quantity(1).build()))
                .build());

        Order persisted = orderRepository.findByIdAndMerchantId(created.getId(), merchant.getId()).orElseThrow();
        var line = persisted.getOrderFicha().stream()
                .filter(l -> l.getIngredientName().equals("sacola")).findFirst().orElseThrow();
        assertThat(line.getId()).isNotNull();
        assertThat(line.getIngredientUnit()).isEqualTo("un");
        assertThat(line.getQuantity()).isEqualByComparingTo("1");
        assertThat(line.getCostPerUnit()).isEqualByComparingTo("0.80");
        assertThat(line.getIngredient()).isNotNull();
    }
}
