package com.MenuBank.MenuBank.billing;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "min_revenue", nullable = false, precision = 15, scale = 2)
    private BigDecimal minRevenue;

    @Column(name = "max_revenue", precision = 15, scale = 2)
    private BigDecimal maxRevenue;

    @Column(name = "price_monthly", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceMonthly;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> features = new HashMap<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Legacy AbacatePay linkage, kept on purpose. The AbacatePay integration was removed and
     * nothing writes this field any more, but existing rows map plans to real AbacatePay
     * products and accounting reconciles historical invoices through them — never drop the
     * column and never rename the field. The next payment provider must add its own column.
     */
    @Column(name = "abacatepay_product_id", length = 255, unique = true)
    private String abacatepayProductId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
