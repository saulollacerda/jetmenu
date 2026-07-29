package com.jetmenu.integration.anotaai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnotaAISyncResult {

    private int ordersImported;
    private int ordersSkipped;
    private int categoriesCreated;
    private int categoriesUpdated;
    private int productsCreated;
    private int productsUpdated;

    /**
     * Unique display names of ingredients referenced by imported orders that
     * are not yet registered in the system. Surfaced to the UI for immediate
     * feedback; persistent tracking lives in the notifications domain.
     */
    private List<String> missingIngredientNames;

    private List<String> errors;
}
