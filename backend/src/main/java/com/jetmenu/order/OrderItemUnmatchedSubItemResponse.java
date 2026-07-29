package com.jetmenu.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A subItem of an imported order that had no matching ingredient. Rendered in the order
 * details with a button to create the missing ingredient (prefilled with {@link #rawName}).
 * Only present while no ingredient with the same canonical name exists; once created, the
 * entry is filtered out of the response so the button disappears on the next fetch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemUnmatchedSubItemResponse {

    private UUID id;
    private String rawName;
    private Integer quantity;

    /** Unit price paid by the customer. May be {@code null}/{@code 0} for a base complement. */
    private BigDecimal salePricePerUnit;

    /** Total price paid by the customer. May be {@code null}/{@code 0} for a base complement. */
    private BigDecimal salePriceTotal;
}
