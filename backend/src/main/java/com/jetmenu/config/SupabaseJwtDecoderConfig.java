package com.jetmenu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Production JWT validation backed by Supabase's JWKS (asymmetric).
 * Discovers the JWK set from the issuer and validates issuer, timestamps and audience.
 */
@Configuration
@Profile("prod")
public class SupabaseJwtDecoderConfig {

    @Value("${supabase.issuer-uri}")
    private String issuerUri;

    @Value("${supabase.audience:authenticated}")
    private String audience;

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                withIssuer, new AudienceValidator(audience)));
        return decoder;
    }
}
