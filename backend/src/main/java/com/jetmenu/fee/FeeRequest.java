package com.jetmenu.fee;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 100, message = "Nome não pode ter mais de 100 caracteres")
    private String name;

    @NotNull(message = "Taxa é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true, message = "Taxa deve ser maior ou igual a zero")
    @Digits(integer = 3, fraction = 4, message = "Taxa deve ter no máximo 3 dígitos inteiros e 4 casas decimais")
    private BigDecimal feeRate;
}
