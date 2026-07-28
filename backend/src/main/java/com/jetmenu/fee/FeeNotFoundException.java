package com.jetmenu.fee;

import java.util.UUID;

public class FeeNotFoundException extends RuntimeException {

    public FeeNotFoundException(UUID id) {
        super("Taxa com ID " + id + " não encontrada");
    }
}
