package com.MenuBank.MenuBank.billing;

import java.util.UUID;

/**
 * Seam between MenuBank's billing domain and whatever external payment platform is
 * currently integrated.
 * <p>
 * MenuBank owns plans, subscriptions, invoices and the activation rules
 * ({@link SubscriptionActivationService}); a provider only has to turn a
 * (merchant, plan) pair into a hosted checkout the merchant can pay.
 * <p>
 * To integrate a new provider:
 * <ol>
 *   <li>Implement this interface in {@code integration/<provider>/} and expose it as a
 *       {@code @Service} annotated {@code @Primary} (or delete
 *       {@link UnavailableBillingProvider}, which is the current default bean).</li>
 *   <li>Add a webhook controller for the provider's "payment confirmed" event and call
 *       {@link SubscriptionActivationService#activatePaidSubscription} with MenuBank's
 *       own identifiers. The provider is responsible for carrying the merchant and plan
 *       ids through checkout (metadata / external id) and decoding them back.</li>
 *   <li>Permit the new webhook path in {@code SecurityConfig} — the previous provider's
 *       entry was removed with it.</li>
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
