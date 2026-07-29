package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodCatalogBatchAcceptedResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogBatchResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryCreatedResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogItemRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPriceUpdateRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogStatusUpdateRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Write client for the iFood Catalog API v2.0. Complements the read-only
 * {@link IfoodCatalogClient}: same base URL and Bearer auth, but the write endpoints are
 * merchant-scoped ({@code /merchants/{merchantId}/...}) instead of catalog-scoped.
 *
 * <p>Pure transport — no retry, no error translation. Both live in
 * {@code IfoodCatalogPublishService}, which owns the resilience policy.
 */
@Component
public class IfoodCatalogWriteClient {

    private final RestClient restClient;

    public IfoodCatalogWriteClient(
            RestClient.Builder builder,
            @Value("${ifood.catalog-base-url}") String catalogBaseUrl) {
        this.restClient = builder.baseUrl(catalogBaseUrl).build();
    }

    public IfoodCatalogCategoryCreatedResponse createCategory(String accessToken,
                                                              String ifoodMerchantId,
                                                              IfoodCatalogCategoryRequest request) {
        return restClient.post()
                .uri("/merchants/{merchantId}/categories", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(IfoodCatalogCategoryCreatedResponse.class);
    }

    /** Creates or fully replaces an item. Idempotent — safe to repeat on transient failures. */
    public void upsertItem(String accessToken, String ifoodMerchantId,
                           IfoodCatalogItemRequest request) {
        restClient.put()
                .uri("/merchants/{merchantId}/items", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    /** Asynchronous batch price update — returns the {@code batchId} to follow up. */
    public String updatePrices(String accessToken, String ifoodMerchantId,
                               IfoodCatalogPriceUpdateRequest request) {
        IfoodCatalogBatchAcceptedResponse accepted = restClient.patch()
                .uri("/merchants/{merchantId}/items/price", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(IfoodCatalogBatchAcceptedResponse.class);
        return accepted != null ? accepted.batchId() : null;
    }

    /** Asynchronous batch status update — returns the {@code batchId} to follow up. */
    public String updateStatus(String accessToken, String ifoodMerchantId,
                               IfoodCatalogStatusUpdateRequest request) {
        IfoodCatalogBatchAcceptedResponse accepted = restClient.patch()
                .uri("/merchants/{merchantId}/items/status", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(IfoodCatalogBatchAcceptedResponse.class);
        return accepted != null ? accepted.batchId() : null;
    }

    public IfoodCatalogBatchResponse getBatch(String accessToken, String ifoodMerchantId,
                                              String batchId) {
        return restClient.get()
                .uri("/merchants/{merchantId}/batch/{batchId}", ifoodMerchantId, batchId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(IfoodCatalogBatchResponse.class);
    }
}
