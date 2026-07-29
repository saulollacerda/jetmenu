package com.jetmenu.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CnpjValidator")
class CnpjValidatorTest {

    private final CnpjValidator validator = new CnpjValidator();

    @Test
    @DisplayName("deve aceitar CNPJ válido com apenas dígitos")
    void shouldAcceptValidCnpjDigitsOnly() {
        assertThat(validator.isValid("12345678000195", null)).isTrue();
    }

    @Test
    @DisplayName("deve aceitar CNPJ válido com máscara")
    void shouldAcceptValidCnpjWithMask() {
        assertThat(validator.isValid("12.345.678/0001-95", null)).isTrue();
    }

    @Test
    @DisplayName("deve rejeitar CNPJ com dígitos verificadores inválidos")
    void shouldRejectInvalidCheckDigits() {
        assertThat(validator.isValid("12345678000199", null)).isFalse();
    }

    @Test
    @DisplayName("deve rejeitar CNPJ com tamanho inválido")
    void shouldRejectInvalidLength() {
        assertThat(validator.isValid("123456", null)).isFalse();
    }

    @Test
    @DisplayName("deve rejeitar CNPJ com todos os dígitos iguais")
    void shouldRejectRepeatedDigits() {
        assertThat(validator.isValid("00000000000000", null)).isFalse();
    }
}

