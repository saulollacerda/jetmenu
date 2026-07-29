package com.jetmenu.integration.anotaai;

import com.jetmenu.integration.RawJsonResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AnotaAIClient {

    private final RestClient ordersClient;
    private final RestClient menuClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnotaAIClient(
            RestClient.Builder builder,
            @Value("${anotaai.orders-base-url:https://api-parceiros.anota.ai}") String ordersBaseUrl,
            @Value("${anotaai.menu-base-url:https://api-menu.anota.ai}") String menuBaseUrl) {
        this.ordersClient = builder.baseUrl(ordersBaseUrl).build();
        this.menuClient = builder.baseUrl(menuBaseUrl).build();
    }

    public AnotaAIOrderListResponse getOrderList(String apiKey) {
        return ordersClient.get()
                .uri("/partnerauth/ping/list")
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(AnotaAIOrderListResponse.class);
    }

    /**
     * Fetches the order detail keeping the raw JSON body alongside the parsed DTO —
     * the raw payload is stored for financial auditing and preserves fields the DTO
     * ignores (e.g. {@code discounts}).
     */
    public RawJsonResponse<AnotaAIOrderDetailResponse> getOrderDetail(String apiKey, String orderId) {
        String body = ordersClient.get()
                .uri("/partnerauth/ping/get/{orderId}", orderId)
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(String.class);
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return new RawJsonResponse<>(
                    objectMapper.readValue(body, AnotaAIOrderDetailResponse.class), body);
        } catch (JsonProcessingException e) {
            throw new AnotaAIIntegrationException(
                    "Resposta inválida do Anota.AI para o pedido " + orderId);
        }
    }

    public AnotaAICatalogResponse getCatalog(String apiKey) {
        return menuClient.get()
                .uri("/partnerauth/v2/nm-category/rest/simple-item/export/v2")
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(AnotaAICatalogResponse.class);
    }
}
