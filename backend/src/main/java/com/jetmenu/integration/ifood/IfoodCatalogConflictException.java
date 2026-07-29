package com.jetmenu.integration.ifood;

/**
 * Raised when iFood returns a {@code 409 CONFLICT} for a catalog write — typically an
 * {@code externalCode} already used by another item. Mapped by the controller to a
 * {@code 409}.
 */
public class IfoodCatalogConflictException extends RuntimeException {

    private final String detail;

    public IfoodCatalogConflictException(String detail) {
        super("iFood reported a catalog conflict: " + detail);
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }
}
