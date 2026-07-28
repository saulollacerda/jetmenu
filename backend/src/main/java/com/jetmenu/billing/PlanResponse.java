package com.jetmenu.billing;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse {

    private UUID id;
    private String name;
    private BigDecimal minRevenue;
    private BigDecimal maxRevenue;
    private BigDecimal priceMonthly;
    private Map<String, Object> features;
    private boolean active;
    private LocalDateTime createdAt;
}
