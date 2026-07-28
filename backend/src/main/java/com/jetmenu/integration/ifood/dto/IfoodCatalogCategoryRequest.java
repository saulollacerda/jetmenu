package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Body of {@code POST /merchants/{merchantId}/categories}. JetMenu only creates plain
 * {@code DEFAULT} categories — pizza templates are out of scope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IfoodCatalogCategoryRequest(
        String name,
        String status,
        String template,
        Integer index) {
}
