package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/integrations/ifood/catalog/publish} and
 * {@code PATCH .../prices}. An omitted or empty list means "todos os produtos ativos".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodCatalogPublishRequest(List<UUID> productIds) {
}
