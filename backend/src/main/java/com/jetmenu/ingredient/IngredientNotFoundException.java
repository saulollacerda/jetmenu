package com.jetmenu.ingredient;

import java.util.UUID;

public class IngredientNotFoundException extends RuntimeException {

    public IngredientNotFoundException(UUID id) {
        super("Ingrediente com ID " + id + " não encontrado");
    }

    public IngredientNotFoundException(String message) {
        super(message);
    }
}

