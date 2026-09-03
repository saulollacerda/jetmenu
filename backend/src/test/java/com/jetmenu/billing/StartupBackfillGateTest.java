package com.jetmenu.billing;

import com.jetmenu.merchant.MerchantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Os backfills de boot são migrações de dados de uma vez só, mas rodam a <b>cada</b> boot — e
 * no Cloud Run "cada boot" passa a significar cada instância nova, várias vezes por dia.
 *
 * <p>O {@link LegacyPendingSubscriptionBackfill} é o caso mais caro: dois {@code findAll()}
 * inteiros (assinaturas e merchants) carregados em memória mesmo quando não há nada a fazer.
 *
 * <p>Por isso o gate é na existência do bean, e não numa guarda dentro do {@code run}: o custo
 * está justamente nas consultas que descobrem que não há trabalho. O default é <b>ligado</b> —
 * quem precisa desligar é a produção, onde os dados legados já foram migrados; um ambiente
 * novo continua sendo semeado sem precisar saber que a flag existe.
 */
@DisplayName("Gate dos backfills de inicialização")
class StartupBackfillGateTest {

    @Nested
    @DisplayName("BasicPlanSeeder")
    class Seeder {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(PlanRepository.class, () -> mock(PlanRepository.class))
                .withUserConfiguration(BasicPlanSeeder.class);

        @Test
        @DisplayName("sem a flag o bean existe — ambiente novo precisa do plano semeado")
        void shouldRegisterByDefault() {
            runner.run(context -> assertThat(context).hasSingleBean(BasicPlanSeeder.class));
        }

        @Test
        @DisplayName("com a flag desligada o bean não existe")
        void shouldNotRegisterWhenDisabled() {
            runner.withPropertyValues("app.startup.backfills-enabled=false")
                    .run(context -> assertThat(context).doesNotHaveBean(BasicPlanSeeder.class));
        }

    }

    @Nested
    @DisplayName("LegacyPendingSubscriptionBackfill")
    class PendingSubscriptions {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(SubscriptionRepository.class, () -> mock(SubscriptionRepository.class))
                .withBean(MerchantRepository.class, () -> mock(MerchantRepository.class))
                .withBean(DefaultPlanResolver.class, () -> mock(DefaultPlanResolver.class))
                .withUserConfiguration(LegacyPendingSubscriptionBackfill.class);

        @Test
        @DisplayName("sem a flag o bean existe")
        void shouldRegisterByDefault() {
            runner.run(context ->
                    assertThat(context).hasSingleBean(LegacyPendingSubscriptionBackfill.class));
        }

        @Test
        @DisplayName("com a flag desligada o bean não existe")
        void shouldNotRegisterWhenDisabled() {
            runner.withPropertyValues("app.startup.backfills-enabled=false")
                    .run(context ->
                            assertThat(context).doesNotHaveBean(LegacyPendingSubscriptionBackfill.class));
        }

    }
}
