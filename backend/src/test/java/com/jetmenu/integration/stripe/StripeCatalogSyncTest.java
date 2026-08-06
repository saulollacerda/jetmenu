package com.jetmenu.integration.stripe;

import com.jetmenu.billing.Plan;
import com.jetmenu.billing.PlanRepository;
import com.stripe.exception.ApiConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("StripeCatalogSync")
class StripeCatalogSyncTest {

    @Mock
    private StripeCatalogGateway gateway;

    @Mock
    private PlanRepository planRepository;

    private StripeProperties properties;
    private StripeCatalogSync sync;

    @BeforeEach
    void setUp() {
        properties = new StripeProperties();
        properties.setApiKey("sk_test_123");
        sync = new StripeCatalogSync(properties, gateway, planRepository);
    }

    private StripeCatalogEntry entry(String priceId, String lookupKey, String productName, long amount) {
        return new StripeCatalogEntry(priceId, "prod_1", lookupKey, productName, amount, "brl", "month");
    }

    private Plan existingPlan(String slug, String name, String price) {
        return Plan.builder()
                .name(name)
                .slug(slug)
                .minRevenue(BigDecimal.ZERO)
                .priceMonthly(new BigDecimal(price))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("não deve chamar a Stripe quando a chave não está configurada")
    void shouldSkipWhenUnconfigured() throws Exception {
        properties.setApiKey("");

        assertThat(sync.sync().ran()).isFalse();
        then(gateway).should(never()).listRecurringPrices();
    }

    @Test
    @DisplayName("deve casar o price pelo lookup_key e sobrescrever o preço do plano existente")
    void shouldMirrorPriceOntoExistingPlanMatchedByLookupKey() throws Exception {
        Plan basico = existingPlan("basico", "Básico", "50.00");
        given(gateway.listRecurringPrices())
                .willReturn(List.of(entry("price_abc", "basico", "Jetmenu", 7000)));
        given(planRepository.findBySlug("basico")).willReturn(Optional.of(basico));

        StripeCatalogSync.SyncResult result = sync.sync();

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(basico.getPriceMonthly()).isEqualByComparingTo("70.00");
        assertThat(basico.getStripePriceId()).isEqualTo("price_abc");
        assertThat(basico.getStripeProductId()).isEqualTo("prod_1");
    }

    @Test
    @DisplayName("não deve renomear um plano existente com o nome do produto da Stripe")
    void shouldNotRenameAnExistingPlan() throws Exception {
        Plan basico = existingPlan("basico", "Básico", "50.00");
        given(gateway.listRecurringPrices())
                .willReturn(List.of(entry("price_abc", "basico", "Jetmenu", 7000)));
        given(planRepository.findBySlug("basico")).willReturn(Optional.of(basico));

        sync.sync();

        assertThat(basico.getName()).isEqualTo("Básico");
    }

    @Test
    @DisplayName("deve criar plano novo quando o price ainda não tem plano correspondente")
    void shouldCreateAPlanForANewPrice() throws Exception {
        given(gateway.listRecurringPrices())
                .willReturn(List.of(entry("price_pro", "pro", "Jetmenu Pro", 15000)));
        given(planRepository.findBySlug("pro")).willReturn(Optional.empty());

        StripeCatalogSync.SyncResult result = sync.sync();

        ArgumentCaptor<Plan> saved = ArgumentCaptor.forClass(Plan.class);
        then(planRepository).should().save(saved.capture());

        assertThat(result.created()).isEqualTo(1);
        assertThat(saved.getValue().getName()).isEqualTo("Jetmenu Pro");
        assertThat(saved.getValue().getSlug()).isEqualTo("pro");
        assertThat(saved.getValue().getPriceMonthly()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("deve cair no nome do produto quando o price não tem lookup_key")
    void shouldFallBackToTheProductNameWithoutALookupKey() throws Exception {
        given(gateway.listRecurringPrices())
                .willReturn(List.of(entry("price_abc", null, "Jetmenu", 7000)));
        given(planRepository.findBySlug("jetmenu")).willReturn(Optional.empty());

        sync.sync();

        ArgumentCaptor<Plan> saved = ArgumentCaptor.forClass(Plan.class);
        then(planRepository).should().save(saved.capture());
        assertThat(saved.getValue().getSlug()).isEqualTo("jetmenu");
    }

    @Test
    @DisplayName("não deve aplicar nenhum dos dois prices quando ambos resolvem para o mesmo plano")
    void shouldApplyNeitherPriceWhenTheyCollideOnOneSlug() throws Exception {
        given(gateway.listRecurringPrices()).willReturn(List.of(
                entry("price_mensal", null, "Jetmenu", 7000),
                entry("price_outro", null, "Jetmenu", 9000)));

        StripeCatalogSync.SyncResult result = sync.sync();

        // Escolher qualquer um dos dois seria palpite, e o palpite errado cobra o valor errado.
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        then(planRepository).should(never()).save(any(Plan.class));
    }

    @Test
    @DisplayName("deve ignorar price anual, porque plans.price_monthly é mensal")
    void shouldIgnoreNonMonthlyPrices() throws Exception {
        given(gateway.listRecurringPrices()).willReturn(List.of(
                new StripeCatalogEntry("price_ano", "prod_1", "basico", "Jetmenu", 70000, "brl", "year")));

        StripeCatalogSync.SyncResult result = sync.sync();

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        then(planRepository).should(never()).save(any(Plan.class));
    }

    @Test
    @DisplayName("deve ignorar price em moeda estrangeira")
    void shouldIgnoreForeignCurrencyPrices() throws Exception {
        given(gateway.listRecurringPrices()).willReturn(List.of(
                new StripeCatalogEntry("price_usd", "prod_1", "basico", "Jetmenu", 1200, "usd", "month")));

        assertThat(sync.sync().created()).isZero();
        then(planRepository).should(never()).save(any(Plan.class));
    }

    @Test
    @DisplayName("não deve derrubar a subida quando a Stripe está fora do ar")
    void shouldSurviveAStripeOutage() throws Exception {
        willThrow(new ApiConnectionException("sem rede")).given(gateway).listRecurringPrices();

        assertThatCode(() -> assertThat(sync.sync().ran()).isFalse()).doesNotThrowAnyException();
        then(planRepository).should(never()).save(any(Plan.class));
    }
}
