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

    /**
     * O pedido em si. Duas formas de entrega usam esta classe, e a diferença já quase custou
     * um bug silencioso: {@code /ping/get/{id}} devolve o pedido dentro do envelope
     * {@code {success, info}} desta classe externa, mas o <b>webhook manda este objeto na
     * raiz do corpo</b>. Como todas as classes aqui têm
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)}, desserializar o corpo do webhook
     * na classe externa não lança nada: produz {@code success=false, info=null}, e o pedido
     * some sem erro nenhum.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderDetail {
        @JsonProperty("_id")
        private String id;
        /**
         * A loja do lado da Anota.AI. Só vem no corpo do webhook — o {@code /ping/get/{id}}
         * não precisa dela, porque a chamada já parte da chave da loja. É o que permite
         * recusar cadastro cruzado entre lojistas.
         */
        private AnotaAIMerchant merchant;
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
    public static class AnotaAIMerchant {
        private String id;
        private String name;
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
