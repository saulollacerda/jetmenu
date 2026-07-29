package com.jetmenu.order;

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
public class OrderItemExtraIngredientResponse {

    private UUID id;
    private UUID ingredientId;
    private String ingredientName;
    private String ingredientUnit;
    private BigDecimal quantity;
    private BigDecimal costPerUnit;
    private BigDecimal totalCost;

    /**
     * Preço unitário pago pelo cliente por este adicional. {@code 0} = complemento base.
     * Nulo quando o extra não tem preço conhecido (pedido manual/iFood ou importado
     * antes da migração V16).
     */
    private BigDecimal salePricePerUnit;

    /**
     * Valor total pago pelo cliente por este adicional, como veio do payload da Anota.AI.
     * Independente de {@link #totalCost}, que é o custo de produção.
     */
    private BigDecimal salePriceTotal;
}

