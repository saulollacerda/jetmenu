package com.jetmenu.dashboard;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeakHour {

    private Integer hour;
    private Long orderCount;
    private BigDecimal pct;
}
