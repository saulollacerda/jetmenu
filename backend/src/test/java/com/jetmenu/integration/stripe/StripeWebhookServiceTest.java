package com.jetmenu.integration.stripe;

import com.jetmenu.billing.SubscriptionActivationService;
import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Decodes a verified Stripe event back into JetMenu's own terms and hands it to
 * {@link SubscriptionActivationService}, which owns what "paid" means. No activation
 * logic lives in the Stripe package.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StripeWebhookService")
class StripeWebhookServiceTest {

    @Mock
    private SubscriptionActivationService activationService;

    @InjectMocks
    private StripeWebhookService service;

    private Event parse(String payload) {
        return ApiResource.GSON.fromJson(payload, Event.class);
    }

    @Test
    @DisplayName("deve ativar a assinatura ao receber checkout.session.completed pago")
    void shouldActivateSubscriptionOnPaidCheckoutSession() {
        UUID merchantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        service.handle(parse(StripeTestEvents.checkoutSessionCompleted(merchantId, planId)));

        then(activationService).should().activatePaidSubscription(
                eq(merchantId), eq(planId), eq(new BigDecimal("50.00")), eq("cs_test_123"));
    }

    @Test
    @DisplayName("deve usar o id da Checkout Session como chave de idempotência — "
            + "reentrega do mesmo evento vira no-op no serviço de ativação")
    void shouldUseCheckoutSessionIdAsIdempotencyKey() {
        UUID merchantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Event event = parse(StripeTestEvents.checkoutSessionCompleted(merchantId, planId));

        service.handle(event);
        service.handle(event);

        then(activationService).should(org.mockito.Mockito.times(2)).activatePaidSubscription(
                eq(merchantId), eq(planId), any(), eq("cs_test_123"));
    }

    @Test
    @DisplayName("deve converter amount_total (centavos) para reais")
    void shouldConvertAmountFromCents() {
        UUID merchantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        service.handle(parse(StripeTestEvents.checkoutSessionCompleted(
                "cs_test_999", merchantId, planId, 12345L, "paid")));

        then(activationService).should().activatePaidSubscription(
                any(), any(), eq(new BigDecimal("123.45")), any());
    }

    @Test
    @DisplayName("deve passar amount nulo quando a Stripe não informa total (cai no preço do plano)")
    void shouldPassNullAmountWhenStripeReportsNone() {
        UUID merchantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        service.handle(parse(StripeTestEvents.checkoutSessionCompleted(
                "cs_test_888", merchantId, planId, null, "paid")));

        then(activationService).should().activatePaidSubscription(
                any(), any(), isNull(), any());
    }

    @Test
    @DisplayName("deve ignorar eventos que não tratamos, sem ativar nada")
    void shouldIgnoreUnhandledEventTypes() {
        service.handle(parse(StripeTestEvents.unhandledEvent()));

        then(activationService).should(never()).activatePaidSubscription(any(), any(), any(), any());
    }

    @Test
    @DisplayName("deve ignorar sessão não paga")
    void shouldIgnoreUnpaidSession() {
        service.handle(parse(StripeTestEvents.checkoutSessionCompleted(
                "cs_test_777", UUID.randomUUID(), UUID.randomUUID(), 5000L, "unpaid")));

        then(activationService).should(never()).activatePaidSubscription(any(), any(), any(), any());
    }

    @Test
    @DisplayName("deve ignorar sessão sem metadata de merchant/plano em vez de estourar")
    void shouldIgnoreSessionWithoutMetadata() {
        service.handle(parse(StripeTestEvents.checkoutSessionCompleted(
                "cs_test_666", null, null, 5000L, "paid")));

        then(activationService).should(never()).activatePaidSubscription(any(), any(), any(), any());
    }

    @Test
    @DisplayName("deve renovar a assinatura ao receber invoice.paid de ciclo, lendo o "
            + "merchant/plano do metadata da assinatura")
    void shouldRenewSubscriptionOnCycleInvoice() {
        UUID merchantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        service.handle(parse(StripeTestEvents.invoicePaid(
                "in_test_123", merchantId, planId, 7000L, "subscription_cycle")));

        then(activationService).should().activatePaidSubscription(
                eq(merchantId), eq(planId), eq(new BigDecimal("70.00")), eq("in_test_123"));
    }

    @Test
    @DisplayName("NÃO deve renovar na invoice da cobrança inicial — o checkout.session.completed "
            + "já ativou aquele mesmo dinheiro, e o id da invoice é outra chave de idempotência")
    void shouldNotRenewOnTheFirstPaymentInvoice() {
        service.handle(parse(StripeTestEvents.invoicePaid(
                "in_test_first", UUID.randomUUID(), UUID.randomUUID(), 7000L, "subscription_create")));

        then(activationService).should(never()).activatePaidSubscription(any(), any(), any(), any());
    }

    @Test
    @DisplayName("deve usar o id da invoice como chave de idempotência — reentrega da mesma "
            + "renovação não concede um segundo período")
    void shouldUseInvoiceIdAsIdempotencyKey() {
        UUID merchantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Event event = parse(StripeTestEvents.invoicePaid(
                "in_test_repeat", merchantId, planId, 7000L, "subscription_cycle"));

        service.handle(event);
        service.handle(event);

        then(activationService).should(org.mockito.Mockito.times(2)).activatePaidSubscription(
                any(), any(), any(), eq("in_test_repeat"));
    }

    @Test
    @DisplayName("não deve estender período em invoice paga com billing_reason desconhecido")
    void shouldNotExtendOnUnknownBillingReason() {
        service.handle(parse(StripeTestEvents.invoicePaid(
                "in_test_manual", UUID.randomUUID(), UUID.randomUUID(), 7000L, "manual")));

        then(activationService).should(never()).activatePaidSubscription(any(), any(), any(), any());
    }

    @Test
    @DisplayName("deve ignorar invoice sem metadata na assinatura em vez de estourar")
    void shouldIgnoreInvoiceWithoutSubscriptionMetadata() {
        service.handle(parse(StripeTestEvents.invoicePaid(
                "in_test_nometa", null, null, 7000L, "subscription_cycle")));

        then(activationService).should(never()).activatePaidSubscription(any(), any(), any(), any());
    }
}
