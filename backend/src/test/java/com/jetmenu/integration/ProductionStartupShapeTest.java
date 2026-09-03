package com.jetmenu.integration;

import com.jetmenu.integration.anotaai.AnotaAIReconciliationService;
import com.jetmenu.integration.anotaai.AnotaAIWebhookController;
import com.jetmenu.integration.anotaai.OrderSyncScheduler;
import com.jetmenu.integration.ifood.IfoodOrderSyncScheduler;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadCleanupScheduler;
import com.jetmenu.internal.InternalJobsController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o contexto com <b>todas</b> as flags de produção juntas, incluindo
 * {@code spring.main.lazy-initialization}.
 *
 * <p>Cada gate tem seu próprio teste; o que este cobre é a combinação — e a combinação é onde
 * mora o risco real. Lazy initialization muda quando cada bean nasce, e o resto da suíte roda
 * com o padrão (tudo no boot), então sem este teste a configuração que de fato vai para o ar
 * seria a única nunca exercitada.
 *
 * <p>O contrato em uma frase: nada que faça polling ou trabalho de boot existe, e tudo que
 * atende requisição continua existindo.
 */
@SpringBootTest(properties = {
        "ifood.polling-enabled=false",
        "anotaai.polling-enabled=false",
        "app.jobs.cleanup-scheduler-enabled=false",
        "app.startup.backfills-enabled=false",
        "app.startup.stripe-catalog-sync-enabled=false",
        "app.internal-jobs.token=token-de-teste",
        "spring.main.lazy-initialization=true",
})
@DisplayName("Forma de produção — nada de polling nem trabalho de boot")
class ProductionStartupShapeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("nenhum scheduler existe")
    void shouldHaveNoSchedulers() {
        assertThat(context.getBeanNamesForType(IfoodOrderSyncScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OrderSyncScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ExternalOrderRawPayloadCleanupScheduler.class)).isEmpty();
    }

    /**
     * Sobra um {@code CommandLineRunner}: o {@code DefaultPlanSubscriptionBackfill}, que já é
     * no-op em produção sem precisar de flag — {@code app.billing.default-plan-name} vazio faz
     * o {@code DefaultPlanResolver} devolver {@code null} <i>antes</i> de qualquer consulta.
     * Ele não paga o custo que os outros pagavam, então não ganhou gate. Esta asserção existe
     * para que a lista seja explícita: um runner novo que apareça aqui tem que ser uma
     * decisão, não um acidente.
     */
    @Test
    @DisplayName("só sobra o runner que já é no-op em produção sem consultar o banco")
    void shouldHaveNoStartupWorkLeft() {
        assertThat(context.getBeanNamesForType(org.springframework.boot.CommandLineRunner.class))
                .containsExactly("defaultPlanSubscriptionBackfill");
    }

    /**
     * O que substituiu os schedulers precisa continuar de pé — senão o resultado do gate não é
     * "escala a zero", é "não importa mais pedido".
     */
    @Test
    @DisplayName("webhook e jobs internos continuam atendendo")
    void shouldKeepRequestDrivenEntryPoints() {
        assertThat(context.getBeanNamesForType(AnotaAIWebhookController.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(InternalJobsController.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(AnotaAIReconciliationService.class)).isNotEmpty();
    }
}
