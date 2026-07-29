package com.jetmenu.integration.ifood.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Resumo de uma publicação de catálogo no iFood: contadores agregados para o modal e o
 * detalhamento por produto. {@code skippedProducts} agrega tudo que não foi publicado
 * (validação reprovada + falha na API), sempre com o motivo em pt-BR no item.
 */
@Data
@AllArgsConstructor
public class IfoodCatalogPublishResult {

    public enum Outcome { PUBLISHED, SKIPPED, FAILED }

    @Data
    @AllArgsConstructor
    public static class ItemOutcome {
        private UUID productId;
        private String name;
        private String externalCode;
        private Outcome outcome;
        private String reason;
    }

    private int publishedProducts;
    private int skippedProducts;
    private List<ItemOutcome> items;

    public static IfoodCatalogPublishResult of(List<ItemOutcome> items) {
        int published = 0;
        int skipped = 0;
        for (ItemOutcome item : items) {
            if (item.getOutcome() == Outcome.PUBLISHED) {
                published++;
            } else {
                skipped++;
            }
        }
        return new IfoodCatalogPublishResult(published, skipped, items);
    }
}
