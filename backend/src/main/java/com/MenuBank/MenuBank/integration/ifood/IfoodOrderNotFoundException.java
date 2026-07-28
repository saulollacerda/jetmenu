package com.MenuBank.MenuBank.integration.ifood;

import java.util.UUID;

/**
 * Raised when the local order targeted by an iFood lifecycle action does not exist for
 * the authenticated merchant. Mapped by the controller to a {@code 404}.
 */
public class IfoodOrderNotFoundException extends RuntimeException {

    public IfoodOrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}
