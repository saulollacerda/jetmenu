package com.jetmenu.integration.stripe;

import com.jetmenu.billing.BillingProviderUnavailableException;
import com.stripe.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real SDK signature verification with genuinely signed payloads.
 * Verification is pure crypto, so this test needs no Stripe account and no network.
 */
@DisplayName("StripeEventVerifier")
class StripeEventVerifierTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_for_unit_tests";

    private StripeProperties properties;
    private StripeEventVerifier verifier;
    private String payload;

    @BeforeEach
    void setUp() {
        properties = new StripeProperties();
        properties.setApiKey("sk_test_dummy");
        properties.setWebhookSecret(WEBHOOK_SECRET);
        verifier = new StripeEventVerifier(properties, new StripeClientFactory(properties));

        payload = StripeTestEvents.checkoutSessionCompleted(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("deve aceitar um payload com assinatura válida e devolver o Event")
    void shouldAcceptValidSignature() {
        String header = StripeTestEvents.signatureHeader(payload, WEBHOOK_SECRET);

        Event event = verifier.verify(payload, header);

        assertThat(event.getType()).isEqualTo("checkout.session.completed");
    }

    @Test
    @DisplayName("deve rejeitar assinatura gerada com outro segredo")
    void shouldRejectSignatureFromAnotherSecret() {
        String header = StripeTestEvents.signatureHeader(payload, "whsec_wrong_secret");

        assertThatThrownBy(() -> verifier.verify(payload, header))
                .isInstanceOf(StripeWebhookSignatureException.class);
    }

    @Test
    @DisplayName("deve rejeitar payload adulterado após a assinatura")
    void shouldRejectTamperedPayload() {
        String header = StripeTestEvents.signatureHeader(payload, WEBHOOK_SECRET);
        String tampered = payload.replace("\"amount_total\": 5000", "\"amount_total\": 1");

        assertThatThrownBy(() -> verifier.verify(tampered, header))
                .isInstanceOf(StripeWebhookSignatureException.class);
    }

    @Test
    @DisplayName("deve rejeitar requisição sem cabeçalho Stripe-Signature — callbacks não assinados nunca passam")
    void shouldRejectMissingSignatureHeader() {
        assertThatThrownBy(() -> verifier.verify(payload, null))
                .isInstanceOf(StripeWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify(payload, "  "))
                .isInstanceOf(StripeWebhookSignatureException.class);
    }

    @Test
    @DisplayName("deve rejeitar cabeçalho malformado")
    void shouldRejectMalformedSignatureHeader() {
        assertThatThrownBy(() -> verifier.verify(payload, "not-a-signature"))
                .isInstanceOf(StripeWebhookSignatureException.class);
    }

    @Test
    @DisplayName("deve falhar como indisponível (503) quando STRIPE_WEBHOOK_SECRET não está configurado")
    void shouldFailWhenWebhookSecretIsNotConfigured() {
        properties.setWebhookSecret("");
        String header = StripeTestEvents.signatureHeader(payload, WEBHOOK_SECRET);

        assertThatThrownBy(() -> verifier.verify(payload, header))
                .isInstanceOf(BillingProviderUnavailableException.class);
    }
}
