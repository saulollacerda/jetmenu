package com.jetmenu.billing;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueReportResponse {

    private UUID id;
    private UUID merchantId;
    private BigDecimal reportedRevenue;
    private LocalDate referenceMonth;
    private UUID suggestedPlanId;
    private String suggestedPlanName;
    private LocalDateTime createdAt;
}
