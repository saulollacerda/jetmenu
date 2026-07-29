package com.jetmenu.integration.stripe;

import com.jetmenu.billing.BillingProviderUnavailableException;
import com.jetmenu.billing.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plan → Stripe Price resolution comes from configuration, never from a database column:
 * the operator pastes {@code STRIPE_PRICE_<SLUG>} values in and nothing needs a migration.
 */
@DisplayName("StripePriceResolver")
class StripePriceResolverTest {

    private StripeProperties properties;
    private StripePriceResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new StripeProperties();
        resolver = new StripePriceResolver(properties);
    }

    private Plan plan(String name) {
        return Plan.builder()
                .name(name)
                .minRevenue(BigDecimal.ZERO)
                .priceMonthly(new BigDecimal("50.00"))
                .build();
    }

    @Test
    @DisplayName("deve resolver o price id configurado para o plano 'Básico' pelo slug sem acento")
    void shouldResolveConfiguredPriceIdByUnaccentedSlug() {
        properties.getPriceIds().put("basico", "price_123");

        assertThat(resolver.resolvePriceId(plan("Básico"))).isEqualTo("price_123");
    }

    @Test
    @DisplayName("deve gerar slug sem acento, em minúsculas e com hífens")
    void shouldSlugifyPlanName() {
        assertThat(StripePriceResolver.slugify("Básico")).isEqualTo("basico");
        assertThat(StripePriceResolver.slugify("Plano Avançado")).isEqualTo("plano-avancado");
        assertThat(StripePriceResolver.slugify("  Básico  ")).isEqualTo("basico");
    }

    @Test
    @DisplayName("deve falhar com 503 em pt-BR quando o plano não tem price id configurado")
    void shouldFailWhenPlanHasNoConfiguredPriceId() {
        assertThatThrownBy(() -> resolver.resolvePriceId(plan("Básico")))
                .isInstanceOf(BillingProviderUnavailableException.class)
                .hasMessageContaining("Básico");
    }

    @Test
    @DisplayName("deve tratar price id em branco como não configurado (env var vazia)")
    void shouldTreatBlankPriceIdAsMissing() {
        properties.getPriceIds().put("basico", "   ");

        assertThatThrownBy(() -> resolver.resolvePriceId(plan("Básico")))
                .isInstanceOf(BillingProviderUnavailableException.class);
    }
}
