package com.jetmenu.notification;

public enum NotificationType {
    MISSING_INGREDIENT,
    MISSING_PRODUCT,
    ORDER_CANCELLED,
    /**
     * The customer (or the platform) asked to cancel an order and the merchant still has to
     * accept or reject it. Unlike {@link #ORDER_CANCELLED}, nothing changed in the order yet.
     */
    ORDER_CANCELLATION_REQUESTED
}
