package com.jetmenu.billing;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Merchants registered before the billing feature existed have no row in the
 * subscriptions table, because subscription creation only happens on sign-up
 * ({@link com.jetmenu.merchant.MerchantService#create}). For those
 * legacy merchants {@code GET /api/subscription/me} throws
 * {@link SubscriptionNotFoundException}, which maps to HTTP 404.
 *
 * <p>This backfill assigns a PENDING subscription (no plan yet) to every
 * merchant that lacks one, so they hit the "choose a plan" gate instead of a
 * 404. It is idempotent: merchants that already have a subscription are skipped.
 *
 * <p>Where a default plan is configured (dev only, see {@link DefaultPlanResolver}) the
 * created subscription follows the same rule as a fresh sign-up — the default plan with
 * status ACTIVE — otherwise a legacy dev merchant would just get a brand-new blocked
 * subscription at startup. Production has no default plan, so the PENDING behaviour is
 * unchanged there.
 *
 * <p>Deliberately <em>not</em> TRIAL: unlike
 * {@link SubscriptionService#createPendingSubscription}, this does not hand a free trial
 * to merchants who have been using the system for months — the trial is a sign-up
 * incentive, not a retroactive grant. Merchants that already have a subscription (TRIAL
 * included) are never touched, so a running trial cannot be duplicated or overwritten.
 *
 * <p>Runs after {@link BasicPlanSeeder} (see {@link Order}), which seeds that plan.
 */
@Component
@Order(LegacyPendingSubscriptionBackfill.ORDER)
class LegacyPendingSubscriptionBackfill implements CommandLineRunner {

    static final int ORDER = BasicPlanSeeder.ORDER + 1;

    private static final Logger log = LoggerFactory.getLogger(LegacyPendingSubscriptionBackfill.class);

    private final SubscriptionRepository subscriptionRepository;
    private final MerchantRepository merchantRepository;
    private final DefaultPlanResolver defaultPlanResolver;

    LegacyPendingSubscriptionBackfill(SubscriptionRepository subscriptionRepository,
                                      MerchantRepository merchantRepository,
                                      DefaultPlanResolver defaultPlanResolver) {
        this.subscriptionRepository = subscriptionRepository;
        this.merchantRepository = merchantRepository;
        this.defaultPlanResolver = defaultPlanResolver;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Set<UUID> merchantsWithSubscription = subscriptionRepository.findAll().stream()
                .map(Subscription::getMerchantId)
                .collect(Collectors.toSet());

        List<UUID> merchantsWithoutSubscription = merchantRepository.findAll().stream()
                .map(Merchant::getId)
                .filter(merchantId -> !merchantsWithSubscription.contains(merchantId))
                .toList();

        if (merchantsWithoutSubscription.isEmpty()) {
            return;
        }

        // Resolved once for the whole batch: it hits the database and may log a warning.
        Plan defaultPlan = defaultPlanResolver.resolve();
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> toCreate = merchantsWithoutSubscription.stream()
                .map(merchantId -> Subscription.builder()
                        .merchantId(merchantId)
                        .plan(defaultPlan)
                        .status(defaultPlan != null ? SubscriptionStatus.ACTIVE : SubscriptionStatus.PENDING)
                        .currentPeriodStart(defaultPlan != null ? now : null)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();

        subscriptionRepository.saveAll(toCreate);
        log.info("Backfill: {} lojistas legados receberam assinatura {}",
                toCreate.size(), defaultPlan != null ? "'" + defaultPlan.getName() + "' (ACTIVE)" : "PENDING");
    }
}
