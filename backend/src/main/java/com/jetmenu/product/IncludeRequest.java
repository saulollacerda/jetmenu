package com.jetmenu.product;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncludeRequest {

    @NotBlank(message = "Nome do include é obrigatório")
    @Size(max = 255, message = "Nome não pode ter mais de 255 caracteres")
    private String name;

    @NotNull(message = "Custo é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true, message = "Custo não pode ser negativo")
    private BigDecimal cost;

    @DecimalMin(value = "0.0", inclusive = false, message = "Quantidade deve ser maior que zero")
    private BigDecimal quantity;

    private IncludeKind kind;

    private Integer sortOrder;
}
