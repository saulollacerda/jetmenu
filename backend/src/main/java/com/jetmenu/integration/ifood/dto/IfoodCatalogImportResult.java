package com.jetmenu.integration.ifood.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Resumo de uma importação de catálogo do iFood: contadores agregados para o modal
 * e o detalhamento por item (motivo incluído quando SKIPPED).
 */
@Data
@AllArgsConstructor
public class IfoodCatalogImportResult {

    public enum Outcome { IMPORTED, LINKED, SKIPPED }

    @Data
    @AllArgsConstructor
    public static class ItemOutcome {
        private String name;
        private String externalCode;
        private Outcome outcome;
        private String reason;
    }

    private int importedProducts;
    private int linkedProducts;
    private int skippedProducts;
    private int importedCategories;
    private int linkedCategories;
    private List<ItemOutcome> items;

    public static IfoodCatalogImportResult of(List<ItemOutcome> items,
                                              int importedCategories,
                                              int linkedCategories) {
        int imported = 0;
        int linked = 0;
        int skipped = 0;
        for (ItemOutcome item : items) {
            switch (item.getOutcome()) {
                case IMPORTED -> imported++;
                case LINKED -> linked++;
                case SKIPPED -> skipped++;
            }
        }
        return new IfoodCatalogImportResult(
                imported, linked, skipped, importedCategories, linkedCategories, items);
    }
}
