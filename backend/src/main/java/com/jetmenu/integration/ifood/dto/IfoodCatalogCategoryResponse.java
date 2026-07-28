package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IfoodCatalogCategoryResponse {

    private String id;
    private String name;
    private String status;
    private String template;
    private List<Item> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String id;
        private String name;
        private String description;
        private String externalCode;
        private String status;
        private Price price;
        private List<ContextModifier> contextModifiers;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Price {
        private BigDecimal value;
        private BigDecimal originalValue;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContextModifier {
        private String catalogContext;
        private Price price;
        private String status;
        private String externalCode;
    }
}
