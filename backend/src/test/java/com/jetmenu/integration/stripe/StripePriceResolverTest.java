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
 * The Stripe Price of a plan is stored on the plan, mirrored from the Stripe catalog — not
 * configured per environment and not derived from the plan's display name.
 */
@DisplayName("StripePriceResolver")
class StripePriceResolverTest {

    private StripePriceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new StripePriceResolver();
    }

    private Plan plan(String name, String slug, String stripePriceId) {
        return Plan.builder()
                .name(name)
                .slug(slug)
                .stripePriceId(stripePriceId)
                .minRevenue(BigDecimal.ZERO)
                .priceMonthly(new BigDecimal("70.00"))
                .build();
    }

    @Test
    @DisplayName("deve devolver o price id sincronizado no plano")
    void shouldReturnTheSyncedPriceId() {
        assertThat(resolver.resolvePriceId(plan("Básico", "basico", "price_123")))
                .isEqualTo("price_123");
    }

    @Test
    @DisplayName("deve continuar resolvendo depois de o plano ser renomeado")
    void shouldKeepResolvingAfterPlanIsRenamed() {
        assertThat(resolver.resolvePriceId(plan("Essencial", "basico", "price_123")))
                .isEqualTo("price_123");
    }

    @Test
    @DisplayName("deve falhar com 503 em pt-BR quando o plano não foi sincronizado")
    void shouldFailWhenPlanWasNeverSynced() {
        assertThatThrownBy(() -> resolver.resolvePriceId(plan("Básico", "basico", null)))
                .isInstanceOf(BillingProviderUnavailableException.class)
                .hasMessageContaining("Básico");
    }

    @Test
    @DisplayName("deve tratar price id em branco como ausente")
    void shouldTreatBlankPriceIdAsMissing() {
        assertThatThrownBy(() -> resolver.resolvePriceId(plan("Básico", "basico", "   ")))
                .isInstanceOf(BillingProviderUnavailableException.class);
    }
}
