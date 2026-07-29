package com.jetmenu.integration.ifood;

/**
 * Raised when an iFood lifecycle action is not applicable to the local order — the order
 * did not come from iFood, has no iFood identifier, already reached a terminal status
 * ({@code CANCELLED}/{@code TEST}), or its {@code orderType} contradicts the action.
 * Mapped by the controller to a {@code 409} with a pt-BR message chosen from {@link Reason}.
 */
public class IfoodOrderActionNotAllowedException extends RuntimeException {

    public enum Reason {
        NOT_IFOOD_ORDER,
        MISSING_EXTERNAL_ID,
        TERMINAL_STATUS,
        /** {@code readyToPickup} on an order known not to be {@code TAKEOUT}. */
        NOT_A_TAKEOUT_ORDER,
        /** {@code dispatch} on an order known not to be {@code DELIVERY}. */
        NOT_A_DELIVERY_ORDER
    }

    private final Reason reason;

    public IfoodOrderActionNotAllowedException(Reason reason) {
        super("iFood order action not allowed: " + reason);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
