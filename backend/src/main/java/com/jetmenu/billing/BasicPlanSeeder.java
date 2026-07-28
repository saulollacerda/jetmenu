package com.jetmenu.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Seeds the "Básico" plan. Runs first (see {@link Order}): the billing backfills look this
 * plan up by name, and would find nothing if bean discovery happened to run them earlier.
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
        if (planRepository.existsByName(BASIC_PLAN_NAME)) {
            return;
        }

        Plan basicPlan = Plan.builder()
                .name(BASIC_PLAN_NAME)
                .minRevenue(BigDecimal.ZERO)
                .maxRevenue(null)
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
}
