package com.jetmenu.ingredient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IngredientNameNormalizer")
class IngredientNameNormalizerTest {

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource(textBlock = """
            Creme de Chocolate            , creme de chocolate
            CREME DE CHOCOLATE            , creme de chocolate
            Creme  de   Chocolate         , creme de chocolate
            '  Creme de Chocolate  '      , creme de chocolate
            Açaí                          , acai
            BANANA                        , banana
            Pão de Açúcar                 , pao de acucar
            Crème Brûlée                  , creme brulee
            Bacon-Cheddar                 , bacon-cheddar
            """)
    @DisplayName("normaliza nome: lowercase + trim + sem acento + espaços colapsados")
    void shouldNormalize(String input, String expected) {
        assertThat(IngredientNameNormalizer.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("retorna string vazia para entrada nula ou em branco")
    void shouldReturnEmptyForBlankInput(String input) {
        assertThat(IngredientNameNormalizer.normalize(input)).isEmpty();
    }
}
