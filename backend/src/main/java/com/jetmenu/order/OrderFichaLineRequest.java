package com.jetmenu.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** Uma linha da ficha do pedido: ingrediente + quantidade consumida por pedido. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFichaLineRequest {

    @NotNull(message = "Ingrediente é obrigatório")
    private UUID ingredientId;

    @NotNull(message = "Quantidade é obrigatória")
    @DecimalMin(value = "0.0", inclusive = false, message = "Quantidade deve ser maior que zero")
    @Digits(integer = 13, fraction = 6, message = "Quantidade deve ter no máximo 6 casas decimais")
    private BigDecimal quantity;
}
