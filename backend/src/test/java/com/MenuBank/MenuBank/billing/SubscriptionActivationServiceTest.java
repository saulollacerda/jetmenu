package com.MenuBank.MenuBank.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Provider-agnostic subscription activation. These cases were inherited from the removed
 * payment provider's billing service: the provider changed, the domain rules did not.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionActivationService")
class SubscriptionActivationServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private SubscriptionActivationService service;

    private UUID merchantId;
    private UUID planId;
    private Plan plan;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        planId = UUID.randomUUID();

        plan = Plan.builder()
                .id(planId)
                .name("Básico")
                .minRevenue(BigDecimal.ZERO)
                .priceMonthly(new BigDecimal("50.00"))
                .features(Map.of("allFeatures", true))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .merchantId(merchantId)
                .status(SubscriptionStatus.TRIAL)
                .createdAt(LocalDateTime.now().minusDays(3))
                .updatedAt(LocalDateTime.now().minusDays(3))
                .build();
    }

    @Test
    @DisplayName("deve ativar a assinatura por 1 mês e registrar fatura paga")
    void shouldActivateSubscriptionAndCreatePaidInvoice() {
        given(invoiceRepository.findByExternalPaymentReference("pay_123")).willReturn(Optional.empty());
        given(subscriptionRepository.findByMerchantId(merchantId)).willReturn(Optional.of(subscription));
        given(planRepository.findById(planId)).willReturn(Optional.of(plan));

        service.activatePaidSubscription(merchantId, planId, new BigDecimal("50.00"), "pay_123");

        then(subscriptionRepository).should().save(argThat(sub ->
                SubscriptionStatus.ACTIVE.equals(sub.getStatus())
                        && plan.equals(sub.getPlan())
                        && sub.getCurrentPeriodStart() != null
                        && sub.getCurrentPeriodEnd() != null
                        && sub.getCurrentPeriodEnd().isAfter(LocalDateTime.now().plusDays(27))
        ));

        then(invoiceRepository).should().save(argThat(invoice ->
                InvoiceStatus.PAID.equals(invoice.getStatus())
                        && new BigDecimal("50.00").compareTo(invoice.getAmount()) == 0
                        && "pay_123".equals(invoice.getExternalPaymentReference())
                        && invoice.getPaidAt() != null
        ));
    }

    @Test
    @DisplayName("deve usar o preço do plano quando o valor pago não é informado")
    void shouldFallBackToPlanPriceWhenAmountIsNull() {
        given(invoiceRepository.findByExternalPaymentReference("pay_123")).willReturn(Optional.empty());
        given(subscriptionRepository.findByMerchantId(merchantId)).willReturn(Optional.of(subscription));
        given(planRepository.findById(planId)).willReturn(Optional.of(plan));

        service.activatePaidSubscription(merchantId, planId, null, "pay_123");

        then(invoiceRepository).should().save(argThat(invoice ->
                new BigDecimal("50.00").compareTo(invoice.getAmount()) == 0));
    }

    @Test
    @DisplayName("deve ignorar pagamento já processado (mesma referência externa)")
    void shouldBeIdempotentForAlreadyProcessedPayment() {
        Invoice alreadyPaid = Invoice.builder().status(InvoiceStatus.PAID).build();
        alreadyPaid.setExternalPaymentReference("pay_123");
        given(invoiceRepository.findByExternalPaymentReference("pay_123")).willReturn(Optional.of(alreadyPaid));

        service.activatePaidSubscription(merchantId, planId, new BigDecimal("50.00"), "pay_123");

        then(subscriptionRepository).should(never()).save(any());
        then(invoiceRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("deve lançar SubscriptionNotFoundException quando o merchant não tem assinatura")
    void shouldThrowWhenSubscriptionNotFound() {
        given(invoiceRepository.findByExternalPaymentReference("pay_123")).willReturn(Optional.empty());
        given(subscriptionRepository.findByMerchantId(merchantId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.activatePaidSubscription(merchantId, planId, new BigDecimal("50.00"), "pay_123"))
                .isInstanceOf(SubscriptionNotFoundException.class);

        then(invoiceRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("deve lançar PlanNotFoundException quando o plano não existe")
    void shouldThrowWhenPlanNotFound() {
        given(invoiceRepository.findByExternalPaymentReference("pay_123")).willReturn(Optional.empty());
        given(subscriptionRepository.findByMerchantId(merchantId)).willReturn(Optional.of(subscription));
        given(planRepository.findById(planId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.activatePaidSubscription(merchantId, planId, new BigDecimal("50.00"), "pay_123"))
                .isInstanceOf(PlanNotFoundException.class);

        then(subscriptionRepository).should(never()).save(any());
        then(invoiceRepository).should(never()).save(any());
    }
}
