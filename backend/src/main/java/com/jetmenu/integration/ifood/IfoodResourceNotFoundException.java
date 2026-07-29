package com.jetmenu.integration.ifood;

/**
 * Raised when iFood returns a {@code 404} for the merchant module. Mapped by the
 * controller to a {@code 404}.
 */
public class IfoodResourceNotFoundException extends RuntimeException {

    public IfoodResourceNotFoundException() {
        super("Resource not found on iFood");
    }
}
