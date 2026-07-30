package com.jetmenu.integration.stripe;

import com.jetmenu.billing.Plan;
import com.jetmenu.billing.PlanRepository;
import com.jetmenu.billing.PlanSlug;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the Stripe catalog into {@code plans}: Stripe is where a plan is created and priced,
 * JetMenu stores a copy so the app can list and sell it without calling out on every request.
 * <p>
 * <b>Why the amount is mirrored and not typed in.</b> Before this, a plan's price lived in
 * three places — {@code plans.price_monthly}, the landing page copy, and the Stripe Price —
 * with nothing reconciling them. They had already drifted in the sandbox (R$ 50 stored against
 * R$ 70 charged) before a single merchant had paid. Only one of the three actually takes money,
 * so that one is the source of truth and the rest follow.
 * <p>
 * <b>Idempotent by construction.</b> Runs at boot, from a webhook trigger, or by hand, always
 * converging on the same rows. Nothing here consumes a webhook payload: see
 * {@link StripeCatalogGateway} for why the event is a trigger and not a data source.
 * <p>
 * <b>What is deliberately not mirrored.</b> The name of an existing plan. Stripe's Product name
 * is billing-statement copy ("Jetmenu"), while {@code plans.name} is what a merchant reads in
 * Settings ("Básico"); letting a dashboard edit rename it for everyone is the surprise that
 * {@code plans.slug} exists to prevent. A plan appearing for the first time takes its name from
 * the Product, because something has to name it.
 */
@Service
public class StripeCatalogSync {

    private static final Logger log = LoggerFactory.getLogger(StripeCatalogSync.class);

    /** {@code plans.price_monthly} is a monthly amount, so a yearly Price cannot mirror onto it. */
    private static final String MONTHLY = "month";

    /** The product is sold in Brazil; a USD Price would silently mis-state every plan. */
    private static final String CURRENCY = "brl";

    private final StripeProperties properties;
    private final StripeCatalogGateway gateway;
    private final PlanRepository planRepository;

    StripeCatalogSync(StripeProperties properties,
                      StripeCatalogGateway gateway,
                      PlanRepository planRepository) {
        this.properties = properties;
        this.gateway = gateway;
        this.planRepository = planRepository;
    }

    /**
     * Pulls the catalog and upserts it. Never throws: a Stripe outage at boot must not stop the
     * application, and the rows from the previous sync remain perfectly valid.
     *
     * @return how many plans were created and updated, for logging and for the caller's report
     */
    @Transactional
    public SyncResult sync() {
        if (!properties.isConfigured()) {
            log.info("Stripe não configurada (STRIPE_API_KEY vazia) — catálogo não sincronizado");
            return SyncResult.skipped();
        }

        List<StripeCatalogEntry> entries;
        try {
            entries = gateway.listRecurringPrices();
        } catch (StripeException e) {
            log.error("Falha ao ler o catálogo da Stripe — os planos já sincronizados seguem "
                    + "válidos: {}", e.getMessage(), e);
            return SyncResult.failed();
        }

        int created = 0;
        int updated = 0;

        for (Map.Entry<String, List<StripeCatalogEntry>> bySlug : groupBySlug(entries).entrySet()) {
            String slug = bySlug.getKey();
            List<StripeCatalogEntry> claiming = bySlug.getValue();

            if (claiming.size() > 1) {
                // Two Prices claiming one plan: picking either is a guess, and the wrong guess
                // bills the wrong amount — so neither is applied and the plan keeps whatever it
                // already had. A distinct lookup_key on each Price resolves it.
                log.error("Prices {} resolvem todos para o plano '{}' — nenhum foi aplicado. "
                                + "Defina um lookup_key distinto em cada Price na Stripe.",
                        claiming.stream().map(StripeCatalogEntry::priceId).toList(), slug);
                continue;
            }

            if (upsert(slug, claiming.getFirst())) {
                created++;
            } else {
                updated++;
            }
        }

        log.info("Catálogo sincronizado: {} plano(s) criado(s), {} atualizado(s)", created, updated);
        return new SyncResult(true, created, updated);
    }

