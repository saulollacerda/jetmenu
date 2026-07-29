package com.jetmenu.order;

/**
 * How the customer receives the order, as reported by the marketplace ({@code orderType}).
 * Null for manual orders and for orders imported before this field existed.
 */
public enum OrderType {
    DELIVERY,
    TAKEOUT,
    DINE_IN;

    /**
     * Lenient parser: an unknown or blank value yields {@code null} instead of failing the
     * import — the marketplace may add new types at any time.
     */
    public static OrderType fromExternal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
