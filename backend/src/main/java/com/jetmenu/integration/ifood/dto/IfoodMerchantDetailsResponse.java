package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of the iFood {@code GET /merchants/{merchantId}} payload exposed by JetMenu.
 * The iFood response carries many more fields (operations, address); unknown properties
 * are ignored so the passthrough stays resilient to schema additions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodMerchantDetailsResponse(
        String id,
        String name,
        String corporateName) {
}
