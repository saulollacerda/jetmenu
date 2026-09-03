package com.jetmenu.integration;

import com.jetmenu.integration.anotaai.AnotaAISyncService;
import com.jetmenu.integration.anotaai.OrderSyncScheduler;
import com.jetmenu.integration.ifood.IfoodOrderSyncScheduler;
import com.jetmenu.integration.ifood.IfoodOrderSyncService;
import com.jetmenu.merchant.MerchantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * O polling é desligado <b>suprimindo o bean</b>, não guardando o corpo do método.
 *
 * <p>A diferença importa: uma guarda em código ainda acorda a JVM a cada tick — 30s no iFood,
 * 10min no Anota.AI — e é justamente esse tráfego constante que impede o serviço de dormir no
 * Cloud Run, que é o objetivo inteiro da migração. Sem o bean não há {@code @Scheduled}, e sem
 * {@code @Scheduled} não há tick.
 *
 * <p>É também por isso que o mecanismo aqui é {@code @ConditionalOnProperty}, e não o
 * {@code @Value} + {@code if} que o resto do codebase usa (ex.:
 * {@code IfoodAuthController.requireConnectionEnabled}): lá se quer recusar uma chamada, aqui
 * se quer que ela nunca aconteça.
 *
 * <p>Usa {@link ApplicationContextRunner} em vez de {@code @SpringBootTest} para exercitar a
 * condição sem subir a aplicação inteira três vezes.
 */
@DisplayName("Gate dos schedulers de polling")
class PollingSchedulerGateTest {

    @Nested
    @DisplayName("iFood — ifood.polling-enabled")
    class Ifood {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(IfoodOrderSyncService.class, () -> mock(IfoodOrderSyncService.class))
                .withUserConfiguration(IfoodOrderSyncScheduler.class);

        @Test
        @DisplayName("sem a flag o bean não existe — o default é não fazer polling")
        void shouldNotRegisterSchedulerWhenFlagIsAbsent() {
            runner.run(context -> assertThat(context).doesNotHaveBean(IfoodOrderSyncScheduler.class));
        }

        @Test
        @DisplayName("com a flag desligada o bean não existe")
        void shouldNotRegisterSchedulerWhenFlagIsOff() {
            runner.withPropertyValues("ifood.polling-enabled=false")
                    .run(context -> assertThat(context).doesNotHaveBean(IfoodOrderSyncScheduler.class));
        }

        @Test
        @DisplayName("com a flag ligada o bean existe")
        void shouldRegisterSchedulerWhenFlagIsOn() {
            runner.withPropertyValues("ifood.polling-enabled=true")
                    .run(context -> assertThat(context).hasSingleBean(IfoodOrderSyncScheduler.class));
        }
    }

    @Nested
    @DisplayName("Anota.AI — anotaai.polling-enabled")
    class AnotaAi {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(MerchantRepository.class, () -> mock(MerchantRepository.class))
                .withBean(AnotaAISyncService.class, () -> mock(AnotaAISyncService.class))
                .withUserConfiguration(OrderSyncScheduler.class);

        @Test
        @DisplayName("sem a flag o bean não existe — o default é não fazer polling")
        void shouldNotRegisterSchedulerWhenFlagIsAbsent() {
            runner.run(context -> assertThat(context).doesNotHaveBean(OrderSyncScheduler.class));
        }

        @Test
        @DisplayName("com a flag desligada o bean não existe")
        void shouldNotRegisterSchedulerWhenFlagIsOff() {
            runner.withPropertyValues("anotaai.polling-enabled=false")
                    .run(context -> assertThat(context).doesNotHaveBean(OrderSyncScheduler.class));
        }

        @Test
        @DisplayName("com a flag ligada o bean existe")
        void shouldRegisterSchedulerWhenFlagIsOn() {
            runner.withPropertyValues("anotaai.polling-enabled=true")
                    .run(context -> assertThat(context).hasSingleBean(OrderSyncScheduler.class));
        }
    }
}
