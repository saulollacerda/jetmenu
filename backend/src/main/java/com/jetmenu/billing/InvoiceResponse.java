package com.jetmenu.billing;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponse {

    private UUID id;
    private UUID subscriptionId;
    private BigDecimal amount;
    private InvoiceStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime dueAt;
    private LocalDateTime createdAt;
}
