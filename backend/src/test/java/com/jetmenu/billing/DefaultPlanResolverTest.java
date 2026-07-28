package com.jetmenu.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultPlanResolver")
class DefaultPlanResolverTest {

    private static final String BASIC_PLAN_NAME = "Básico";

    @Mock
    private PlanRepository planRepository;

    private DefaultPlanResolver resolverWithName(String defaultPlanName) {
        return new DefaultPlanResolver(planRepository, defaultPlanName);
    }

    private Plan basicPlan() {
        return Plan.builder()
                .id(UUID.randomUUID())
                .name(BASIC_PLAN_NAME)
                .minRevenue(BigDecimal.ZERO)
                .priceMonthly(new BigDecimal("50.00"))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("deve retornar null sem consultar o banco quando a propriedade não está definida (produção)")
    void shouldReturnNullWithoutQueryingWhenPropertyIsNull() {
        Plan resolved = resolverWithName(null).resolve();

        assertThat(resolved).isNull();
        then(planRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("deve retornar null sem consultar o banco quando a propriedade está em branco")
    void shouldReturnNullWithoutQueryingWhenPropertyIsBlank() {
        Plan resolved = resolverWithName("   ").resolve();

        assertThat(resolved).isNull();
        then(planRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("deve retornar o plano configurado quando ele existe")
    void shouldReturnConfiguredPlan() {
        Plan basicPlan = basicPlan();
        given(planRepository.findByName(BASIC_PLAN_NAME)).willReturn(Optional.of(basicPlan));

        Plan resolved = resolverWithName(BASIC_PLAN_NAME).resolve();

        assertThat(resolved).isSameAs(basicPlan);
    }

    @Test
    @DisplayName("deve retornar null quando o plano configurado não existe no banco")
    void shouldReturnNullWhenConfiguredPlanIsMissing() {
        given(planRepository.findByName(BASIC_PLAN_NAME)).willReturn(Optional.empty());

        Plan resolved = resolverWithName(BASIC_PLAN_NAME).resolve();

        assertThat(resolved).isNull();
    }
}
