package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** One requested availability change: a JetMenu product and its target iFood status. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodCatalogStatusChange(UUID productId, String status) {
}
