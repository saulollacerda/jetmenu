package com.jetmenu.integration.anotaai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnotaAIOrderListResponse {

    private boolean success;
    private OrderListInfo info;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderListInfo {
        private List<OrderSummary> docs;
        private int count;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderSummary {
        @JsonProperty("_id")
        private String id;
        private int check;
        private String from;
        @JsonProperty("salesChannel")
        private String salesChannel;
        @JsonProperty("updatedAt")
        private String updatedAt;
    }
}
