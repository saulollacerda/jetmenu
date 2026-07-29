package com.jetmenu.product;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncludeResponse {

    private UUID id;
    private UUID productId;
    private String name;
    private BigDecimal cost;
    private BigDecimal quantity;
    private BigDecimal totalCost;
    private IncludeKind kind;
    private Integer sortOrder;
}
