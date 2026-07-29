package com.jetmenu.integration.ifood;

import com.jetmenu.integration.RawJsonResponse;
import com.jetmenu.integration.ifood.dto.IfoodEventResponse;
import com.jetmenu.integration.ifood.dto.IfoodOrderDetailResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class IfoodOrderClient {

    /** iFood's request limit for POST /events/acknowledgment. */
    private static final int ACKNOWLEDGMENT_BATCH_SIZE = 2000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IfoodOrderClient(
            RestClient.Builder builder,
            @Value("${ifood.order-base-url}") String orderBaseUrl) {
        this.restClient = builder.baseUrl(orderBaseUrl).build();
    }

    public List<IfoodEventResponse> pollEvents(String accessToken, List<String> ifoodMerchantIds) {
        String body = restClient.get()
                .uri("/events:polling")
                .header("Authorization", "Bearer " + accessToken)
                .header("x-polling-merchants", String.join(",", ifoodMerchantIds))
                .retrieve()
                .body(String.class);

        // 204 No Content (no pending events) yields a null body; the payload may be either
        // a bare array or an {"events":[...]} wrapper depending on the API variant
        if (body == null || body.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode events = root.isArray() ? root : root.path("events");
            if (!events.isArray()) return List.of();
            return objectMapper.convertValue(events,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, IfoodEventResponse.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unexpected iFood polling response payload", e);
        }
    }

    /**
     * Acknowledges the given events. iFood caps each acknowledgment request at
     * {@value #ACKNOWLEDGMENT_BATCH_SIZE} ids, so the list is split into batches.
     */
    public void acknowledgeEvents(String accessToken, List<String> eventIds) {
        for (int start = 0; start < eventIds.size(); start += ACKNOWLEDGMENT_BATCH_SIZE) {
            int end = Math.min(start + ACKNOWLEDGMENT_BATCH_SIZE, eventIds.size());
            acknowledgeBatch(accessToken, eventIds.subList(start, end));
        }
    }

    private void acknowledgeBatch(String accessToken, List<String> eventIds) {
        restClient.post()
                .uri("/events/acknowledgment")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(eventIds.stream().map(id -> Map.of("id", id)).toList())
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Fetches the order detail keeping the raw JSON body alongside the parsed DTO —
     * the raw payload is stored for financial auditing and preserves fields the DTO
     * ignores.
     */
    public RawJsonResponse<IfoodOrderDetailResponse> getOrderDetail(String accessToken, String orderId) {
        String body = restClient.get()
                .uri("/orders/{orderId}", orderId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(String.class);
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return new RawJsonResponse<>(
                    objectMapper.readValue(body, IfoodOrderDetailResponse.class), body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unexpected iFood order detail payload", e);
        }
    }
}