    /**
     * Buckets the sellable catalog by the slug each entry claims, so a collision is seen before
     * anything is written. Order is preserved to keep the logs stable.
     */
    private Map<String, List<StripeCatalogEntry>> groupBySlug(List<StripeCatalogEntry> entries) {
        Map<String, List<StripeCatalogEntry>> bySlug = new LinkedHashMap<>();
        for (StripeCatalogEntry entry : sellable(entries)) {
            String slug = slugOf(entry);
            if (!StringUtils.hasText(slug)) {
                log.error("Price {} não tem lookup_key nem nome de produto — impossível "
                        + "identificar o plano; ignorado", entry.priceId());
                continue;
            }
            bySlug.computeIfAbsent(slug, key -> new ArrayList<>()).add(entry);
        }
        return bySlug;
    }

    /** Only what can honestly become a monthly BRL plan row; everything else is reported. */
    private List<StripeCatalogEntry> sellable(List<StripeCatalogEntry> entries) {
        List<StripeCatalogEntry> sellable = new ArrayList<>();
        for (StripeCatalogEntry entry : entries) {
            if (!MONTHLY.equals(entry.interval())) {
                log.info("Price {} ignorado: recorrência '{}' (só mensal vira plano hoje)",
                        entry.priceId(), entry.interval());
            } else if (!CURRENCY.equalsIgnoreCase(entry.currency())) {
                log.info("Price {} ignorado: moeda '{}' (só BRL vira plano hoje)",
                        entry.priceId(), entry.currency());
            } else {
                sellable.add(entry);
            }
        }
        return sellable;
    }

    /**
     * The Price's {@code lookup_key} is Stripe's own field for a stable integration-side name,
     * so it maps straight onto the slug. Falling back to the product name keeps a catalog that
     * predates any lookup_key working — but only until two Prices of one Product collide,
     * which is reported rather than guessed at.
     */
    private static String slugOf(StripeCatalogEntry entry) {
        return StringUtils.hasText(entry.lookupKey())
                ? PlanSlug.of(entry.lookupKey())
                : PlanSlug.of(entry.productName());
    }

    /** @return true when a new plan row was created */
    private boolean upsert(String slug, StripeCatalogEntry entry) {
        Plan plan = planRepository.findBySlug(slug).orElse(null);
        boolean isNew = plan == null;

        if (isNew) {
            plan = Plan.builder()
                    .name(StringUtils.hasText(entry.productName()) ? entry.productName() : slug)
                    .slug(slug)
                    .minRevenue(BigDecimal.ZERO)
                    .features(new HashMap<>())
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        BigDecimal newPrice = entry.monthlyPrice();
        if (!isNew && plan.getPriceMonthly() != null
                && plan.getPriceMonthly().compareTo(newPrice) != 0) {
            log.warn("Plano '{}' reprecificado pela Stripe: R$ {} → R$ {}",
                    plan.getName(), plan.getPriceMonthly(), newPrice);
        }

        plan.setPriceMonthly(newPrice);
        plan.setStripePriceId(entry.priceId());
        plan.setStripeProductId(entry.productId());
        planRepository.save(plan);

        log.info("Plano '{}' ({}) {} a partir da Stripe: {} por R$ {}",
                plan.getName(), slug, isNew ? "criado" : "atualizado",
                entry.priceId(), newPrice);
        return isNew;
    }

    /**
     * @param ran     false when Stripe is unconfigured or unreachable — nothing was written
     * @param created plans that did not exist before this sync
     * @param updated plans whose Stripe linkage or price was refreshed
     */
    public record SyncResult(boolean ran, int created, int updated) {

        static SyncResult skipped() {
            return new SyncResult(false, 0, 0);
        }

        static SyncResult failed() {
            return new SyncResult(false, 0, 0);
        }
    }
}
