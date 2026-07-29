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
public class OrderFichaLineResponse {

    private UUID id;
    private UUID ingredientId;
    private String ingredientName;
    private String ingredientUnit;
    private BigDecimal quantity;
    private BigDecimal costPerUnit;
    /** {@code quantity × costPerUnit} — quanto esta linha custa em cada pedido. */
    private BigDecimal totalCost;
}
