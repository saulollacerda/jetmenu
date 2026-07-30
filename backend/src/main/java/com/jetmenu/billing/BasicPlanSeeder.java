package com.jetmenu.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Seeds the "Básico" plan and gives every plan a slug. Runs first (see {@link Order}): the
 * billing backfills look this plan up by name, and would find nothing if bean discovery
 * happened to run them earlier.
 */
@Component
@Order(BasicPlanSeeder.ORDER)
class BasicPlanSeeder implements CommandLineRunner {

    static final int ORDER = 0;

    private static final String BASIC_PLAN_NAME = "Básico";
    private static final Logger log = LoggerFactory.getLogger(BasicPlanSeeder.class);

    private final PlanRepository planRepository;

    BasicPlanSeeder(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        backfillMissingSlugs();

        if (planRepository.existsByName(BASIC_PLAN_NAME)) {
            return;
        }

        Plan basicPlan = Plan.builder()
                .name(BASIC_PLAN_NAME)
                .slug(PlanSlug.of(BASIC_PLAN_NAME))
                .minRevenue(BigDecimal.ZERO)
                .maxRevenue(null)
                // Placeholder only. The amount that counts is the one Stripe charges, and
                // StripeCatalogSync overwrites this with it on the first sync — matching the
                // plan by slug against the Price's lookup_key. Hand-maintaining a second copy
                // of the price is what let R$ 50 here drift from R$ 70 in the sandbox.
                .priceMonthly(new BigDecimal("50.00"))
                .features(Map.of(
                        "allFeatures", true,
                        "description", "Acesso a todas as funcionalidades"))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        planRepository.save(basicPlan);

        log.info("Seed: plano '{}' criado (R$ {}/mês)", BASIC_PLAN_NAME, basicPlan.getPriceMonthly());
    }

    /**
     * Fills the slug of plans that predate the column. Deriving it from the name is correct
     * exactly once, here: from this point on the slug is frozen and renaming the plan no
     * longer moves it. Production reaches the same state through V31 — this covers dev, where
     * the schema comes from {@code ddl-auto=update} and no migration ever runs.
     */
    private void backfillMissingSlugs() {
        List<Plan> withoutSlug = planRepository.findBySlugIsNull();
        if (withoutSlug.isEmpty()) {
            return;
        }

        withoutSlug.forEach(plan -> {
            plan.setSlug(PlanSlug.of(plan.getName()));
            log.info("Backfill: plano '{}' recebeu o slug '{}'", plan.getName(), plan.getSlug());
        });
        planRepository.saveAll(withoutSlug);
    }
}
