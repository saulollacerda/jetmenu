package com.jetmenu.integration.ifood.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body of {@code PATCH /merchants/{merchantId}/items/price}. The price here is a flat
 * number — unlike {@code PUT /items}, which nests it under {@code price.value}.
 */
public record IfoodCatalogPriceUpdateRequest(List<PriceUpdate> prices) {

    public record PriceUpdate(String productId, BigDecimal price) {}
}
