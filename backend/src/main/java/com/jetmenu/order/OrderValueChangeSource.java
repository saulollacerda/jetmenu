package com.jetmenu.order;

/**
 * O que causou uma mudança em totalValue/totalCost/estimatedProfit de um pedido já
 * existente — grava em {@link OrderValueChange} para permitir debugar "por que esse
 * pedido está com valor/lucro diferente do esperado".
 */
public enum OrderValueChangeSource {
    /** Correção manual do lojista via {@code OrderService.updateValues}. */
    MANUAL_OVERRIDE,
    /** Lojista restaurou os valores originais via {@code OrderService.restoreValues}. */
    RESTORE,
    /** Edição dos itens do pedido via {@code OrderService.update}. */
    ITEM_EDIT,
    /** Recomputo assíncrono do custo pelo backfill de ingredientes da Anota.AI. */
    INGREDIENT_BACKFILL
}
