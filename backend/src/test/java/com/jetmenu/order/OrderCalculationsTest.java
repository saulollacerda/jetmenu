package com.jetmenu.order;

import com.jetmenu.fee.Fee;
import com.jetmenu.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderCalculations.calculateEstimatedProfit(order)")
class OrderCalculationsTest {

    private OrderItem item(BigDecimal unitPrice, BigDecimal unitCost, int quantity,
                           List<OrderItemExtraIngredient> extras) {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        return OrderItem.builder()
                .product(product)
                .unitPrice(unitPrice)
                .unitCost(unitCost)
                .quantity(quantity)
                .extraIngredients(extras)
                .build();
    }

    private OrderItemExtraIngredient extra(BigDecimal qty, BigDecimal costPerUnit) {
        return OrderItemExtraIngredient.builder()
                .quantity(qty)
                .costPerUnit(costPerUnit)
                .ingredientName("x").ingredientUnit("g")
                .build();
    }

    private Order order(BigDecimal totalValue, BigDecimal deliveryFee, BigDecimal totalCost) {
        return Order.builder()
                .totalValue(totalValue)
                .deliveryFee(deliveryFee)
                .totalCost(totalCost)
                .build();
    }

    private Order orderWithServiceFee(BigDecimal totalValue, BigDecimal deliveryFee,
                                      BigDecimal serviceFee, BigDecimal totalCost) {
        return Order.builder()
                .totalValue(totalValue)
                .deliveryFee(deliveryFee)
                .serviceFee(serviceFee)
                .totalCost(totalCost)
                .build();
    }

    @Test
    @DisplayName("lucro = totalValue − deliveryFee − totalCost")
    void shouldSubtractDeliveryAndCostFromTotalValue() {
        Order o = order(new BigDecimal("50.00"), new BigDecimal("5.00"), new BigDecimal("12.00"));

        BigDecimal profit = OrderCalculations.calculateEstimatedProfit(o);

        // 50.00 − 5.00 − 12.00 = 33.00
        assertThat(profit).isEqualByComparingTo("33.00");
    }

