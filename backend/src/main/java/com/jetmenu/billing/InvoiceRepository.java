package com.jetmenu.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findBySubscriptionId(UUID subscriptionId);

    /**
     * Idempotency lookup for {@link SubscriptionActivationService}, backed by the
     * provider-agnostic {@code invoices.payment_reference} column (migration V30). The legacy
     * {@code abacatepay_billing_id} column is deliberately not consulted: V30 copied its
     * values across, so historical AbacatePay payments are matched through
     * {@code payment_reference} like everything else.
     *
     * @see Invoice#getExternalPaymentReference()
     */
    @Query("SELECT i FROM Invoice i WHERE i.paymentReference = :reference")
    Optional<Invoice> findByExternalPaymentReference(@Param("reference") String reference);

    List<Invoice> findBySubscriptionIdAndStatus(UUID subscriptionId, InvoiceStatus status);
}
