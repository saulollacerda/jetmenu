package com.jetmenu.product;

import com.jetmenu.category.CatalogOrigin;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private UUID id;
    private String name;
    private BigDecimal price;
    private ProductStatus status;
    private UUID categoryId;
    private String categoryName;
    private BigDecimal unitCost;
    private BigDecimal marginPct;
    private CatalogOrigin origin;
}
