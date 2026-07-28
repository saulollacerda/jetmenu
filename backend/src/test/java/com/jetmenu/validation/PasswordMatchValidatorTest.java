package com.jetmenu.validation;

import com.jetmenu.merchant.MerchantRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordMatchValidator")
class PasswordMatchValidatorTest {

    private final PasswordMatchValidator validator = new PasswordMatchValidator();

    @Test
    @DisplayName("deve aceitar quando senha e confirmação são iguais")
    void shouldAcceptWhenPasswordsMatch() {
        MerchantRequest request = MerchantRequest.builder()
                .password("senha123")
                .confirmPassword("senha123")
                .build();

        assertThat(validator.isValid(request, null)).isTrue();
    }

    @Test
    @DisplayName("deve rejeitar quando senha e confirmação são diferentes")
    void shouldRejectWhenPasswordsDoNotMatch() {
        MerchantRequest request = MerchantRequest.builder()
                .password("senha123")
                .confirmPassword("outraSenha")
                .build();

        assertThat(validator.isValid(request, null)).isFalse();
    }

    @Test
    @DisplayName("deve ignorar validação quando senha está ausente")
    void shouldIgnoreWhenPasswordIsMissing() {
        MerchantRequest request = MerchantRequest.builder()
                .confirmPassword("senha123")
                .build();

        assertThat(validator.isValid(request, null)).isTrue();
    }
}

