package com.jetmenu.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {

    @NotNull(message = "ID do produto é obrigatório")
    private UUID productId;

    @NotNull(message = "Quantidade é obrigatória")
    @Min(value = 1, message = "Quantidade deve ser no mínimo 1")
    private Integer quantity;

    @Valid
    @Builder.Default
    private List<OrderItemExtraIngredientRequest> extraIngredients = List.of();

    /**
     * Ids dos includes da ficha técnica que o operador desmarcou neste item.
     * Ausente/vazio = ficha completa entra no custo.
     */
    @Builder.Default
    private List<UUID> excludedIncludeIds = List.of();
}

