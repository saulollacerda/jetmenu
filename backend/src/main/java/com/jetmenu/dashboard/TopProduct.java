package com.jetmenu.dashboard;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopProduct {

    private UUID productId;
    private String productName;
    private String categoryName;
    private Long quantitySold;
    private BigDecimal revenue;
    private BigDecimal marginPct;
}

