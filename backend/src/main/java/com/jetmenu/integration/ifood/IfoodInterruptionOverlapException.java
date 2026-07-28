package com.jetmenu.integration.ifood;

/**
 * Raised when iFood rejects a new interruption because it overlaps an existing pause
 * ({@code 409 InterruptionOverlap}). Mapped by the controller to a {@code 409}.
 */
public class IfoodInterruptionOverlapException extends RuntimeException {

    public IfoodInterruptionOverlapException() {
        super("iFood interruption overlaps an existing pause");
    }
}
