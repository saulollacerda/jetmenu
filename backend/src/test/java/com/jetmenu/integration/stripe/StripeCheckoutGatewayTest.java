package com.jetmenu.integration.stripe;

import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the shape of the Checkout Session we ask Stripe for. {@code toMap()} is the
 * actual wire form, so these assertions fail if a parameter stops being sent.
 * <p>
 * No HTTP happens here — building the params is pure, and the network call is a
 * one-liner on top of it.
 */
@DisplayName("StripeCheckoutGateway — parâmetros do Checkout Session")
class StripeCheckoutGatewayTest {

    private final UUID merchantId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();

    private StripeCheckoutCommand command() {
        return new StripeCheckoutCommand(
                "price_basico_123",
                merchantId.toString(),
                Map.of(StripeMetadata.MERCHANT_ID, merchantId.toString(),
                        StripeMetadata.PLAN_ID, planId.toString()),
                "dono@goatacai.com.br",
                "https://app.jetmenu.com.br/settings?checkout=success",
                "https://app.jetmenu.com.br/settings?checkout=cancelled");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> paramsMap() {
        SessionCreateParams params = StripeCheckoutGateway.buildParams(command());
        return params.toMap();
    }

    @Test
    @DisplayName("deve criar a sessão em modo subscription (assinatura recorrente)")
    void shouldUseSubscriptionMode() {
        assertThat(paramsMap()).containsEntry("mode", "subscription");
    }

    @Test
    @DisplayName("deve enviar exatamente um line item com o price id do plano e quantidade 1")
    @SuppressWarnings("unchecked")
    void shouldSendSingleLineItemWithPlanPrice() {
        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) paramsMap().get("line_items");

        assertThat(lineItems).hasSize(1);
        assertThat(lineItems.get(0)).containsEntry("price", "price_basico_123");
        assertThat(lineItems.get(0)).containsEntry("quantity", 1L);
    }

    @Test
    @DisplayName("deve carregar merchant_id e plan_id no metadata da sessão e da assinatura")
    @SuppressWarnings("unchecked")
    void shouldCarryMetadataOnSessionAndSubscription() {
        Map<String, Object> map = paramsMap();

        Map<String, String> sessionMetadata = (Map<String, String>) map.get("metadata");
        assertThat(sessionMetadata)
                .containsEntry(StripeMetadata.MERCHANT_ID, merchantId.toString())
                .containsEntry(StripeMetadata.PLAN_ID, planId.toString());

        Map<String, Object> subscriptionData = (Map<String, Object>) map.get("subscription_data");
        Map<String, String> subscriptionMetadata = (Map<String, String>) subscriptionData.get("metadata");
        assertThat(subscriptionMetadata)
                .containsEntry(StripeMetadata.MERCHANT_ID, merchantId.toString())
                .containsEntry(StripeMetadata.PLAN_ID, planId.toString());
    }

    @Test
    @DisplayName("deve enviar client_reference_id, e-mail e as URLs de retorno")
    void shouldSendReferenceEmailAndReturnUrls() {
        Map<String, Object> map = paramsMap();

        assertThat(map).containsEntry("client_reference_id", merchantId.toString());
        assertThat(map).containsEntry("customer_email", "dono@goatacai.com.br");
        assertThat(map).containsEntry("success_url",
                "https://app.jetmenu.com.br/settings?checkout=success");
        assertThat(map).containsEntry("cancel_url",
                "https://app.jetmenu.com.br/settings?checkout=cancelled");
    }

    @Test
    @DisplayName("nunca deve fixar payment_method_types — os meios de pagamento vêm do dashboard da Stripe")
    void shouldNotPinPaymentMethodTypes() {
        assertThat(paramsMap()).doesNotContainKey("payment_method_types");
    }

    @Test
    @DisplayName("deve apresentar o checkout em pt-BR")
    void shouldRenderCheckoutInBrazilianPortuguese() {
        assertThat(paramsMap()).containsEntry("locale", "pt-BR");
    }
}
