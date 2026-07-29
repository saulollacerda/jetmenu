package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Body for replacing the store opening hours. Forwarded as-is to iFood, which rejects
 * overlapping shifts with a {@code 400}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodOpeningHoursRequest(List<Shift> shifts) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shift(
            String dayOfWeek,
            String start,
            Integer duration) {
    }
}
