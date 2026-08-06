package com.jetmenu.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("BasicPlanSeeder")
class BasicPlanSeederTest {

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private BasicPlanSeeder seeder;

    @Test
    @DisplayName("deve criar o plano Básico de R$ 50,00 com acesso a todas as funcionalidades quando ele não existe")
    void shouldSeedBasicPlanWhenMissing() {
        given(planRepository.existsByName("Básico")).willReturn(false);

        seeder.run();

        then(planRepository).should().save(argThat(plan ->
                "Básico".equals(plan.getName())
                        && "basico".equals(plan.getSlug())
                        && new BigDecimal("50.00").compareTo(plan.getPriceMonthly()) == 0
                        && BigDecimal.ZERO.compareTo(plan.getMinRevenue()) == 0
                        && plan.getMaxRevenue() == null
                        && Boolean.TRUE.equals(plan.getFeatures().get("allFeatures"))
                        && plan.isActive()
                        && plan.getCreatedAt() != null
        ));
    }

    @Test
    @DisplayName("não deve criar plano duplicado quando o plano Básico já existe")
    void shouldNotSeedWhenBasicPlanAlreadyExists() {
        given(planRepository.existsByName("Básico")).willReturn(true);

        seeder.run();

        then(planRepository).should(never()).save(any(Plan.class));
    }

    @Test
    @DisplayName("deve preencher o slug dos planos criados antes da coluna existir")
    void shouldBackfillSlugOfPlansThatPredateTheColumn() {
        Plan legacy = Plan.builder()
                .name("Plano Avançado")
                .minRevenue(BigDecimal.ZERO)
                .priceMonthly(new BigDecimal("90.00"))
                .build();
        given(planRepository.findBySlugIsNull()).willReturn(List.of(legacy));
        given(planRepository.existsByName("Básico")).willReturn(true);

        seeder.run();

        assertThat(legacy.getSlug()).isEqualTo("plano-avancado");
        then(planRepository).should().saveAll(List.of(legacy));
    }

    @Test
    @DisplayName("não deve gravar nada quando todos os planos já têm slug")
    void shouldNotWriteWhenEveryPlanAlreadyHasASlug() {
        given(planRepository.existsByName("Básico")).willReturn(true);

        seeder.run();

        then(planRepository).should(never()).saveAll(anyList());
    }
}
