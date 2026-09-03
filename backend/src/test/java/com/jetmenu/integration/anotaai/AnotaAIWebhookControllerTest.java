package com.jetmenu.integration.anotaai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Status codes are the whole protocol with Anota.AI: {@code 200} means "delivered, stop
 * retrying", {@code 404} hides whether the merchant exists from anyone without the secret,
 * {@code 400} refuses a body that is not a real order, and {@code 5xx} asks for a redelivery.
 */
@WebMvcTest(AnotaAIWebhookController.class)
@WithMockUser
@DisplayName("AnotaAIWebhookController")
class AnotaAIWebhookControllerTest {

    private static final String MERCHANT_ID = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
    private static final String URL = "/api/webhooks/anotaai/" + MERCHANT_ID;
    private static final String SECRET = "segredo-do-lojista";
    private static final String BODY = "{\"_id\":\"abc\",\"items\":[{\"name\":\"x\"}]}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnotaAIWebhookService webhookService;

    @Test
    @DisplayName("entrega válida responde 200 e repassa merchantId, segredo e corpo bruto")
    void shouldReturnOkForValidDelivery() throws Exception {
        given(webhookService.handle(any(), any(), any()))
                .willReturn(AnotaAIWebhookService.Outcome.IMPORTED);

        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", SECRET)
                        .content(BODY))
                .andExpect(status().isOk());

        then(webhookService).should().handle(eq(MERCHANT_ID), eq(SECRET), eq(BODY.getBytes()));
    }

    @Test
    @DisplayName("entrega duplicada responde 200 — a Anota.AI deve parar de reenviar")
    void shouldReturnOkForDuplicate() throws Exception {
        given(webhookService.handle(any(), any(), any()))
                .willReturn(AnotaAIWebhookService.Outcome.DUPLICATE);

        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", SECRET)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("pedido de canal ignorado responde 200")
    void shouldReturnOkForIgnored() throws Exception {
        given(webhookService.handle(any(), any(), any()))
                .willReturn(AnotaAIWebhookService.Outcome.IGNORED);

        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", SECRET)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("segredo ausente ou errado responde 404 — não confirma que o merchant existe")
    void shouldReturnNotFoundForBadSecret() throws Exception {
        willThrow(new AnotaAIWebhookService.UnknownDeliveryException("entrega desconhecida"))
                .given(webhookService).handle(any(), any(), any());

        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "errado")
                        .content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sem header Authorization responde 404, nunca 401")
    void shouldReturnNotFoundWithoutAuthorizationHeader() throws Exception {
        willThrow(new AnotaAIWebhookService.UnknownDeliveryException("entrega desconhecida"))
                .given(webhookService).handle(any(), any(), any());

        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound());

        then(webhookService).should().handle(eq(MERCHANT_ID), eq(null), any());
    }

    @Test
    @DisplayName("corpo que não produz um pedido utilizável responde 400")
    void shouldReturnBadRequestForUnusablePayload() throws Exception {
        willThrow(new AnotaAIWebhookService.InvalidPayloadException("corpo sem pedido"))
                .given(webhookService).handle(any(), any(), any());

        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", SECRET)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("entrega sem corpo chega ao serviço como corpo vazio")
    void shouldForwardEmptyBody() throws Exception {
        willThrow(new AnotaAIWebhookService.InvalidPayloadException("corpo vazio"))
                .given(webhookService).handle(any(), any(), any());

        mockMvc.perform(post(URL).with(csrf())
                        .header("Authorization", SECRET))
                .andExpect(status().isBadRequest());
    }

    /**
     * Falha nossa (banco fora, etc.) tem que virar 5xx para a Anota.AI reenviar. O controller
     * consegue isso <b>não</b> tratando a exceção: engolir e responder 200 perderia o pedido
     * em silêncio, que é exatamente o modo de falha que esta integração precisa evitar.
     */
    @Test
    @DisplayName("falha nossa não vira 200 — a exceção sobe para a Anota.AI reenviar")
    void shouldNotSwallowOurFailure() {
        willThrow(new RuntimeException("banco fora do ar"))
                .given(webhookService).handle(any(), any(), any());

        assertThatThrownBy(() -> mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", SECRET)
                        .content(BODY)))
                .hasRootCauseMessage("banco fora do ar");
    }
}
