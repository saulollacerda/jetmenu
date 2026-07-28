package com.jetmenu.integration.ifood;

import java.time.LocalDateTime;

/**
 * Estado das três etapas de ativação da integração iFood de um merchant:
 * conectado (etapa 1), catálogo importado (etapa 2) e sincronia de pedidos (etapa 3).
 */
public record IfoodIntegrationStatus(boolean connected,
                                     LocalDateTime catalogImportedAt,
                                     boolean orderSyncEnabled) {

    public static IfoodIntegrationStatus disconnected() {
        return new IfoodIntegrationStatus(false, null, false);
    }
}
