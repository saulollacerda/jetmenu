package com.jetmenu.billing;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService")
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private RevenueReportRepository revenueReportRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    private SubscriptionService subscriptionService;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        // No default plan configured — this is the prod-like setup, where sign-up
        // must always produce a plan-less PENDING subscription.
        subscriptionService = serviceWithDefaultPlanName(null);
    }

    private SubscriptionService serviceWithDefaultPlanName(String defaultPlanName) {
        // The real resolver is used on purpose: it reads the mocked PlanRepository, so the
        // "must not look the plan up" assertions below stay just as strict as before.
        return new SubscriptionService(
                subscriptionRepository,
                planRepository,
                revenueReportRepository,
                invoiceRepository,
                new DefaultPlanResolver(planRepository, defaultPlanName));
    }

    // -------------------------------------------------------------------------
    // createPendingSubscription()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("createPendingSubscription()")
    class CreatePendingSubscription {

        @Test
        @DisplayName("deve criar subscription com status PENDING, sem plano e sem trial")
        void shouldCreatePendingSubscription() {
            given(subscriptionRepository.save(any(Subscription.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            subscriptionService.createPendingSubscription(merchantId);

            then(subscriptionRepository).should().save(argThat(sub ->
                    merchantId.equals(sub.getMerchantId())
                            && SubscriptionStatus.PENDING.equals(sub.getStatus())
                            && sub.getTrialEndsAt() == null
                            && sub.getPlan() == null
            ));
        }

        @Test
        @DisplayName("deve preencher createdAt e updatedAt no momento da criação")
        void shouldSetTimestamps() {
            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            given(subscriptionRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            subscriptionService.createPendingSubscription(merchantId);

            Subscription saved = captor.getValue();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("não deve consultar plano padrão quando a propriedade não está configurada (comportamento de produção)")
        void shouldNotLookUpDefaultPlanWhenPropertyAbsent() {
            given(subscriptionRepository.save(any(Subscription.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            subscriptionService.createPendingSubscription(merchantId);

            then(planRepository).should(never()).findByName(anyString());
        }

        @Test
        @DisplayName("deve manter plano nulo e status PENDING quando a propriedade está em branco")
        void shouldKeepPendingWhenPropertyIsBlank() {
            subscriptionService = serviceWithDefaultPlanName("   ");
            given(subscriptionRepository.save(any(Subscription.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            subscriptionService.createPendingSubscription(merchantId);

            then(planRepository).should(never()).findByName(anyString());
            then(subscriptionRepository).should().save(argThat(sub ->
                    sub.getPlan() == null
                            && SubscriptionStatus.PENDING.equals(sub.getStatus())
            ));
        }
    }

    // -------------------------------------------------------------------------
    // createPendingSubscription() with a configured default plan (dev profile)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("createPendingSubscription() com plano padrão configurado")
    class CreateSubscriptionWithDefaultPlan {

        private static final String BASIC_PLAN_NAME = "Básico";

        private Plan basicPlan;

        @BeforeEach
        void setUp() {
            basicPlan = Plan.builder()
                    .id(UUID.randomUUID())
                    .name(BASIC_PLAN_NAME)
                    .minRevenue(BigDecimal.ZERO)
                    .priceMonthly(new BigDecimal("50.00"))
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            subscriptionService = serviceWithDefaultPlanName(BASIC_PLAN_NAME);
        }

        @Test
        @DisplayName("deve atribuir o plano Básico com status ACTIVE para uso imediato")
        void shouldAssignDefaultPlanAsActive() {
            given(planRepository.findByName(BASIC_PLAN_NAME)).willReturn(Optional.of(basicPlan));
            given(subscriptionRepository.save(any(Subscription.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            subscriptionService.createPendingSubscription(merchantId);

            then(subscriptionRepository).should().save(argThat(sub ->
                    merchantId.equals(sub.getMerchantId())
                            && basicPlan.equals(sub.getPlan())
                            && SubscriptionStatus.ACTIVE.equals(sub.getStatus())
                            && sub.getTrialEndsAt() == null
            ));
        }

        @Test
        @DisplayName("não deve definir fim de período, para que a assinatura de desenvolvimento nunca expire")
        void shouldNotSetPeriodEnd() {
            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            given(planRepository.findByName(BASIC_PLAN_NAME)).willReturn(Optional.of(basicPlan));
            given(subscriptionRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            subscriptionService.createPendingSubscription(merchantId);

            assertThat(captor.getValue().getCurrentPeriodEnd()).isNull();
        }

        @Test
        @DisplayName("deve degradar para plano nulo e PENDING quando o plano configurado não existe")
        void shouldFallBackToPendingWhenConfiguredPlanIsMissing() {
            given(planRepository.findByName(BASIC_PLAN_NAME)).willReturn(Optional.empty());
            given(subscriptionRepository.save(any(Subscription.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            subscriptionService.createPendingSubscription(merchantId);

            then(subscriptionRepository).should().save(argThat(sub ->
                    sub.getPlan() == null
                            && SubscriptionStatus.PENDING.equals(sub.getStatus())
            ));
        }
    }

    // -------------------------------------------------------------------------
    // getMySubscription()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getMySubscription()")
    class GetMySubscription {

        @Test
        @DisplayName("deve retornar SubscriptionResponse quando merchant tem subscription")
        void shouldReturnResponseWhenExists() {
            Subscription subscription = Subscription.builder()
                    .id(UUID.randomUUID())
                    .merchantId(merchantId)
                    .status(SubscriptionStatus.TRIAL)
                    .trialEndsAt(LocalDateTime.now().plusDays(5))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            given(subscriptionRepository.findByMerchantId(merchantId))
                    .willReturn(Optional.of(subscription));

            SubscriptionResponse result = subscriptionService.getMySubscription(merchantId);

            assertThat(result).isNotNull();
            assertThat(result.getMerchantId()).isEqualTo(merchantId);
            assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
        }

        @Test
        @DisplayName("deve lançar SubscriptionNotFoundException quando não encontrada")
        void shouldThrowWhenNotFound() {
            given(subscriptionRepository.findByMerchantId(merchantId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.getMySubscription(merchantId))
                    .isInstanceOf(SubscriptionNotFoundException.class);
        }
    }

    // -------------------------------------------------------------------------
    // submitRevenueReport()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("submitRevenueReport()")
    class SubmitRevenueReport {

        private Plan basicPlan;
        private Plan proPlan;

        @BeforeEach
        void setUpPlans() {
            basicPlan = Plan.builder()
                    .id(UUID.randomUUID())
                    .name("Básico")
                    .minRevenue(new BigDecimal("0.00"))
                    .maxRevenue(new BigDecimal("10000.00"))
                    .priceMonthly(new BigDecimal("99.00"))
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            proPlan = Plan.builder()
                    .id(UUID.randomUUID())
                    .name("Pro")
                    .minRevenue(new BigDecimal("10000.00"))
                    .maxRevenue(null)
                    .priceMonthly(new BigDecimal("199.00"))
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("deve salvar relatório e sugerir plano correto pela faixa de faturamento")
        void shouldSaveReportAndSuggestMatchingPlan() {
            RevenueReportRequest request = RevenueReportRequest.builder()
                    .reportedRevenue(new BigDecimal("5000.00"))
                    .referenceMonth(LocalDate.of(2026, 5, 1))
                    .build();

            given(revenueReportRepository.findByMerchantIdAndReferenceMonth(merchantId, request.getReferenceMonth()))
                    .willReturn(Optional.empty());
            given(planRepository.findByActiveTrueOrderByMinRevenueAsc())
                    .willReturn(List.of(basicPlan, proPlan));
            given(revenueReportRepository.save(any(RevenueReport.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            RevenueReportResponse result = subscriptionService.submitRevenueReport(merchantId, request);

            assertThat(result.getSuggestedPlanId()).isEqualTo(basicPlan.getId());
            assertThat(result.getSuggestedPlanName()).isEqualTo("Básico");
        }

        @Test
        @DisplayName("deve sugerir plano sem limite superior quando faturamento excede todos os limites")
        void shouldSuggestUnboundedPlanForHighRevenue() {
            RevenueReportRequest request = RevenueReportRequest.builder()
                    .reportedRevenue(new BigDecimal("50000.00"))
                    .referenceMonth(LocalDate.of(2026, 5, 1))
                    .build();

            given(revenueReportRepository.findByMerchantIdAndReferenceMonth(merchantId, request.getReferenceMonth()))
                    .willReturn(Optional.empty());
            given(planRepository.findByActiveTrueOrderByMinRevenueAsc())
                    .willReturn(List.of(basicPlan, proPlan));
            given(revenueReportRepository.save(any(RevenueReport.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            RevenueReportResponse result = subscriptionService.submitRevenueReport(merchantId, request);

            assertThat(result.getSuggestedPlanId()).isEqualTo(proPlan.getId());
            assertThat(result.getSuggestedPlanName()).isEqualTo("Pro");
        }

        @Test
        @DisplayName("deve lançar DuplicateRevenueReportException quando já existe relatório no mês")
        void shouldThrowWhenReportAlreadyExistsForMonth() {
            RevenueReportRequest request = RevenueReportRequest.builder()
                    .reportedRevenue(new BigDecimal("5000.00"))
                    .referenceMonth(LocalDate.of(2026, 5, 1))
                    .build();

            given(revenueReportRepository.findByMerchantIdAndReferenceMonth(merchantId, request.getReferenceMonth()))
                    .willReturn(Optional.of(RevenueReport.builder().build()));

            assertThatThrownBy(() -> subscriptionService.submitRevenueReport(merchantId, request))
                    .isInstanceOf(DuplicateRevenueReportException.class);

            then(revenueReportRepository).should(never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // getMyInvoices()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getMyInvoices()")
    class GetMyInvoices {

        @Test
        @DisplayName("deve retornar lista de invoices do merchant")
        void shouldReturnInvoicesForMerchant() {
            UUID subscriptionId = UUID.randomUUID();
            Subscription subscription = Subscription.builder()
                    .id(subscriptionId)
                    .merchantId(merchantId)
                    .status(SubscriptionStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            Invoice invoice = Invoice.builder()
                    .id(UUID.randomUUID())
                    .subscription(subscription)
                    .amount(new BigDecimal("99.00"))
                    .status(InvoiceStatus.PAID)
                    .dueAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();

            given(subscriptionRepository.findByMerchantId(merchantId))
                    .willReturn(Optional.of(subscription));
            given(invoiceRepository.findBySubscriptionId(subscriptionId))
                    .willReturn(List.of(invoice));

            List<InvoiceResponse> result = subscriptionService.getMyInvoices(merchantId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("99.00"));
            assertThat(result.get(0).getStatus()).isEqualTo(InvoiceStatus.PAID);
        }

        @Test
        @DisplayName("deve lançar SubscriptionNotFoundException quando merchant não tem subscription")
        void shouldThrowWhenNoSubscription() {
            given(subscriptionRepository.findByMerchantId(merchantId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.getMyInvoices(merchantId))
                    .isInstanceOf(SubscriptionNotFoundException.class);
        }
    }
}
