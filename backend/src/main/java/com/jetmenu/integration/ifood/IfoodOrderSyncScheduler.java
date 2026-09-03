package com.jetmenu.integration.ifood;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispara o polling de eventos do iFood na cadência exigida pela homologação.
 *
 * <p>A execução é {@link Async} de propósito: o pool do agendador tem uma única thread e é
 * compartilhado com os outros schedulers da aplicação (sincronia do Anota AI e limpeza
 * noturna de payloads), que não podem ficar na fila atrás de um sync de pedidos disparado a
 * cada 30 segundos.
 *
 * <p>O preço do {@code @Async} é que o {@code fixedDelay} passa a contar a partir do disparo,
 * e não do fim da execução — na prática vira {@code fixedRate}. Como um ciclo pode passar de
 * 30s (cada evento pode render um {@code GET /orders/{id}}, e o retry com backoff soma
 * segundos), a trava {@code running} descarta o tick que chegar com o anterior ainda no ar.
 * Sem ela haveria duas chamadas a {@code /events:polling} na mesma janela — exatamente o que
 * o rate limit do iFood barra — e duas execuções concorrentes lendo o registro de
 * deduplicação antes de qualquer gravação, fazendo o mesmo evento ser processado duas vezes.
 *
 * <p><b>Existe apenas com {@code ifood.polling-enabled=true}</b>, e o default é não existir.
 * O gate está na existência do bean, e não no corpo do método, porque o que precisa sumir é o
 * tick: um {@code if} lá dentro ainda acordaria a JVM a cada 30 segundos, e é esse tráfego
 * constante que impede o serviço de escalar a zero. Em produção a flag fica desligada junto
 * com {@code ifood.connection-enabled} — nenhum lojista depende do iFood enquanto a
 * homologação não anda. Nada aqui foi removido: religar é mudar a variável de ambiente.
 */
@Component
@ConditionalOnProperty(name = "ifood.polling-enabled", havingValue = "true", matchIfMissing = false)
public class IfoodOrderSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(IfoodOrderSyncScheduler.class);

    private final IfoodOrderSyncService syncService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public IfoodOrderSyncScheduler(IfoodOrderSyncService syncService) {
        this.syncService = syncService;
    }

    // Every 30 seconds — the cadence iFood requires for /events:polling during Order
    // homologation, and also its rate limit: polling faster than this gets throttled.
    @Scheduled(fixedDelay = 30_000)
    @Async
    public void syncOrders() {
        if (!running.compareAndSet(false, true)) {
            log.info("[iFood] tick descartado — a sincronização anterior ainda está em andamento");
            return;
        }
        try {
            syncService.syncOrders();
        } catch (IfoodReauthorizationRequiredException e) {
            log.warn("[iFood] token expirado — reautorização necessária pelo lojista");
        } catch (Exception e) {
            log.error("[iFood] sync automático falhou: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }
}
