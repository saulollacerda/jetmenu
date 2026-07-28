package com.jetmenu.billing;

import java.util.UUID;

public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(UUID merchantId) {
        super("Assinatura não encontrada para o merchant " + merchantId);
    }
}
