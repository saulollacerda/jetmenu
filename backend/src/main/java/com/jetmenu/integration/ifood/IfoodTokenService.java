package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodTokenResponse;
import com.jetmenu.integration.ifood.dto.IfoodUserCodeResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class IfoodTokenService {

    private final IfoodAuthClient authClient;
    private final IfoodAppTokenRepository tokenRepository;
    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Setter
    @Value("${ifood.client-id:}")
    private String clientId;

    @Setter
    @Value("${ifood.client-secret:}")
    private String clientSecret;

    private final Map<UUID, String> pendingVerifiers = new ConcurrentHashMap<>();

    public IfoodTokenService(IfoodAuthClient authClient,
                             IfoodAppTokenRepository tokenRepository,
                             MerchantRepository merchantRepository) {
        this.authClient = authClient;
        this.tokenRepository = tokenRepository;
        this.merchantRepository = merchantRepository;
    }

    public IfoodUserCodeResponse startAuthorization(UUID merchantId) {
        IfoodUserCodeResponse response = authClient.requestUserCode(clientId);
        pendingVerifiers.put(merchantId, response.getAuthorizationCodeVerifier());
        return response;
    }

    @Transactional
    public void connect(UUID merchantId, String authorizationCode) {
        String verifier = pendingVerifiers.remove(merchantId);
        if (verifier == null) {
            throw new IllegalStateException("No pending authorization for merchant " + merchantId);
        }

        IfoodTokenResponse tokenResponse = authClient.exchangeCode(clientId, clientSecret, authorizationCode, verifier);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
        String ifoodMerchantId = resolveIfoodMerchantId(tokenResponse.getAccessToken());

        persistToken(tokenResponse);

        merchant.setIfoodMerchantId(ifoodMerchantId);
        merchant.setIfoodAuthorizedAt(LocalDateTime.now());
        merchantRepository.save(merchant);
    }

    private String resolveIfoodMerchantId(String accessToken) {
        Set<String> scopedMerchantIds = extractMerchantScopeIds(accessToken);
        Set<String> alreadyKnownIds = merchantRepository.findAllByIfoodMerchantIdIsNotNull().stream()
                .map(Merchant::getIfoodMerchantId)
                .collect(Collectors.toSet());

        List<String> newMerchantIds = scopedMerchantIds.stream()
                .filter(id -> !alreadyKnownIds.contains(id))
                .toList();

        if (newMerchantIds.size() != 1) {
            throw new IfoodMerchantMatchException(
                    "Expected exactly one new iFood merchant in token merchant_scope, found " + newMerchantIds.size());
        }

        return newMerchantIds.get(0);
    }

    private Set<String> extractMerchantScopeIds(String accessToken) {
        JsonNode payload = decodeJwtPayload(accessToken);
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode entry : payload.path("merchant_scope")) {
            String value = entry.asText();
            int colonIndex = value.indexOf(':');
            ids.add(colonIndex >= 0 ? value.substring(0, colonIndex) : value);
        }
        return ids;
    }

    // A resposta de /oauth/token do iFood não traz de forma confiável a validade do
    // refreshToken (o campo refreshTokenExpiresIn pode vir zerado) — o próprio JWT do
    // refreshToken carrega essa informação no claim "exp", que é a fonte de verdade.
    private LocalDateTime extractRefreshTokenExpiration(String refreshToken) {
        long expEpochSeconds = decodeJwtPayload(refreshToken).path("exp").asLong();
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(expEpochSeconds), ZoneId.systemDefault());
    }

    private JsonNode decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalStateException("Malformed iFood JWT");
        }

        byte[] payloadJson = Base64.getUrlDecoder().decode(padBase64(parts[1]));
        try {
            return objectMapper.readTree(new String(payloadJson, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse iFood JWT payload", e);
        }
    }

    private static String padBase64(String value) {
        int padding = (4 - value.length() % 4) % 4;
        return value + "=".repeat(padding);
    }

    public String getAccessToken() {
        IfoodAppToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new IfoodReauthorizationRequiredException());

        if (token.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IfoodReauthorizationRequiredException();
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) {
            token = doRefresh(token);
        }

        return token.getAccessToken();
    }

    @Transactional
    public String handleUnauthorized() {
        IfoodAppToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new IfoodReauthorizationRequiredException());

        if (token.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IfoodReauthorizationRequiredException();
        }

        return doRefresh(token).getAccessToken();
    }

    public boolean isConnected(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .map(merchant -> merchant.getIfoodMerchantId() != null)
                .orElse(false);
    }

    @Transactional
    public void revoke(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
        merchant.setIfoodMerchantId(null);
        merchant.setIfoodAuthorizedAt(null);
        merchant.setIfoodOrderSyncEnabled(false);
        merchantRepository.save(merchant);
        pendingVerifiers.remove(merchantId);

        if (merchantRepository.countByIfoodMerchantIdIsNotNull() == 0) {
            tokenRepository.deleteAll();
        }
    }

    private IfoodAppToken doRefresh(IfoodAppToken current) {
        IfoodTokenResponse refreshed = authClient.refreshToken(clientId, clientSecret, current.getRefreshToken());
        return persistToken(refreshed);
    }

    private IfoodAppToken persistToken(IfoodTokenResponse tokenResponse) {
        tokenRepository.deleteAll();
        IfoodAppToken token = IfoodAppToken.builder()
                .accessToken(tokenResponse.getAccessToken())
                .refreshToken(tokenResponse.getRefreshToken())
                .expiresAt(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()))
                .refreshExpiresAt(extractRefreshTokenExpiration(tokenResponse.getRefreshToken()))
                .updatedAt(LocalDateTime.now())
                .build();
        return tokenRepository.save(token);
    }
}
