package com.jetmenu.integration;

/**
 * Parsed API response paired with the raw JSON body it was deserialized from.
 * The raw body preserves fields the DTO ignores, so it can be stored for
 * financial auditing of imported orders.
 */
public record RawJsonResponse<T>(T body, String rawJson) {
}
