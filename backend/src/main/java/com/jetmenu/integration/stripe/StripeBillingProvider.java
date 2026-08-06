package com.jetmenu.integration.stripe;

import com.jetmenu.billing.BillingProvider;
import com.jetmenu.billing.BillingProviderUnavailableException;
import com.jetmenu.billing.CheckoutResponse;
import com.jetmenu.billing.Plan;
import com.jetmenu.billing.PlanNotFoundException;
import com.jetmenu.billing.PlanRepository;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantNotFoundException;
import com.jetmenu.merchant.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Stripe implementation of the billing seam: turns a (merchant, plan) pair into a hosted
 * Stripe Checkout Session in subscription mode and returns its URL. The frontend only
 * redirects there — no publishable key and no Stripe.js are involved.
 * <p>
 * This is the only unqualified {@link BillingProvider} bean; the previous
 * {@code UnavailableBillingProvider} placeholder was deleted rather than left alongside a
 * {@code @Primary} bean, because its whole job — failing explicitly with a pt-BR 503 when no
 * payment is possible — is now covered here for the "Stripe unconfigured" case.
 * <p>
 * Activation is not this class's business: it happens when Stripe confirms the payment, in
 * {@link StripeWebhookService} → {@code SubscriptionActivationService}.
 */
@Service
public class StripeBillingProvider implements BillingProvider {

    private static final Logger log = LoggerFactory.getLogger(StripeBillingProvider.class);

    /** Where the merchant lands after checkout. Both pages are the billing area of the SPA. */
    private static final String RETURN_PATH = "/settings";

    private final StripeProperties properties;
    private final MerchantRepository merchantRepository;
    private final PlanRepository planRepository;
    private final StripePriceResolver priceResolver;
    private final StripeCheckoutGateway checkoutGateway;
    private final String frontendBaseUrl;

    public StripeBillingProvider(StripeProperties properties,
                                 MerchantRepository merchantRepository,
                                 PlanRepository planRepository,
                                 StripePriceResolver priceResolver,
                                 StripeCheckoutGateway checkoutGateway,
                                 @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.properties = properties;
        this.merchantRepository = merchantRepository;
        this.planRepository = planRepository;
        this.priceResolver = priceResolver;
        this.checkoutGateway = checkoutGateway;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public CheckoutResponse createCheckout(UUID merchantId, UUID planId) {
        // Checked before anything else so an unconfigured environment answers a clean 503
        // instead of touching the database and then failing deeper down with a 500.
        if (!properties.isConfigured()) {
            log.warn("Checkout solicitado pelo merchant {} para o plano {}, mas STRIPE_API_KEY "
                    + "não está configurada", merchantId, planId);
            throw new BillingProviderUnavailableException();
        }

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));

        String priceId = priceResolver.resolvePriceId(plan);

        // merchantId and planId are what the webhook decodes back; Stripe just echoes them.
        // client_reference_id carries the merchant as well, so a session can be reconciled
        // straight from the Stripe dashboard.
        StripeCheckoutCommand command = new StripeCheckoutCommand(
                priceId,
                merchantId.toString(),
                Map.of(StripeMetadata.MERCHANT_ID, merchantId.toString(),
                        StripeMetadata.PLAN_ID, planId.toString()),
                merchant.getEmail(),
                successUrl(),
                cancelUrl());

        String url = checkoutGateway.createSubscriptionCheckoutUrl(command);
        log.info("Checkout Stripe criado para o merchant {} no plano '{}'", merchantId, plan.getName());

        return CheckoutResponse.builder().url(url).build();
    }

    /**
     * {@code {CHECKOUT_SESSION_ID}} is a literal placeholder that Stripe substitutes on the
     * redirect — it must not be URL-encoded or resolved here.
     */
    private String successUrl() {
        return baseUrl() + RETURN_PATH + "?checkout=success&session_id={CHECKOUT_SESSION_ID}";
    }

    private String cancelUrl() {
        return baseUrl() + RETURN_PATH + "?checkout=cancelled";
    }

    private String baseUrl() {
        return frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }
}
