package com.jetmenu.integration.anotaai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnotaAICatalogResponse {

    private boolean success;
    private String message;

    @JsonProperty("data")
    private List<AnotaAICategory> categories;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnotaAICategory {
        private String title;
        private String id;
        private List<AnotaAIItem> itens;

        @JsonProperty("is_additional")
        private boolean isAdditional;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnotaAIItem {
        private String id;
        private String title;
        private boolean out;

        @JsonProperty("week_prices")
        private List<WeekPrice> weekPrices;

        @JsonProperty("external_id")
        private String externalId;

        @JsonProperty("next_steps")
        private List<NextStep> nextSteps;

        public double getPrice() {
            if (weekPrices == null || weekPrices.isEmpty()) {
                return 0.0;
            }
            return weekPrices.get(0).getPrice();
        }

        public void setPrice(double price) {
            WeekPrice wp = new WeekPrice();
            wp.setPrice(price);
            this.weekPrices = java.util.List.of(wp);
        }
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeekPrice {
        private double price;

        @JsonProperty("short_name")
        private String shortName;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NextStep {
        @JsonProperty("category_id")
        private String categoryId;

        @JsonProperty("category_title")
        private String categoryTitle;

        private int min;
        private int max;
    }
}
