package com.jetmenu.order;

import com.jetmenu.ingredient.Ingredient;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Snapshot de uma linha da ficha do PEDIDO no momento em que ele foi criado/importado.
 *
 * <p>A ficha do pedido reúne os insumos consumidos UMA VEZ por pedido, independentemente
 * de quantos itens ele tenha — sacola de entrega, guardanapo, cupom. Diferente da ficha
 * técnica do produto ({@link com.jetmenu.product.Include}), que é multiplicada
 * pela quantidade do item: 2 copos consomem 2 copos, mas 1 sacola entrega os 2 copos.
 *
 * <p>Espelha {@link OrderItemExtraIngredient}: copia {@code ingredientName},
 * {@code ingredientUnit}, {@code quantity} e {@code costPerUnit} para o pedido em vez de
 * apontar para a configuração viva ({@link OrderFichaLine}). Sem esse snapshot, editar a
 * ficha do pedido reescreveria retroativamente o custo e a margem de pedidos já fechados.
 */
@Entity
@Table(name = "order_ficha_ingredients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFichaIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /** Quantidade consumida por pedido, na unidade do ingrediente. */
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    /** Snapshot de {@code Ingredient.costPerUnit} no momento do pedido. */
    @Column(name = "cost_per_unit", nullable = false, precision = 19, scale = 4)
    private BigDecimal costPerUnit;

    @Column(name = "ingredient_name", nullable = false)
    private String ingredientName;

    @Column(name = "ingredient_unit", nullable = false)
    private String ingredientUnit;
}
