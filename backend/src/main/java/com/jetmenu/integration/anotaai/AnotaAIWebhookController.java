package com.jetmenu.integration.anotaai;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Receives Anota.AI's order webhooks, currently in OBSERVE mode: every delivery is recorded
 * and <b>nothing is imported</b>.
 * <p>
 * Anota.AI does not document the webhook contract, so this endpoint exists to find out what
 * they really send — which header carries the "Token Externo", whether any signature header
 * comes along, and the exact body shape. Until that is known there is nothing to validate
 * against, so the path is public in {@code SecurityConfig}; that is safe precisely
 * <i>because</i> the endpoint imports nothing, leaving an attacker no way to inject an order.
 * <p>
 * Orders keep arriving through the existing polling ({@link OrderSyncScheduler}) during this
 * window, so answering 200 without importing loses nothing.
 * <p>
 * <b>It always answers 200.</b> A rejected delivery is a lost sample, and a webhook that
 * returns errors tends to get retried or switched off on the provider's side.
 */
@RestController
@RequestMapping("/api/webhooks/anotaai")
public class AnotaAIWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AnotaAIWebhookController.class);

    private final AnotaAIWebhookObserver observer;

    public AnotaAIWebhookController(AnotaAIWebhookObserver observer) {
        this.observer = observer;
    }

    /**
     * The merchant id is bound as {@code String}, not {@code UUID}: a value Spring cannot
     * convert would be answered 400 before reaching the capture, and a malformed path is
     * itself something worth recording.
     * <p>
     * The panel offers POST or PUT per event, so both are accepted — which of them Anota.AI
     * actually uses is part of what this capture answers.
     */
    @RequestMapping(value = "/{merchantId}", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<Void> handle(@PathVariable String merchantId,
                                       @RequestBody(required = false) byte[] rawPayload,
                                       HttpServletRequest request) {

        String body = rawPayload == null ? "" : new String(rawPayload, StandardCharsets.UTF_8);

        try {
            observer.capture(merchantId, request.getMethod(), request.getRequestURI(),
                    collectHeaders(request), body);
        } catch (RuntimeException e) {
            log.error("[Anota.AI][OBSERVE] captura falhou, respondendo 200 assim mesmo: {}",
                    e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Every header, unfiltered and lower-cased. The unknown ones are the point of the
     * exercise — the name of the header carrying the "Token Externo" is not documented.
     */
    private Map<String, String> collectHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name)));
        return headers;
    }
}
