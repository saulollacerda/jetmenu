package com.jetmenu.integration.anotaai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnotaAIWebhookTokenService")
class AnotaAIWebhookTokenServiceTest {

    private final AnotaAIWebhookTokenService service = new AnotaAIWebhookTokenService();

    @Test
    @DisplayName("gera segredo longo o bastante para não ser adivinhado")
    void shouldGenerateLongSecret() {
        String secret = service.generateSecret();

        // 32 bytes em base64url sem padding — o mesmo peso de um whsec_ da Stripe.
        assertThat(secret).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    @DisplayName("gera segredo seguro para URL e cópia manual")
    void shouldGenerateUrlSafeSecret() {
        assertThat(service.generateSecret()).matches("[A-Za-z0-9_-]+");
    }

    @Test
    @DisplayName("não repete segredo — quem gera é o JetMenu, não o lojista")
    void shouldNotRepeatSecrets() {
        Set<String> secrets = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            secrets.add(service.generateSecret());
        }
        assertThat(secrets).hasSize(200);
    }

    @Test
    @DisplayName("aceita apenas o segredo exato")
    void shouldMatchOnlyExactSecret() {
        String secret = service.generateSecret();

        assertThat(service.matches(secret, secret)).isTrue();
        assertThat(service.matches(secret, secret + "x")).isFalse();
        assertThat(service.matches(secret, secret.substring(0, secret.length() - 1))).isFalse();
        assertThat(service.matches(secret, secret.toUpperCase())).isFalse();
    }

    @Test
    @DisplayName("segredo nulo ou em branco nunca casa")
    void shouldNeverMatchBlankSecret() {
        assertThat(service.matches(null, "qualquer")).isFalse();
        assertThat(service.matches("  ", "  ")).isFalse();
        assertThat(service.matches("segredo", null)).isFalse();
        assertThat(service.matches("segredo", "")).isFalse();
    }
}
