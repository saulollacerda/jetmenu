package com.jetmenu.integration.stripe;

import com.jetmenu.billing.SubscriptionActivationService;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
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
 * <b>Scope.</b> Only {@code checkout.session.completed} is handled — the event that closes
 * the flow this provider starts. Recurring renewals ({@code invoice.paid}) are not handled
 * yet: Stripe will keep charging the subscription, but JetMenu's own period does not roll
 * forward on its own. See BILLING_PROVIDER.md.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private static final String CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";

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
        if (!CHECKOUT_SESSION_COMPLETED.equals(event.getType())) {
            log.debug("Evento Stripe '{}' ignorado (não tratado)", event.getType());
            return;
        }

        Optional<Session> maybeSession = extractSession(event);
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
     * Stripe delivers events serialized at the account's API version, which may differ from
     * the one this SDK is pinned to; {@code getObject()} then returns empty. The unsafe
     * deserializer reads it anyway, which is correct here because we only touch fields that
     * do not change shape between versions.
     */
    private Optional<Session> extractSession(Event event) {
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

        return stripeObject instanceof Session session ? Optional.of(session) : Optional.empty();
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
