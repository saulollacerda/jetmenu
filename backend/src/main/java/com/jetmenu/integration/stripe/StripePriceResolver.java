package com.jetmenu.integration.stripe;

import com.jetmenu.billing.BillingProviderUnavailableException;
import com.jetmenu.billing.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Maps a JetMenu {@link Plan} to the Stripe recurring Price it is sold as.
 * <p>
 * The mapping is stored on the plan itself ({@code plans.stripe_price_id}), mirrored from the
 * Stripe catalog by {@link StripeCatalogSync}. It used to be configuration —
 * {@code stripe.price-ids.<slug>}, one environment variable per plan — which meant every new
 * plan needed a line in two {@code .properties} files and a redeploy, and left the plan's price
 * typed in twice, once in Stripe and once in the database, with nothing keeping them equal.
 * <p>
 * Storing the id is safe precisely because it is synced and not entered by hand: a test-mode
 * price id does not exist in live mode, and each environment fills its own value from its own
 * Stripe account.
 */
@Component
public class StripePriceResolver {

    private static final Logger log = LoggerFactory.getLogger(StripePriceResolver.class);

    /**
     * @return the Stripe Price id this plan is sold as
     * @throws BillingProviderUnavailableException when the plan carries no price id — it was
     *         never matched in the Stripe catalog. Deliberately loud: this must surface as an
     *         explicit pt-BR 503, never as a checkout that quietly does nothing.
     */
    public String resolvePriceId(Plan plan) {
        String priceId = plan.getStripePriceId();

        if (!StringUtils.hasText(priceId)) {
            log.error("Plano '{}' (slug '{}') não tem price da Stripe associado — crie o Price "
                            + "com lookup_key '{}' e rode a sincronização do catálogo",
                    plan.getName(), plan.getSlug(), plan.getSlug());
            throw new BillingProviderUnavailableException(
                    "O plano '" + plan.getName() + "' ainda não está disponível para pagamento "
                            + "online. Entre em contato com o suporte para ativá-lo.");
        }

        return priceId;
    }
}
