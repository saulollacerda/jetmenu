package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodTokenResponse;
import com.jetmenu.integration.ifood.dto.IfoodUserCodeResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("IfoodTokenService")
class IfoodTokenServiceTest {

    @Mock private IfoodAuthClient authClient;
    @Mock private IfoodAppTokenRepository tokenRepository;
    @Mock private MerchantRepository merchantRepository;

    @InjectMocks private IfoodTokenService service;

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        merchant = Merchant.builder().id(merchantId).merchantName("Pizzaria Central").build();
        service.setClientId("client-id");
        service.setClientSecret("client-secret");
    }

    @Test
    @DisplayName("startAuthorization chama requestUserCode e retorna resposta do iFood")
    void startAuthorization_shouldCallClientAndReturnResponse() {
        IfoodUserCodeResponse apiResponse = new IfoodUserCodeResponse();
        apiResponse.setUserCode("HJLX-LPSQ");
        apiResponse.setAuthorizationCodeVerifier("verifier123");
        apiResponse.setVerificationUrl("https://portal.ifood.com.br/apps/code");
        apiResponse.setExpiresIn(600);
        given(authClient.requestUserCode("client-id")).willReturn(apiResponse);

        IfoodUserCodeResponse result = service.startAuthorization(merchantId);

        assertThat(result.getUserCode()).isEqualTo("HJLX-LPSQ");
        assertThat(result.getExpiresIn()).isEqualTo(600);
        verify(authClient).requestUserCode("client-id");
    }

    @Test
    @DisplayName("connect troca código, persiste token e atualiza merchant com o ifoodMerchantId extraído do merchant_scope do token")
    void connect_shouldExchangeCodePersistTokenAndUpdateMerchant() {
        IfoodTokenResponse tokenResponse = tokenResponse("ifood-id-1");
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(authClient.requestUserCode("client-id")).willReturn(userCodeResponse());
        given(authClient.exchangeCode(any(), any(), any(), any())).willReturn(tokenResponse);
        given(merchantRepository.findAllByIfoodMerchantIdIsNotNull()).willReturn(List.of());
        given(merchantRepository.save(any())).willReturn(merchant);

        service.startAuthorization(merchantId);
        service.connect(merchantId, "auth-code");

        ArgumentCaptor<IfoodAppToken> tokenCaptor = ArgumentCaptor.forClass(IfoodAppToken.class);
        verify(tokenRepository).deleteAll();
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getAccessToken()).isEqualTo(tokenResponse.getAccessToken());
        assertThat(tokenCaptor.getValue().getRefreshToken()).isEqualTo(tokenResponse.getRefreshToken());

        ArgumentCaptor<Merchant> merchantCaptor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(merchantCaptor.capture());
        assertThat(merchantCaptor.getValue().getIfoodAuthorizedAt()).isNotNull();
        assertThat(merchantCaptor.getValue().getIfoodMerchantId()).isEqualTo("ifood-id-1");
    }

    @Test
    @DisplayName("connect deriva refreshExpiresAt do claim exp do JWT do refreshToken, ignorando refreshTokenExpiresIn (que a API do iFood retorna zerado)")
    void connect_shouldDeriveRefreshExpiresAtFromRefreshTokenJwtExp() {
        long expEpochSeconds = Instant.now().plusSeconds(604800).getEpochSecond();
        IfoodTokenResponse tokenResponse = tokenResponse("ifood-id-1");
        tokenResponse.setRefreshToken(fakeRefreshToken(expEpochSeconds));
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(authClient.requestUserCode("client-id")).willReturn(userCodeResponse());
        given(authClient.exchangeCode(any(), any(), any(), any())).willReturn(tokenResponse);
        given(merchantRepository.findAllByIfoodMerchantIdIsNotNull()).willReturn(List.of());
        given(merchantRepository.save(any())).willReturn(merchant);

        service.startAuthorization(merchantId);
        service.connect(merchantId, "auth-code");

        ArgumentCaptor<IfoodAppToken> tokenCaptor = ArgumentCaptor.forClass(IfoodAppToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        LocalDateTime expected = LocalDateTime.ofInstant(Instant.ofEpochSecond(expEpochSeconds), ZoneId.systemDefault());
        assertThat(tokenCaptor.getValue().getRefreshExpiresAt()).isCloseTo(expected, within(2, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("connect lança exceção e não persiste nada quando o merchant_scope do token não traz nenhum merchant novo")
    void connect_shouldThrowAndNotPersistWhenNoNewMerchantInScope() {
        IfoodTokenResponse tokenResponse = tokenResponse("already-known-id");
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(authClient.requestUserCode("client-id")).willReturn(userCodeResponse());
        given(authClient.exchangeCode(any(), any(), any(), any())).willReturn(tokenResponse);
        Merchant alreadyConnected = Merchant.builder().build();
        alreadyConnected.setIfoodMerchantId("already-known-id");
        given(merchantRepository.findAllByIfoodMerchantIdIsNotNull()).willReturn(List.of(alreadyConnected));

        service.startAuthorization(merchantId);

        assertThatThrownBy(() -> service.connect(merchantId, "auth-code"))
                .isInstanceOf(IfoodMerchantMatchException.class);
        verify(merchantRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("connect lança exceção e não persiste nada quando o merchant_scope do token traz mais de um merchant novo")
    void connect_shouldThrowAndNotPersistWhenMultipleNewMerchantsInScope() {
        IfoodTokenResponse tokenResponse = tokenResponse("new-id-1", "new-id-2");
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(authClient.requestUserCode("client-id")).willReturn(userCodeResponse());
        given(authClient.exchangeCode(any(), any(), any(), any())).willReturn(tokenResponse);
        given(merchantRepository.findAllByIfoodMerchantIdIsNotNull()).willReturn(List.of());

        service.startAuthorization(merchantId);

        assertThatThrownBy(() -> service.connect(merchantId, "auth-code"))
                .isInstanceOf(IfoodMerchantMatchException.class);
        verify(merchantRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAccessToken retorna token válido sem renovar")
    void getAccessToken_shouldReturnValidTokenWithoutRefresh() {
        IfoodAppToken token = IfoodAppToken.builder()
                .accessToken("valid.jwt")
                .refreshToken("refresh.jwt")
                .expiresAt(LocalDateTime.now().plusHours(2))
                .refreshExpiresAt(LocalDateTime.now().plusDays(6))
                .updatedAt(LocalDateTime.now())
                .build();
        given(tokenRepository.findTopByOrderByUpdatedAtDesc()).willReturn(Optional.of(token));

        String result = service.getAccessToken();

        assertThat(result).isEqualTo("valid.jwt");
    }

    @Test
    @DisplayName("getAccessToken renova via refreshToken quando próximo da expiração")
    void getAccessToken_shouldRefreshWhenNearExpiry() {
        IfoodAppToken token = IfoodAppToken.builder()
                .accessToken("old.jwt")
                .refreshToken("refresh.jwt")
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .refreshExpiresAt(LocalDateTime.now().plusDays(6))
                .updatedAt(LocalDateTime.now())
                .build();
        IfoodTokenResponse refreshed = tokenResponse("ifood-id-1");
        refreshed.setAccessToken("new.jwt");
        given(tokenRepository.findTopByOrderByUpdatedAtDesc()).willReturn(Optional.of(token));
        given(authClient.refreshToken("client-id", "client-secret", "refresh.jwt")).willReturn(refreshed);
        given(tokenRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = service.getAccessToken();

        assertThat(result).isEqualTo("new.jwt");
    }

    @Test
    @DisplayName("getAccessToken lança exceção quando refreshToken expirou")
    void getAccessToken_shouldThrowWhenRefreshTokenExpired() {
        IfoodAppToken token = IfoodAppToken.builder()
                .accessToken("old.jwt")
                .refreshToken("refresh.jwt")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .refreshExpiresAt(LocalDateTime.now().minusHours(1))
                .updatedAt(LocalDateTime.now())
                .build();
        given(tokenRepository.findTopByOrderByUpdatedAtDesc()).willReturn(Optional.of(token));

        assertThatThrownBy(() -> service.getAccessToken())
                .isInstanceOf(IfoodReauthorizationRequiredException.class);
    }

    @Test
    @DisplayName("revoke limpa campos do merchant, desativa a sincronia e remove token se for o último")
    void revoke_shouldClearMerchantAndDeleteTokenWhenLastMerchant() {
        merchant.setIfoodMerchantId("ifood-merchant-id");
        merchant.setIfoodAuthorizedAt(LocalDateTime.now());
        merchant.setIfoodOrderSyncEnabled(true);
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(merchantRepository.countByIfoodMerchantIdIsNotNull()).willReturn(0L);

        service.revoke(merchantId);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(captor.capture());
        assertThat(captor.getValue().getIfoodMerchantId()).isNull();
        assertThat(captor.getValue().getIfoodAuthorizedAt()).isNull();
        assertThat(captor.getValue().isIfoodOrderSyncEnabled()).isFalse();
        verify(tokenRepository).deleteAll();
    }

    @Test
    @DisplayName("isConnected retorna true quando o merchant possui ifoodMerchantId")
    void isConnected_shouldReturnTrueWhenMerchantHasIfoodMerchantId() {
        merchant.setIfoodMerchantId("ifood-merchant-id");
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

        assertThat(service.isConnected(merchantId)).isTrue();
    }

    @Test
    @DisplayName("isConnected retorna false quando o merchant não possui ifoodMerchantId")
    void isConnected_shouldReturnFalseWhenMerchantHasNoIfoodMerchantId() {
        merchant.setIfoodMerchantId(null);
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));

        assertThat(service.isConnected(merchantId)).isFalse();
    }

    @Test
    @DisplayName("isConnected retorna false quando o merchant não existe")
    void isConnected_shouldReturnFalseWhenMerchantNotFound() {
        given(merchantRepository.findById(merchantId)).willReturn(Optional.empty());

        assertThat(service.isConnected(merchantId)).isFalse();
    }

    private IfoodUserCodeResponse userCodeResponse() {
        IfoodUserCodeResponse r = new IfoodUserCodeResponse();
        r.setUserCode("HJLX-LPSQ");
        r.setAuthorizationCodeVerifier("verifier123");
        r.setVerificationUrl("https://portal.ifood.com.br/apps/code");
        r.setExpiresIn(600);
        return r;
    }

    private IfoodTokenResponse tokenResponse(String... merchantScopeIds) {
        IfoodTokenResponse r = new IfoodTokenResponse();
        r.setAccessToken(fakeAccessToken(merchantScopeIds));
        r.setRefreshToken(fakeRefreshToken(Instant.now().plusSeconds(604800).getEpochSecond()));
        r.setExpiresIn(10800);
        return r;
    }

    private String fakeAccessToken(String... merchantIds) {
        String scopeJson = Arrays.stream(merchantIds)
                .map(id -> "\"" + id + ":order\"")
                .collect(Collectors.joining(",", "[", "]"));
        return fakeJwt("{\"merchant_scope\":" + scopeJson + "}");
    }

    private String fakeRefreshToken(long expEpochSeconds) {
        return fakeJwt("{\"exp\":" + expEpochSeconds + "}");
    }

    private String fakeJwt(String payloadJson) {
        String header = base64Url("{\"alg\":\"none\"}");
        return header + "." + base64Url(payloadJson) + ".sig";
    }

    private String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
