package com.jetmenu.integration.rawpayload;

import com.jetmenu.order.OrderOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Persists the raw JSON payload of every imported external order and purges
 * payloads older than the retention window. Saving is best-effort: an audit
 * failure must never break the order import that triggered it.
 */
@Service
public class ExternalOrderRawPayloadService {

    private static final Logger log = LoggerFactory.getLogger(ExternalOrderRawPayloadService.class);

    static final int RETENTION_DAYS = 3;
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ExternalOrderRawPayloadRepository repository;

    public ExternalOrderRawPayloadService(ExternalOrderRawPayloadRepository repository) {
        this.repository = repository;
    }

    public void save(UUID merchantId, OrderOrigin origin, String externalOrderId, String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return;
        }
        try {
            repository.save(ExternalOrderRawPayload.builder()
                    .merchantId(merchantId)
                    .origin(origin)
                    .externalOrderId(externalOrderId)
                    .payload(rawPayload)
                    .createdAt(LocalDateTime.now(BRAZIL_ZONE))
                    .build());
        } catch (RuntimeException e) {
            log.error("[RawPayload] falha ao salvar payload bruto — pedido={} origin={}: {}",
                    externalOrderId, origin, e.getMessage(), e);
        }
    }

    @Transactional
    public long purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now(BRAZIL_ZONE).minusDays(RETENTION_DAYS);
        long removed = repository.deleteByCreatedAtBefore(cutoff);
        log.info("[RawPayload] limpeza concluída — {} payloads anteriores a {} removidos", removed, cutoff);
        return removed;
    }
}
