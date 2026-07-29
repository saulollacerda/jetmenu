package com.jetmenu.product;

import com.jetmenu.merchant.Merchant;

import com.jetmenu.order.Order;
import com.jetmenu.order.OrderFichaIngredient;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderItemExtraIngredient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderCostCalculatorService — modelo aditivo")
class OrderCostCalculatorServiceTest {

    private OrderCostCalculatorService calc;
    private UUID merchantId;
    private Product product;

    @BeforeEach
    void setUp() {
        calc = new OrderCostCalculatorService();
        merchantId = UUID.randomUUID();
        product = Product.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Açaí").build();
    }

    private OrderItem item(int quantity, BigDecimal unitCost, List<OrderItemExtraIngredient> extras) {
        return OrderItem.builder()
                .product(product)
                .quantity(quantity)
                .unitPrice(new BigDecimal("20.00"))
                .unitCost(unitCost)
                .extraIngredients(extras)
                .build();
    }

    private OrderItemExtraIngredient extra(BigDecimal qty, BigDecimal costPerUnit) {
        return OrderItemExtraIngredient.builder()
                .quantity(qty).costPerUnit(costPerUnit)
                .ingredientName("x").ingredientUnit("g")
                .build();
    }

    @Test
    @DisplayName("computeItemUnitCost = item.unitCost + sum(extras.quantity × costPerUnit)")
    void itemUnitCostIsAdditive() {
        OrderItem i = item(1, new BigDecimal("5.00"), List.of(
                extra(new BigDecimal("20"), new BigDecimal("0.10")),
                extra(new BigDecimal("40"), new BigDecimal("0.0533"))
        ));

        BigDecimal unit = calc.computeItemUnitCost(i, merchantId);

        // 5 + (20*0.10) + (40*0.0533) = 5 + 2 + 2.132 = 9.132
        assertThat(unit).isEqualByComparingTo("9.132");
    }

