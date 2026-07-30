package com.jetmenu.integration.stripe;

import com.jetmenu.billing.BillingProvider;
import com.jetmenu.billing.BillingProviderUnavailableException;
import com.jetmenu.billing.CheckoutResponse;
import com.jetmenu.billing.Plan;
import com.jetmenu.billing.PlanNotFoundException;
import com.jetmenu.billing.PlanRepository;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantNotFoundException;
import com.jetmenu.merchant.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StripeBillingProvider")
class StripeBillingProviderTest {

    private static final String FRONTEND_BASE_URL = "https://app.jetmenu.com.br";

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private StripeCheckoutGateway checkoutGateway;

    private StripeProperties properties;
    private StripeBillingProvider provider;

    private UUID merchantId;
    private UUID planId;
    private Merchant merchant;
    private Plan plan;

    @BeforeEach
    void setUp() {
        properties = new StripeProperties();
        properties.setApiKey("sk_test_dummy");

        provider = new StripeBillingProvider(
                properties,
                merchantRepository,
                planRepository,
                new StripePriceResolver(),
                checkoutGateway,
                FRONTEND_BASE_URL);

        merchantId = UUID.randomUUID();
        planId = UUID.randomUUID();

        merchant = Merchant.builder()
                .id(merchantId)
                .merchantName("Goat Açaí")
                .email("dono@goatacai.com.br")
                .build();

        plan = Plan.builder()
                .id(planId)
                .name("Básico")
                .slug("basico")
                .stripePriceId("price_basico_123")
                .minRevenue(BigDecimal.ZERO)
                .priceMonthly(new BigDecimal("70.00"))
                .build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(planRepository.findById(planId)).willReturn(Optional.of(plan));
        given(checkoutGateway.createSubscriptionCheckoutUrl(org.mockito.ArgumentMatchers.any()))
                .willReturn("https://checkout.stripe.com/c/pay/cs_test_123");
    }

    @Test
    @DisplayName("deve implementar a interface BillingProvider (o seam do domínio de billing)")
    void shouldImplementTheBillingProviderSeam() {
        assertThat(provider).isInstanceOf(BillingProvider.class);
    }

    @Nested
    @DisplayName("checkout com Stripe configurado")
    class ConfiguredCheckout {

        @Test
        @DisplayName("deve retornar a URL do Checkout Session hospedado pela Stripe")
        void shouldReturnHostedCheckoutUrl() {
            CheckoutResponse response = provider.createCheckout(merchantId, planId);

            assertThat(response.getUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_123");
        }

        @Test
        @DisplayName("deve carregar merchantId e planId no metadata e no client_reference_id")
        void shouldCarryMerchantAndPlanThroughTheSession() {
            provider.createCheckout(merchantId, planId);

            ArgumentCaptor<StripeCheckoutCommand> captor =
                    ArgumentCaptor.forClass(StripeCheckoutCommand.class);
            then(checkoutGateway).should().createSubscriptionCheckoutUrl(captor.capture());

            StripeCheckoutCommand command = captor.getValue();
            assertThat(command.metadata())
                    .containsEntry(StripeMetadata.MERCHANT_ID, merchantId.toString())
                    .containsEntry(StripeMetadata.PLAN_ID, planId.toString());
            assertThat(command.clientReferenceId()).isEqualTo(merchantId.toString());
        }

        @Test
        @DisplayName("deve usar o price id configurado para o plano")
        void shouldUseConfiguredPriceId() {
            provider.createCheckout(merchantId, planId);

            ArgumentCaptor<StripeCheckoutCommand> captor =
                    ArgumentCaptor.forClass(StripeCheckoutCommand.class);
            then(checkoutGateway).should().createSubscriptionCheckoutUrl(captor.capture());

            assertThat(captor.getValue().priceId()).isEqualTo("price_basico_123");
        }

        @Test
        @DisplayName("deve montar success/cancel URLs a partir de app.frontend-base-url")
        void shouldBuildReturnUrlsFromFrontendBaseUrl() {
            provider.createCheckout(merchantId, planId);

            ArgumentCaptor<StripeCheckoutCommand> captor =
                    ArgumentCaptor.forClass(StripeCheckoutCommand.class);
            then(checkoutGateway).should().createSubscriptionCheckoutUrl(captor.capture());

            StripeCheckoutCommand command = captor.getValue();
            assertThat(command.successUrl()).startsWith(FRONTEND_BASE_URL + "/settings");
            assertThat(command.cancelUrl()).startsWith(FRONTEND_BASE_URL + "/settings");
        }

        @Test
        @DisplayName("deve enviar o e-mail do merchant para pré-preencher o checkout")
        void shouldSendMerchantEmail() {
            provider.createCheckout(merchantId, planId);

            ArgumentCaptor<StripeCheckoutCommand> captor =
                    ArgumentCaptor.forClass(StripeCheckoutCommand.class);
            then(checkoutGateway).should().createSubscriptionCheckoutUrl(captor.capture());

            assertThat(captor.getValue().customerEmail()).isEqualTo("dono@goatacai.com.br");
        }
    }

    @Nested
    @DisplayName("erros")
    class Errors {

        @Test
        @DisplayName("deve lançar PlanNotFoundException (404) quando o plano não existe")
        void shouldThrowWhenPlanDoesNotExist() {
            given(planRepository.findById(planId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> provider.createCheckout(merchantId, planId))
                    .isInstanceOf(PlanNotFoundException.class);

            then(checkoutGateway).should(never()).createSubscriptionCheckoutUrl(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("deve lançar MerchantNotFoundException (404) quando o merchant não existe")
        void shouldThrowWhenMerchantDoesNotExist() {
            given(merchantRepository.findById(merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> provider.createCheckout(merchantId, planId))
                    .isInstanceOf(MerchantNotFoundException.class);

            then(checkoutGateway).should(never()).createSubscriptionCheckoutUrl(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("deve lançar BillingProviderUnavailableException (503) quando STRIPE_API_KEY está vazia")
        void shouldThrowWhenStripeIsNotConfigured() {
            properties.setApiKey("");

            assertThatThrownBy(() -> provider.createCheckout(merchantId, planId))
                    .isInstanceOf(BillingProviderUnavailableException.class)
                    .hasMessageContaining("indisponível");

            then(checkoutGateway).should(never()).createSubscriptionCheckoutUrl(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("deve lançar BillingProviderUnavailableException (503), nunca sucesso silencioso, "
                + "quando o plano não foi sincronizado com o catálogo da Stripe")
        void shouldThrowWhenPlanHasNoConfiguredPrice() {
            plan.setStripePriceId(null);

            assertThatThrownBy(() -> provider.createCheckout(merchantId, planId))
                    .isInstanceOf(BillingProviderUnavailableException.class);

            then(checkoutGateway).should(never()).createSubscriptionCheckoutUrl(org.mockito.ArgumentMatchers.any());
        }
    }
}
