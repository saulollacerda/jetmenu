package com.jetmenu.security;

import com.jetmenu.config.LocalJwtDecoderConfig;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Mints JWTs signed with the same in-memory RSA key that {@link LocalJwtDecoderConfig}
 * validates against, so the dev auth endpoints can issue tokens the app accepts without
 * a live Supabase. Dev/test only — never active in prod (where Supabase is the issuer).
 */
@Component
@Profile({"dev", "test"})
public class LocalTokenIssuer {

    private static final long TOKEN_TTL_HOURS = 1;

    private final JwtEncoder encoder;

    public LocalTokenIssuer(LocalJwtDecoderConfig keys) {
        RSAKey jwk = new RSAKey.Builder(keys.getPublicKey())
                .privateKey(keys.getPrivateKey())
                .keyID(UUID.randomUUID().toString())
                .build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /** Issues a token whose {@code sub} is the provider user id (resolved to a merchant downstream). */
    public String issue(String subject, String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS))
                .claim("email", email)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
