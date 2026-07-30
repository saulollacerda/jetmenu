package com.jetmenu.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link SubscriptionService#createPendingSubscription} only applies the default plan to
 * accounts created from then on. Merchants that signed up earlier keep the plan-less
 * PENDING row they already had, and the frontend gate keys off the status, so they stay
 * blocked behind the "choose a plan" overlay forever. This backfill migrates those rows.
 *
 * <p>Runs after {@link BasicPlanSeeder} (see {@link Order}), which creates the plan row
 * this looks up by name.
 *
 * <p>Scope is deliberately narrow, since being wrong here means handing a paying customer
 * a free active plan:
 * <ul>
 *   <li>No default plan configured (production) — returns before reading anything, so not
 *       a single statement is issued.</li>
 *   <li>Only PENDING subscriptions <em>without</em> a plan are touched. A subscription
 *       that already has a plan, or that is TRIAL/ACTIVE/PAST_DUE/CANCELED, is left
 *       exactly as it is — a canceled account is never resurrected, and a running free
 *       trial is never cut short. A sign-up trial also has {@code plan == null}, so it is
 *       the status check alone that keeps it out of scope.</li>
 *   <li>Idempotent: upgraded rows are no longer PENDING, so a second run matches nothing.</li>
 * </ul>
 */
@Component
@Order(DefaultPlanSubscriptionBackfill.ORDER)
class DefaultPlanSubscriptionBackfill implements CommandLineRunner {

    static final int ORDER = LegacyPendingSubscriptionBackfill.ORDER + 1;

    private static final Logger log = LoggerFactory.getLogger(DefaultPlanSubscriptionBackfill.class);

    private final SubscriptionRepository subscriptionRepository;
    private final DefaultPlanResolver defaultPlanResolver;

    DefaultPlanSubscriptionBackfill(SubscriptionRepository subscriptionRepository,
                                    DefaultPlanResolver defaultPlanResolver) {
        this.subscriptionRepository = subscriptionRepository;
        this.defaultPlanResolver = defaultPlanResolver;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Plan defaultPlan = defaultPlanResolver.resolve();
        if (defaultPlan == null) {
            return;
        }

        List<Subscription> toUpgrade = subscriptionRepository.findAll().stream()
                .filter(DefaultPlanSubscriptionBackfill::isEligible)
                .toList();

        if (toUpgrade.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        toUpgrade.forEach(subscription -> {
            subscription.setPlan(defaultPlan);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodStart(now);
            subscription.setUpdatedAt(now);
        });

        subscriptionRepository.saveAll(toUpgrade);
        log.info("Backfill: {} assinatura(s) PENDING migrada(s) para o plano '{}' (ACTIVE)",
                toUpgrade.size(), defaultPlan.getName());
    }

    /**
     * Only a subscription still waiting for a plan choice may be upgraded. Anything with a
     * plan already picked, or in any other status, belongs to the regular billing flow.
     */
    private static boolean isEligible(Subscription subscription) {
        return subscription.getPlan() == null
                && SubscriptionStatus.PENDING.equals(subscription.getStatus());
    }
}
