package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Body of {@code PATCH /api/integrations/ifood/catalog/status}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodCatalogStatusSyncRequest(List<IfoodCatalogStatusChange> items) {
}
