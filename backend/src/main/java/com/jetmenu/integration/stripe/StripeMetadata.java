package com.jetmenu.integration.stripe;

/**
 * Keys under which JetMenu's own identifiers travel through a Stripe Checkout Session and
 * come back on the webhook. Stripe never learns what they mean — it just echoes them.
 */
public final class StripeMetadata {

    /** JetMenu merchant id (UUID) — also sent as {@code client_reference_id}. */
    public static final String MERCHANT_ID = "merchant_id";

    /** JetMenu plan id (UUID). */
    public static final String PLAN_ID = "plan_id";

    private StripeMetadata() {
    }
}
