package com.jetmenu.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev default-plan name carries an accent ("Básico") and must match the plan
 * row seeded by {@link BasicPlanSeeder} exactly, otherwise the lookup silently
 * misses and dev accounts fall back to PENDING. This guards the encoding of the
 * value as it is actually loaded from the properties file.
 */
@DisplayName("app.billing.default-plan-name (dev)")
class DevDefaultPlanPropertyTest {

    private static final String PROPERTY = "app.billing.default-plan-name";

    @Test
    @DisplayName("deve resolver exatamente o nome do plano semeado, sem corromper o acento")
    void shouldResolveSeededPlanName() throws IOException {
        List<PropertySource<?>> sources = new PropertiesPropertySourceLoader()
                .load("application-dev", new ClassPathResource("application-dev.properties"));

        Object value = sources.stream()
                .map(source -> source.getProperty(PROPERTY))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        assertThat(value).isEqualTo("Básico");
    }

    @Test
    @DisplayName("não deve ser definido no profile de produção, preservando o fluxo de cobrança")
    void shouldNotBeDefinedInProdProfile() throws IOException {
        List<PropertySource<?>> sources = new PropertiesPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.properties"));

        assertThat(sources).allSatisfy(source ->
                assertThat(source.getProperty(PROPERTY)).isNull());
    }
}
