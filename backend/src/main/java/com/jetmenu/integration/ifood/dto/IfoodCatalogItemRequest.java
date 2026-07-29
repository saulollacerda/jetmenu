package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body of {@code PUT /merchants/{merchantId}/items} — full replace of an item (item,
 * products, option groups and options in a single call). Idempotent: sending the same
 * payload twice does not duplicate anything.
 *
 * <p>JetMenu always publishes through {@link #whitelabelItem}: the item root is pushed as
 * {@code UNAVAILABLE} and the real status/price/externalCode travel on a
 * {@code contextModifiers} entry with {@code catalogContext: WHITELABEL}. Contexts that are
 * not listed inherit the root values, so the item stays hidden on delivery ({@code DEFAULT})
 * and dine-in ({@code INDOOR}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IfoodCatalogItemRequest(
        Item item,
        List<Product> products,
        List<Object> optionGroups,
        List<Object> options) {

    /** Item type JetMenu publishes — PIZZA and COMBO_V2 are out of scope. */
    public static final String TYPE_DEFAULT = "DEFAULT";
    public static final String CONTEXT_WHITELABEL = "WHITELABEL";
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(
            String id,
            String type,
            String categoryId,
            String status,
            Price price,
            String externalCode,
            List<ContextModifier> contextModifiers) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Product(String id, String name, String description) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Price(BigDecimal value) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextModifier(
            String catalogContext,
            Price price,
            String status,
            String externalCode) {}

    /**
     * Builds a simple (no complements) item scoped to the WHITELABEL context.
     *
     * @param whitelabelStatus the status the item really has on the digital menu
     */
    public static IfoodCatalogItemRequest whitelabelItem(String itemId,
                                                         String categoryId,
                                                         String externalCode,
                                                         BigDecimal price,
                                                         String whitelabelStatus,
                                                         String productId,
                                                         String productName,
                                                         String productDescription) {
        Price itemPrice = new Price(price);
        return new IfoodCatalogItemRequest(
                new Item(itemId, TYPE_DEFAULT, categoryId, STATUS_UNAVAILABLE, itemPrice, externalCode,
                        List.of(new ContextModifier(
                                CONTEXT_WHITELABEL, itemPrice, whitelabelStatus, externalCode))),
                List.of(new Product(productId, productName, productDescription)),
                List.of(),
                List.of());
    }
}
