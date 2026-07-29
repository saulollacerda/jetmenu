package com.jetmenu.category;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private UUID id;
    private String name;
    private Long productCount;
    private String colorHex;
    private CatalogOrigin origin;
}

