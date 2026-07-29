package com.jetmenu.customer;

import java.util.UUID;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(UUID id) {
        super("Cliente com ID " + id + " não encontrado");
    }
}

