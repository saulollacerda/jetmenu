package com.jetmenu.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * Correção manual do valor final ({@link Order#getTotalValue()}) e do custo total
 * ({@link Order#getTotalCost()}) de um pedido. Usado em {@code PATCH /api/orders/{id}/values}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderValuesRequest {

    @NotNull(message = "Valor total é obrigatório")
    @DecimalMin(value = "0.0", message = "Valor total não pode ser negativo")
    private BigDecimal totalValue;

    @NotNull(message = "Custo total é obrigatório")
    @DecimalMin(value = "0.0", message = "Custo total não pode ser negativo")
    private BigDecimal totalCost;
}
