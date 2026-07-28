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

@Entity
@Table(name = "order_item_extra_ingredients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemExtraIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /**
     * Gramatura total deste extra pedida pelo cliente (por unidade do produto pedido).
     * Para subItems da Anota.AI = {@code subItem.quantity × ingredient.defaultQuantity}.
     * O custo total é {@code quantity × costPerUnit × orderItem.quantity}.
     */
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    /**
     * Snapshot do custo por unidade-de-medida ({@code Ingredient.costPerUnit}, em R$/grama
     * para {@code unit="g"}) no momento do pedido.
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal costPerUnit;

    @Column(nullable = false)
    private String ingredientName;

    @Column(nullable = false)
    private String ingredientUnit;

    /**
     * Preço de venda unitário pago pelo cliente por este adicional, copiado literalmente
     * de {@code subItem.price} da Anota.AI. {@code 0.00} = complemento base (incluso, sem
     * valor agregado). NÃO tem relação com {@link #costPerUnit}: preço é o que o cliente
     * pagou, custo é o que a produção gasta.
     *
     * <p>Nulo para extras sem origem Anota.AI (pedidos manuais, iFood) e para pedidos
     * importados antes da migração V16 — nunca houve backfill.
     */
    @Column(name = "sale_price_per_unit", precision = 19, scale = 4)
    private BigDecimal salePricePerUnit;

    /**
     * Valor total pago pelo cliente por este adicional, copiado literalmente de
     * {@code subItem.total} da Anota.AI (que equivale a {@code price × subItem.quantity}).
     *
     * <p><b>Nunca derive este valor de {@link #quantity}</b>: {@code quantity} é gramatura
     * (g/ml), multiplicar por preço produziria reais × gramas.
     *
     * <p><b>Assunção documentada:</b> o valor é gravado como o payload entrega, sem
     * multiplicar por {@code orderItem.quantity}. Todos os payloads observados até hoje
     * têm {@code item.quantity == 1}, então não há evidência de como a Anota.AI escala
     * o total do subItem quando o produto pai é pedido em múltiplas unidades. Como
     * {@code item.total} já embute os totais dos subItems, multiplicar aqui arriscaria
     * double-counting. Reavaliar quando surgir um pedido real com {@code item.quantity > 1}.
     */
    @Column(name = "sale_price_total", precision = 19, scale = 4)
    private BigDecimal salePriceTotal;
}

