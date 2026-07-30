package com.jetmenu.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final RevenueReportRepository revenueReportRepository;
    private final InvoiceRepository invoiceRepository;

    /**
     * Resolves the plan auto-assigned on sign-up, or null when the behaviour is disabled.
     * Only the dev profile enables it (see application-dev.properties); prod leaves it
     * unset, so new accounts keep going through the regular billing flow.
     */
    private final DefaultPlanResolver defaultPlanResolver;

    /** Length of the free trial granted on sign-up, in days. */
    private final long trialDays;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               PlanRepository planRepository,
                               RevenueReportRepository revenueReportRepository,
                               InvoiceRepository invoiceRepository,
                               DefaultPlanResolver defaultPlanResolver,
                               @Value("${app.billing.trial-days:14}") long trialDays) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.revenueReportRepository = revenueReportRepository;
        this.invoiceRepository = invoiceRepository;
        this.defaultPlanResolver = defaultPlanResolver;
        this.trialDays = trialDays;
    }

    /**
     * Creates the sign-up subscription for a merchant.
     *
     * <p>By default (prod) the subscription has no plan and status TRIAL, with
     * {@code trialEndsAt} set {@code app.billing.trial-days} days ahead (14 by default).
     * This is what makes the landing page's "teste grátis" real: the merchant uses the
     * whole product without paying, and the frontend gate only blocks once that date has
     * passed. No period end is set — during the trial, access is governed by
     * {@code trialEndsAt} alone.
     *
     * <p>When {@code app.billing.default-plan-name} is configured (dev only), the named
     * plan is assigned with status ACTIVE, no trial and no period end, so a freshly
     * registered dev account is immediately usable and never expires. Assigning the plan
     * alone would not be enough: the frontend gate keys off the status, not the plan.
     * If the configured plan is missing, this degrades to the trial above.
     */
    @Transactional
    public void createPendingSubscription(UUID merchantId) {
        LocalDateTime now = LocalDateTime.now();
        Plan defaultPlan = defaultPlanResolver.resolve();
        boolean trial = defaultPlan == null;
        Subscription subscription = Subscription.builder()
                .merchantId(merchantId)
                .plan(defaultPlan)
                .status(trial ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE)
                .trialEndsAt(trial ? now.plusDays(trialDays) : null)
                .currentPeriodStart(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        subscriptionRepository.save(subscription);
    }

    public List<PlanResponse> listActivePlans() {
        return planRepository.findByActiveTrueOrderByMinRevenueAsc()
                .stream()
                .map(this::toPlanResponse)
                .toList();
    }

    public SubscriptionResponse getMySubscription(UUID merchantId) {
        Subscription subscription = subscriptionRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(merchantId));
        return toSubscriptionResponse(subscription);
    }

    @Transactional
    public RevenueReportResponse submitRevenueReport(UUID merchantId, RevenueReportRequest request) {
        if (revenueReportRepository.findByMerchantIdAndReferenceMonth(merchantId, request.getReferenceMonth()).isPresent()) {
            throw new DuplicateRevenueReportException(request.getReferenceMonth());
        }

        Plan suggestedPlan = findPlanForRevenue(request.getReportedRevenue());

        RevenueReport report = RevenueReport.builder()
                .merchantId(merchantId)
                .reportedRevenue(request.getReportedRevenue())
                .referenceMonth(request.getReferenceMonth())
                .suggestedPlan(suggestedPlan)
                .createdAt(LocalDateTime.now())
                .build();

        RevenueReport saved = revenueReportRepository.save(report);
        return toRevenueReportResponse(saved);
    }

    public List<InvoiceResponse> getMyInvoices(UUID merchantId) {
        Subscription subscription = subscriptionRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new SubscriptionNotFoundException(merchantId));
        return invoiceRepository.findBySubscriptionId(subscription.getId())
                .stream()
                .map(this::toInvoiceResponse)
                .toList();
    }

    private Plan findPlanForRevenue(BigDecimal revenue) {
        return planRepository.findByActiveTrueOrderByMinRevenueAsc()
                .stream()
                .filter(p -> p.getMinRevenue().compareTo(revenue) <= 0
                        && (p.getMaxRevenue() == null || p.getMaxRevenue().compareTo(revenue) > 0))
                .findFirst()
                .orElse(null);
    }

    private PlanResponse toPlanResponse(Plan plan) {
        return PlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .minRevenue(plan.getMinRevenue())
                .maxRevenue(plan.getMaxRevenue())
                .priceMonthly(plan.getPriceMonthly())
                .features(plan.getFeatures())
                .active(plan.isActive())
                .createdAt(plan.getCreatedAt())
                .build();
    }

    private SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .merchantId(subscription.getMerchantId())
                .planId(subscription.getPlan() != null ? subscription.getPlan().getId() : null)
                .planName(subscription.getPlan() != null ? subscription.getPlan().getName() : null)
                .status(subscription.getStatus())
                .trialEndsAt(subscription.getTrialEndsAt())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }

    private RevenueReportResponse toRevenueReportResponse(RevenueReport report) {
        return RevenueReportResponse.builder()
                .id(report.getId())
                .merchantId(report.getMerchantId())
                .reportedRevenue(report.getReportedRevenue())
                .referenceMonth(report.getReferenceMonth())
                .suggestedPlanId(report.getSuggestedPlan() != null ? report.getSuggestedPlan().getId() : null)
                .suggestedPlanName(report.getSuggestedPlan() != null ? report.getSuggestedPlan().getName() : null)
                .createdAt(report.getCreatedAt())
                .build();
    }

    private InvoiceResponse toInvoiceResponse(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .subscriptionId(invoice.getSubscription().getId())
                .amount(invoice.getAmount())
                .status(invoice.getStatus())
                .paidAt(invoice.getPaidAt())
                .dueAt(invoice.getDueAt())
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}
