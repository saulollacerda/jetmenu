package com.jetmenu.billing;

import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Invoice.getExternalPaymentReference()} used to be a {@code @Transient} alias over
 * the legacy {@code abacatepay_billing_id} column. It is now backed by the provider-agnostic
 * {@code invoices.payment_reference} column, so Stripe payments never write into AbacatePay
 * reconciliation data.
 * <p>
 * Runs against the real Postgres used by the suite, so it proves the column actually exists
 * and round-trips — not just that the getter returns a field.
 */
@DisplayName("Invoice — payment_reference (referência externa de pagamento)")
class InvoicePaymentReferenceTest extends IntegrationTestBase {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    private Subscription persistedSubscription() {
        Merchant merchant = createMerchant();
        LocalDateTime now = LocalDateTime.now();
        return subscriptionRepository.save(Subscription.builder()
                .merchantId(merchant.getId())
                .status(SubscriptionStatus.TRIAL)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private Invoice persistedInvoice(String externalPaymentReference) {
        LocalDateTime now = LocalDateTime.now();
        Invoice invoice = Invoice.builder()
                .subscription(persistedSubscription())
                .amount(new BigDecimal("50.00"))
                .status(InvoiceStatus.PAID)
                .paidAt(now)
                .dueAt(now)
                .createdAt(now)
                .build();
        invoice.setExternalPaymentReference(externalPaymentReference);
        return invoiceRepository.save(invoice);
    }

    @Test
    @DisplayName("deve gravar a referência externa na coluna payment_reference, não em abacatepay_billing_id")
    void shouldStoreReferenceInItsOwnColumn() {
        Invoice saved = persistedInvoice("cs_test_stripe_123");

        assertThat(saved.getPaymentReference()).isEqualTo("cs_test_stripe_123");
        assertThat(saved.getAbacatepayBillingId())
                .as("dados de reconciliação da AbacatePay não podem ser sobrescritos")
                .isNull();
    }

    @Test
    @DisplayName("findByExternalPaymentReference deve consultar payment_reference")
    void shouldLookUpByPaymentReference() {
        Invoice saved = persistedInvoice("cs_test_stripe_456");
        invoiceRepository.flush();

        Optional<Invoice> found = invoiceRepository.findByExternalPaymentReference("cs_test_stripe_456");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("não deve encontrar invoice por uma referência legada da AbacatePay gravada só na coluna antiga")
    void shouldNotMatchLegacyColumn() {
        LocalDateTime now = LocalDateTime.now();
        Invoice legacy = Invoice.builder()
                .subscription(persistedSubscription())
                .amount(new BigDecimal("50.00"))
                .status(InvoiceStatus.PAID)
                .abacatepayBillingId("bill_legacy_abacatepay")
                .paidAt(now)
                .dueAt(now)
                .createdAt(now)
                .build();
        invoiceRepository.saveAndFlush(legacy);

        assertThat(invoiceRepository.findByExternalPaymentReference("bill_legacy_abacatepay"))
                .as("a coluna legada não é mais a chave de idempotência; a migration V30 "
                        + "copia esses valores para payment_reference")
                .isEmpty();
    }

    @Test
    @DisplayName("referência ausente deve resultar em busca vazia")
    void shouldReturnEmptyForUnknownReference() {
        assertThat(invoiceRepository.findByExternalPaymentReference("cs_test_does_not_exist"))
                .isEmpty();
    }
}
