package com.jetmenu.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Substituição completa da ficha do pedido do lojista. Lista vazia = sem ficha do pedido
 * (custo zero, comportamento idêntico ao de antes da funcionalidade).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFichaRequest {

    @NotNull(message = "Lista de insumos é obrigatória")
    @Valid
    private List<OrderFichaLineRequest> lines;
}
