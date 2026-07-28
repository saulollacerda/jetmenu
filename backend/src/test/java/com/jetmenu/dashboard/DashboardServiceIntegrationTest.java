package com.jetmenu.dashboard;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.customer.Customer;
import com.jetmenu.customer.CustomerRepository;
import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DashboardService — integração com Postgres")
class DashboardServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private DashboardService dashboardService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Merchant merchant;
    private Customer customer;
    private Product productA;
    private Product productB;

    @BeforeEach
    void setup() {
        merchant = createMerchant();
        Category cat = categoryRepository.save(Category.builder()
                .merchant(merchant).name("Açaí").build());
        productA = productRepository.save(Product.builder()
                .merchant(merchant).name("Açaí 330ml").price(new BigDecimal("15"))
                .status(ProductStatus.ACTIVE).category(cat).build());
        productB = productRepository.save(Product.builder()
                .merchant(merchant).name("Açaí 500ml").price(new BigDecimal("20"))
                .status(ProductStatus.ACTIVE).category(cat).build());
        customer = customerRepository.save(Customer.builder()
                .merchant(merchant).name("Cliente").phone("99").build());
    }

    @Test
    @DisplayName("getDashboard deve agregar totalSales, orderCount, averageTicket apenas para pedidos PAID")
    void getDashboard_shouldAggregatePaidOrders() {
        LocalDate today = LocalDate.now();
        persistOrder(today.atTime(10, 0), OrderStatus.PAID, new BigDecimal("50.00"), productA, 2);
        persistOrder(today.atTime(11, 0), OrderStatus.PAID, new BigDecimal("30.00"), productB, 1);
        // PENDING não deve entrar
        persistOrder(today.atTime(12, 0), OrderStatus.PENDING, new BigDecimal("999.00"), productA, 1);

        DashboardResponse response = dashboardService.getDashboard(merchant.getId(), today, today);

        assertThat(response.getOrderCount()).isEqualTo(2);
        assertThat(response.getTotalSales()).isEqualByComparingTo("80.00");
        assertThat(response.getAverageTicket()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("getDashboard deve retornar zeros quando não há pedidos PAID no intervalo")
    void getDashboard_shouldReturnZeros() {
        DashboardResponse response = dashboardService.getDashboard(merchant.getId(), LocalDate.now(), LocalDate.now());

        assertThat(response.getOrderCount()).isZero();
        assertThat(response.getTotalSales()).isEqualByComparingTo("0");
        assertThat(response.getAverageTicket()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("getDashboard topProducts deve ordenar por quantidade vendida (decrescente)")
    void getDashboard_shouldRankTopProductsByQuantity() {
        LocalDate today = LocalDate.now();
        persistOrder(today.atTime(10, 0), OrderStatus.PAID, new BigDecimal("100"), productB, 5);
        persistOrder(today.atTime(11, 0), OrderStatus.PAID, new BigDecimal("50"), productA, 2);

        DashboardResponse response = dashboardService.getDashboard(merchant.getId(), today, today);

        assertThat(response.getTopProducts()).hasSize(2);
        assertThat(response.getTopProducts().get(0).getProductName()).isEqualTo("Açaí 500ml");
        assertThat(response.getTopProducts().get(0).getQuantitySold()).isEqualTo(5);
        assertThat(response.getTopProducts().get(1).getProductName()).isEqualTo("Açaí 330ml");
    }

    @Test
    @DisplayName("getDashboard salesByDay deve agrupar por dia em ordem crescente de data")
    void getDashboard_shouldGroupSalesByDay() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        persistOrder(yesterday.atTime(10, 0), OrderStatus.PAID, new BigDecimal("100"), productA, 1);
        persistOrder(today.atTime(10, 0), OrderStatus.PAID, new BigDecimal("50"), productA, 1);
        persistOrder(today.atTime(15, 0), OrderStatus.PAID, new BigDecimal("30"), productA, 1);

        DashboardResponse response = dashboardService.getDashboard(merchant.getId(), yesterday, today);

        assertThat(response.getSalesByDay()).hasSize(2);
        assertThat(response.getSalesByDay().get(0).getDate()).isEqualTo(yesterday);
        assertThat(response.getSalesByDay().get(0).getTotal()).isEqualByComparingTo("100");
        assertThat(response.getSalesByDay().get(1).getDate()).isEqualTo(today);
        assertThat(response.getSalesByDay().get(1).getTotal()).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("getDashboard NÃO deve incluir pedidos de outros merchants (isolamento)")
    void getDashboard_shouldIsolateAcrossMerchants() {
        LocalDate today = LocalDate.now();
        persistOrder(today.atTime(10, 0), OrderStatus.PAID, new BigDecimal("100"), productA, 1);

        // Cria pedido em outro merchant
        Merchant other = createMerchant("Outro");
        Category otherCat = categoryRepository.save(Category.builder()
                .merchant(other).name("X").build());
        Product otherProduct = productRepository.save(Product.builder()
                .merchant(other).name("X").price(new BigDecimal("1"))
                .status(ProductStatus.ACTIVE).category(otherCat).build());
        Customer otherCustomer = customerRepository.save(Customer.builder()
                .merchant(other).name("Outro").phone("1").build());
        Order otherOrder = Order.builder()
                .merchant(other).customer(otherCustomer)
                .dateTime(today.atTime(10, 0)).status(OrderStatus.PAID)
                .totalValue(new BigDecimal("9999")).estimatedProfit(BigDecimal.ZERO)
                .origin(OrderOrigin.JETMENU).build();
        OrderItem otherItem = OrderItem.builder().order(otherOrder).product(otherProduct)
                .quantity(1).unitPrice(new BigDecimal("9999")).build();
        otherOrder.setItems(new ArrayList<>(List.of(otherItem)));
        orderRepository.save(otherOrder);

        DashboardResponse response = dashboardService.getDashboard(merchant.getId(), today, today);

        // Não enxerga o pedido de R$ 9999 do outro merchant
        assertThat(response.getTotalSales()).isEqualByComparingTo("100");
        assertThat(response.getOrderCount()).isEqualTo(1);
    }

    private void persistOrder(LocalDateTime when, OrderStatus status, BigDecimal totalValue,
                              Product product, int quantity) {
        Order order = Order.builder()
                .merchant(merchant).customer(customer)
                .dateTime(when).status(status)
                .totalValue(totalValue)
                .estimatedProfit(totalValue.multiply(new BigDecimal("0.5")))
                .origin(OrderOrigin.JETMENU).build();
        OrderItem item = OrderItem.builder()
                .order(order).product(product).quantity(quantity)
                .unitPrice(product.getPrice()).build();
        order.setItems(new ArrayList<>(List.of(item)));
        orderRepository.save(order);
    }
}
