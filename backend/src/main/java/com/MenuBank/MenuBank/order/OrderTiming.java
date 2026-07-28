package com.MenuBank.MenuBank.order;

/**
 * Whether the order must be prepared right away or is scheduled for later
 * ({@code orderTiming} in the marketplace payload). Null for manual orders and for orders
 * imported before this field existed.
 */
public enum OrderTiming {
    IMMEDIATE,
    SCHEDULED;

    /**
     * Lenient parser: an unknown or blank value yields {@code null} instead of failing the
     * import.
     */
    public static OrderTiming fromExternal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
