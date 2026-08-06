package com.jetmenu.integration.stripe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the single most common way this integration silently fails: without a
 * {@code permitAll()} entry in {@code SecurityConfig}, Stripe's server-to-server
 * callbacks are rejected with 401 and no subscription ever activates.
 * <p>
 * Boots the whole context (so the real filter chain runs) and posts <b>unauthenticated</b>,
 * exactly as Stripe does. Stripe is unconfigured under the test profile, so the request is
 * expected to reach the controller and be answered 503 — anything but 401/403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SecurityConfig — webhook da Stripe")
class StripeWebhookSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("deve permitir POST não autenticado em /api/webhooks/stripe (nunca 401/403)")
    void shouldPermitUnauthenticatedStripeCallbacks() throws Exception {
        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=deadbeef")
                        .content("{\"id\":\"evt_test\",\"object\":\"event\",\"type\":\"ping\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
