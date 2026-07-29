package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Opening hours as returned by iFood — a flat list of shifts. Each shift starts at
 * {@code start} (HH:mm:ss) on {@code dayOfWeek} and lasts {@code duration} minutes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodOpeningHoursResponse(List<Shift> shifts) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shift(
            String id,
            String dayOfWeek,
            String start,
            Integer duration) {
    }
}
