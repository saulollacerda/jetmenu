package com.jetmenu.integration.stripe;

/**
 * Raised when a callback on the Stripe webhook endpoint is missing a
 * {@code Stripe-Signature} header or carries one that does not verify against
 * {@code STRIPE_WEBHOOK_SECRET}. Mapped to HTTP 400 by {@code GlobalExceptionHandler}:
 * unsigned callbacks are never processed.
 */
public class StripeWebhookSignatureException extends RuntimeException {

    public StripeWebhookSignatureException(String message) {
        super(message);
    }
}
