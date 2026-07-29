package com.jetmenu.integration.ifood;

/**
 * Raised when iFood rejects an opening-hours update because the informed shifts overlap
 * ({@code 400}). Mapped by the controller to a {@code 400}.
 */
public class IfoodShiftOverlapException extends RuntimeException {

    public IfoodShiftOverlapException() {
        super("iFood opening-hours shifts overlap");
    }
}
