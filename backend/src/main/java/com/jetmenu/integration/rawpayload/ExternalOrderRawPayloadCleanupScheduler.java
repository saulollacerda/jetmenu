package com.jetmenu.integration.rawpayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limpeza dos payloads brutos fora da janela de retenção, às 4h de Brasília.
 *
 * <p><b>Existe apenas com {@code app.jobs.cleanup-scheduler-enabled=true}</b>, e o default é
 * não existir. Em produção quem chama a limpeza é o Cloud Scheduler, em
 * {@code POST /api/internal/jobs/raw-payload-cleanup}: um {@code @Scheduled} exige uma
 * instância viva 24h por dia esperando o relógio — exatamente a RAM ociosa que a migração
 * quer eliminar.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.cleanup-scheduler-enabled", havingValue = "true",
        matchIfMissing = false)
public class ExternalOrderRawPayloadCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExternalOrderRawPayloadCleanupScheduler.class);

    private final ExternalOrderRawPayloadService service;

    public ExternalOrderRawPayloadCleanupScheduler(ExternalOrderRawPayloadService service) {
        this.service = service;
    }

    // Daily at 04:00 (Brasília), off-peak for delivery restaurants
    @Scheduled(cron = "0 0 4 * * *", zone = "America/Sao_Paulo")
    @Async
    public void purgeExpiredPayloads() {
        try {
            service.purgeExpired();
        } catch (Exception e) {
            log.error("[RawPayload] limpeza automática falhou: {}", e.getMessage(), e);
        }
    }
}
