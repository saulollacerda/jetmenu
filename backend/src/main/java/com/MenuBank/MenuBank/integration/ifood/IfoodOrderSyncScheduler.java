package com.MenuBank.MenuBank.integration.ifood;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IfoodOrderSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(IfoodOrderSyncScheduler.class);

    private final IfoodOrderSyncService syncService;

    public IfoodOrderSyncScheduler(IfoodOrderSyncService syncService) {
        this.syncService = syncService;
    }

    // Every 30 seconds — the cadence iFood requires for /events:polling during Order
    // homologation, and also its rate limit: polling faster than this gets throttled.
    @Scheduled(fixedDelay = 30_000)
    @Async
    public void syncOrders() {
        try {
            syncService.syncOrders();
        } catch (IfoodReauthorizationRequiredException e) {
            log.warn("[iFood] token expirado — reautorização necessária pelo lojista");
        } catch (Exception e) {
            log.error("[iFood] sync automático falhou: {}", e.getMessage(), e);
        }
    }
}