    @Test
    @DisplayName("computeItemUnitCost trata item.unitCost nulo como zero")
    void itemUnitCostHandlesNullUnitCost() {
        OrderItem i = item(1, null, List.of(extra(new BigDecimal("10"), new BigDecimal("0.50"))));
        assertThat(calc.computeItemUnitCost(i, merchantId)).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("computeItemUnitCost sem extras = item.unitCost")
    void itemUnitCostWithoutExtras() {
        OrderItem i = item(1, new BigDecimal("7.50"), List.of());
        assertThat(calc.computeItemUnitCost(i, merchantId)).isEqualByComparingTo("7.50");
    }

    @Test
    @DisplayName("computeOrderTotalCost = sum(computeItemUnitCost × quantity)")
    void orderTotalCostMultipliesByQuantity() {
        OrderItem i = item(2, new BigDecimal("3.00"), List.of(
                extra(new BigDecimal("10"), new BigDecimal("0.20"))
        ));
        Order order = Order.builder().merchant(Merchant.builder().id(merchantId).build()).items(List.of(i)).build();

        // unitCost = 3 + 10*0.20 = 5.00; total = 5.00 × 2 = 10.00
        assertThat(calc.computeOrderTotalCost(order)).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("computeOrderTotalCost soma vários itens do pedido")
    void orderTotalCostSumsMultipleItems() {
        OrderItem a = item(1, new BigDecimal("5.00"), List.of());
        OrderItem b = item(3, new BigDecimal("2.00"), List.of(extra(BigDecimal.ONE, BigDecimal.ONE)));
        Order order = Order.builder().merchant(Merchant.builder().id(merchantId).build()).items(List.of(a, b)).build();

        // a: 5.00; b: (2 + 1) × 3 = 9.00; total = 14.00
        assertThat(calc.computeOrderTotalCost(order)).isEqualByComparingTo("14.00");
    }

    @Test
    @DisplayName("computeOrderTotalCost retorna zero para pedido sem itens ou null")
    void orderTotalCostReturnsZeroForEmptyOrNull() {
        assertThat(calc.computeOrderTotalCost(null)).isEqualByComparingTo("0");
        assertThat(calc.computeOrderTotalCost(Order.builder().merchant(Merchant.builder().id(merchantId).build()).items(List.of()).build()))
                .isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------------
    // Ficha do pedido — insumos cobrados UMA VEZ por pedido (sacola, guardanapo)
    // ---------------------------------------------------------------------

    private OrderFichaIngredient fichaLine(String name, BigDecimal qty, BigDecimal costPerUnit) {
        return OrderFichaIngredient.builder()
                .quantity(qty).costPerUnit(costPerUnit)
                .ingredientName(name).ingredientUnit("un")
                .build();
    }

    @Test
    @DisplayName("ficha do pedido é cobrada UMA vez, mesmo com vários itens e quantidade > 1")
    void orderFichaChargedExactlyOnceRegardlessOfItems() {
        // 3 itens, somando 6 unidades — a sacola continua sendo uma só.
        OrderItem a = item(3, new BigDecimal("2.00"), List.of());
        OrderItem b = item(2, new BigDecimal("1.00"), List.of());
        OrderItem c = item(1, new BigDecimal("4.00"), List.of());
        Order order = Order.builder()
                .merchant(Merchant.builder().id(merchantId).build())
                .items(List.of(a, b, c))
                .orderFicha(List.of(fichaLine("Sacola", BigDecimal.ONE, new BigDecimal("0.50"))))
                .build();

        // itens: (2×3) + (1×2) + (4×1) = 12.00; ficha do pedido: 1 × 0.50 = 0.50 (uma vez)
        assertThat(calc.computeOrderTotalCost(order)).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("ficha do pedido não escala com a quantidade do item")
    void orderFichaDoesNotScaleWithItemQuantity() {
        Order oneUnit = Order.builder()
                .merchant(Merchant.builder().id(merchantId).build())
                .items(List.of(item(1, new BigDecimal("2.00"), List.of())))
                .orderFicha(List.of(fichaLine("Sacola", BigDecimal.ONE, new BigDecimal("0.50"))))
                .build();
        Order tenUnits = Order.builder()
                .merchant(Merchant.builder().id(merchantId).build())
                .items(List.of(item(10, new BigDecimal("2.00"), List.of())))
                .orderFicha(List.of(fichaLine("Sacola", BigDecimal.ONE, new BigDecimal("0.50"))))
                .build();

        BigDecimal fichaOnOne = calc.computeOrderTotalCost(oneUnit).subtract(new BigDecimal("2.00"));
        BigDecimal fichaOnTen = calc.computeOrderTotalCost(tenUnits).subtract(new BigDecimal("20.00"));

        assertThat(fichaOnOne).isEqualByComparingTo(fichaOnTen).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("computeOrderFichaCost soma quantity × costPerUnit de todas as linhas")
    void orderFichaCostSumsAllLines() {
        Order order = Order.builder()
                .merchant(Merchant.builder().id(merchantId).build())
                .items(List.of())
                .orderFicha(List.of(
                        fichaLine("Sacola", BigDecimal.ONE, new BigDecimal("0.50")),
                        fichaLine("Guardanapo", new BigDecimal("2"), new BigDecimal("0.03"))))
                .build();

        // (1 × 0.50) + (2 × 0.03) = 0.56
        assertThat(calc.computeOrderFichaCost(order)).isEqualByComparingTo("0.56");
    }

    @Test
    @DisplayName("sem ficha do pedido (null/vazia) o custo é idêntico ao modelo anterior — no-op")
    void orderFichaAbsentIsNoOp() {
        OrderItem i = item(2, new BigDecimal("3.00"), List.of());
        Order nullFicha = Order.builder()
                .merchant(Merchant.builder().id(merchantId).build()).items(List.of(i)).build();
        Order emptyFicha = Order.builder()
                .merchant(Merchant.builder().id(merchantId).build()).items(List.of(i))
                .orderFicha(List.of()).build();

        assertThat(calc.computeOrderTotalCost(nullFicha)).isEqualByComparingTo("6.00");
        assertThat(calc.computeOrderTotalCost(emptyFicha)).isEqualByComparingTo("6.00");
        assertThat(calc.computeOrderFichaCost(nullFicha)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("computeOrderFichaCost trata quantity/costPerUnit nulos como zero")
    void orderFichaCostHandlesNulls() {
        Order order = Order.builder()
                .merchant(Merchant.builder().id(merchantId).build()).items(List.of())
                .orderFicha(java.util.Arrays.asList(
                        fichaLine("Sacola", null, new BigDecimal("0.50")),
                        fichaLine("Guardanapo", new BigDecimal("2"), null)))
                .build();

        assertThat(calc.computeOrderFichaCost(order)).isEqualByComparingTo("0");
        assertThat(calc.computeOrderFichaCost(null)).isEqualByComparingTo("0");
    }
}
