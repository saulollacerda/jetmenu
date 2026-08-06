package com.jetmenu.integration.stripe;

import com.jetmenu.billing.BillingProviderUnavailableException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Proves a webhook callback really came from Stripe before anything acts on it.
 * <p>
 * The {@code /api/webhooks/stripe} path is {@code permitAll()} in {@code SecurityConfig} —
 * it has to be, because Stripe sends no bearer token — so this signature check is the only
 * thing standing between the public internet and
 * {@code SubscriptionActivationService.activatePaidSubscription}. Unsigned callbacks are
 * never accepted, not even when the payload looks well-formed.
 */
@Component
public class StripeEventVerifier {

    private static final Logger log = LoggerFactory.getLogger(StripeEventVerifier.class);

    private final StripeProperties properties;
    private final StripeClientFactory clientFactory;

    public StripeEventVerifier(StripeProperties properties, StripeClientFactory clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    /**
     * @param payload         the raw request body, byte-for-byte as received — any
     *                        reserialization breaks the signature
     * @param signatureHeader the {@code Stripe-Signature} request header
     * @return the verified event
     * @throws StripeWebhookSignatureException    when the header is missing or does not verify (400)
     * @throws BillingProviderUnavailableException when {@code STRIPE_WEBHOOK_SECRET} is not
     *                                            configured (503, so Stripe retries once the
     *                                            environment is fixed)
     */
    public Event verify(String payload, String signatureHeader) {
        if (!properties.isWebhookConfigured()) {
            log.error("Webhook da Stripe recebido, mas STRIPE_WEBHOOK_SECRET não está "
                    + "configurado — impossível verificar a assinatura");
            throw new BillingProviderUnavailableException();
        }

        if (!StringUtils.hasText(signatureHeader)) {
            log.warn("Webhook da Stripe recebido sem cabeçalho Stripe-Signature — rejeitado");
            throw new StripeWebhookSignatureException(
                    "Cabeçalho Stripe-Signature ausente");
        }

        try {
            return clientFactory.client()
                    .constructEvent(payload, signatureHeader, properties.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Assinatura do webhook da Stripe inválida — rejeitado: {}", e.getMessage());
            throw new StripeWebhookSignatureException("Assinatura do webhook inválida");
        }
    }
}
