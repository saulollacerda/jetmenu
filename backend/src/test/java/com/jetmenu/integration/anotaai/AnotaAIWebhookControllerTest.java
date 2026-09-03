package com.jetmenu.integration.anotaai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The endpoint runs in OBSERVE mode: its only job is to record what Anota.AI actually
 * sends, because they do not document the webhook contract. It must therefore never
 * reject a delivery — a rejected request is a lost sample.
 */
@WebMvcTest(AnotaAIWebhookController.class)
@WithMockUser
class AnotaAIWebhookControllerTest {

    private static final String MERCHANT_ID = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
    private static final String URL = "/api/webhooks/anotaai/" + MERCHANT_ID;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnotaAIWebhookObserver observer;

    @Test
    @DisplayName("responde 200 e entrega a captura ao observer")
    void shouldCaptureDelivery() throws Exception {
        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Secret", "segredo-do-lojista")
                        .content("{\"success\":true}"))
                .andExpect(status().isOk());

        verify(observer).capture(eq(MERCHANT_ID), eq("POST"), anyString(), any(), eq("{\"success\":true}"));
    }

    @Test
    @DisplayName("repassa todos os headers recebidos, inclusive os desconhecidos")
    void shouldForwardEveryHeader() throws Exception {
        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Secret", "segredo-do-lojista")
                        .header("X-Algum-Header-Novo", "valor-inesperado")
                        .content("{}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(observer).capture(anyString(), anyString(), anyString(), headers.capture(), anyString());

        assertThat(headers.getValue())
                .containsEntry("x-webhook-secret", "segredo-do-lojista")
                .containsEntry("x-algum-header-novo", "valor-inesperado");
    }

    @Test
    @DisplayName("aceita entrega sem corpo")
    void shouldAcceptEmptyBody() throws Exception {
        mockMvc.perform(post(URL).with(csrf()))
                .andExpect(status().isOk());

        verify(observer).capture(anyString(), anyString(), anyString(), any(), eq(""));
    }

    @Test
    @DisplayName("aceita merchantId que não é UUID — em observação nada pode ser recusado")
    void shouldAcceptNonUuidMerchantId() throws Exception {
        mockMvc.perform(post("/api/webhooks/anotaai/nao-e-uuid").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(observer).capture(eq("nao-e-uuid"), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("responde 200 mesmo se a gravação da captura falhar")
    void shouldStillReturnOkWhenObserverFails() throws Exception {
        willThrow(new RuntimeException("banco fora do ar"))
                .given(observer).capture(anyString(), anyString(), anyString(), any(), anyString());

        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
