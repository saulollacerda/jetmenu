package com.jetmenu.integration.stripe;

import com.jetmenu.billing.BillingProviderUnavailableException;
import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StripeWebhookController.class)
@WithMockUser
@DisplayName("StripeWebhookController")
class StripeWebhookControllerTest {

    private static final String PATH = "/api/webhooks/stripe";
    private static final String SIGNATURE_HEADER = "Stripe-Signature";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StripeEventVerifier eventVerifier;

    @MockitoBean
    private StripeWebhookService webhookService;

    private UUID merchantId;
    private UUID planId;
    private String payload;
    private Event event;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        planId = UUID.randomUUID();
        payload = StripeTestEvents.checkoutSessionCompleted(merchantId, planId);
        event = ApiResource.GSON.fromJson(payload, Event.class);
    }

    @Test
    @DisplayName("deve retornar 200 e repassar o evento verificado quando a assinatura é válida")
    void shouldAcceptSignedEvent() throws Exception {
        given(eventVerifier.verify(any(), any())).willReturn(event);

        mockMvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, "t=1,v1=deadbeef")
                        .content(payload))
                .andExpect(status().isOk());

        then(webhookService).should().handle(eq(event));
    }

    @Test
    @DisplayName("deve verificar a assinatura com o corpo bruto exatamente como recebido")
    void shouldVerifyAgainstRawBody() throws Exception {
        given(eventVerifier.verify(any(), any())).willReturn(event);

        mockMvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, "t=1,v1=deadbeef")
                        .content(payload))
                .andExpect(status().isOk());

        then(eventVerifier).should().verify(eq(payload), eq("t=1,v1=deadbeef"));
    }

    @Test
    @DisplayName("deve retornar 400 e não processar nada quando a assinatura é inválida")
    void shouldRejectInvalidSignature() throws Exception {
        willThrow(new StripeWebhookSignatureException("Assinatura inválida"))
                .given(eventVerifier).verify(any(), any());

        mockMvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, "t=1,v1=forged")
                        .content(payload))
                .andExpect(status().isBadRequest());

        then(webhookService).should(never()).handle(any());
    }

    @Test
    @DisplayName("deve retornar 400 quando o cabeçalho Stripe-Signature está ausente")
    void shouldRejectMissingSignatureHeader() throws Exception {
        willThrow(new StripeWebhookSignatureException("Cabeçalho ausente"))
                .given(eventVerifier).verify(any(), any());

        mockMvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        then(webhookService).should(never()).handle(any());
    }

    @Test
    @DisplayName("deve retornar 200 para eventos que não tratamos, para a Stripe parar de reenviar")
    void shouldReturn200ForUnhandledEvents() throws Exception {
        Event unhandled = ApiResource.GSON.fromJson(StripeTestEvents.unhandledEvent(), Event.class);
        given(eventVerifier.verify(any(), any())).willReturn(unhandled);

        mockMvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, "t=1,v1=deadbeef")
                        .content(StripeTestEvents.unhandledEvent()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("reentrega do mesmo evento deve responder 200 novamente (idempotência a cargo da ativação)")
    void shouldReturn200OnDuplicateDelivery() throws Exception {
        given(eventVerifier.verify(any(), any())).willReturn(event);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(PATH)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SIGNATURE_HEADER, "t=1,v1=deadbeef")
                            .content(payload))
                    .andExpect(status().isOk());
        }

        then(webhookService).should(times(2)).handle(eq(event));
    }

    @Test
    @DisplayName("deve retornar 503 quando o webhook não está configurado, para a Stripe tentar de novo")
    void shouldReturn503WhenWebhookIsNotConfigured() throws Exception {
        willThrow(new BillingProviderUnavailableException("Stripe não configurado"))
                .given(eventVerifier).verify(any(), any());

        mockMvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIGNATURE_HEADER, "t=1,v1=deadbeef")
                        .content(payload))
                .andExpect(status().isServiceUnavailable());
    }
}
