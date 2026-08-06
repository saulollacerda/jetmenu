package com.jetmenu.integration.stripe;

import com.jetmenu.billing.SubscriptionActivationService;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Translates a verified Stripe event back into JetMenu's own terms and hands it to
 * {@link SubscriptionActivationService}, which owns what "paid" means.
 * <p>
 * Nothing about activation is reimplemented here: the period roll-forward, the {@code PAID}
 * invoice and the idempotency guard all live in the billing domain and outlive Stripe.
 * <p>
 * <b>Scope.</b> Two events, one per moment money actually arrives:
 * <ul>
 *   <li>{@code checkout.session.completed} — the first payment, closing the flow this
 *       provider starts.</li>
 *   <li>{@code invoice.paid} with {@code billing_reason=subscription_cycle} — every renewal
 *       Stripe collects on its own afterwards.</li>
 * </ul>
 * Everything else is ignored and answered 200 so Stripe stops redelivering.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private static final String CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";
    private static final String INVOICE_PAID = "invoice.paid";

    /**
     * The renewal Stripe collects on its own at the end of each period — the only billing
     * reason that must roll JetMenu's period forward.
     */
    private static final String BILLING_REASON_RENEWAL = "subscription_cycle";

    /**
     * The invoice of the <b>first</b> payment. It is emitted alongside
     * {@code checkout.session.completed} for the very same money, under a different id, so
     * handling it too would grant two periods for one payment: the idempotency guard keys off
     * the payment reference, and {@code cs_…} and {@code in_…} are not the same reference.
     */
    private static final String BILLING_REASON_FIRST_PAYMENT = "subscription_create";

    /**
     * A subscription session is settled when it was paid, or when nothing was due (100%
     * coupon / trial). Anything else — notably {@code unpaid} for async methods still
     * clearing — must not activate; Stripe sends a later event when it does settle.
     */
    private static final Set<String> SETTLED_PAYMENT_STATUSES = Set.of("paid", "no_payment_required");

    private final SubscriptionActivationService activationService;

    public StripeWebhookService(SubscriptionActivationService activationService) {
        this.activationService = activationService;
    }

    /**
     * Never throws for an event we simply do not care about — the caller answers 200 so
     * Stripe stops retrying.
     */
    public void handle(Event event) {
        switch (event.getType()) {
            case CHECKOUT_SESSION_COMPLETED -> handleCheckoutCompleted(event);
            case INVOICE_PAID -> handleInvoicePaid(event);
            default -> log.debug("Evento Stripe '{}' ignorado (não tratado)", event.getType());
        }
    }

    private void handleCheckoutCompleted(Event event) {
        Optional<Session> maybeSession = extractObject(event, Session.class);
        if (maybeSession.isEmpty()) {
            log.error("Evento {} do tipo {} não pôde ser desserializado como Checkout Session",
                    event.getId(), event.getType());
            return;
        }
        Session session = maybeSession.get();

        if (!SETTLED_PAYMENT_STATUSES.contains(session.getPaymentStatus())) {
            log.info("Checkout Session {} ignorada: payment_status={}",
                    session.getId(), session.getPaymentStatus());
            return;
        }

        Map<String, String> metadata = session.getMetadata();
        UUID merchantId = readUuid(metadata, StripeMetadata.MERCHANT_ID, session.getId());
        UUID planId = readUuid(metadata, StripeMetadata.PLAN_ID, session.getId());
        if (merchantId == null || planId == null) {
            log.error("Checkout Session {} sem metadata de merchant/plano — nada a ativar. "
                            + "A sessão foi criada fora do fluxo do JetMenu?", session.getId());
            return;
        }

        // The Checkout Session id is the idempotency key: it is stable across Stripe's
        // retries of the same event, so a redelivery is a no-op inside the activation
        // service instead of a second paid period.
        activationService.activatePaidSubscription(
                merchantId, planId, toReais(session.getAmountTotal()), session.getId());
    }

    /**
     * Rolls the subscription forward when Stripe collects a renewal.
     * <p>
     * Without this the merchant keeps paying every month and JetMenu blocks them at the end of
     * the first period — the worst possible failure, because the money leaves and nothing in
     * the product says why. The billing gate reads {@code status = ACTIVE} and the period, so
     * an unrolled period is indistinguishable from an abandoned subscription.
     */
    private void handleInvoicePaid(Event event) {
        Optional<Invoice> maybeInvoice = extractObject(event, Invoice.class);
        if (maybeInvoice.isEmpty()) {
            log.error("Evento {} do tipo {} não pôde ser desserializado como Invoice",
                    event.getId(), event.getType());
            return;
        }
        Invoice invoice = maybeInvoice.get();

        String billingReason = invoice.getBillingReason();
        if (BILLING_REASON_FIRST_PAYMENT.equals(billingReason)) {
            log.debug("Invoice {} ignorada: é a cobrança inicial, já ativada pelo "
                    + "checkout.session.completed", invoice.getId());
            return;
        }
        if (!BILLING_REASON_RENEWAL.equals(billingReason)) {
            // Not silently dropped: a reason we do not know about is money we may be failing
            // to honour, and it must be visible without a merchant having to complain.
            log.warn("Invoice {} paga com billing_reason='{}', que não é uma renovação — "
                    + "período NÃO foi estendido", invoice.getId(), billingReason);
            return;
        }

        Map<String, String> metadata = subscriptionMetadata(invoice);
        UUID merchantId = readUuid(metadata, StripeMetadata.MERCHANT_ID, invoice.getId());
        UUID planId = readUuid(metadata, StripeMetadata.PLAN_ID, invoice.getId());
        if (merchantId == null || planId == null) {
            log.error("Invoice {} sem metadata de merchant/plano na assinatura — renovação "
                            + "não pôde ser aplicada. A assinatura foi criada fora do fluxo "
                            + "do JetMenu?", invoice.getId());
            return;
        }

        // The invoice id is the idempotency key here: unlike the Checkout Session it is a new
        // value on every cycle, so each renewal grants exactly one period and a redelivery of
        // the same renewal grants none.
        activationService.activatePaidSubscription(
                merchantId, planId, toReais(invoice.getAmountPaid()), invoice.getId());

        log.info("Renovação aplicada para o merchant {} (invoice {})", merchantId, invoice.getId());
    }

    /**
     * The merchant and plan ride on the Stripe Subscription, not on the invoice: they were
     * copied onto {@code subscription_data.metadata} when the Checkout Session was created
     * (see {@code StripeCheckoutGateway}), precisely so renewals — which have no session —
     * can still be traced back. Verified against a real {@code invoice.paid} payload on API
     * version {@code 2026-06-24.dahlia}.
     */
    private Map<String, String> subscriptionMetadata(Invoice invoice) {
        return Optional.ofNullable(invoice.getParent())
                .map(Invoice.Parent::getSubscriptionDetails)
                .map(Invoice.Parent.SubscriptionDetails::getMetadata)
                .orElse(null);
    }

    /**
     * Stripe delivers events serialized at the account's API version, which may differ from
     * the one this SDK is pinned to; {@code getObject()} then returns empty. The unsafe
     * deserializer reads it anyway, which is correct here because we only touch fields that
     * do not change shape between versions.
     */
    private <T extends StripeObject> Optional<T> extractObject(Event event, Class<T> type) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                log.error("Falha ao desserializar o payload do evento {}: {}",
                        event.getId(), e.getMessage());
                return null;
            }
        });

        return type.isInstance(stripeObject) ? Optional.of(type.cast(stripeObject)) : Optional.empty();
    }

    private UUID readUuid(Map<String, String> metadata, String key, String sessionId) {
        if (metadata == null) {
            return null;
        }
        String raw = metadata.get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            log.error("Metadata '{}' da Checkout Session {} não é um UUID válido: {}",
                    key, sessionId, raw);
            return null;
        }
    }

    /** Stripe reports amounts in the currency's smallest unit (centavos for BRL). */
    private BigDecimal toReais(Long amountInCents) {
        return amountInCents == null ? null : BigDecimal.valueOf(amountInCents).movePointLeft(2);
    }
}
