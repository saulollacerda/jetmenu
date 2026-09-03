package com.jetmenu.integration.anotaai;

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
 * Guards the way this integration would silently fail: without a {@code permitAll()} entry
 * in {@code SecurityConfig}, every Anota.AI delivery is rejected with 401 and the capture
 * records nothing.
 * <p>
 * Boots the whole context so the real filter chain runs, and posts <b>unauthenticated</b>,
 * exactly as Anota.AI does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SecurityConfig — webhook da Anota.AI")
class AnotaAIWebhookSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("deve permitir POST não autenticado em /api/webhooks/anotaai/{merchantId} (nunca 401/403)")
    void shouldPermitUnauthenticatedAnotaAiCallbacks() throws Exception {
        mockMvc.perform(post("/api/webhooks/anotaai/3f2504e0-4f89-11d3-9a0c-0305e82c3301")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Secret", "qualquer")
                        .content("{\"success\":true}"))
                .andExpect(status().isOk());
    }
}
