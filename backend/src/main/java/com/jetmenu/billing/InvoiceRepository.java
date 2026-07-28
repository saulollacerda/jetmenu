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
     * Idempotency lookup for {@link SubscriptionActivationService}. Backed by the legacy
     * {@code abacatepay_billing_id} column because it is the only external-reference column
     * the schema has; the next payment provider must add its own column and repoint this
     * query. See {@link Invoice#getExternalPaymentReference()}.
     */
    @Query("SELECT i FROM Invoice i WHERE i.abacatepayBillingId = :reference")
    Optional<Invoice> findByExternalPaymentReference(@Param("reference") String reference);

    List<Invoice> findBySubscriptionIdAndStatus(UUID subscriptionId, InvoiceStatus status);
}
