package com.jetmenu.integration.ifood;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registry of iFood event ids already handled by the polling loop.
 *
 * <p>iFood may redeliver the same event (network retries, overlapping polling windows,
 * acknowledgment that did not reach the API), and homologation requires detecting and
 * discarding duplicates instead of reprocessing them.
 *
 * <p>Rows are purged after 7 days by {@link IfoodOrderSyncService}: order details expire
 * after that window, so an older id can never come back with anything meaningful.
 */
@Entity
@Table(name = "ifood_processed_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IfoodProcessedEvent {

    @Id
    @Column(name = "event_id", columnDefinition = "TEXT", nullable = false)
    private String eventId;

    @Column(nullable = false)
    private LocalDateTime processedAt;
}
