package com.jetmenu.ingredient;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientCostRequest {

    @NotNull(message = "Custo por unidade é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true, message = "Custo por unidade deve ser >= 0")
    private BigDecimal costPerUnit;

    /** Gramatura padrão sugerida para este ingrediente (opcional). */
    @DecimalMin(value = "0.0", inclusive = true, message = "Gramatura padrão deve ser >= 0")
    private BigDecimal defaultQuantity;

    /** Unidade (ex: g, ml, un). Se nula, mantém o valor atual. */
    private String unit;
}
