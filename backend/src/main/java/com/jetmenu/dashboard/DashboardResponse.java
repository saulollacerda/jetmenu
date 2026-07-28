package com.jetmenu.dashboard;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private BigDecimal totalSales;
    private Long orderCount;
    private BigDecimal averageTicket;
    private BigDecimal estimatedProfit;
    private List<DailySales> salesByDay;
    private List<TopProduct> topProducts;
    private BigDecimal totalSalesChangePct;
    private BigDecimal orderCountChangePct;
    private BigDecimal averageTicketChangePct;
    private BigDecimal estimatedProfitChangePct;
    private BigDecimal estimatedMarginPct;
    private BigDecimal estimatedMarginChangePct;
    private BigDecimal averageMarginPct;
    private Long customerCount;
    private BigDecimal customerCountChangePct;
}

