package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response of {@code GET /merchants/{merchantId}/batch/{batchId}} — consolidated result of
 * an asynchronous price/status batch. Returned to the frontend as-is (no entity exposed).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodCatalogBatchResponse(
        String batchId,
        String status,
        Integer successCount,
        Integer failureCount,
        List<Result> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String resourceId, String result) {}
}
