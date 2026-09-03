package com.jetmenu.integration.anotaai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadService;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.OrderOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Records what Anota.AI actually delivers. Anota.AI does not document the webhook
 * contract, so the capture is the only source of truth for the header that carries the
 * "Token Externo" and for the real body shape.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnotaAIWebhookObserverTest {

    private static final UUID MERCHANT_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ExternalOrderRawPayloadService rawPayloadService;

    @Mock
    private MerchantRepository merchantRepository;

    @InjectMocks
    private AnotaAIWebhookObserver observer;

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-webhook-secret", "segredo-do-lojista");
        headers.put("content-type", "application/json");
        return headers;
    }

    private JsonNode captureEnvelope() throws Exception {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(rawPayloadService).save(eq(MERCHANT_ID), eq(OrderOrigin.ANOTA_AI), anyString(), payload.capture());
        return objectMapper.readTree(payload.getValue());
    }

    @Test
    @DisplayName("grava envelope com método, path, headers e corpo")
    void shouldRecordFullEnvelope() throws Exception {
        given(merchantRepository.existsById(MERCHANT_ID)).willReturn(true);

        observer.capture(MERCHANT_ID.toString(), "POST", "/api/webhooks/anotaai/" + MERCHANT_ID,
                headers(), "{\"success\":true}");

        JsonNode envelope = captureEnvelope();
        assertThat(envelope.get("method").asText()).isEqualTo("POST");
        assertThat(envelope.get("path").asText()).contains(MERCHANT_ID.toString());
        assertThat(envelope.get("capturedAt").asText()).isNotBlank();
        assertThat(envelope.get("headers").get("x-webhook-secret").asText()).isEqualTo("segredo-do-lojista");
    }

    @Test
    @DisplayName("guarda o corpo como string escapada, para sobreviver a payload que não é JSON")
    void shouldStoreBodyAsEscapedString() throws Exception {
        given(merchantRepository.existsById(MERCHANT_ID)).willReturn(true);

        observer.capture(MERCHANT_ID.toString(), "POST", "/qualquer", headers(), "isto <nao> e json");

        JsonNode envelope = captureEnvelope();
        assertThat(envelope.get("body").isTextual()).isTrue();
        assertThat(envelope.get("body").asText()).isEqualTo("isto <nao> e json");
    }

    @Test
    @DisplayName("usa o _id do pedido como external_order_id quando o corpo permite")
    void shouldExtractExternalOrderId() {
        given(merchantRepository.existsById(MERCHANT_ID)).willReturn(true);

        observer.capture(MERCHANT_ID.toString(), "POST", "/qualquer", headers(),
                "{\"success\":true,\"info\":{\"_id\":\"6a0e094aa2335ae5e05c5eae\"}}");

        verify(rawPayloadService).save(eq(MERCHANT_ID), eq(OrderOrigin.ANOTA_AI),
                eq("6a0e094aa2335ae5e05c5eae"), anyString());
    }

    @Test
    @DisplayName("cai para um marcador quando o corpo não traz o id do pedido")
    void shouldFallBackWhenNoOrderId() {
        given(merchantRepository.existsById(MERCHANT_ID)).willReturn(true);

        observer.capture(MERCHANT_ID.toString(), "POST", "/qualquer", headers(), "sem id nenhum");

        verify(rawPayloadService).save(eq(MERCHANT_ID), eq(OrderOrigin.ANOTA_AI),
                eq(AnotaAIWebhookObserver.UNKNOWN_ORDER_ID), anyString());
    }

    @Test
    @DisplayName("não tenta gravar quando o merchantId não é UUID — a coluna tem FK")
    void shouldNotPersistWhenMerchantIdIsNotUuid() {
        observer.capture("nao-e-uuid", "POST", "/qualquer", headers(), "{}");

        verify(rawPayloadService, never()).save(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("não tenta gravar quando o merchant não existe — evitaria violação de FK")
    void shouldNotPersistWhenMerchantIsUnknown() {
        given(merchantRepository.existsById(MERCHANT_ID)).willReturn(false);

        observer.capture(MERCHANT_ID.toString(), "POST", "/qualquer", headers(), "{}");

        verify(rawPayloadService, never()).save(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("nunca propaga exceção — perder a entrega é pior que perder a captura")
    void shouldNeverThrow() {
        given(merchantRepository.existsById(MERCHANT_ID)).willReturn(true);
        willThrow(new RuntimeException("banco fora do ar"))
                .given(rawPayloadService).save(any(), any(), anyString(), anyString());

        assertThatCode(() -> observer.capture(MERCHANT_ID.toString(), "POST", "/qualquer", headers(), "{}"))
                .doesNotThrowAnyException();
    }
}
