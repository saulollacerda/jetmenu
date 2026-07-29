package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One entry of the iFood {@code GET /merchants/{merchantId}/status} array. Each entry
 * describes an operation/sales-channel combination with its availability, aggregate
 * {@code state} and the list of validations that produced it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IfoodMerchantStatusResponse(
        String operation,
        String salesChannel,
        boolean available,
        String state,
        Message message,
        List<Validation> validations) {

    /**
     * iFood omits {@code validations} for some operations, which would leave the record
     * component null. The frontend types it as a non-optional array, so we normalize a
     * missing/null list to an empty one here. {@code message} stays nullable — the
     * frontend guards it.
     */
    public IfoodMerchantStatusResponse {
        validations = validations != null ? validations : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String title, String subtitle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Validation(String id, String code, String state, Message message) {
    }
}
