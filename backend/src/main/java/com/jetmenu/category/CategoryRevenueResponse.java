package com.jetmenu.category;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRevenueResponse {

    private UUID categoryId;
    private BigDecimal revenue;
}
