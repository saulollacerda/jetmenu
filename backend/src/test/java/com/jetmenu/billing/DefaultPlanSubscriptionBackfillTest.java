package com.jetmenu.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Existing dev accounts already carry a plan-less PENDING subscription row, which the
 * frontend gate blocks behind the "choose a plan" overlay. This backfill upgrades them
 * to the configured default plan, and must be an exact no-op wherever the property is
 * unset — a paying customer silently moved to a free ACTIVE plan is the worst outcome.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultPlanSubscriptionBackfill")
class DefaultPlanSubscriptionBackfillTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private DefaultPlanResolver defaultPlanResolver;

    private DefaultPlanSubscriptionBackfill backfill;

    private Plan basicPlan;

    @BeforeEach
    void setUp() {
        backfill = new DefaultPlanSubscriptionBackfill(subscriptionRepository, defaultPlanResolver);
        basicPlan = Plan.builder()
                .id(UUID.randomUUID())
                .name("Básico")
                .minRevenue(BigDecimal.ZERO)
                .priceMonthly(new BigDecimal("50.00"))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Subscription subscription(SubscriptionStatus status, Plan plan) {
        LocalDateTime past = LocalDateTime.now().minusDays(10);
        return Subscription.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .plan(plan)
                .status(status)
                .createdAt(past)
                .updatedAt(past)
                .build();
    }

    private static <T> List<T> toList(Iterable<T> iter) {
        List<T> list = new ArrayList<>();
        iter.forEach(list::add);
        return list;
    }

    // -------------------------------------------------------------------------
    // Production behaviour: the property is unset, so nothing may happen at all.
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("sem plano padrão configurado (produção)")
    class WithoutDefaultPlan {

        @Test
        @DisplayName("não deve ler nem escrever assinatura alguma")
        void shouldNotTouchAnySubscription() {
            given(defaultPlanResolver.resolve()).willReturn(null);

            backfill.run();

            then(subscriptionRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("não deve alterar assinatura PENDING sem plano de cliente real")
        void shouldLeavePendingSubscriptionUntouched() {
            given(defaultPlanResolver.resolve()).willReturn(null);

            backfill.run();

            then(subscriptionRepository).should(never()).findAll();
            then(subscriptionRepository).should(never()).saveAll(any());
            then(subscriptionRepository).should(never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // Dev behaviour: the property resolves to the seeded plan.
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("com plano padrão configurado (dev)")
    class WithDefaultPlan {

        @BeforeEach
        void setUp() {
            given(defaultPlanResolver.resolve()).willReturn(basicPlan);
        }

        @Test
        @DisplayName("deve migrar assinatura PENDING sem plano para o plano padrão com status ACTIVE")
        void shouldUpgradePendingSubscriptionWithoutPlan() {
            Subscription pending = subscription(SubscriptionStatus.PENDING, null);
            UUID merchantId = pending.getMerchantId();
            LocalDateTime previousUpdatedAt = pending.getUpdatedAt();
            given(subscriptionRepository.findAll()).willReturn(List.of(pending));

            backfill.run();

            ArgumentCaptor<Iterable<Subscription>> captor = ArgumentCaptor.captor();
            then(subscriptionRepository).should().saveAll(captor.capture());

            List<Subscription> saved = toList(captor.getValue());
            assertThat(saved).hasSize(1);
            Subscription upgraded = saved.get(0);
            assertThat(upgraded.getMerchantId()).isEqualTo(merchantId);
            assertThat(upgraded.getPlan()).isSameAs(basicPlan);
            assertThat(upgraded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(upgraded.getUpdatedAt()).isAfter(previousUpdatedAt);
        }

        @Test
        @DisplayName("não deve definir fim de período, para que a assinatura de desenvolvimento nunca expire")
        void shouldNotSetPeriodEnd() {
            Subscription pending = subscription(SubscriptionStatus.PENDING, null);
            given(subscriptionRepository.findAll()).willReturn(List.of(pending));

            backfill.run();

            ArgumentCaptor<Iterable<Subscription>> captor = ArgumentCaptor.captor();
            then(subscriptionRepository).should().saveAll(captor.capture());
            assertThat(toList(captor.getValue()).get(0).getCurrentPeriodEnd()).isNull();
        }

        @Test
        @DisplayName("não deve alterar assinatura que já possui um plano real")
        void shouldNotTouchSubscriptionThatAlreadyHasPlan() {
            Plan paidPlan = Plan.builder().id(UUID.randomUUID()).name("Pro").build();
            Subscription pendingWithPlan = subscription(SubscriptionStatus.PENDING, paidPlan);
            given(subscriptionRepository.findAll()).willReturn(List.of(pendingWithPlan));

            backfill.run();

            then(subscriptionRepository).should(never()).saveAll(any());
            assertThat(pendingWithPlan.getPlan()).isSameAs(paidPlan);
            assertThat(pendingWithPlan.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        }

        @Test
        @DisplayName("não deve alterar assinaturas ACTIVE, PAST_DUE ou CANCELED, mesmo sem plano")
        void shouldNotTouchNonPendingSubscriptions() {
            Subscription active = subscription(SubscriptionStatus.ACTIVE, null);
            Subscription pastDue = subscription(SubscriptionStatus.PAST_DUE, null);
            Subscription canceled = subscription(SubscriptionStatus.CANCELED, null);
            given(subscriptionRepository.findAll()).willReturn(List.of(active, pastDue, canceled));

            backfill.run();

            then(subscriptionRepository).should(never()).saveAll(any());
            assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(pastDue.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
            assertThat(canceled.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            assertThat(active.getPlan()).isNull();
            assertThat(pastDue.getPlan()).isNull();
            assertThat(canceled.getPlan()).isNull();
        }

        @Test
        @DisplayName("deve migrar apenas as assinaturas elegíveis quando há uma mistura delas")
        void shouldUpgradeOnlyEligibleSubscriptions() {
            Subscription eligible = subscription(SubscriptionStatus.PENDING, null);
            Subscription canceled = subscription(SubscriptionStatus.CANCELED, null);
            Subscription pendingWithPlan = subscription(SubscriptionStatus.PENDING,
                    Plan.builder().id(UUID.randomUUID()).name("Pro").build());
            given(subscriptionRepository.findAll())
                    .willReturn(List.of(eligible, canceled, pendingWithPlan));

            backfill.run();

            ArgumentCaptor<Iterable<Subscription>> captor = ArgumentCaptor.captor();
            then(subscriptionRepository).should().saveAll(captor.capture());

            List<Subscription> saved = toList(captor.getValue());
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getId()).isEqualTo(eligible.getId());
        }

        @Test
        @DisplayName("não deve escrever quando não há assinatura elegível")
        void shouldNotWriteWhenNothingIsEligible() {
            given(subscriptionRepository.findAll()).willReturn(List.of());

            backfill.run();

            then(subscriptionRepository).should(never()).saveAll(any());
        }

        @Test
        @DisplayName("deve ser idempotente: a segunda execução não altera mais nada")
        void shouldBeIdempotent() {
            Subscription pending = subscription(SubscriptionStatus.PENDING, null);
            given(subscriptionRepository.findAll()).willReturn(List.of(pending));

            backfill.run();
            // The row was upgraded in place, which is what a real re-read would return.
            backfill.run();

            then(subscriptionRepository).should(org.mockito.Mockito.times(1)).saveAll(any());
            assertThat(pending.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(pending.getPlan()).isSameAs(basicPlan);
        }
    }

    // -------------------------------------------------------------------------
    // The configured plan row is missing (misconfiguration / encoding mismatch).
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("não deve escrever quando o plano configurado não existe no banco")
    void shouldNotWriteWhenConfiguredPlanIsMissing() {
        given(defaultPlanResolver.resolve()).willReturn(null);

        backfill.run();

        then(subscriptionRepository).shouldHaveNoInteractions();
    }
}
