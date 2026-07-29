package com.jetmenu.billing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueReportRequest {

    @NotNull(message = "Faturamento reportado é obrigatório")
    @DecimalMin(value = "0.0", message = "Faturamento não pode ser negativo")
    private BigDecimal reportedRevenue;

    @NotNull(message = "Mês de referência é obrigatório")
    private LocalDate referenceMonth;
}
