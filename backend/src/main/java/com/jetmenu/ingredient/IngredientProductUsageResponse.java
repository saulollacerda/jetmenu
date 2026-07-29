package com.jetmenu.ingredient;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientProductUsageResponse {

    private UUID productIngredientId;
    private UUID productId;
    private String productName;
    private BigDecimal grammage;
    private boolean isOptional;
    private BigDecimal costPerUnit;
    private BigDecimal totalCost;
}
