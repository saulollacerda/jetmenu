package com.jetmenu.integration.stripe;

import com.jetmenu.billing.BillingProviderUnavailableException;
import com.stripe.StripeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hands out the {@link StripeClient}, built lazily on first use.
 * <p>
 * Lazily on purpose: the application must boot with Stripe unconfigured (empty
 * {@code STRIPE_API_KEY}), and a client created eagerly at startup would either break the
 * context or hide the misconfiguration behind a 500 later. Instead, asking for a client
 * without a key raises {@link BillingProviderUnavailableException}, which
 * {@code GlobalExceptionHandler} turns into an explicit pt-BR 503.
 * <p>
 * The same client serves test mode and live mode — only the key's value differs.
 */
@Component
public class StripeClientFactory {

    private static final Logger log = LoggerFactory.getLogger(StripeClientFactory.class);

    private final StripeProperties properties;

    private volatile StripeClient client;

    public StripeClientFactory(StripeProperties properties) {
        this.properties = properties;
    }

    /**
     * @throws BillingProviderUnavailableException when {@code STRIPE_API_KEY} is empty
     */
    public StripeClient client() {
        if (!properties.isConfigured()) {
            log.warn("Stripe não está configurado (STRIPE_API_KEY vazia) — respondendo 503");
            throw new BillingProviderUnavailableException();
        }

        StripeClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    // The key is never logged: keys leaking into logs are the leading cause
                    // of Stripe API key takeovers.
                    local = new StripeClient(properties.getApiKey());
                    client = local;
                }
            }
        }
        return local;
    }
}
