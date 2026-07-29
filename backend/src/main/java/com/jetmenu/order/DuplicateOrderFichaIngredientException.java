package com.jetmenu.order;

/**
 * O mesmo ingrediente foi informado mais de uma vez na ficha do pedido. A ficha tem uma
 * linha por ingrediente (unique merchant + ingredient): a quantidade deve ser somada numa
 * linha só, e não repetida.
 */
public class DuplicateOrderFichaIngredientException extends RuntimeException {

    public DuplicateOrderFichaIngredientException(String ingredientName) {
        super("Insumo '" + ingredientName + "' informado mais de uma vez na ficha do pedido");
    }
}
