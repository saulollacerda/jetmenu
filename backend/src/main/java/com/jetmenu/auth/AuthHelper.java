package com.jetmenu.auth;

import com.jetmenu.identity.Identity;
import com.jetmenu.identity.IdentityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Resolves the authenticated merchant from the provider user id placed in the
 * {@link Authentication} principal by {@code JwtAuthFilter}.
 * <p>
 * The {@code uuid -> merchantId} mapping is cached (Caffeine, 10 min TTL). The cache
 * key is the principal (the provider user id). The 403 thrown for a not-yet-provisioned
 * user is an exception, so it is never cached.
 * <p>
 * The identity provider name comes from {@code app.auth.provider} (default {@code supabase};
 * dev uses {@code local} so the dev auth endpoints' identities resolve here).
 */
@Component
public class AuthHelper {

    @Value("${app.auth.provider:supabase}")
    private String provider = "supabase";

    @Autowired
    private IdentityRepository identityRepository;

    @Cacheable(value = "merchantIdByProviderUser", key = "#auth.principal")
    public UUID getMerchantId(Authentication auth) {
        String uuid = (String) auth.getPrincipal();
        return identityRepository
                .findByProviderAndProviderUserId(provider, uuid)
                .map(Identity::getMerchantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Nenhum merchant encontrado para este usuário"));
    }
}
