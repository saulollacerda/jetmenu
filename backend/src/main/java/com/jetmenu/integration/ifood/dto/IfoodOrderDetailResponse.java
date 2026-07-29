package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IfoodOrderDetailResponse {

    private String id;
    private String displayId;
    private String orderType;
    private String orderTiming;
    private String salesChannel;
    private String category;
    private String createdAt;
    private String preparationStartDateTime;

    // iFood documentation names this field "isTest" but some payloads serialize it as "test"
    @JsonAlias("isTest")
    private boolean test;

    private String extraInfo;

    private MerchantInfo merchant;
    private CustomerInfo customer;
    private List<Item> items;
    private Total total;

    /** Payment breakdown — card brand and cash change are required by the Order homologation. */
    private Payments payments;

    /** Coupons/discounts applied to the order, with the split between iFood and the merchant. */
    private List<Benefit> benefits;

    private Delivery delivery;
    private Takeout takeout;
    private DineIn dineIn;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MerchantInfo {
        private String id;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomerInfo {
        private String id;
        private String name;
        private String documentNumber;
        private Phone phone;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Phone {
        private String number;
        private String localizer;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Integer index;
        private String id;
        private String uniqueId;
        private String externalCode;
        private String ean;
        private String name;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal optionsPrice;
        private BigDecimal totalPrice;
        private String observations;
        private List<Option> options;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Option {
        private Integer index;
        private String id;
        private String name;
        private String groupName;
        private String externalCode;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal addition;
        private BigDecimal price;
        // TODO: options[].customizations[] (3rd level) intentionally not mapped in v1
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Total {
        private BigDecimal subTotal;
        private BigDecimal deliveryFee;
        private BigDecimal additionalFees;
        private BigDecimal benefits;
        private BigDecimal orderAmount;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payments {
        /** Amount already paid online (through iFood). */
        private BigDecimal prepaid;
        /** Amount still to be collected on delivery. */
        private BigDecimal pending;
        private List<PaymentMethod> methods;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentMethod {
        private BigDecimal value;
        private String currency;
        /** CREDIT, DEBIT, CASH, MEAL_VOUCHER, PIX, ... */
        private String method;
        /** ONLINE (paid through iFood) or OFFLINE (collected by the merchant). */
        private String type;
        private Card card;
        private Cash cash;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Card {
        /** VISA, MASTERCARD, ELO, ... — must be displayed on the order screen. */
        private String brand;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cash {
        /** Bill the customer will hand over; the change is {@code changeFor - value}. */
        private BigDecimal changeFor;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Benefit {
        private BigDecimal value;
        /** CART, DELIVERY_FEE, ITEM, ... — what the discount was applied to. */
        private String target;
        private String targetId;
        private String campaign;
        private List<SponsorshipValue> sponsorshipValues;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SponsorshipValue {
        /** IFOOD or MERCHANT — who pays for this share of the discount. */
        private String name;
        private BigDecimal value;
        private String description;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delivery {
        /** DEFAULT, ECONOMIC, ... */
        private String mode;
        /** IFOOD or MERCHANT — who performs the delivery. */
        private String deliveredBy;
        private String deliveryDateTime;
        /** Free-text delivery instructions that must be shown on the order ticket. */
        private String observations;
        /** Collection code the courier must present to the merchant. */
        private String pickupCode;
        // deliveryAddress intentionally not mapped: JetMenu does not persist addresses
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Takeout {
        private String mode;
        private String takeoutDateTime;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DineIn {
        private String startDateTime;
        private String table;
    }
}
