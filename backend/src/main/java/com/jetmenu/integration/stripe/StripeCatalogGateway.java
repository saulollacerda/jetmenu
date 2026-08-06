package com.jetmenu.integration.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.param.PriceListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads the Stripe catalog: every active recurring Price, with its Product expanded.
 * <p>
 * This is a <b>pull</b>, on purpose. The obvious alternative is to listen for
 * {@code price.created} and write what the event carries, but an event is delivered once and
 * a missed delivery leaves a plan silently absent. Listing is idempotent and re-runnable, so
 * it can be triggered at boot, from a webhook, or by hand, and always converges on the same
 * state. Webhook events are best used as a trigger for this call, not as a data source.
 */
@Component
class StripeCatalogGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeCatalogGateway.class);

    /** Stripe pages at 100; a plan catalog that outgrows one page is not a thing today. */
    private static final long PAGE_LIMIT = 100L;

    private final StripeClientFactory clientFactory;

    StripeCatalogGateway(StripeClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /**
     * @return every active recurring Price in the configured Stripe account
     * @throws StripeException when the catalog cannot be read; callers decide whether that is
     *         fatal (it is not, at boot: the previous sync's rows stay valid)
     */
    List<StripeCatalogEntry> listRecurringPrices() throws StripeException {
        PriceListParams params = PriceListParams.builder()
                .setActive(true)
                .setType(PriceListParams.Type.RECURRING)
                // Without the expansion, price.getProductObject() is null and the plan name
                // would need a second round trip per price.
                .addExpand("data.product")
                .setLimit(PAGE_LIMIT)
                .build();

        List<Price> prices = clientFactory.client().prices().list(params).getData();
        log.info("Catálogo da Stripe: {} price(s) recorrente(s) ativo(s)", prices.size());

        return prices.stream().map(StripeCatalogGateway::toEntry).toList();
    }

    private static StripeCatalogEntry toEntry(Price price) {
        Product product = price.getProductObject();
        return new StripeCatalogEntry(
                price.getId(),
                product != null ? product.getId() : price.getProduct(),
                price.getLookupKey(),
                product != null ? product.getName() : null,
                price.getUnitAmount() != null ? price.getUnitAmount() : 0L,
                price.getCurrency(),
                price.getRecurring() != null ? price.getRecurring().getInterval() : null);
    }
}
