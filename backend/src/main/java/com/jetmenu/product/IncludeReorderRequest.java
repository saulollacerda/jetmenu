package com.jetmenu.product;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncludeReorderRequest {

    @NotNull
    private UUID id;

    @NotNull
    private Integer sortOrder;
}
