package com.jetmenu.order;

import com.jetmenu.product.IncludeResponse;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal unitCost;
    private BigDecimal totalCost;

    /**
     * Margem ideal (%) do produto gravada no item quando o pedido foi criado (snapshot).
     * Nula em pedidos antigos e em produtos sem margem ideal cadastrada.
     */
    private BigDecimal targetMarginPct;

    /**
     * Observação/instrução especial que o cliente escreveu para este item ("sem cebola").
     * Nula em pedidos manuais e em importações sem observação.
     */
    private String observations;

    /**
     * Insumos da ficha técnica do produto (Includes) no momento da consulta.
     * Esses são os ingredientes base do produto, fixos por receita.
     */
    @Builder.Default
    private List<IncludeResponse> insumos = List.of();

    /**
     * Ingredientes extras adicionados pelo cliente neste pedido (subItems no Anota.AI).
     */
    @Builder.Default
    private List<OrderItemExtraIngredientResponse> extraIngredients = List.of();

    /**
     * SubItems importados que não casaram com nenhum ingrediente cadastrado. A UI mostra cada
     * um com um botão para cadastrar o ingrediente faltante. Some da resposta assim que um
     * ingrediente com o mesmo nome canônico passa a existir.
     */
    @Builder.Default
    private List<OrderItemUnmatchedSubItemResponse> unmatchedSubItems = List.of();

    /**
     * Ids dos includes da ficha técnica desmarcados neste item (pedido manual).
     * Usado pela UI para restaurar os checkboxes na edição.
     */
    @Builder.Default
    private List<UUID> excludedIncludeIds = List.of();
}

