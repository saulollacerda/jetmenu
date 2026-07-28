package com.jetmenu.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemExtraIngredientRequest {

    @NotNull(message = "ID do ingrediente é obrigatório")
    private UUID ingredientId;

    @NotNull(message = "Quantidade do ingrediente extra é obrigatória")
    @PositiveOrZero(message = "Quantidade do ingrediente extra deve ser maior ou igual a zero")
    private BigDecimal quantity;
}

