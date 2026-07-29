package com.jetmenu.order;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.customer.Customer;
import com.jetmenu.customer.CustomerRepository;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the SQL cost of the paged order list ({@code GET /api/orders}).
 *
 * <p>The paged query fetches only {@code customer} and {@code fee}; the whole item graph
 * is loaded lazily while the response is mapped. The lazy collections are batched
 * ({@code @BatchSize}), so they cost a constant number of round trips — but the lookup of
 * already-registered ingredient names for the unmatched sub-items used to run once per
 * order, an N+1 that only shows up for imported orders (Anota.AI / iFood): a page of 20
 * cost 29 statements against 11 for a page of 2.
 *
 * <p>The assertion is a ceiling, not an exact count: what matters is that the cost stays
 * flat when the page grows from a handful of orders to a full page of 20.
 */
@DisplayName("OrderService.findAll — custo em SQL da listagem paginada")
class OrderListQueryCountIntegrationTest extends IntegrationTestBase {

    /**
     * Statement ceiling for one page. A page of 20 orders must not cost more than a
     * constant number of round trips: page + count + one batch per lazy association.
     */
    private static final int MAX_STATEMENTS_PER_PAGE = 12;

    private static final int PAGE_SIZE = 20;

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private IncludeRepository includeRepository;

    @PersistenceContext private EntityManager entityManager;

    @Test
    @DisplayName("uma página de 20 pedidos não deve disparar mais consultas que uma de 2 (sem N+1)")
    void findAll_shouldNotScaleQueriesWithPageSize() {
        Merchant merchant = createMerchantAndAuthenticate();
        Category category = categoryRepository.save(Category.builder()
                .merchant(merchant).name("Açaí").build());
        Ingredient ingredient = ingredientRepository.save(Ingredient.builder()
                .merchant(merchant).name("leite ninho").canonicalName("leite ninho")
                .unit("g").costPerUnit(new BigDecimal("0.05"))
                .defaultQuantity(new BigDecimal("20"))
                .status(IngredientStatus.ACTIVE).build());

        // Distinct customer and product per order: production pages do not repeat rows,
        // so nothing may be served from the first-level cache by accident.
        for (int i = 0; i < PAGE_SIZE; i++) {
            Product product = productRepository.save(Product.builder()
                    .merchant(merchant).name("Açaí 500ml #" + i).price(new BigDecimal("20.00"))
                    .status(ProductStatus.ACTIVE).category(category).build());
            includeRepository.save(Include.builder()
                    .product(product).name("embalagem")
                    .cost(new BigDecimal("0.05")).quantity(new BigDecimal("10"))
                    .kind(IncludeKind.PACKAGING).build());
            Customer customer = customerRepository.save(Customer.builder()
                    .merchant(merchant).name("Cliente " + i).phone("1199999000" + i).build());
            orderRepository.save(buildOrder(merchant, customer, product, ingredient, i));
        }

        int statementsForSmallPage = countStatements(merchant, 2);
        int statementsForFullPage = countStatements(merchant, PAGE_SIZE);

        assertThat(statementsForFullPage)
                .as("statements for a page of %d orders (a page of 2 costs %d)",
                        PAGE_SIZE, statementsForSmallPage)
                .isLessThanOrEqualTo(MAX_STATEMENTS_PER_PAGE)
                .isLessThanOrEqualTo(statementsForSmallPage);
    }

    /** Runs one page load against a cold persistence context and returns the JDBC statement count. */
    private int countStatements(Merchant merchant, int pageSize) {
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Page<OrderResponse> page = orderService.findAll(merchant.getId(), "", null,
                PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "dateTime")));

        // Force the mapping to be fully materialised before counting.
        assertThat(page.getContent()).hasSize(pageSize);
        assertThat(page.getContent().get(0).getItems()).isNotEmpty();

        return (int) statistics.getPrepareStatementCount();
    }

    private Order buildOrder(Merchant merchant, Customer customer, Product product,
                             Ingredient ingredient, int index) {
        Order order = Order.builder()
                .merchant(merchant)
                .customer(customer)
                .dateTime(LocalDateTime.now().minusMinutes(index))
                .status(OrderStatus.PAID)
                // Imported origin: this is the shape of the merchant's real data
                // (Anota.AI / iFood), where items carry unmatched sub-items.
                .origin(OrderOrigin.ANOTA_AI)
                .totalValue(new BigDecimal("40.00"))
                .estimatedProfit(new BigDecimal("30.00"))
                .totalCost(new BigDecimal("10.00"))
                .items(new ArrayList<>())
                .orderFicha(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(2)
                .unitPrice(new BigDecimal("20.00"))
                .unitCost(new BigDecimal("1.00"))
                .build();
        OrderItemExtraIngredient extra = OrderItemExtraIngredient.builder()
                .orderItem(item)
                .ingredient(ingredient)
                .ingredientName(ingredient.getName())
                .ingredientUnit(ingredient.getUnit())
                .quantity(new BigDecimal("30"))
                .costPerUnit(new BigDecimal("0.05"))
                .build();
        OrderItemUnmatchedSubItem unmatched = OrderItemUnmatchedSubItem.builder()
                .orderItem(item)
                .rawName("Granola")
                .canonicalName("granola")
                .quantity(1)
                .salePricePerUnit(new BigDecimal("2.00"))
                .salePriceTotal(new BigDecimal("2.00"))
                .build();
        item.setExtraIngredients(new ArrayList<>(List.of(extra)));
        item.setUnmatchedSubItems(new ArrayList<>(List.of(unmatched)));
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }
}
