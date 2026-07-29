package com.jetmenu.billing;

import java.util.UUID;

public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(UUID planId) {
        super("Plano com ID " + planId + " não encontrado");
    }
}
