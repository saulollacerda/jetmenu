package com.jetmenu.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlanSlug")
class PlanSlugTest {

    @Test
    @DisplayName("deve gerar slug sem acento, em minúsculas e com hífens")
    void shouldSlugifyPlanName() {
        assertThat(PlanSlug.of("Básico")).isEqualTo("basico");
        assertThat(PlanSlug.of("Plano Avançado")).isEqualTo("plano-avancado");
        assertThat(PlanSlug.of("  Básico  ")).isEqualTo("basico");
        assertThat(PlanSlug.of("Pró & Cia.")).isEqualTo("pro-cia");
    }

    @Test
    @DisplayName("deve tratar nome nulo como slug vazio em vez de estourar")
    void shouldTreatNullNameAsEmptySlug() {
        assertThat(PlanSlug.of(null)).isEmpty();
    }

    @Test
    @DisplayName("deve converter o slug no sufixo da variável de ambiente")
    void shouldConvertSlugToEnvSuffix() {
        assertThat(PlanSlug.toEnvSuffix("basico")).isEqualTo("BASICO");
        assertThat(PlanSlug.toEnvSuffix("plano-avancado")).isEqualTo("PLANO_AVANCADO");
    }
}
