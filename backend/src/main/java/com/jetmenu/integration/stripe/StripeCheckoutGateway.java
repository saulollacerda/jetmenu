package com.jetmenu.integration.stripe;

import com.jetmenu.billing.BillingProviderUnavailableException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The only place that talks to the Stripe Checkout API.
 * <p>
 * Everything that decides <em>what</em> to ask for lives in {@link #buildParams}, which is
 * pure and therefore fully assertable in a unit test; the network call on top of it is a
 * single line.
 */
@Component
public class StripeCheckoutGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutGateway.class);

    private final StripeClientFactory clientFactory;

    public StripeCheckoutGateway(StripeClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /**
     * Opens a hosted Checkout Session and returns the URL the merchant must be sent to.
     *
     * @throws BillingProviderUnavailableException when Stripe is unconfigured or the call
     *         fails — a provider outage must surface as 503, never as a 500
     */
    public String createSubscriptionCheckoutUrl(StripeCheckoutCommand command) {
        try {
            Session session = clientFactory.client().checkout().sessions().create(buildParams(command));
            log.info("Checkout Session {} criada na Stripe para o price {}",
                    session.getId(), command.priceId());
            return session.getUrl();
        } catch (StripeException e) {
            log.error("Falha ao criar Checkout Session na Stripe (price {}): {}",
                    command.priceId(), e.getMessage(), e);
            throw new BillingProviderUnavailableException();
        }
    }

    /**
     * Builds the Checkout Session request.
     * <p>
     * Notes on the choices here:
     * <ul>
     *   <li>{@code mode=subscription} — this is recurring billing, so Stripe owns renewals,
     *       retries and dunning rather than us rebuilding them.</li>
     *   <li>{@code payment_method_types} is deliberately <b>not</b> set. Omitting it enables
     *       dynamic payment methods, so which methods appear is controlled from the Stripe
     *       dashboard; pinning it here would lock out Pix/boleto/wallets and hurt
     *       conversion.</li>
     *   <li>The JetMenu identifiers ride along in {@code metadata} and are echoed back on the
     *       webhook. They are copied onto {@code subscription_data.metadata} too, so the
     *       Subscription that Stripe creates carries the same identifiers for renewal events
     *       and for anyone reading the dashboard.</li>
     * </ul>
     */
    static SessionCreateParams buildParams(StripeCheckoutCommand command) {
        SessionCreateParams.SubscriptionData.Builder subscriptionData =
                SessionCreateParams.SubscriptionData.builder();
        command.metadata().forEach(subscriptionData::putMetadata);

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(command.priceId())
                        .setQuantity(1L)
                        .build())
                .setSubscriptionData(subscriptionData.build())
                .setClientReferenceId(command.clientReferenceId())
                .setSuccessUrl(command.successUrl())
                .setCancelUrl(command.cancelUrl())
                .setLocale(SessionCreateParams.Locale.PT_BR);

        command.metadata().forEach(params::putMetadata);

        if (StringUtils.hasText(command.customerEmail())) {
            params.setCustomerEmail(command.customerEmail());
        }

        return params.build();
    }
}
