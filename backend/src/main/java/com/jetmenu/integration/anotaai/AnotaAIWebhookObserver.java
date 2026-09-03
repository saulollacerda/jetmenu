package com.jetmenu.integration.anotaai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadService;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.OrderOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * OBSERVE mode for the Anota.AI webhook: records what they actually deliver, and imports
 * nothing.
 * <p>
 * Anota.AI does not document the webhook contract — neither the header that carries the
 * "Token Externo" configured in their panel, nor the exact body shape. This capture is the
 * only way to find out, so it is written twice: to {@code external_order_raw_payload} (3-day
 * retention, narrow access) and to the log (survives a failed insert).
 * <p>
 * <b>Logging the headers is a deliberate, temporary exception.</b> The captured secret is a
 * throwaway set up for this test and should be rotated afterwards. When the endpoint moves to
 * ENFORCE mode the header logging must go — past TLS termination the value is plain text, and
 * logs are typically readable by more people than the database.
 */
@Service
public class AnotaAIWebhookObserver {

    private static final Logger log = LoggerFactory.getLogger(AnotaAIWebhookObserver.class);

    /** {@code external_order_id} is NOT NULL, so a body with no recognizable id still needs a value. */
    static final String UNKNOWN_ORDER_ID = "desconhecido";

    /** Matches the column width of {@code external_order_raw_payload.external_order_id}. */
    private static final int MAX_ORDER_ID_LENGTH = 255;

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ExternalOrderRawPayloadService rawPayloadService;
    private final MerchantRepository merchantRepository;

    public AnotaAIWebhookObserver(ExternalOrderRawPayloadService rawPayloadService,
                                  MerchantRepository merchantRepository) {
        this.rawPayloadService = rawPayloadService;
        this.merchantRepository = merchantRepository;
    }

    /**
     * Never throws: losing the capture is bad, but answering Anota.AI with an error — which
     * would make them retry or disable the webhook — is worse.
     */
    public void capture(String merchantIdRaw,
                        String method,
                        String path,
                        Map<String, String> headers,
                        String body) {
        try {
            String envelope = buildEnvelope(method, path, headers, body);
            log.info("[Anota.AI][OBSERVE] entrega recebida: {}", envelope);

            UUID merchantId = parseMerchantId(merchantIdRaw);
            if (merchantId == null) {
                log.warn("[Anota.AI][OBSERVE] merchantId da URL não é um UUID ({}) — captura só no log",
                        merchantIdRaw);
                return;
            }
            // external_order_raw_payload.merchant_id is a real FK to merchants(id): saving an
            // unknown merchant would only produce a constraint violation.
            if (!merchantRepository.existsById(merchantId)) {
                log.warn("[Anota.AI][OBSERVE] merchant {} não existe — captura só no log", merchantId);
                return;
            }

            rawPayloadService.save(merchantId, OrderOrigin.ANOTA_AI, extractOrderId(body), envelope);
        } catch (RuntimeException e) {
            log.error("[Anota.AI][OBSERVE] falha ao registrar a entrega: {}", e.getMessage(), e);
        }
    }

    /**
     * The body goes in as an escaped string rather than nested JSON, so a delivery that is not
     * JSON at all still gets recorded — the column is {@code jsonb NOT NULL}, and embedding the
     * raw bytes would make exactly the most surprising payload the one that fails to save.
     */
    private String buildEnvelope(String method, String path, Map<String, String> headers, String body) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("capturedAt", LocalDateTime.now(BRAZIL_ZONE).toString());
        envelope.put("method", method);
        envelope.put("path", path);

        ObjectNode headerNode = envelope.putObject("headers");
        if (headers != null) {
            headers.forEach(headerNode::put);
        }

        envelope.put("body", body == null ? "" : body);
        return envelope.toString();
    }

    private UUID parseMerchantId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /**
     * Best-effort: {@code info._id} is where the order id sits in the {@code /ping/get/{id}}
     * shape the docs say the webhook mirrors. Anything unrecognized still gets stored.
     */
    private String extractOrderId(String body) {
        if (body == null || body.isBlank()) {
            return UNKNOWN_ORDER_ID;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode id = root.at("/info/_id");
            if (id.isMissingNode() || id.isNull()) {
                id = root.at("/_id");
            }
            if (id.isMissingNode() || id.isNull() || id.asText().isBlank()) {
                return UNKNOWN_ORDER_ID;
            }
            String value = id.asText();
            return value.length() > MAX_ORDER_ID_LENGTH ? value.substring(0, MAX_ORDER_ID_LENGTH) : value;
        } catch (Exception e) {
            return UNKNOWN_ORDER_ID;
        }
    }
}
