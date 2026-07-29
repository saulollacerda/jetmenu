package com.jetmenu.order;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One payment method of an imported order. A single order can legitimately be split across
 * more than one method (e.g. part online, the rest in cash), which is why this is a child
 * table instead of columns on {@link Order}.
 *
 * <p>Purely descriptive: these values are displayed on the order ticket (card brand, cash
 * change) and never take part in the order's financial math.
 *
 * <p>Empty for manual orders and for orders imported before this table existed.
 */
@Entity
@Table(name = "order_payment_methods")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    /** CREDIT, DEBIT, CASH, MEAL_VOUCHER, PIX, ... as reported by the marketplace. */
    @Column(name = "method", length = 60)
    private String method;

    /** ONLINE (already paid through the marketplace) or OFFLINE (collected by the merchant). */
    @Column(name = "type", length = 40)
    private String type;

    /** Card brand (VISA, MASTERCARD, ELO, ...). Null when the method is not a card. */
    @Column(name = "card_brand", length = 60)
    private String cardBrand;

    @Column(name = "value", precision = 19, scale = 4)
    private BigDecimal value;

    @Column(name = "currency", length = 10)
    private String currency;

    /**
     * Bill the customer will hand over when paying in cash — the change owed is
     * {@code changeFor - value}. Null for every non-cash method and for cash without change.
     */
    @Column(name = "change_for", precision = 19, scale = 4)
    private BigDecimal changeFor;
}
