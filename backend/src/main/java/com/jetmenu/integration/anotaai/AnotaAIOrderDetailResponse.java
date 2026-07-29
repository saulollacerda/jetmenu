package com.jetmenu.integration.anotaai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnotaAIOrderDetailResponse {

    private boolean success;
    private OrderDetail info;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderDetail {
        @JsonProperty("_id")
        private String id;
        // NOTA: `discounts` ainda não é usado — segue ignorado pelo
        // @JsonIgnoreProperties(ignoreUnknown=true). Se precisar usar futuramente,
        // criar uma classe Discount em vez de List<Double>.
        private int check;
        private String createdAt;
        private AnotaAICustomer customer;
        private double deliveryFee;
        // Taxas adicionais repassadas ao iFood (ex.: RESTAURANT_SERVICE_FEE, "Taxa de serviço").
        // Estão inclusas no `total`, mas não são receita do restaurante.
        private List<AdditionalFee> additionalFees;
        private String observation;
        private List<AnotaAIOrderItem> items;
        private List<AnotaAIPayment> payments;
        private double total;
        private String type;
        private String salesChannel;
        private int shortReference;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdditionalFee {
        private String type;
        private String description;
        private double value;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnotaAICustomer {
        private String id;
        private String name;
        private String phone;
        private String taxPayerIdentificationNumber;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnotaAIOrderItem {
        @JsonProperty("_id")
        private String itemId;
        private String name;
        private int quantity;
        private String internalId;
        private String externalId;
        private double price;
        private double total;
        private List<AnotaAISubItem> subItems;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnotaAISubItem {
        private String name;
        private int quantity;
        private double price;
        private double total;
        private String internalId;
        private String externalId;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnotaAIPayment {
        private String name;
        private String code;
        private String value;
        private boolean prepaid;
    }
}
