package com.jetmenu.integration.ifood;

/**
 * Raised when a merchant outside {@code ifood.diagnostics-merchant-ids} tries to reach the
 * diagnostics screen. Mapped by the controller to a {@code 403} — the screen ships to
 * production but stays closed to everyone who is not explicitly whitelisted.
 */
public class IfoodDiagnosticsNotAllowedException extends RuntimeException {

    public IfoodDiagnosticsNotAllowedException() {
        super("iFood diagnostics is not enabled for this merchant");
    }
}
