package com.jetmenu.integration.stripe;

import com.stripe.net.Webhook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds Stripe webhook payloads and <b>genuinely signed</b> {@code Stripe-Signature}
 * headers for tests. Signing is pure HMAC-SHA256 (see {@link Webhook.Util}) — no Stripe
 * account, no network and no credentials are involved.
 */
final class StripeTestEvents {

    private StripeTestEvents() {
    }

    static String checkoutSessionCompleted(UUID merchantId, UUID planId) {
        return checkoutSessionCompleted("cs_test_123", merchantId, planId, 5000L, "paid");
    }

    static String checkoutSessionCompleted(String sessionId,
                                           UUID merchantId,
                                           UUID planId,
                                           Long amountTotal,
                                           String paymentStatus) {
        List<String> entries = new ArrayList<>();
        if (merchantId != null) {
            entries.add("\"" + StripeMetadata.MERCHANT_ID + "\": \"" + merchantId + "\"");
        }
        if (planId != null) {
            entries.add("\"" + StripeMetadata.PLAN_ID + "\": \"" + planId + "\"");
        }
        String metadata = "{" + String.join(", ", entries) + "}";

        return """
        {
          "id": "evt_test_%s",
          "object": "event",
          "api_version": "2024-06-20",
          "created": 1700000000,
          "livemode": false,
          "type": "checkout.session.completed",
          "data": {
            "object": {
              "id": "%s",
              "object": "checkout.session",
              "mode": "subscription",
              "status": "complete",
              "payment_status": "%s",
              "currency": "brl",
              "amount_total": %s,
              "client_reference_id": "%s",
              "metadata": %s
            }
          }
        }""".formatted(
                sessionId,
                sessionId,
                paymentStatus,
                amountTotal == null ? "null" : amountTotal.toString(),
                merchantId == null ? "" : merchantId.toString(),
                metadata);
    }

    /**
     * An {@code invoice.paid} shaped like the real thing: the merchant/plan metadata lives on
     * {@code parent.subscription_details.metadata}, not on the invoice. Copied from an actual
     * payload delivered on API version {@code 2026-06-24.dahlia} — the nesting is the whole
     * point of this builder, since reading it from the wrong place silently yields no renewal.
     */
    static String invoicePaid(String invoiceId,
                              UUID merchantId,
                              UUID planId,
                              Long amountPaid,
                              String billingReason) {
        List<String> entries = new ArrayList<>();
        if (merchantId != null) {
            entries.add("\"" + StripeMetadata.MERCHANT_ID + "\": \"" + merchantId + "\"");
        }
        if (planId != null) {
            entries.add("\"" + StripeMetadata.PLAN_ID + "\": \"" + planId + "\"");
        }
        String metadata = "{" + String.join(", ", entries) + "}";

        return """
        {
          "id": "evt_test_%s",
          "object": "event",
          "api_version": "2026-06-24.dahlia",
          "created": 1700000000,
          "livemode": false,
          "type": "invoice.paid",
          "data": {
            "object": {
              "id": "%s",
              "object": "invoice",
              "status": "paid",
              "currency": "brl",
              "amount_paid": %s,
              "billing_reason": "%s",
              "parent": {
                "type": "subscription_details",
                "quote_details": null,
                "subscription_details": {
                  "subscription": "sub_test_123",
                  "metadata": %s
                }
              }
            }
          }
        }""".formatted(
                invoiceId,
                invoiceId,
                amountPaid == null ? "null" : amountPaid.toString(),
                billingReason,
                metadata);
    }

    static String unhandledEvent() {
        return """
        {
          "id": "evt_test_unhandled",
          "object": "event",
          "api_version": "2024-06-20",
          "created": 1700000000,
          "livemode": false,
          "type": "customer.subscription.updated",
          "data": {
            "object": {
              "id": "sub_test_123",
              "object": "subscription"
            }
          }
        }""";
    }

    /** Produces the header Stripe would send for {@code payload} signed with {@code secret}. */
    static String signatureHeader(String payload, String secret) {
        try {
            long timestamp = Instant.now().getEpochSecond();
            String signature = Webhook.Util.computeHmacSha256(secret, timestamp + "." + payload);
            return "t=" + timestamp + ",v1=" + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar payload de teste", e);
        }
    }
}
