package com.MenuBank.MenuBank.billing;

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
     * It doubles as the only external-payment-reference slot the schema has today, which is
     * what {@link #getExternalPaymentReference()} exposes. The next payment provider must
     * add its <b>own</b> column (plus migration) and repoint the two accessors below, so
     * new payments never pollute the AbacatePay reconciliation data.
     */
    @Column(name = "abacatepay_billing_id", length = 255, unique = true)
    private String abacatepayBillingId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Provider-agnostic view of the external payment reference used as the activation
     * idempotency key. See {@link #abacatepayBillingId} for the storage caveat.
     */
    @Transient
    public String getExternalPaymentReference() {
        return abacatepayBillingId;
    }

    public void setExternalPaymentReference(String externalPaymentReference) {
        this.abacatepayBillingId = externalPaymentReference;
    }
}
