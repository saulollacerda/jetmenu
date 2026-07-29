package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodInterruptionRequest;
import com.jetmenu.integration.ifood.dto.IfoodInterruptionResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantDetailsResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantStatusResponse;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursRequest;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Passthrough client for the iFood Merchant API v1.0 — store status, interruptions
 * (pauses) and opening hours live entirely in iFood, so every call hits the API at
 * request time and JetMenu persists none of it.
 */
@Component
public class IfoodMerchantClient {

    private final RestClient restClient;

    public IfoodMerchantClient(
            RestClient.Builder builder,
            @Value("${ifood.merchant-base-url}") String merchantBaseUrl) {
        this.restClient = builder.baseUrl(merchantBaseUrl).build();
    }

    public IfoodMerchantDetailsResponse getDetails(String accessToken, String ifoodMerchantId) {
        return restClient.get()
                .uri("/merchants/{merchantId}", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(IfoodMerchantDetailsResponse.class);
    }

    public List<IfoodMerchantStatusResponse> getStatus(String accessToken, String ifoodMerchantId) {
        List<IfoodMerchantStatusResponse> status = restClient.get()
                .uri("/merchants/{merchantId}/status", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return status != null ? status : List.of();
    }

    public List<IfoodInterruptionResponse> getInterruptions(String accessToken, String ifoodMerchantId) {
        List<IfoodInterruptionResponse> interruptions = restClient.get()
                .uri("/merchants/{merchantId}/interruptions", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return interruptions != null ? interruptions : List.of();
    }

    public IfoodInterruptionResponse createInterruption(String accessToken, String ifoodMerchantId,
                                                        IfoodInterruptionRequest request) {
        return restClient.post()
                .uri("/merchants/{merchantId}/interruptions", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(IfoodInterruptionResponse.class);
    }

    public void deleteInterruption(String accessToken, String ifoodMerchantId, String interruptionId) {
        restClient.delete()
                .uri("/merchants/{merchantId}/interruptions/{interruptionId}", ifoodMerchantId, interruptionId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }

    public IfoodOpeningHoursResponse getOpeningHours(String accessToken, String ifoodMerchantId) {
        return restClient.get()
                .uri("/merchants/{merchantId}/opening-hours", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(IfoodOpeningHoursResponse.class);
    }

    public IfoodOpeningHoursResponse updateOpeningHours(String accessToken, String ifoodMerchantId,
                                                        IfoodOpeningHoursRequest request) {
        return restClient.put()
                .uri("/merchants/{merchantId}/opening-hours", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(IfoodOpeningHoursResponse.class);
    }
}
