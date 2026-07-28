package com.MenuBank.MenuBank.integration.ifood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IfoodOrderSyncScheduler")
class IfoodOrderSyncSchedulerTest {

    @Test
    @DisplayName("deve executar o sync de pedidos a cada 30 segundos (cadência exigida na homologação)")
    void shouldPollEveryThirtySeconds() throws NoSuchMethodException {
        Method syncOrders = IfoodOrderSyncScheduler.class.getDeclaredMethod("syncOrders");
        Scheduled scheduled = syncOrders.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelay()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("não pode consultar o polling mais rápido que 30s — abaixo disso viola o rate limit")
    void shouldNeverPollFasterThanThirtySeconds() throws NoSuchMethodException {
        Method syncOrders = IfoodOrderSyncScheduler.class.getDeclaredMethod("syncOrders");
        Scheduled scheduled = syncOrders.getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelay()).isGreaterThanOrEqualTo(30_000L);
    }
}
