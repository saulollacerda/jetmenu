package com.jetmenu.integration.stripe;

import com.jetmenu.billing.BillingProviderUnavailableException;
import com.jetmenu.billing.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Maps a JetMenu {@link Plan} to the Stripe recurring Price it is sold as.
 * <p>
 * The mapping lives in configuration ({@code stripe.price-ids.<slug>}), never in a database
 * column: switching between sandbox and production is then just a different
 * {@code STRIPE_PRICE_<SLUG>} value, with no migration and no rows to insert.
 */
@Component
public class StripePriceResolver {

    private static final Logger log = LoggerFactory.getLogger(StripePriceResolver.class);

    private final StripeProperties properties;

    public StripePriceResolver(StripeProperties properties) {
        this.properties = properties;
    }

    /**
     * @return the configured Stripe Price id for the plan
     * @throws BillingProviderUnavailableException when the plan has no price id configured.
     *         Deliberately loud: a missing price id must surface as an explicit pt-BR 503,
     *         never as a checkout that quietly does nothing.
     */
    public String resolvePriceId(Plan plan) {
        String slug = slugify(plan.getName());
        String priceId = properties.getPriceIds().get(slug);

        if (!StringUtils.hasText(priceId)) {
            log.error("Plano '{}' não tem price id da Stripe configurado — defina "
                            + "stripe.price-ids.{} (variável de ambiente STRIPE_PRICE_{})",
                    plan.getName(), slug, slug.toUpperCase(Locale.ROOT).replace('-', '_'));
            throw new BillingProviderUnavailableException(
                    "O plano '" + plan.getName() + "' ainda não está disponível para pagamento "
                            + "online. Entre em contato com o suporte para ativá-lo.");
        }

        return priceId;
    }

    /**
     * Plan name → configuration key: lowercase, accents stripped, non-alphanumerics collapsed
     * into hyphens. {@code "Básico"} becomes {@code "basico"}.
     * <p>
     * Stripping the accent is not cosmetic: {@code .properties} files are read as ISO-8859-1,
     * so an accented key would have to be written as a unicode escape and would silently stop
     * matching the moment someone typed it literally.
     */
    static String slugify(String planName) {
        String withoutAccents = Normalizer.normalize(planName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
