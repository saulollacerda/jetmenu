package com.jetmenu.integration.rawpayload;

import com.jetmenu.order.OrderOrigin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Raw JSON payload of an imported external order (Anota.AI / iFood), kept for a
 * few days so financial inconsistencies can be audited against the original data.
 * Rows are purged daily by {@link ExternalOrderRawPayloadCleanupScheduler}.
 */
@Entity
@Table(name = "external_order_raw_payload")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalOrderRawPayload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderOrigin origin;

    @Column(name = "external_order_id", nullable = false)
    private String externalOrderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
