package com.jetmenu.integration.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Stripe configuration. <b>Sandbox and production differ by these VALUES ONLY</b> — the same
 * code path runs in both, so switching environments never requires a code or schema change:
 *
 * <table>
 *   <caption>Value shapes per environment</caption>
 *   <tr><th>Property</th><th>Sandbox (test mode)</th><th>Production (live mode)</th></tr>
 *   <tr><td>{@code stripe.api-key}</td><td>{@code sk_test_…}</td><td>{@code sk_live_…}</td></tr>
 *   <tr><td>{@code stripe.webhook-secret}</td><td>{@code whsec_…} (test endpoint)</td>
 *       <td>{@code whsec_…} (live endpoint)</td></tr>
 * </table>
 *
 * Both default to empty so the application still boots unconfigured; checkout then answers 503
 * with a pt-BR ProblemDetail instead of failing with a 500.
 * <p>
 * There is deliberately no price id here any more. Plans and their prices are created in the
 * Stripe dashboard and mirrored into {@code plans} by {@link StripeCatalogSync}, so adding a
 * plan costs no property, no environment variable and no deploy — and the amount charged
 * cannot drift from the amount displayed, because only one of them is written by hand.
 */
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {

    /** Secret API key. {@code sk_test_…} in sandbox, {@code sk_live_…} in production. */
    private String apiKey = "";

    /** Signing secret ({@code whsec_…}) of the webhook endpoint, used to verify callbacks. */
    private String webhookSecret = "";

    /** {@code false} when {@code STRIPE_API_KEY} is empty — checkout must answer 503. */
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    /** {@code false} when {@code STRIPE_WEBHOOK_SECRET} is empty — callbacks cannot be verified. */
    public boolean isWebhookConfigured() {
        return StringUtils.hasText(webhookSecret);
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
