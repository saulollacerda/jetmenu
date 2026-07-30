package com.jetmenu.product;

import com.jetmenu.category.CatalogOrigin;
import com.jetmenu.category.Category;
import com.jetmenu.merchant.Merchant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "products")
@BatchSize(size = 50)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Merchant merchant;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Column(name = "canonical_name")
    private String canonicalName;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    /**
     * Margem ideal (%) definida pelo lojista para este produto. Opcional: null significa
     * "não acompanhada" — produtos existentes permanecem nulos e nada é retroativo.
     */
    @Column(name = "target_margin_pct", precision = 5, scale = 2)
    private BigDecimal targetMarginPct;

    // columnDefinition default is required for dev (ddl-auto=update on non-empty tables);
    // prod relies on the matching Flyway migration.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'JETMENU'")
    private CatalogOrigin origin = CatalogOrigin.JETMENU;

    // iFood Catalog publish identity — generated on the first publish and reused afterwards
    // so republishing is idempotent (PUT /items never duplicates). Null until published.
    @Column(name = "ifood_item_id", length = 36)
    private String ifoodItemId;

    @Column(name = "ifood_product_id", length = 36)
    private String ifoodProductId;

    @Column(name = "ifood_published_at")
    private LocalDateTime ifoodPublishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<Include> includes = List.of();
}
