package com.jetmenu.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linha da ficha do pedido como foi gravada NO pedido (snapshot), exibida no detalhe
 * para que o custo do pedido seja explicável e não apareça do nada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFichaIngredientResponse {

    private UUID id;
    private UUID ingredientId;
    private String ingredientName;
    private String ingredientUnit;
    private BigDecimal quantity;
    private BigDecimal costPerUnit;
    private BigDecimal totalCost;
}
