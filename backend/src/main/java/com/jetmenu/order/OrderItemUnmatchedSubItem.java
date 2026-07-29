package com.jetmenu.order;

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
 * A subItem (Anota.AI) / option (iFood) of an imported order that did NOT match any
 * registered ingredient at import time. Persisted per {@link OrderItem} so it can still be
 * shown in the order details with a "create ingredient" call to action, instead of being
 * silently dropped.
 *
 * <p>Stores the raw display name (used to prefill the ingredient creation form), the
 * canonical name (used to derive whether a matching ingredient now exists — the create
 * button disappears once it does) and the quantity/price charged as they came in the
 * payload. There is no ingredient reference: by definition none matched.
 */
@Entity
@Table(name = "order_item_unmatched_subitems")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemUnmatchedSubItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private OrderItem orderItem;

    /** Display name exactly as received in the payload — prefills the ingredient form. */
    @Column(name = "raw_name", nullable = false)
    private String rawName;

    /**
     * Normalized name used as the match key against the ingredients table. Once an
     * ingredient with this canonical name exists, the create button is derived away.
     */
    @Column(name = "canonical_name", nullable = false)
    private String canonicalName;

    /** Quantity the customer ordered, copied from the payload. */
    @Column(nullable = false)
    private Integer quantity;

    /** Unit price paid by the customer for this subItem, copied literally from the payload. */
    @Column(name = "sale_price_per_unit", precision = 19, scale = 4)
    private BigDecimal salePricePerUnit;

    /** Total price paid by the customer for this subItem, copied literally from the payload. */
    @Column(name = "sale_price_total", precision = 19, scale = 4)
    private BigDecimal salePriceTotal;
}
