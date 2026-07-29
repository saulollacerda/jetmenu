package com.jetmenu.integration.rawpayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
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
