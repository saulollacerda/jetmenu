package com.jetmenu.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class OrderCalculations {

    private OrderCalculations() {
    }

    public static BigDecimal calculateTotalCost(List<OrderItem> items) {
        return items.stream()
                .map(item -> {
                    BigDecimal cost = item.getUnitCost();
                    if (cost == null) {
                        cost = BigDecimal.ZERO;
                    }
                    BigDecimal baseCost = cost.multiply(BigDecimal.valueOf(item.getQuantity()));
                    BigDecimal extraCost = calculateExtraIngredientsCost(item);
                    return baseCost.add(extraCost);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal calculateExtraIngredientsCost(OrderItem item) {
        if (item.getExtraIngredients() == null || item.getExtraIngredients().isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal perUnitExtraCost = item.getExtraIngredients().stream()
                .map(extra -> extra.getQuantity().multiply(extra.getCostPerUnit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return perUnitExtraCost.multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    /**
     * Lucro estimado do pedido = (totalValue − deliveryFee − serviceFee) − totalCost − taxa.
     * <p>
     * Todos os campos podem ser {@code null}; tratados como zero. A taxa de entrega e a taxa
     * de serviço ({@code serviceFee}, repassada ao iFood em pedidos importados via Anota.AI)
     * são deduzidas porque são repassadas à plataforma/entregador e não ficam com o
     * restaurante. A taxa de meio de pagamento (em %) incide sobre o subtotal dos produtos
     * ({@code totalValue − deliveryFee − serviceFee}) e também é deduzida, pois é cobrada
     * pela plataforma/adquirente.
     * <p>
     * O percentual usado é {@code order.getFeeRate()} — o snapshot tirado na venda — e NÃO
     * {@code order.getFee().getFeeRate()} ao vivo: a Fee pode ter sido editada depois, e isso
     * não pode mudar o lucro de um pedido já fechado.
     */
    public static BigDecimal calculateEstimatedProfit(Order order) {
        if (order == null) return BigDecimal.ZERO;
        return calculateEstimatedProfit(order.getTotalValue(), order.getDeliveryFee(),
                order.getServiceFee(), order.getTotalCost(), order.getFeeRate());
    }

    /**
     * @deprecated use {@link #calculateEstimatedProfit(BigDecimal, BigDecimal, BigDecimal, BigDecimal)}
     * para incluir a taxa de meio de pagamento. Mantido para compatibilidade.
     */
    @Deprecated
    public static BigDecimal calculateEstimatedProfit(BigDecimal totalValue,
                                                       BigDecimal deliveryFee,
                                                       BigDecimal totalCost) {
        return calculateEstimatedProfit(totalValue, deliveryFee, totalCost, null);
    }

    /**
     * @deprecated use {@link #calculateEstimatedProfit(BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal)}
     * para incluir a taxa de serviço. Mantido para compatibilidade — assume {@code serviceFee = 0}.
     */
    @Deprecated
    public static BigDecimal calculateEstimatedProfit(BigDecimal totalValue,
                                                       BigDecimal deliveryFee,
                                                       BigDecimal totalCost,
                                                       BigDecimal feeRate) {
        return calculateEstimatedProfit(totalValue, deliveryFee, null, totalCost, feeRate);
    }

    /**
     * Overload usado em {@code toResponse}, quando {@code totalCost} já foi resolvido
     * a partir do snapshot ou recalculado a partir dos items (fallback legado).
     *
     * @param serviceFee taxa de serviço repassada ao iFood; {@code null}/zero = sem taxa.
     * @param feeRate percentual da taxa de meio de pagamento (ex.: {@code 2.5} = 2,5%);
     *                {@code null}/zero = sem taxa.
     */
    public static BigDecimal calculateEstimatedProfit(BigDecimal totalValue,
                                                       BigDecimal deliveryFee,
                                                       BigDecimal serviceFee,
                                                       BigDecimal totalCost,
                                                       BigDecimal feeRate) {
        BigDecimal subtotal = calculateProductsSubtotal(totalValue, deliveryFee, serviceFee);
        BigDecimal feeAmount = subtotal
                .multiply(nz(feeRate))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return subtotal.subtract(nz(totalCost)).subtract(feeAmount);
    }

    /**
     * Valor cobrado do cliente por um item = {@code unitPrice × quantity} mais o que foi pago
     * pelos adicionais ({@code salePriceTotal}).
     * <p>
     * É o denominador correto da margem do item, porque o custo do item já soma o custo dos
     * extras: apurar a margem só sobre {@code unitPrice} descontaria o custo do adicional de
     * uma receita que não inclui o que o cliente pagou por ele, subestimando a margem.
     * <p>
     * Só entram extras com preço conhecido e positivo — mesma regra da UI. Complemento base
     * ({@code salePriceTotal = 0}, incluso no produto) e extras sem preço ({@code null}: pedido
     * manual/iFood ou importado antes da V16) não agregam receita.
     * <p>
     * {@code salePriceTotal} é usado como veio do payload, sem multiplicar por
     * {@code quantity} — ver a assunção documentada em
     * {@link OrderItemExtraIngredient#getSalePriceTotal()}.
     *
     * @return {@code null} quando o item não tem preço unitário — margem indefinida.
     */
    public static BigDecimal calculateItemChargedValue(OrderItem item) {
        if (item == null || item.getUnitPrice() == null) {
            return null;
        }
        BigDecimal base = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return base.add(calculatePaidExtrasRevenue(item));
    }

    /** Soma dos adicionais efetivamente pagos pelo cliente neste item. */
    public static BigDecimal calculatePaidExtrasRevenue(OrderItem item) {
        if (item == null || item.getExtraIngredients() == null) {
            return BigDecimal.ZERO;
        }
        return item.getExtraIngredients().stream()
                .map(OrderItemExtraIngredient::getSalePriceTotal)
                .filter(paid -> paid != null && paid.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * @deprecated use {@link #calculateProductsSubtotal(BigDecimal, BigDecimal, BigDecimal)}
     * para excluir também a taxa de serviço. Mantido para compatibilidade — assume {@code serviceFee = 0}.
     */
    @Deprecated
    public static BigDecimal calculateProductsSubtotal(BigDecimal totalValue, BigDecimal deliveryFee) {
        return calculateProductsSubtotal(totalValue, deliveryFee, null);
    }

    /**
     * Subtotal dos produtos do pedido = {@code totalValue − deliveryFee − serviceFee}.
     * <p>
     * É a base sobre a qual o lucro é apurado, e portanto também o denominador
     * da margem: a taxa de entrega e a taxa de serviço são repassadas e não são receita
     * do restaurante, logo não podem inflar o denominador enquanto o numerador (lucro)
     * já as exclui. Todos os campos podem ser {@code null}; tratados como zero.
     */
    public static BigDecimal calculateProductsSubtotal(BigDecimal totalValue, BigDecimal deliveryFee,
                                                       BigDecimal serviceFee) {
        return nz(totalValue).subtract(nz(deliveryFee)).subtract(nz(serviceFee));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
