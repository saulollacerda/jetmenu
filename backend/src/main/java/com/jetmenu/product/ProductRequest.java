package com.jetmenu.product;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 255, message = "Nome não pode ter mais de 255 caracteres")
    private String name;

    @NotNull(message = "Preço é obrigatório")
    @DecimalMin(value = "0.0", inclusive = false, message = "Preço deve ser maior que zero")
    private BigDecimal price;

    @NotNull(message = "Categoria é obrigatória")
    private UUID categoryId;

    /**
     * Margem ideal (%) do produto. Opcional — ausente/nula significa "não acompanhada".
     */
    @DecimalMin(value = "0.0", message = "Margem ideal deve ser maior ou igual a zero")
    @DecimalMax(value = "100.0", message = "Margem ideal deve ser menor ou igual a 100")
    private BigDecimal targetMarginPct;

    private ProductStatus status;
}
