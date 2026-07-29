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

    private ProductStatus status;
}
