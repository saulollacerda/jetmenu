package com.jetmenu.billing;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Subscription subscription;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "stripe_invoice_id", length = 255, unique = true)
    private String stripeInvoiceId;

    /**
     * Legacy AbacatePay linkage, kept on purpose. The AbacatePay integration was removed,
     * but historical rows still hold real billing ids and accounting reconciles invoices
     * against them — never drop the column and never rename the field.
     * <p>
     * It is <b>frozen history</b>: nothing writes it any more. External references from the
     * current provider go to {@link #paymentReference}, so new payments can never pollute
     * the AbacatePay reconciliation data.
     */
    @Column(name = "abacatepay_billing_id", length = 255, unique = true)
    private String abacatepayBillingId;

    /**
     * Provider-agnostic external payment reference — the idempotency key used by
     * {@link SubscriptionActivationService}. Holds the Stripe Checkout Session id
     * ({@code cs_…}) for payments made through Stripe.
     * <p>
     * Added by {@code V31__add_invoice_payment_reference.sql}, which also backfills it from
     * {@link #abacatepayBillingId} so historical AbacatePay references stay visible to the
     * idempotency guard.
     */
    @Column(name = "payment_reference", length = 255, unique = true)
    private String paymentReference;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Provider-agnostic view of the external payment reference used as the activation
     * idempotency key. Backed by {@link #paymentReference} — the legacy AbacatePay column is
     * never written through here.
     */
    @Transient
    public String getExternalPaymentReference() {
        return paymentReference;
    }

    public void setExternalPaymentReference(String externalPaymentReference) {
        this.paymentReference = externalPaymentReference;
    }
}
