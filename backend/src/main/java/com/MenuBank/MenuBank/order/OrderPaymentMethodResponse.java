package com.MenuBank.MenuBank.order;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One payment method of an imported order, as shown on the order ticket.
 * {@code cardBrand} and {@code changeFor} are required by the iFood Order homologation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentMethodResponse {

    private UUID id;

    /** CREDIT, DEBIT, CASH, MEAL_VOUCHER, PIX, ... */
    private String method;

    /** ONLINE (already paid) or OFFLINE (collected by the merchant). */
    private String type;

    /** VISA, MASTERCARD, ELO, ... Null when the method is not a card. */
    private String cardBrand;

    private BigDecimal value;

    private String currency;

    /** Bill the customer hands over when paying in cash. Null when there is no change. */
    private BigDecimal changeFor;

    /**
     * Change owed to the customer ({@code changeFor - value}), already computed so every
     * client shows the same number. Null when the method is not cash with change.
     */
    private BigDecimal changeAmount;
}
