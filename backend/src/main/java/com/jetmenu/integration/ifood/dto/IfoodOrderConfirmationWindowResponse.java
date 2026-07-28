package com.jetmenu.integration.ifood.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Confirmation SLA window of an iFood order. iFood requires DELIVERY and TAKEOUT orders
 * to be confirmed within 8 minutes of creation, regardless of {@code orderTiming}.
 *
 * <p>Rule: {@code deadline = order.dateTime + 8 minutes}, both in {@code America/Sao_Paulo}
 * (the timezone the importer already normalizes {@code createdAt} to). The frontend can
 * either count down locally from {@code deadline} or refresh {@code remainingSeconds}.
 */
public record IfoodOrderConfirmationWindowResponse(
        UUID orderId,
        LocalDateTime createdAt,
        LocalDateTime deadline,
        long remainingSeconds,
        boolean expired) {
}
