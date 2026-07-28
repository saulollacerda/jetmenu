package com.jetmenu.product;

import java.util.UUID;

public class IncludeNotFoundException extends RuntimeException {

    public IncludeNotFoundException(UUID id) {
        super("Include com ID " + id + " não encontrado");
    }
}
