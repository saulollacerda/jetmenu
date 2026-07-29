package com.jetmenu.ingredient;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 255, message = "Nome não pode ter mais de 255 caracteres")
    private String name;

    @NotBlank(message = "Unidade é obrigatória")
    @Size(max = 20, message = "Unidade não pode ter mais de 20 caracteres")
    private String unit;

    @NotNull(message = "Custo por unidade é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true, message = "Custo por unidade deve ser maior ou igual a zero")
    @Digits(integer = 15, fraction = 4, message = "Custo por unidade deve ter no máximo 4 casas decimais")
    private BigDecimal costPerUnit;

    @DecimalMin(value = "0.0", inclusive = true, message = "Quantidade padrão deve ser maior ou igual a zero")
    private BigDecimal defaultQuantity;

    @DecimalMin(value = "0.0", inclusive = true, message = "Preço de venda deve ser maior ou igual a zero")
    private BigDecimal salePrice;

    private IngredientStatus status;

    @DecimalMin(value = "0.0", inclusive = true, message = "Quantidade em estoque deve ser maior ou igual a zero")
    private BigDecimal stockQuantity;

    private java.time.LocalDateTime lastReplenishedAt;

    @DecimalMin(value = "0.0", inclusive = true, message = "Limite de estoque baixo deve ser maior ou igual a zero")
    private BigDecimal lowStockThreshold;
}
