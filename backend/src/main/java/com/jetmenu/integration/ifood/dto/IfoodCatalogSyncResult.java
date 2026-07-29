package com.jetmenu.integration.ifood.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de uma sincronização em lote (preço ou status). A operação é assíncrona no
 * iFood: {@code batchId} é o protocolo para acompanhar em
 * {@code GET /batch/{batchId}}. Fica {@code null} quando nada foi enviado — todos os
 * produtos pedidos caíram em {@code skipped} com o motivo em pt-BR.
 */
@Data
@AllArgsConstructor
public class IfoodCatalogSyncResult {

    @Data
    @AllArgsConstructor
    public static class SkippedProduct {
        private UUID productId;
        private String reason;
    }

    private String batchId;
    private int requested;
    private List<SkippedProduct> skipped;
}
