package com.jetmenu.integration.stripe;

import java.math.BigDecimal;

/**
 * One sellable line of the Stripe catalog: a recurring Price plus the Product it belongs to,
 * flattened into the handful of fields a JetMenu plan mirrors.
 *
 * @param priceId     {@code price_…}, what a Checkout Session is created against
 * @param productId   {@code prod_…}, kept for reconciliation in the dashboard
 * @param lookupKey   the Price's {@code lookup_key} — Stripe's own field for "a stable name
 *                    the integration can key on", which is exactly what a plan slug is. Null
 *                    when nobody set one, in which case the slug falls back to the product
 *                    name; see {@code StripeCatalogSync}.
 * @param productName the Product's name, used to name a plan the first time it appears
 * @param unitAmount  the amount in the currency's smallest unit (7000 = R$ 70,00)
 */
record StripeCatalogEntry(String priceId,
                          String productId,
                          String lookupKey,
                          String productName,
                          long unitAmount,
                          String currency,
                          String interval) {

    /** {@code 7000} → {@code 70.00}. Stripe counts in cents; {@code plans.price_monthly} does not. */
    BigDecimal monthlyPrice() {
        return BigDecimal.valueOf(unitAmount, 2);
    }
}
