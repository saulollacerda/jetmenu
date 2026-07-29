package com.jetmenu.integration.ifood;

/**
 * Raised when iFood returns a generic {@code 400} for the merchant module. Carries the
 * detail extracted from the iFood error body so the controller can surface it.
 */
public class IfoodBadRequestException extends RuntimeException {

    private final String detail;

    public IfoodBadRequestException(String detail) {
        super("iFood rejected the request: " + detail);
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }
}
