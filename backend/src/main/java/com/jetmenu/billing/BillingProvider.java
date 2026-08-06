package com.jetmenu.billing;

import java.util.UUID;

/**
 * Seam between JetMenu's billing domain and whatever external payment platform is
 * currently integrated.
 * <p>
 * JetMenu owns plans, subscriptions, invoices and the activation rules
 * ({@link SubscriptionActivationService}); a provider only has to turn a
 * (merchant, plan) pair into a hosted checkout the merchant can pay.
 * <p>
 * The current implementation is {@code com.jetmenu.integration.stripe.StripeBillingProvider}
 * (hosted Stripe Checkout Sessions in subscription mode). It is the only unqualified bean
 * implementing this interface — a second one would fail context startup.
 * <p>
 * To swap the provider:
 * <ol>
 *   <li>Implement this interface in {@code integration/<provider>/} and keep exactly one
 *       unqualified bean (replace the current one, or mark the new one {@code @Primary}).</li>
 *   <li>Add a webhook controller for the provider's "payment confirmed" event and call
 *       {@link SubscriptionActivationService#activatePaidSubscription} with JetMenu's
 *       own identifiers. The provider is responsible for carrying the merchant and plan
 *       ids through checkout (metadata / external id) and decoding them back.</li>
 *   <li>Permit the new webhook path in {@code SecurityConfig}, next to
 *       {@code /api/webhooks/stripe}.</li>
 * </ol>
 *
 * @see SubscriptionActivationService
 */
public interface BillingProvider {

    /**
     * Creates a hosted checkout for the given merchant and plan.
     *
     * @return the URL the merchant must be sent to in order to pay
     * @throws PlanNotFoundException             when the plan does not exist
     * @throws BillingProviderUnavailableException when no payment provider is integrated
     *                                            or the provider cannot be reached
     */
    CheckoutResponse createCheckout(UUID merchantId, UUID planId);
}
