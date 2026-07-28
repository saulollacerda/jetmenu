package com.MenuBank.MenuBank.integration.ifood.dto;

import com.MenuBank.MenuBank.order.OrderStatus;

import java.util.UUID;

/**
 * Result of an iFood order lifecycle action (confirm, readyToPickup, dispatch):
 * the local order identifiers plus the local status after the action.
 */
public record IfoodOrderActionResponse(
        UUID orderId,
        String externalOrderId,
        OrderStatus status) {
}
