package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response of the asynchronous {@code PATCH} operations (price/status) — carries only the
 * {@code batchId} to follow up with {@code GET /batch/{batchId}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodCatalogBatchAcceptedResponse(String batchId) {
}
