package com.jetmenu.integration.stripe;

import java.util.Map;

/**
 * Everything needed to open a hosted Stripe Checkout Session, already translated out of
 * JetMenu's domain. Keeping this separate from {@code Plan}/{@code Merchant} is what lets
 * the session parameters be asserted without touching the database.
 *
 * @param priceId           Stripe recurring Price id resolved from configuration
 * @param clientReferenceId opaque reference echoed back by Stripe (the merchant id)
 * @param metadata          JetMenu identifiers carried through checkout, see {@link StripeMetadata}
 * @param customerEmail     pre-fills the checkout form; may be {@code null}
 * @param successUrl        where Stripe sends the merchant after paying
 * @param cancelUrl         where Stripe sends the merchant if they give up
 */
public record StripeCheckoutCommand(String priceId,
                                    String clientReferenceId,
                                    Map<String, String> metadata,
                                    String customerEmail,
                                    String successUrl,
                                    String cancelUrl) {
}
