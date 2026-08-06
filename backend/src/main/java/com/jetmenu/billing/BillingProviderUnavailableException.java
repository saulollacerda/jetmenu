package com.jetmenu.billing;

/**
 * Raised when a checkout cannot be created because the payment provider is not configured
 * or cannot be reached — {@code STRIPE_API_KEY} empty, the plan has no
 * {@code STRIPE_PRICE_<SLUG>}, or Stripe itself failed. Mapped to HTTP 503 by
 * {@code GlobalExceptionHandler}.
 * <p>
 * The point is to fail <b>explicitly</b>: a misconfigured environment answers 503 with a
 * pt-BR ProblemDetail rather than a 500, and never a silent success.
 */
public class BillingProviderUnavailableException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "O pagamento online está temporariamente indisponível. Tente novamente em alguns "
                    + "instantes ou entre em contato com o suporte para ativar seu plano.";

    public BillingProviderUnavailableException() {
        super(DEFAULT_MESSAGE);
    }

    public BillingProviderUnavailableException(String message) {
        super(message);
    }
}
