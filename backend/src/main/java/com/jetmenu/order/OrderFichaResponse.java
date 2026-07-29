package com.jetmenu.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ficha do pedido configurada pelo lojista, com o custo resultante por pedido —
 * assim a tela mostra exatamente quanto cada pedido passa a custar a mais.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFichaResponse {

    private List<OrderFichaLineResponse> lines;
    /** Soma das linhas: custo aplicado UMA vez em cada pedido. */
    private BigDecimal totalCost;
}
