package com.jetmenu.product;

import java.math.BigDecimal;
import java.util.List;

/**
 * Utilitario para calcular custos a partir dos {@link Include}s (componentes da ficha
 * tecnica) de um produto.
 *
 * <ul>
 *   <li>{@link #computeUnitCost(List)} — custo teorico da receita completa (todos os
 *       includes). Usado no catalogo de produtos (custo/margem do produto).</li>
 *   <li>{@link #computeOrderBaseCost(List)} — custo base que entra em todo pedido:
 *       apenas {@link IncludeKind#PACKAGING} (copo, colher, embalagem...). Ingredientes
 *       sao opcoes de personalizacao e so geram custo quando pedidos (via
 *       {@code OrderItemExtraIngredient}).</li>
 * </ul>
 */
public final class ProductCostCalculator {

    private ProductCostCalculator() {
    }

    /**
     * Soma {@code cost x quantity} de TODOS os {@link Include}s do produto (receita completa).
     * Retorna {@link BigDecimal#ZERO} se a lista estiver vazia/nula.
     */
    public static BigDecimal computeUnitCost(List<Include> includes) {
        return sum(includes, inc -> true);
    }

    /**
     * Soma {@code cost x quantity} apenas dos {@link Include}s {@link IncludeKind#PACKAGING}.
     * Esse e o custo base de um item de pedido IMPORTADO (AnotaAI/iFood): o que esta sempre
     * presente independentemente do que o cliente escolheu. Includes {@code INGREDIENT}
     * (ou legados sem kind) ficam de fora — so contam quando chegam como extras (subItems).
     * Retorna {@link BigDecimal#ZERO} se a lista estiver vazia/nula ou sem PACKAGING.
     */
    public static BigDecimal computeOrderBaseCost(List<Include> includes) {
        return sum(includes, inc -> inc.getKind() == IncludeKind.PACKAGING);
    }

    /**
     * Soma {@code cost x quantity} dos insumos — {@link IncludeKind#PACKAGING} e legados
     * sem kind —, pulando os includes cujo id esteja em {@code excludedIncludeIds}.
     * Modelo do pedido MANUAL: os insumos acompanham o produto por padrao e o operador
     * desmarca os que ficaram de fora. Includes {@code INGREDIENT} nunca entram: sao
     * opcoes de personalizacao e so geram custo quando pedidos como extras.
     * {@code excludedIncludeIds} nulo/vazio equivale a todos os insumos.
     */
    public static BigDecimal computeSelectedCost(List<Include> includes,
                                                 java.util.Set<java.util.UUID> excludedIncludeIds) {
        java.util.Set<java.util.UUID> excluded =
                excludedIncludeIds != null ? excludedIncludeIds : java.util.Set.of();
        return sum(includes, inc -> inc.getKind() != IncludeKind.INGREDIENT
                && (inc.getId() == null || !excluded.contains(inc.getId())));
    }

    /**
     * Margem (%) sobre o preço de venda: {@code (price − unitCost) / price × 100}, com 2 casas.
     * Fórmula única da margem de um produto — usada tanto no catálogo (margem do produto)
     * quanto no item de pedido (margem realizada naquele pedido). {@code unitCost} nulo conta
     * como zero. Retorna null quando {@code price} é nulo ou zero (margem indefinida).
     */
    public static BigDecimal computeMarginPct(BigDecimal price, BigDecimal unitCost) {
        if (price == null || price.signum() == 0) {
            return null;
        }
        BigDecimal cost = unitCost != null ? unitCost : BigDecimal.ZERO;
        return price.subtract(cost)
                .divide(price, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<Include> includes, java.util.function.Predicate<Include> filter) {
        if (includes == null || includes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return includes.stream()
                .filter(filter)
                .map(inc -> {
                    BigDecimal cost = inc.getCost() != null ? inc.getCost() : BigDecimal.ZERO;
                    BigDecimal qty = inc.getQuantity() != null ? inc.getQuantity() : BigDecimal.ONE;
                    return cost.multiply(qty);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
