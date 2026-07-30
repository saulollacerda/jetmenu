package com.jetmenu.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for {@code app.billing.default-plan-name}: the plan every
 * account is put on automatically, or none at all.
 *
 * <p>Only the dev profile sets the property (see application-dev.properties). Prod
 * deliberately leaves it unset, and {@link #resolve()} then returns null <em>without
 * touching the database</em>, so every caller degrades to the regular billing flow and
 * no paying customer is ever moved onto a free plan.
 *
 * <p>The configured name must match the plan seeded by {@link BasicPlanSeeder} exactly.
 * A missing row is a misconfiguration (typically an encoding mismatch on the accent),
 * so it is logged and treated as "no default plan" rather than failing startup.
 */
@Component
class DefaultPlanResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultPlanResolver.class);

    private final PlanRepository planRepository;
    private final String defaultPlanName;

    DefaultPlanResolver(PlanRepository planRepository,
                        @Value("${app.billing.default-plan-name:}") String defaultPlanName) {
        this.planRepository = planRepository;
        this.defaultPlanName = defaultPlanName;
    }

    /**
     * @return the configured default plan, or null when the property is unset/blank
     * (production) or the configured plan does not exist.
     */
    Plan resolve() {
        if (defaultPlanName == null || defaultPlanName.isBlank()) {
            return null;
        }
        return planRepository.findByName(defaultPlanName)
                .orElseGet(() -> {
                    log.warn("Plano padrão '{}' não encontrado — assinaturas seguem sem plano (teste grátis)",
                            defaultPlanName);
                    return null;
                });
    }
}
