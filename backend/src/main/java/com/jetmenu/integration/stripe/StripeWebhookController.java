package com.jetmenu.integration.stripe;

import com.stripe.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Receives Stripe's server-to-server callbacks.
 * <p>
 * This path is {@code permitAll()} in {@code SecurityConfig} because Stripe sends no bearer
 * token; authentication is the {@code Stripe-Signature} check in {@link StripeEventVerifier}.
 * <p>
 * Status codes are the whole protocol with Stripe: {@code 200} means "delivered, stop
 * retrying" (including for events we ignore), {@code 400} means the callback was not
 * genuine, and {@code 503} means we are misconfigured and Stripe should try again later.
 */
@RestController
@RequestMapping("/api/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeEventVerifier eventVerifier;
    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeEventVerifier eventVerifier,
                                   StripeWebhookService webhookService) {
        this.eventVerifier = eventVerifier;
        this.webhookService = webhookService;
    }

    /**
     * The body is bound as {@code byte[]}, not as a DTO or a parsed JSON tree: signature
     * verification runs over the exact bytes Stripe signed, and any reserialization would
     * invalidate it. Stripe sends UTF-8 JSON, so decoding is lossless.
     */
    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestBody(required = false) byte[] rawPayload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        String payload = rawPayload == null ? "" : new String(rawPayload, StandardCharsets.UTF_8);

        Event event = eventVerifier.verify(payload, signature);
        log.info("Webhook da Stripe recebido e verificado: evento {} do tipo {}",
                event.getId(), event.getType());

        webhookService.handle(event);

        // Always 200 once the event is genuine — even for event types we do not handle, so
        // Stripe stops redelivering them.
        return ResponseEntity.ok().build();
    }
}
