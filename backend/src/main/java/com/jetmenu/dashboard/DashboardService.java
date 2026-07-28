package com.jetmenu.dashboard;

import com.jetmenu.order.Order;
import com.jetmenu.order.OrderCalculations;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import com.jetmenu.product.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final OrderRepository orderRepository;

    public DashboardService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public DashboardResponse getDashboard(UUID merchantId, LocalDate startDate, LocalDate endDate) {

        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(days - 1);
        LocalDateTime prevStartDateTime = prevStart.atStartOfDay();
        LocalDateTime prevEndDateTime = prevEnd.atTime(23, 59, 59);

        PeriodMetrics current = computeMetrics(merchantId, startDateTime, endDateTime);
        PeriodMetrics previous = computeMetrics(merchantId, prevStartDateTime, prevEndDateTime);

        List<DailySales> salesByDay = calculateSalesByDay(current.paidOrders);
        List<TopProduct> topProducts = calculateTopProducts(current.paidOrders);

        return DashboardResponse.builder()
                .totalSales(current.totalSales)
                .orderCount(current.orderCount)
                .averageTicket(current.averageTicket)
                .estimatedProfit(current.estimatedProfit)
                .salesByDay(salesByDay)
                .topProducts(topProducts)
                .totalSalesChangePct(pctChange(current.totalSales, previous.totalSales))
                .orderCountChangePct(pctChange(BigDecimal.valueOf(current.orderCount), BigDecimal.valueOf(previous.orderCount)))
                .averageTicketChangePct(pctChange(current.averageTicket, previous.averageTicket))
                .estimatedProfitChangePct(pctChange(current.estimatedProfit, previous.estimatedProfit))
                .estimatedMarginPct(marginPct(current.estimatedProfit, current.totalSales))
                .estimatedMarginChangePct(pctChange(
                        marginPct(current.estimatedProfit, current.totalSales),
                        marginPct(previous.estimatedProfit, previous.totalSales)))
                .averageMarginPct(marginPct(current.estimatedProfit, current.productsSubtotal))
                .customerCount(current.customerCount)
                .customerCountChangePct(pctChange(
                        BigDecimal.valueOf(current.customerCount),
                        BigDecimal.valueOf(previous.customerCount)))
                .build();
    }

    public List<PeakHour> peakHours(UUID merchantId, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        List<Object[]> rows = orderRepository.peakHoursByMerchantIdAndDateTimeBetween(
                merchantId, startDateTime, endDateTime);

        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        return rows.stream()
                .map(r -> {
                    int hour = ((Number) r[0]).intValue();
                    long count = ((Number) r[1]).longValue();
                    BigDecimal pct = total == 0
                            ? BigDecimal.ZERO
                            : BigDecimal.valueOf(count)
                                    .multiply(new BigDecimal("100"))
                                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
                    return PeakHour.builder().hour(hour).orderCount(count).pct(pct).build();
                })
                .sorted(Comparator.comparingInt(PeakHour::getHour))
                .toList();
    }

    public List<ChannelBreakdown> channels(UUID merchantId, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        List<Object[]> rows = orderRepository.countByOriginForMerchant(
                merchantId, startDateTime, endDateTime);

        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        return rows.stream()
                .map(r -> {
                    OrderOrigin origin = (OrderOrigin) r[0];
                    long count = ((Number) r[1]).longValue();
                    BigDecimal pct = total == 0
                            ? BigDecimal.ZERO
                            : BigDecimal.valueOf(count)
                                    .multiply(new BigDecimal("100"))
                                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
                    return ChannelBreakdown.builder()
                            .origin(origin != null ? origin : OrderOrigin.JETMENU)
                            .orderCount(count)
                            .pct(pct)
                            .build();
                })
                .toList();
    }

    /**
     * Ranking of ingredients consumed by a merchant's PAID orders over the period, combining
     * the order ficha snapshot (once per order) and the item extras (per item quantity). Each
     * entry carries the ingredient name, its unit, the total quantity consumed and the total
     * cost. Sorted by total cost descending. Dates default to today, like the other endpoints.
     */
    public List<IngredientConsumption> ingredientRanking(UUID merchantId, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        Map<Object, IngredientConsumptionAccumulator> map = new LinkedHashMap<>();

        List<Object[]> fichaRows = orderRepository.sumFichaIngredientConsumptionForMerchant(
                merchantId, startDateTime, endDateTime, OrderStatus.PAID);
        List<Object[]> extraRows = orderRepository.sumExtraIngredientConsumptionForMerchant(
                merchantId, startDateTime, endDateTime, OrderStatus.PAID);

        accumulate(map, fichaRows);
        accumulate(map, extraRows);

        return map.values().stream()
                .map(acc -> IngredientConsumption.builder()
                        .ingredientName(acc.name)
                        .unit(acc.unit)
                        .totalQuantity(acc.totalQuantity)
                        .totalCost(acc.totalCost)
                        .build())
                .sorted(Comparator.comparing(IngredientConsumption::getTotalCost).reversed())
                .toList();
    }

    private static void accumulate(Map<Object, IngredientConsumptionAccumulator> map, List<Object[]> rows) {
        for (Object[] row : rows) {
            Object ingredientId = row[0];
            String name = (String) row[1];
            String unit = (String) row[2];
            BigDecimal quantity = toBigDecimal(row[3]);
            BigDecimal cost = toBigDecimal(row[4]);

            IngredientConsumptionAccumulator acc = map.computeIfAbsent(
                    ingredientId, id -> new IngredientConsumptionAccumulator(name, unit));
            acc.totalQuantity = acc.totalQuantity.add(quantity);
            acc.totalCost = acc.totalCost.add(cost);
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private static final class IngredientConsumptionAccumulator {
        final String name;
        final String unit;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        IngredientConsumptionAccumulator(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }
    }

    private PeriodMetrics computeMetrics(UUID merchantId, LocalDateTime start, LocalDateTime end) {
        List<Order> paidOrders = orderRepository.findAllForReportByMerchantAndPeriodAndStatus(
                merchantId, start, end, OrderStatus.PAID);

        BigDecimal totalSales = paidOrders.stream()
                .map(Order::getTotalValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long orderCount = paidOrders.size();
        BigDecimal averageTicket = orderCount == 0
                ? BigDecimal.ZERO
                : totalSales.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

        BigDecimal estimatedProfit = paidOrders.stream()
                .map(Order::getEstimatedProfit)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Base da margem: subtotal dos produtos (totalValue − deliveryFee − serviceFee),
        // mesma base sobre a qual o lucro é apurado. Taxas de entrega/serviço são repassadas
        // e não são receita do restaurante, logo não entram no denominador.
        BigDecimal productsSubtotal = paidOrders.stream()
                .map(order -> OrderCalculations.calculateProductsSubtotal(
                        order.getTotalValue(), order.getDeliveryFee(), order.getServiceFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Long customerCount = orderRepository.countDistinctCustomersByMerchantIdAndDateTimeBetween(
                merchantId, start, end);

        return new PeriodMetrics(paidOrders, totalSales, orderCount, averageTicket, estimatedProfit,
                productsSubtotal, customerCount != null ? customerCount : 0L);
    }

    private static BigDecimal pctChange(BigDecimal current, BigDecimal previous) {
        if (current == null) current = BigDecimal.ZERO;
        if (previous == null || previous.signum() == 0) {
            return current.signum() == 0 ? BigDecimal.ZERO : new BigDecimal("100.00");
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal marginPct(BigDecimal profit, BigDecimal sales) {
        if (sales == null || sales.signum() == 0 || profit == null) {
            return null;
        }
        return profit.divide(sales, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<DailySales> calculateSalesByDay(List<Order> orders) {
        Map<LocalDate, BigDecimal> salesMap = orders.stream()
                .collect(Collectors.groupingBy(
                        order -> order.getDateTime().toLocalDate(),
                        Collectors.reducing(BigDecimal.ZERO, Order::getTotalValue, BigDecimal::add)
                ));

        return salesMap.entrySet().stream()
                .map(entry -> DailySales.builder()
                        .date(entry.getKey())
                        .total(entry.getValue())
                        .build())
                .sorted(Comparator.comparing(DailySales::getDate))
                .toList();
    }

    private List<TopProduct> calculateTopProducts(List<Order> orders) {
        Map<UUID, TopProductAccumulator> map = new HashMap<>();
        for (Order order : orders) {
            if (order.getItems() == null) continue;
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                if (product == null) continue;
                TopProductAccumulator acc = map.computeIfAbsent(product.getId(),
                        id -> new TopProductAccumulator(product));
                acc.quantity += item.getQuantity();
                BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;
                BigDecimal lineRevenue = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal lineCost = unitCost.multiply(BigDecimal.valueOf(item.getQuantity()));
                acc.revenue = acc.revenue.add(lineRevenue);
                acc.cost = acc.cost.add(lineCost);
            }
        }

        return map.values().stream()
                .map(acc -> TopProduct.builder()
                        .productId(acc.product.getId())
                        .productName(acc.product.getName())
                        .categoryName(acc.product.getCategory() != null ? acc.product.getCategory().getName() : null)
                        .quantitySold(acc.quantity)
                        .revenue(acc.revenue)
                        .marginPct(marginPct(acc.revenue.subtract(acc.cost), acc.revenue))
                        .build())
                .sorted(Comparator.comparingLong(TopProduct::getQuantitySold).reversed())
                .limit(5)
                .toList();
    }

    private record PeriodMetrics(
            List<Order> paidOrders,
            BigDecimal totalSales,
            long orderCount,
            BigDecimal averageTicket,
            BigDecimal estimatedProfit,
            BigDecimal productsSubtotal,
            long customerCount) {}

    private static final class TopProductAccumulator {
        final Product product;
        long quantity;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;

        TopProductAccumulator(Product product) {
            this.product = product;
        }
    }
}
