package com.jetmenu.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both backfills look the default plan up by name, so the plan row must already exist
 * when they run. Spring Boot sorts its CommandLineRunners with the same comparator used
 * here, so this pins the startup order instead of relying on bean-discovery luck.
 */
@DisplayName("Ordem dos CommandLineRunners de billing")
class BillingStartupOrderTest {

    @Test
    @DisplayName("BasicPlanSeeder deve rodar antes dos backfills que dependem do plano semeado")
    void seederShouldRunBeforeBackfills() {
        List<Class<?>> runners = new ArrayList<>(List.of(
                DefaultPlanSubscriptionBackfill.class,
                LegacyPendingSubscriptionBackfill.class,
                BasicPlanSeeder.class));

        AnnotationAwareOrderComparator.sort(runners);

        assertThat(runners).element(0).isEqualTo(BasicPlanSeeder.class);
        assertThat(runners).containsExactly(
                BasicPlanSeeder.class,
                LegacyPendingSubscriptionBackfill.class,
                DefaultPlanSubscriptionBackfill.class);
    }
}
