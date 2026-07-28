package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Body for creating an interruption (pause). Forwarded as-is to iFood, which owns the
 * validation of the period and returns a {@code 409} when it overlaps an existing pause.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodInterruptionRequest(
        String description,
        String start,
        String end) {
}
