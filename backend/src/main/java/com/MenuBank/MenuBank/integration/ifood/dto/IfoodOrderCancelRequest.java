package com.MenuBank.MenuBank.integration.ifood.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Merchant-initiated cancellation of an iFood order. The reason must come from
 * {@code GET /orders/{id}/cancellationReasons}: {@code cancellationCode} is the
 * {@code cancelCodeId} the merchant picked and {@code reason} is its description,
 * forwarded to iFood as free text for the customer.
 */
public record IfoodOrderCancelRequest(
        @NotBlank(message = "é obrigatório") String cancellationCode,
        String reason) {
}
