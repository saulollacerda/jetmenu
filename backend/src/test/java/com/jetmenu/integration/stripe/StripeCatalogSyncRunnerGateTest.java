package com.jetmenu.integration.stripe;

import com.jetmenu.billing.PlanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * O pior trabalho de boot que restava: uma chamada à <b>API da Stripe</b>, com transação
 * aberta durante a ida à rede ({@code StripeCatalogSync.sync}). No Railway isso acontecia uma
 * vez por deploy; no Cloud Run aconteceria a cada instância nova, somando latência de rede
 * externa a todo cold start.
 *
 * <p>O catálogo muda raramente e não é dado quente: passa a ser sincronizado uma vez por dia
 * pelo Cloud Scheduler, em {@code POST /api/internal/jobs/stripe-catalog-sync}. Em
 * desenvolvimento continua no boot, onde a conveniência vale mais que os segundos.
 */
@DisplayName("Gate do StripeCatalogSyncRunner")
class StripeCatalogSyncRunnerGateTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(StripeCatalogSync.class, () -> mock(StripeCatalogSync.class))
            .withBean(PlanRepository.class, () -> mock(PlanRepository.class))
            .withUserConfiguration(StripeCatalogSyncRunner.class);

    @Test
    @DisplayName("sem a flag o bean existe — em dev o catálogo continua vindo no boot")
    void shouldRegisterByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(StripeCatalogSyncRunner.class));
    }

    @Test
    @DisplayName("com a flag desligada o bean não existe — nenhuma chamada à Stripe no boot")
    void shouldNotRegisterWhenDisabled() {
        runner.withPropertyValues("app.startup.stripe-catalog-sync-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(StripeCatalogSyncRunner.class));
    }
}
