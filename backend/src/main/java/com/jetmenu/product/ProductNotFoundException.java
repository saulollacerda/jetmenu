package com.jetmenu.product;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID id) {
        super("Produto com ID " + id + " não encontrado");
    }
}

