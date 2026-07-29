package com.jetmenu.billing;

/**
 * Raised when a checkout cannot be created because no payment provider is currently
 * integrated. Mapped to HTTP 503 by {@code GlobalExceptionHandler}.
 */
public class BillingProviderUnavailableException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "O pagamento online está temporariamente indisponível enquanto integramos um novo "
                    + "provedor. Entre em contato com o suporte para ativar seu plano.";

    public BillingProviderUnavailableException() {
        super(DEFAULT_MESSAGE);
    }

    public BillingProviderUnavailableException(String message) {
        super(message);
    }
}
