package com.jetmenu.billing;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {

    private UUID id;
    private UUID merchantId;
    private UUID planId;
    private String planName;
    private SubscriptionStatus status;
    private LocalDateTime trialEndsAt;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
