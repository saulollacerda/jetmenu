package com.jetmenu.ingredient;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Payload do PATCH /api/ingredients/{id}/position: nova posição global (zero-based)
 * do ingrediente na ordenação padrão do merchant. O service faz o clamp para o
 * intervalo válido e desloca os demais registros.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientPositionRequest {

    @NotNull(message = "Posição é obrigatória")
    @Min(value = 0, message = "Posição deve ser maior ou igual a zero")
    private Integer position;
}
