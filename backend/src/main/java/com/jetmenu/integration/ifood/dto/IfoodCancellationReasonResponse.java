package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A cancellation reason offered by iFood for a given order, as returned by
 * {@code GET /orders/{id}/cancellationReasons}.
 *
 * <p>Homologation requires the merchant to pick from iFood's own list — JetMenu never
 * invents reasons. The merchant reads {@code description}; the cancellation call needs
 * {@code cancelCodeId}, so both travel together all the way to the UI and back.
 *
 * <p>The same record is used for the iFood payload and for the JetMenu REST response:
 * the field names are iFood's and are surfaced verbatim so nothing is lost in translation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodCancellationReasonResponse(
        String cancelCodeId,
        String description) {
}
