package com.jetmenu.dashboard;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailySales {

    private LocalDate date;
    private BigDecimal total;
}

