package com.jetmenu.integration.anotaai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Guards the way this integration would silently fail: without a {@code permitAll()} entry
 * in {@code SecurityConfig}, every Anota.AI delivery is rejected with 401 and no order is
 * ever imported.
 * <p>
 * Boots the whole context so the real filter chain runs, and posts <b>unauthenticated</b>,
 * exactly as Anota.AI does. The endpoint's own credential check answers 404 for a delivery
 * it does not recognize — what must never happen is 401/403, which would mean Spring
 * Security rejected the request before the controller ever saw it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SecurityConfig — webhook da Anota.AI")
class AnotaAIWebhookSecurityTest {

    private static final String URL = "/api/webhooks/anotaai/3f2504e0-4f89-11d3-9a0c-0305e82c3301";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST não autenticado chega ao controller (nunca 401/403)")
    void shouldPermitUnauthenticatedAnotaAiCallbacks() throws Exception {
        int status = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"_id\":\"abc\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    /**
     * The secret travels in {@code Authorization} without the {@code Bearer} prefix, which is
     * the same header {@code JwtAuthFilter} reads. The filter must let it through untouched —
     * this fixes that dependency at the level of the real filter chain.
     */
    @Test
    @DisplayName("Authorization sem Bearer não é tratado como token JWT (nunca 401/403)")
    void shouldNotTreatRawAuthorizationAsJwt() throws Exception {
        int status = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "segredo-cru-do-lojista")
                        .content("{\"_id\":\"abc\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }
}
