package com.jetmenu.dashboard;

import com.jetmenu.merchant.Merchant;

import com.jetmenu.customer.Customer;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService")
class DashboardServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private UUID merchantId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Product productBurguer;
    private Product productPizza;
    private Customer customer;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        startDate = LocalDate.of(2026, 3, 1);
        endDate = LocalDate.of(2026, 3, 31);

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .name("João Silva")
                .build();

        productBurguer = Product.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .name("X-Burguer")
                .price(new BigDecimal("25.00"))
                .status(ProductStatus.ACTIVE)
                .build();

        productPizza = Product.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Pizza Margherita")
                .price(new BigDecimal("45.00"))
                .status(ProductStatus.ACTIVE)
                .build();
    }

    private Order buildOrder(LocalDateTime dateTime, BigDecimal totalValue,
                             BigDecimal estimatedProfit, OrderStatus status,
                             List<OrderItem> items) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .dateTime(dateTime)
                .customer(customer)
                .status(status)
                .totalValue(totalValue)
                .estimatedProfit(estimatedProfit)
                .items(items)
                .build();
        return order;
    }

    private OrderItem buildItem(Product product, int quantity) {
        return OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(quantity)
                .unitPrice(product.getPrice())
                .build();
    }

    private Order buildOrderWithFees(LocalDateTime dateTime, BigDecimal totalValue,
                                     BigDecimal deliveryFee, BigDecimal serviceFee,
                                     BigDecimal estimatedProfit, OrderStatus status) {
        return Order.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .dateTime(dateTime)
                .customer(customer)
                .status(status)
                .totalValue(totalValue)
                .deliveryFee(deliveryFee)
                .serviceFee(serviceFee)
                .estimatedProfit(estimatedProfit)
                .items(List.of())
                .build();
    }

    // -------------------------------------------------------------------------
    // getDashboard() — KPIs
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getDashboard() — KPIs")
    class GetDashboardKpis {

        @Test
        @DisplayName("deve calcular totalSales como soma dos totalValue dos pedidos PAID")
        void shouldCalculateTotalSalesFromPaidOrders() {
            List<Order> paidOrders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("30.00"), OrderStatus.PAID, List.of()),
                    buildOrder(startDate.atTime(14, 0), new BigDecimal("200.00"),
                            new BigDecimal("60.00"), OrderStatus.PAID, List.of())
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(paidOrders);

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getTotalSales()).isEqualByComparingTo(new BigDecimal("300.00"));
        }

        @Test
        @DisplayName("deve calcular orderCount como quantidade de pedidos PAID")
        void shouldCalculateOrderCountFromPaidOrders() {
            List<Order> paidOrders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("30.00"), OrderStatus.PAID, List.of()),
                    buildOrder(startDate.atTime(14, 0), new BigDecimal("200.00"),
                            new BigDecimal("60.00"), OrderStatus.PAID, List.of()),
                    buildOrder(startDate.plusDays(1).atTime(9, 0), new BigDecimal("50.00"),
                            new BigDecimal("15.00"), OrderStatus.PAID, List.of())
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(paidOrders);

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getOrderCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("deve calcular averageTicket como totalSales / orderCount")
        void shouldCalculateAverageTicket() {
            List<Order> paidOrders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("30.00"), OrderStatus.PAID, List.of()),
                    buildOrder(startDate.atTime(14, 0), new BigDecimal("200.00"),
                            new BigDecimal("60.00"), OrderStatus.PAID, List.of())
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(paidOrders);

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            // (100 + 200) / 2 = 150
            assertThat(result.getAverageTicket()).isEqualByComparingTo(new BigDecimal("150.00"));
        }

        @Test
        @DisplayName("deve calcular totalSalesChangePct comparando com período anterior")
        void shouldCalculateTotalSalesChangePct() {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(
                            List.of(buildOrder(startDate.atTime(10, 0), new BigDecimal("200.00"),
                                    new BigDecimal("60.00"), OrderStatus.PAID, List.of())),
                            List.of(buildOrder(startDate.minusDays(31).atTime(10, 0), new BigDecimal("100.00"),
                                    new BigDecimal("30.00"), OrderStatus.PAID, List.of()))
                    );

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            // (200 - 100) / 100 * 100 = 100.00
            assertThat(result.getTotalSalesChangePct()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("deve calcular estimatedMarginPct (estimatedProfit / totalSales × 100)")
        void shouldCalculateEstimatedMarginPct() {
            List<Order> paidOrders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("60.00"), OrderStatus.PAID, List.of())
            );
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(paidOrders);

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getEstimatedMarginPct()).isEqualByComparingTo(new BigDecimal("60.00"));
        }

        @Test
        @DisplayName("deve retornar customerCount obtido do repository")
        void shouldReturnCustomerCount() {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());
            given(orderRepository.countDistinctCustomersByMerchantIdAndDateTimeBetween(eq(merchantId), any(), any()))
                    .willReturn(7L, 4L);

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getCustomerCount()).isEqualTo(7L);
            // (7 - 4) / 4 * 100 = 75.00
            assertThat(result.getCustomerCountChangePct()).isEqualByComparingTo(new BigDecimal("75.00"));
        }

        @Test
        @DisplayName("deve calcular estimatedProfit como soma dos estimatedProfit dos pedidos PAID")
        void shouldCalculateEstimatedProfit() {
            List<Order> paidOrders = List.of(
                    buildOrder(startDate.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("30.00"), OrderStatus.PAID, List.of()),
                    buildOrder(startDate.atTime(14, 0), new BigDecimal("200.00"),
                            new BigDecimal("70.00"), OrderStatus.PAID, List.of())
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(paidOrders);

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getEstimatedProfit()).isEqualByComparingTo(new BigDecimal("100.00"));
        }
    }

    // -------------------------------------------------------------------------
    // getDashboard() — averageMarginPct (margem de lucro média)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getDashboard() — averageMarginPct")
    class GetDashboardAverageMargin {

        @Test
        @DisplayName("deve calcular averageMarginPct = soma(estimatedProfit) / soma(subtotal produtos) × 100, descontando taxas de entrega e serviço")
        void shouldCalculateAverageMarginPctFromProductsSubtotal() {
            // Pedido 1: subtotal = 120 - 10 - 10 = 100 ; lucro = 40
            // Pedido 2: subtotal =  60 -  5 -  5 =  50 ; lucro = 20
            List<Order> paidOrders = List.of(
                    buildOrderWithFees(startDate.atTime(10, 0), new BigDecimal("120.00"),
                            new BigDecimal("10.00"), new BigDecimal("10.00"),
                            new BigDecimal("40.00"), OrderStatus.PAID),
                    buildOrderWithFees(startDate.atTime(14, 0), new BigDecimal("60.00"),
                            new BigDecimal("5.00"), new BigDecimal("5.00"),
                            new BigDecimal("20.00"), OrderStatus.PAID)
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(paidOrders);

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            // (40 + 20) / (100 + 50) * 100 = 40.00
            assertThat(result.getAverageMarginPct()).isEqualByComparingTo(new BigDecimal("40.00"));
        }

        @Test
        @DisplayName("deve retornar averageMarginPct nulo quando não há pedidos no período")
        void shouldReturnNullAverageMarginPctWhenNoOrders() {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getAverageMarginPct()).isNull();
        }

        @Test
        @DisplayName("deve retornar averageMarginPct nulo quando o subtotal de produtos é zero")
        void shouldReturnNullAverageMarginPctWhenProductsSubtotalIsZero() {
            // subtotal = 20 - 10 - 10 = 0 → denominador zero
            List<Order> paidOrders = List.of(
                    buildOrderWithFees(startDate.atTime(10, 0), new BigDecimal("20.00"),
                            new BigDecimal("10.00"), new BigDecimal("10.00"),
                            BigDecimal.ZERO, OrderStatus.PAID)
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(paidOrders);

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getAverageMarginPct()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // getDashboard() — Empty data
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getDashboard() — sem pedidos")
    class GetDashboardEmpty {

        @Test
        @DisplayName("deve retornar KPIs zerados quando não há pedidos no período")
        void shouldReturnZeroKpisWhenNoOrders() {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getTotalSales()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getOrderCount()).isEqualTo(0L);
            assertThat(result.getAverageTicket()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getEstimatedProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("deve retornar salesByDay vazio quando não há pedidos")
        void shouldReturnEmptySalesByDay() {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getSalesByDay()).isEmpty();
        }

        @Test
        @DisplayName("deve retornar topProducts vazio quando não há pedidos")
        void shouldReturnEmptyTopProducts() {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getTopProducts()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // getDashboard() — Sales by Day
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getDashboard() — salesByDay")
    class GetDashboardSalesByDay {

        @Test
        @DisplayName("deve agrupar vendas por dia corretamente")
        void shouldGroupSalesByDay() {
            LocalDate day1 = startDate;
            LocalDate day2 = startDate.plusDays(1);

            List<Order> paidOrders = List.of(
                    buildOrder(day1.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("30.00"), OrderStatus.PAID, List.of()),
                    buildOrder(day1.atTime(14, 0), new BigDecimal("150.00"),
                            new BigDecimal("45.00"), OrderStatus.PAID, List.of()),
                    buildOrder(day2.atTime(9, 0), new BigDecimal("200.00"),
                            new BigDecimal("60.00"), OrderStatus.PAID, List.of())
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(paidOrders);

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getSalesByDay()).hasSize(2);

            DailySales salesDay1 = result.getSalesByDay().stream()
                    .filter(s -> s.getDate().equals(day1)).findFirst().orElseThrow();
            DailySales salesDay2 = result.getSalesByDay().stream()
                    .filter(s -> s.getDate().equals(day2)).findFirst().orElseThrow();

            assertThat(salesDay1.getTotal()).isEqualByComparingTo(new BigDecimal("250.00"));
            assertThat(salesDay2.getTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("deve ordenar salesByDay por data crescente")
        void shouldSortSalesByDayAscending() {
            LocalDate day1 = startDate;
            LocalDate day3 = startDate.plusDays(2);
            LocalDate day2 = startDate.plusDays(1);

            List<Order> paidOrders = List.of(
                    buildOrder(day3.atTime(10, 0), new BigDecimal("50.00"),
                            new BigDecimal("15.00"), OrderStatus.PAID, List.of()),
                    buildOrder(day1.atTime(10, 0), new BigDecimal("100.00"),
                            new BigDecimal("30.00"), OrderStatus.PAID, List.of()),
                    buildOrder(day2.atTime(10, 0), new BigDecimal("75.00"),
                            new BigDecimal("20.00"), OrderStatus.PAID, List.of())
            );

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(paidOrders);

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getSalesByDay()).hasSize(3);
            assertThat(result.getSalesByDay().get(0).getDate()).isEqualTo(day1);
            assertThat(result.getSalesByDay().get(1).getDate()).isEqualTo(day2);
            assertThat(result.getSalesByDay().get(2).getDate()).isEqualTo(day3);
        }
    }

    // -------------------------------------------------------------------------
    // getDashboard() — Top 5 Products
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getDashboard() — topProducts")
    class GetDashboardTopProducts {

        @Test
        @DisplayName("deve retornar top produtos ordenados por quantidade vendida decrescente")
        void shouldReturnTopProductsSortedByQuantityDesc() {
            OrderItem item1 = buildItem(productBurguer, 5);
            OrderItem item2 = buildItem(productPizza, 10);

            Order order = buildOrder(startDate.atTime(10, 0), new BigDecimal("500.00"),
                    new BigDecimal("150.00"), OrderStatus.PAID, List.of(item1, item2));

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of(order));

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getTopProducts()).hasSize(2);
            assertThat(result.getTopProducts().get(0).getProductName()).isEqualTo("Pizza Margherita");
            assertThat(result.getTopProducts().get(0).getQuantitySold()).isEqualTo(10L);
            assertThat(result.getTopProducts().get(1).getProductName()).isEqualTo("X-Burguer");
            assertThat(result.getTopProducts().get(1).getQuantitySold()).isEqualTo(5L);
        }

        @Test
        @DisplayName("deve limitar topProducts a no máximo 5 produtos")
        void shouldLimitTopProductsToFive() {
            Product p1 = Product.builder().id(UUID.randomUUID()).name("Produto A").price(BigDecimal.TEN).status(ProductStatus.ACTIVE).build();
            Product p2 = Product.builder().id(UUID.randomUUID()).name("Produto B").price(BigDecimal.TEN).status(ProductStatus.ACTIVE).build();
            Product p3 = Product.builder().id(UUID.randomUUID()).name("Produto C").price(BigDecimal.TEN).status(ProductStatus.ACTIVE).build();
            Product p4 = Product.builder().id(UUID.randomUUID()).name("Produto D").price(BigDecimal.TEN).status(ProductStatus.ACTIVE).build();
            Product p5 = Product.builder().id(UUID.randomUUID()).name("Produto E").price(BigDecimal.TEN).status(ProductStatus.ACTIVE).build();
            Product p6 = Product.builder().id(UUID.randomUUID()).name("Produto F").price(BigDecimal.TEN).status(ProductStatus.ACTIVE).build();

            Order order = buildOrder(startDate.atTime(10, 0), new BigDecimal("600.00"),
                    new BigDecimal("200.00"), OrderStatus.PAID,
                    List.of(buildItem(p1, 10), buildItem(p2, 8), buildItem(p3, 6),
                            buildItem(p4, 4), buildItem(p5, 2), buildItem(p6, 1)));

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of(order));

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getTopProducts()).hasSize(5);
            assertThat(result.getTopProducts().get(0).getProductName()).isEqualTo("Produto A");
        }

        @Test
        @DisplayName("deve somar quantidades do mesmo produto em pedidos diferentes")
        void shouldSumQuantitiesAcrossOrders() {
            Order order1 = buildOrder(startDate.atTime(10, 0), new BigDecimal("100.00"),
                    new BigDecimal("30.00"), OrderStatus.PAID,
                    List.of(buildItem(productBurguer, 3)));

            Order order2 = buildOrder(startDate.atTime(14, 0), new BigDecimal("100.00"),
                    new BigDecimal("30.00"), OrderStatus.PAID,
                    List.of(buildItem(productBurguer, 7)));

            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of(order1, order2));

            DashboardResponse result = dashboardService.getDashboard(merchantId, startDate, endDate);

            assertThat(result.getTopProducts()).hasSize(1);
            assertThat(result.getTopProducts().get(0).getProductName()).isEqualTo("X-Burguer");
            assertThat(result.getTopProducts().get(0).getQuantitySold()).isEqualTo(10L);
        }
    }

    // -------------------------------------------------------------------------
    // getDashboard() — Default date range
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getDashboard() — data padrão")
    class GetDashboardDefaultDate {

        @Test
        @DisplayName("deve usar data de hoje quando startDate e endDate são nulos")
        void shouldUseCurrentDateWhenDatesAreNull() {
            given(orderRepository.findAllForReportByMerchantAndPeriodAndStatus(eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());

            DashboardResponse result = dashboardService.getDashboard(merchantId, null, null);

            assertThat(result).isNotNull();
            then(orderRepository).should(atLeastOnce()).findAllForReportByMerchantAndPeriodAndStatus(
                    eq(merchantId),
                    eq(LocalDate.now().atStartOfDay()),
                    eq(LocalDate.now().atTime(23, 59, 59)),
                    eq(OrderStatus.PAID)
            );
        }
    }

    // -------------------------------------------------------------------------
    // peakHours()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("peakHours(startDate, endDate)")
    class PeakHours {

        @Test
        @DisplayName("deve retornar items com hour/orderCount/pct calculados")
        void shouldReturnPeakHoursWithPct() {
            given(orderRepository.peakHoursByMerchantIdAndDateTimeBetween(eq(merchantId), any(), any()))
                    .willReturn(List.of(
                            new Object[]{11, 3L},
                            new Object[]{12, 7L}
                    ));

            List<PeakHour> result = dashboardService.peakHours(merchantId, startDate, endDate);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getHour()).isEqualTo(11);
            assertThat(result.get(0).getOrderCount()).isEqualTo(3L);
            // 3 / 10 * 100 = 30.00
            assertThat(result.get(0).getPct()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(result.get(1).getHour()).isEqualTo(12);
            assertThat(result.get(1).getPct()).isEqualByComparingTo(new BigDecimal("70.00"));
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não há pedidos no período")
        void shouldReturnEmptyWhenNoOrders() {
            given(orderRepository.peakHoursByMerchantIdAndDateTimeBetween(eq(merchantId), any(), any()))
                    .willReturn(List.of());

            List<PeakHour> result = dashboardService.peakHours(merchantId, startDate, endDate);

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // channels()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("channels(startDate, endDate)")
    class Channels {

        @Test
        @DisplayName("deve retornar contagem por origin com pct")
        void shouldReturnChannelsWithPct() {
            given(orderRepository.countByOriginForMerchant(eq(merchantId), any(), any()))
                    .willReturn(List.of(
                            new Object[]{com.jetmenu.order.OrderOrigin.JETMENU, 3L},
                            new Object[]{com.jetmenu.order.OrderOrigin.ANOTA_AI, 1L}
                    ));

            List<ChannelBreakdown> result = dashboardService.channels(merchantId, startDate, endDate);

            assertThat(result).hasSize(2);
            BigDecimal jetmenuPct = result.stream()
                    .filter(c -> c.getOrigin() == com.jetmenu.order.OrderOrigin.JETMENU)
                    .findFirst().get().getPct();
            // 3 / 4 * 100 = 75.00
            assertThat(jetmenuPct).isEqualByComparingTo(new BigDecimal("75.00"));
        }
    }

    // -------------------------------------------------------------------------
    // ingredientRanking()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ingredientRanking(startDate, endDate)")
    class IngredientRanking {

        @Test
        @DisplayName("deve combinar ficha e extras, somar o mesmo ingrediente e ordenar por custo total decrescente")
        void shouldCombineFichaAndExtrasSortedByCostDesc() {
            UUID sacolaId = UUID.randomUUID();
            UUID baconId = UUID.randomUUID();
            UUID queijoId = UUID.randomUUID();

            given(orderRepository.sumFichaIngredientConsumptionForMerchant(
                    eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of(
                            new Object[]{sacolaId, "Sacola", "un",
                                    new BigDecimal("10"), new BigDecimal("5.00")},
                            new Object[]{baconId, "Bacon", "g",
                                    new BigDecimal("200"), new BigDecimal("8.00")}
                    ));
            given(orderRepository.sumExtraIngredientConsumptionForMerchant(
                    eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of(
                            new Object[]{baconId, "Bacon", "g",
                                    new BigDecimal("50"), new BigDecimal("2.00")},
                            new Object[]{queijoId, "Queijo", "g",
                                    new BigDecimal("100"), new BigDecimal("12.00")}
                    ));

            List<IngredientConsumption> result =
                    dashboardService.ingredientRanking(merchantId, startDate, endDate);

            assertThat(result).hasSize(3);
            // Queijo (12.00) > Bacon (8.00 + 2.00 = 10.00) > Sacola (5.00)
            assertThat(result.get(0).getIngredientName()).isEqualTo("Queijo");
            assertThat(result.get(0).getUnit()).isEqualTo("g");
            assertThat(result.get(0).getTotalQuantity()).isEqualByComparingTo(new BigDecimal("100"));
            assertThat(result.get(0).getTotalCost()).isEqualByComparingTo(new BigDecimal("12.00"));

            assertThat(result.get(1).getIngredientName()).isEqualTo("Bacon");
            assertThat(result.get(1).getTotalQuantity()).isEqualByComparingTo(new BigDecimal("250"));
            assertThat(result.get(1).getTotalCost()).isEqualByComparingTo(new BigDecimal("10.00"));

            assertThat(result.get(2).getIngredientName()).isEqualTo("Sacola");
            assertThat(result.get(2).getTotalCost()).isEqualByComparingTo(new BigDecimal("5.00"));
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não há consumo no período")
        void shouldReturnEmptyWhenNoConsumption() {
            given(orderRepository.sumFichaIngredientConsumptionForMerchant(
                    eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());
            given(orderRepository.sumExtraIngredientConsumptionForMerchant(
                    eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());

            List<IngredientConsumption> result =
                    dashboardService.ingredientRanking(merchantId, startDate, endDate);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deve usar data de hoje quando startDate e endDate são nulos")
        void shouldUseCurrentDateWhenDatesAreNull() {
            given(orderRepository.sumFichaIngredientConsumptionForMerchant(
                    eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());
            given(orderRepository.sumExtraIngredientConsumptionForMerchant(
                    eq(merchantId), any(), any(), eq(OrderStatus.PAID)))
                    .willReturn(List.of());

            dashboardService.ingredientRanking(merchantId, null, null);

            then(orderRepository).should().sumFichaIngredientConsumptionForMerchant(
                    eq(merchantId),
                    eq(LocalDate.now().atStartOfDay()),
                    eq(LocalDate.now().atTime(23, 59, 59)),
                    eq(OrderStatus.PAID));
        }
    }
}

