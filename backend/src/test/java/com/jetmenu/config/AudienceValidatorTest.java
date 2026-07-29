package com.jetmenu.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AudienceValidator")
class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("authenticated");

    @Test
    @DisplayName("deve aceitar token cuja audience contém o valor esperado")
    void shouldAcceptWhenAudienceMatches() {
        Jwt jwt = jwtWithAudience(List.of("authenticated"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("deve rejeitar token com audience diferente")
    void shouldRejectWhenAudienceDiffers() {
        Jwt jwt = jwtWithAudience(List.of("outro-aud"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("deve rejeitar token sem audience")
    void shouldRejectWhenAudienceMissing() {
        Jwt jwt = jwtWithAudience(null);

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }

    private Jwt jwtWithAudience(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }
}
