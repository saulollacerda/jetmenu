package com.jetmenu.billing;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Stable identifier of the plan, set once at creation from {@link PlanSlug} and never
     * derived from {@link #name} again. External configuration keys on it — the Stripe Price
     * of a plan is read from {@code stripe.price-ids.<slug>} — so renaming a plan for
     * marketing reasons must not break its checkout.
     * <p>
     * Left nullable in the mapping on purpose. Production gets {@code not null unique} from
     * V32, but dev builds its schema with {@code ddl-auto=update}, which cannot add a NOT NULL
     * column to a table that already has rows; {@link BasicPlanSeeder} backfills those at
     * startup. Hibernate's {@code validate} does not check nullability, so prod stays strict.
     */
    @Column(name = "slug", length = 100, unique = true)
    private String slug;

    /**
     * The Stripe Price this plan is sold as, mirrored from the Stripe catalog by
     * {@code StripeCatalogSync} — never typed in by hand. Null until the first sync runs, or
     * forever in an environment with Stripe unconfigured; checkout answers 503 either way.
     * <p>
     * A test-mode price id does not exist in live mode, which is exactly why this is safe to
     * store: each environment syncs against its own Stripe account and fills its own value.
     */
    @Column(name = "stripe_price_id", length = 255, unique = true)
    private String stripePriceId;

    /** The Stripe Product behind {@link #stripePriceId}. Kept for reconciliation only. */
    @Column(name = "stripe_product_id", length = 255)
    private String stripeProductId;

    @Column(name = "min_revenue", nullable = false, precision = 15, scale = 2)
    private BigDecimal minRevenue;

    @Column(name = "max_revenue", precision = 15, scale = 2)
    private BigDecimal maxRevenue;

    @Column(name = "price_monthly", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceMonthly;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> features = new HashMap<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Legacy AbacatePay linkage, kept on purpose. The AbacatePay integration was removed and
     * nothing writes this field any more, but existing rows map plans to real AbacatePay
     * products and accounting reconciles historical invoices through them — never drop the
     * column and never rename the field. The next payment provider must add its own column.
     */
    @Column(name = "abacatepay_product_id", length = 255, unique = true)
    private String abacatepayProductId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
