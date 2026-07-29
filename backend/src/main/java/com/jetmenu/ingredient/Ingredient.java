package com.jetmenu.ingredient;

import com.jetmenu.merchant.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ingredients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Merchant merchant;

    @Column(nullable = false)
    private String name;

    /**
     * Normalized name (lowercase, accent-free, whitespace-collapsed) used to match
     * orders against ingredients by name. Populated by {@code IngredientService} on
     * create/update via {@link IngredientNameNormalizer}.
     */
    @Column(name = "canonical_name")
    private String canonicalName;

    @Column(nullable = false)
    private String unit;

    /** Custo unitário (R$/unidade) cadastrado manualmente pelo restaurante. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal costPerUnit;

    /**
     * Preço de venda do complemento no cardápio Anota.AI (informativo).
     * NÃO substitui {@link #costPerUnit}.
     */
    @Column(name = "sale_price", precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal defaultQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngredientStatus status;

    @Column(name = "stock_quantity", precision = 19, scale = 4)
    private BigDecimal stockQuantity;

    @Column(name = "last_replenished_at")
    private LocalDateTime lastReplenishedAt;

    @Column(name = "low_stock_threshold", precision = 19, scale = 4)
    private BigDecimal lowStockThreshold;

    /**
     * Creation timestamp (America/Sao_Paulo). Populated by {@code IngredientService}
     * on create. Nullable: rows created before this column existed keep NULL.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Zero-based, per-merchant manual ordering index used as the default listing order
     * (drag-and-drop reordering on the ingredients screen). Backfilled by migration V21;
     * new rows get {@code max(position)+1} for the merchant on create. Nullable only as a
     * safety net for legacy rows — such rows sort last.
     */
    @Column(name = "position")
    private Integer position;
}
