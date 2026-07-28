package com.MenuBank.MenuBank.integration.ifood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("IfoodOrderSyncScheduler")
class IfoodOrderSyncSchedulerTest {

    @Mock private IfoodOrderSyncService syncService;

    @InjectMocks private IfoodOrderSyncScheduler scheduler;

    @Nested
    @DisplayName("cadência")
    class Cadence {

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

        @Test
        @DisplayName("roda fora da thread do agendador para não atrasar os outros schedulers")
        void shouldRunOutsideTheSchedulerThread() throws NoSuchMethodException {
            Method syncOrders = IfoodOrderSyncScheduler.class.getDeclaredMethod("syncOrders");

            assertThat(syncOrders.getAnnotation(Async.class)).isNotNull();
        }
    }

    @Nested
    @DisplayName("execuções sobrepostas")
    class OverlappingRuns {

        @Test
        @DisplayName("descarta o tick enquanto a sincronização anterior ainda está em andamento")
        void shouldSkipTickWhileAPreviousRunIsStillInFlight() throws Exception {
            CountDownLatch firstRunStarted = new CountDownLatch(1);
            CountDownLatch releaseFirstRun = new CountDownLatch(1);
            willAnswer(invocation -> {
                firstRunStarted.countDown();
                releaseFirstRun.await(5, TimeUnit.SECONDS);
                return null;
            }).given(syncService).syncOrders();

            Thread firstTick = new Thread(scheduler::syncOrders, "first-tick");
            firstTick.start();
            assertThat(firstRunStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // O segundo tick dispara com o primeiro ainda no ar: precisa ser descartado, senão
            // seriam duas chamadas a /events:polling na mesma janela de 30s (rate limit do iFood)
            // e duas leituras do registro de deduplicação antes de qualquer gravação.
            scheduler.syncOrders();

            releaseFirstRun.countDown();
            firstTick.join(5_000);

            then(syncService).should(times(1)).syncOrders();
        }

        @Test
        @DisplayName("volta a sincronizar no tick seguinte depois que a execução anterior termina")
        void shouldSyncAgainOnTheNextTickAfterThePreviousRunFinishes() {
            scheduler.syncOrders();
            scheduler.syncOrders();

            then(syncService).should(times(2)).syncOrders();
        }

        @Test
        @DisplayName("libera a trava quando a sincronização falha, sem bloquear os ticks seguintes")
        void shouldReleaseTheGuardWhenTheSyncFails() {
            willThrow(new RuntimeException("boom"))
                    .willDoNothing()
                    .given(syncService).syncOrders();

            scheduler.syncOrders();
            scheduler.syncOrders();

            then(syncService).should(times(2)).syncOrders();
        }

        @Test
        @DisplayName("libera a trava quando o token exige reautorização do lojista")
        void shouldReleaseTheGuardWhenReauthorizationIsRequired() {
            willThrow(new IfoodReauthorizationRequiredException())
                    .willDoNothing()
                    .given(syncService).syncOrders();

            scheduler.syncOrders();
            scheduler.syncOrders();

            then(syncService).should(times(2)).syncOrders();
        }
    }
}
