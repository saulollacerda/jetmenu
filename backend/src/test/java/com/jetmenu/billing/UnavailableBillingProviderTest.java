package com.jetmenu.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UnavailableBillingProvider")
class UnavailableBillingProviderTest {

    private final UnavailableBillingProvider provider = new UnavailableBillingProvider();

    @Test
    @DisplayName("deve falhar explicitamente com mensagem em pt-BR enquanto não há provedor de pagamento")
    void shouldFailExplicitlyWithPtBrMessage() {
        assertThatThrownBy(() -> provider.createCheckout(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BillingProviderUnavailableException.class)
                .hasMessageContaining("indisponível");
    }

    @Test
    @DisplayName("deve ser um BillingProvider — o próximo provedor implementa a mesma interface")
    void shouldImplementTheBillingProviderSeam() {
        assertThat(provider).isInstanceOf(BillingProvider.class);
    }
}
