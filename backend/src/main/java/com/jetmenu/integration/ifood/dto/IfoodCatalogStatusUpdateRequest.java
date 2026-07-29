package com.jetmenu.integration.ifood.dto;

import java.util.List;

/** Body of {@code PATCH /merchants/{merchantId}/items/status} — batch pause/reactivate. */
public record IfoodCatalogStatusUpdateRequest(List<StatusUpdate> items) {

    public record StatusUpdate(String id, String status) {}
}
