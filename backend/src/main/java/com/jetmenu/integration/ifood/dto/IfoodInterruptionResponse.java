package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An interruption (pause) as returned by the iFood interruptions endpoints. The
 * {@code start} and {@code end} instants are passed through verbatim as ISO datetimes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodInterruptionResponse(
        String id,
        String description,
        String start,
        String end) {
}
