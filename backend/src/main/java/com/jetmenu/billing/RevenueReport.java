package com.jetmenu.billing;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "revenue_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "reported_revenue", nullable = false, precision = 15, scale = 2)
    private BigDecimal reportedRevenue;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_plan_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Plan suggestedPlan;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