    @Test
    @DisplayName("trata deliveryFee null como zero (pedidos JetMenu sem entrega)")
    void shouldTreatNullDeliveryFeeAsZero() {
        Order o = order(new BigDecimal("30.00"), null, new BigDecimal("10.00"));

        BigDecimal profit = OrderCalculations.calculateEstimatedProfit(o);

        // 30.00 − 0 − 10.00 = 20.00
        assertThat(profit).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("trata totalCost null como zero (pedidos sem custo calculado)")
    void shouldTreatNullTotalCostAsZero() {
        Order o = order(new BigDecimal("30.00"), new BigDecimal("4.00"), null);

        BigDecimal profit = OrderCalculations.calculateEstimatedProfit(o);

        // 30.00 − 4.00 − 0 = 26.00
        assertThat(profit).isEqualByComparingTo("26.00");
    }

    @Test
    @DisplayName("trata totalValue null como zero")
    void shouldTreatNullTotalValueAsZero() {
        Order o = order(null, new BigDecimal("5.00"), new BigDecimal("2.00"));

        BigDecimal profit = OrderCalculations.calculateEstimatedProfit(o);

        // 0 − 5.00 − 2.00 = −7.00
        assertThat(profit).isEqualByComparingTo("-7.00");
    }

    @Test
    @DisplayName("retorna zero para order null")
    void shouldReturnZeroForNullOrder() {
        assertThat(OrderCalculations.calculateEstimatedProfit(null))
                .isEqualByComparingTo("0");
    }

    private Order orderWithFee(BigDecimal totalValue, BigDecimal deliveryFee,
                               BigDecimal totalCost, BigDecimal feeRate) {
        return Order.builder()
                .totalValue(totalValue)
                .deliveryFee(deliveryFee)
                .totalCost(totalCost)
                .fee(Fee.builder().name("Pix").feeRate(feeRate).build())
                .build();
    }

    @Test
    @DisplayName("deduz a taxa (feeRate %) do lucro, sobre (totalValue − deliveryFee)")
    void shouldDeductFeeFromProfit() {
        Order o = orderWithFee(new BigDecimal("50.00"), new BigDecimal("5.00"),
                new BigDecimal("12.00"), new BigDecimal("10"));

        BigDecimal profit = OrderCalculations.calculateEstimatedProfit(o);

        // base = 50 − 5 = 45; taxa = 45 × 10% = 4.50; lucro = 45 − 12 − 4.50 = 28.50
        assertThat(profit).isEqualByComparingTo("28.50");
    }

    @Test
    @DisplayName("base da taxa exclui a taxa de entrega (não incide sobre deliveryFee)")
    void shouldApplyFeeOnSubtotalExcludingDelivery() {
        Order o = orderWithFee(new BigDecimal("100.00"), new BigDecimal("10.00"),
                BigDecimal.ZERO, new BigDecimal("10"));

        BigDecimal profit = OrderCalculations.calculateEstimatedProfit(o);

        // base = 100 − 10 = 90; taxa = 90 × 10% = 9.00; lucro = 90 − 0 − 9 = 81.00
        // (se incidisse sobre totalValue seria 10.00 → lucro 80.00)
        assertThat(profit).isEqualByComparingTo("81.00");
    }

    @Test
    @DisplayName("taxa zero não altera o lucro")
    void shouldNotChangeProfitWhenFeeRateIsZero() {
        Order o = orderWithFee(new BigDecimal("50.00"), new BigDecimal("5.00"),
                new BigDecimal("12.00"), BigDecimal.ZERO);

        BigDecimal profit = OrderCalculations.calculateEstimatedProfit(o);

        assertThat(profit).isEqualByComparingTo("33.00");
    }

    // -------------------------------------------------------------------------
    // serviceFee (taxa de serviço repassada ao iFood via Anota.AI)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("lucro = totalValue − deliveryFee − serviceFee − totalCost")
    void shouldSubtractServiceFeeFromProfit() {
        Order o = orderWithServiceFee(new BigDecimal("50.99"), new BigDecimal("5.00"),
                new BigDecimal("0.99"), new BigDecimal("12.00"));

        BigDecimal profit = OrderCalculations.calculateEstimatedProfit(o);

        // 50.99 − 5.00 − 0.99 − 12.00 = 33.00
        assertThat(profit).isEqualByComparingTo("33.00");
    }

    @Test
    @DisplayName("trata serviceFee null como zero (pedidos sem taxa de serviço)")
    void shouldTreatNullServiceFeeAsZero() {
        Order o = orderWithServiceFee(new BigDecimal("50.00"), new BigDecimal("5.00"),
                null, new BigDecimal("12.00"));

        BigDecimal profit = OrderCalculations.calculateEstimatedProfit(o);

        // 50.00 − 5.00 − 0 − 12.00 = 33.00
        assertThat(profit).isEqualByComparingTo("33.00");
    }

    @Test
    @DisplayName("base da taxa de meio de pagamento exclui a taxa de serviço")
    void shouldApplyPaymentFeeOnSubtotalExcludingServiceFee() {
        Order o = Order.builder()
                .totalValue(new BigDecimal("111.00"))
                .deliveryFee(new BigDecimal("10.00"))
                .serviceFee(new BigDecimal("1.00"))
                .totalCost(BigDecimal.ZERO)
                .fee(Fee.builder().name("Pix").feeRate(new BigDecimal("10")).build())
                .build();

        BigDecimal profit = OrderCalculations.calculateEstimatedProfit(o);

        // subtotal = 111 − 10 − 1 = 100; taxa = 100 × 10% = 10; lucro = 100 − 0 − 10 = 90.00
        assertThat(profit).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("calculateProductsSubtotal exclui deliveryFee e serviceFee do subtotal")
    void calculateProductsSubtotal_shouldExcludeDeliveryAndServiceFee() {
        BigDecimal subtotal = OrderCalculations.calculateProductsSubtotal(
                new BigDecimal("50.99"), new BigDecimal("5.00"), new BigDecimal("0.99"));

        // 50.99 − 5.00 − 0.99 = 45.00
        assertThat(subtotal).isEqualByComparingTo("45.00");
    }

    // -------------------------------------------------------------------------
    // calculateTotalCost — comportamento antigo (mantido para o cálculo do snapshot)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("calculateTotalCost soma custo base × qty + custos de extras × qty")
    void calculateTotalCost_shouldSumBaseAndExtras() {
        OrderItem item = item(new BigDecimal("16.00"), new BigDecimal("1.066"), 1,
                List.of(extra(new BigDecimal("40"), new BigDecimal("0.0533"))));

        BigDecimal totalCost = OrderCalculations.calculateTotalCost(List.of(item));

        // (1.066 + 40*0.0533) * 1 = 1.066 + 2.132 = 3.198
        assertThat(totalCost).isEqualByComparingTo("3.198");
    }
}
