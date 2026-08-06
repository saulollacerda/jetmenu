package com.jetmenu.integration.stripe;

import com.jetmenu.billing.Plan;
import com.jetmenu.billing.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Syncs the Stripe catalog once at startup, then reports any active plan that still cannot be
 * sold. Without the report, the first symptom of a plan missing its Stripe Price is a merchant
 * clicking "Assinar" and getting a 503 — the operator finds out through support.
 * <p>
 * Runs on {@link ApplicationReadyEvent}, which fires after {@code CommandLineRunner}s, so the
 * slug backfill in {@code BasicPlanSeeder} has already happened when the catalog is matched.
 * <p>
 * <b>It logs and does not stop the application, on purpose.</b> {@code plans} is data, not
 * configuration: a row can be inserted straight into the database and the Stripe catalog can
 * change with no deploy at all. A boot-blocking check would turn either into a full outage on
 * the next restart — orders, menu, everything — for merchants who already paid. Checkout
 * already fails loudly and specifically for the affected plan (see {@link StripePriceResolver});
 * this only moves the discovery earlier.
 */
@Component
class StripeCatalogSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(StripeCatalogSyncRunner.class);

    private final StripeCatalogSync catalogSync;
    private final PlanRepository planRepository;

    StripeCatalogSyncRunner(StripeCatalogSync catalogSync, PlanRepository planRepository) {
        this.catalogSync = catalogSync;
        this.planRepository = planRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    void syncAndReport() {
        catalogSync.sync();

        List<Plan> unsellable = planRepository.findByActiveTrueOrderByMinRevenueAsc().stream()
                .filter(plan -> !StringUtils.hasText(plan.getStripePriceId()))
                .toList();

        if (unsellable.isEmpty()) {
            return;
        }

        unsellable.forEach(plan -> log.error(
                "Plano ativo '{}' (slug '{}') não tem price da Stripe — o checkout dele "
                        + "responde 503. Crie o Price na Stripe com lookup_key '{}' e "
                        + "sincronize novamente.",
                plan.getName(), plan.getSlug(), plan.getSlug()));
    }
}
