package com.jetmenu.order;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID id) {
        super("Pedido com ID " + id + " não encontrado");
    }

    public OrderNotFoundException(String message) {
        super(message);
    }
}

