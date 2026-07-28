package com.jetmenu.integration.ifood;

/**
 * Raised when a transient failure ({@code 5xx} or network error) survives every retry
 * attempt. Mapped by the controller to a {@code 503} — the request can be repeated later.
 */
public class IfoodUnavailableException extends RuntimeException {

    public IfoodUnavailableException(Throwable cause) {
        super("iFood is unavailable after exhausting retries", cause);
    }
}
