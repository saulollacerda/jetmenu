package com.jetmenu.product;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resposta de uso de um ingrediente nas fichas tecnicas (includes) dos produtos.
 *
 * <p>Como o {@link Include} nao referencia mais a tabela {@code ingredients}, a busca
 * e feita por <b>match de nome</b> (case-insensitive) entre {@code Ingredient.name}
 * e {@code Include.name}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientProductUsageResponse {

    private UUID includeId;
    private UUID productId;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal cost;
    private BigDecimal totalCost;
}
