package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response of {@code POST /merchants/{merchantId}/categories} — the {@code id} is what
 * JetMenu persists on {@code Category.externalId} to keep publishing idempotent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodCatalogCategoryCreatedResponse(
        String id,
        String name,
        String status,
        String template) {
}
