package com.jetmenu.order;

import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.merchant.Merchant;
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
 * Linha da configuração da ficha do pedido de um lojista: um ingrediente e a quantidade
 * consumida UMA VEZ por pedido, independentemente da quantidade de itens.
 *
 * <p>É a configuração VIVA (editável pelo lojista em "Configurar pedidos"). O custo de um
 * pedido nunca é lido daqui: no momento da criação/importação estas linhas são copiadas
 * para {@link OrderFichaIngredient}, congelando o custo histórico.
 */
@Entity
@Table(name = "order_ficha_lines",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_ficha_lines_merchant_ingredient",
                columnNames = {"merchant_id", "ingredient_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFichaLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /** Quantidade consumida por pedido, na unidade do ingrediente. */
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
