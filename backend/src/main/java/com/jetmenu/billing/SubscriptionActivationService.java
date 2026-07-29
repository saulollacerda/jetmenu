package com.jetmenu.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Turns a confirmed payment into an active subscription. Provider-agnostic on purpose:
 * this is JetMenu's own billing domain and it outlives any single payment platform.
 * <p>
 * A payment provider's webhook is expected to decode its own payload and call
 * {@link #activatePaidSubscription} in JetMenu's terms.
 */
@Service
public class SubscriptionActivationService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionActivationService.class);

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;

    public SubscriptionActivationService(PlanRepository planRepository,
                                         SubscriptionRepository subscriptionRepository,
                                         InvoiceRepository invoiceRepository) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Activates the merchant's subscription on the given plan for one month and records a
     * {@code PAID} invoice.
     * <p>
     * Guarantees:
     * <ul>
     *   <li><b>Idempotent</b> — a payment whose {@code externalPaymentReference} already has
     *       an invoice is ignored, so a provider may safely retry its webhook.</li>
     *   <li>Subscription becomes {@link SubscriptionStatus#ACTIVE}; the current period is
     *       rolled forward to {@code now .. now + 1 month}.</li>
     *   <li>Invoice amount falls back to the plan's monthly price when the provider does not
     *       report a paid amount.</li>
     * </ul>
     * The whole thing runs in one transaction: either the subscription is active and the
     * invoice exists, or neither happened.
     *
     * @param merchantId               merchant whose subscription is being activated
     * @param planId                   plan that was paid for
     * @param amountPaid               amount actually paid, or {@code null} to use the plan price
     * @param externalPaymentReference the provider's payment/charge id — the idempotency key
     * @throws SubscriptionNotFoundException when the merchant has no subscription row
     * @throws PlanNotFoundException         when the plan does not exist
     */
    @Transactional
    public void activatePaidSubscription(UUID merchantId,
                                         UUID planId,
                                         BigDecimal amountPaid,
                                         String externalPaymentReference) {
        if (externalPaymentReference != null
                && invoiceRepository.findByExternalPaymentReference(externalPaymentReference).isPresent()) {
            log.info("Pagamento {} já processado — ignorando", externalPaymentReference);
            return;
        }

        Subscription subscription = subscriptionRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(merchantId));
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));

        LocalDateTime now = LocalDateTime.now();
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusMonths(1));
        subscription.setUpdatedAt(now);
        subscriptionRepository.save(subscription);

        Invoice invoice = Invoice.builder()
                .subscription(subscription)
                .amount(amountPaid != null ? amountPaid : plan.getPriceMonthly())
                .status(InvoiceStatus.PAID)
                .paidAt(now)
                .dueAt(now)
                .createdAt(now)
                .build();
        invoice.setExternalPaymentReference(externalPaymentReference);
        invoiceRepository.save(invoice);

        log.info("Assinatura do merchant {} ativada no plano '{}' até {}",
                merchantId, plan.getName(), subscription.getCurrentPeriodEnd());
    }
}
