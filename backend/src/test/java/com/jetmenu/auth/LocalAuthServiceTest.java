package com.jetmenu.auth;

import com.jetmenu.identity.Identity;
import com.jetmenu.identity.IdentityRepository;
import com.jetmenu.merchant.DuplicateMerchantException;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.billing.SubscriptionService;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.security.LocalTokenIssuer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalAuthService")
class LocalAuthServiceTest {

    private static final String PROVIDER = "local";

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LocalTokenIssuer tokenIssuer;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private LocalAuthService service;

    private DevRegisterRequest registerRequest() {
        return DevRegisterRequest.builder()
                .merchantName("Restaurante Dev")
                .cnpj("12345678000195")
                .email("dev@example.com")
                .phone("11999990000")
                .password("senha123")
                .build();
    }

    @Test
    @DisplayName("register cria Merchant (senha bcrypt) + Identity e emite token")
    void register_shouldCreateMerchantAndIdentityAndIssueToken() {
        UUID merchantId = UUID.randomUUID();
        given(merchantRepository.existsByEmail("dev@example.com")).willReturn(false);
        given(merchantRepository.existsByCnpj("12345678000195")).willReturn(false);
        given(passwordEncoder.encode("senha123")).willReturn("hashed");
        given(merchantRepository.save(any(Merchant.class))).willAnswer(inv -> {
            Merchant m = inv.getArgument(0);
            m.setId(merchantId);
            return m;
        });
        given(tokenIssuer.issue(merchantId.toString(), "dev@example.com")).willReturn("jwt-token");

        DevAuthResponse result = service.register(registerRequest());

        assertThat(result.getAccessToken()).isEqualTo("jwt-token");
        assertThat(result.getMerchant().getId()).isEqualTo(merchantId);
        assertThat(result.getMerchant().getEmail()).isEqualTo("dev@example.com");

        verify(merchantRepository).save(any(Merchant.class));
        verify(identityRepository).save(any(Identity.class));
        // Sem essa linha o lojista paga na Stripe e o webhook responde 404: a ativação
        // atualiza uma assinatura existente, não cria uma.
        verify(subscriptionService).createPendingSubscription(merchantId);
    }

    @Test
    @DisplayName("register com email duplicado lança DuplicateMerchantException")
    void register_shouldRejectDuplicateEmail() {
        given(merchantRepository.existsByEmail("dev@example.com")).willReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest()))
                .isInstanceOf(DuplicateMerchantException.class);

        verify(merchantRepository, never()).save(any());
    }

    @Test
    @DisplayName("register com CNPJ duplicado lança DuplicateMerchantException")
    void register_shouldRejectDuplicateCnpj() {
        given(merchantRepository.existsByEmail("dev@example.com")).willReturn(false);
        given(merchantRepository.existsByCnpj("12345678000195")).willReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest()))
                .isInstanceOf(DuplicateMerchantException.class);

        verify(merchantRepository, never()).save(any());
    }

    @Test
    @DisplayName("login válido confere a senha e emite token")
    void login_shouldVerifyPasswordAndIssueToken() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = Merchant.builder()
                .id(merchantId)
                .email("dev@example.com")
                .password("hashed")
                .build();
        given(merchantRepository.findByEmail("dev@example.com")).willReturn(Optional.of(merchant));
        given(passwordEncoder.matches("senha123", "hashed")).willReturn(true);
        given(identityRepository.findByProviderAndProviderUserId(PROVIDER, merchantId.toString()))
                .willReturn(Optional.of(Identity.builder().merchantId(merchantId).build()));
        given(tokenIssuer.issue(merchantId.toString(), "dev@example.com")).willReturn("jwt-token");

        DevAuthResponse result = service.login(new DevLoginRequest("dev@example.com", "senha123"));

        assertThat(result.getAccessToken()).isEqualTo("jwt-token");
        assertThat(result.getMerchant().getId()).isEqualTo(merchantId);
    }

    @Test
    @DisplayName("login com senha errada lança InvalidCredentialsException")
    void login_shouldRejectWrongPassword() {
        Merchant merchant = Merchant.builder()
                .id(UUID.randomUUID())
                .email("dev@example.com")
                .password("hashed")
                .build();
        given(merchantRepository.findByEmail("dev@example.com")).willReturn(Optional.of(merchant));
        given(passwordEncoder.matches("errada", "hashed")).willReturn(false);

        assertThatThrownBy(() -> service.login(new DevLoginRequest("dev@example.com", "errada")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login com email inexistente lança InvalidCredentialsException")
    void login_shouldRejectUnknownEmail() {
        given(merchantRepository.findByEmail("naoexiste@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new DevLoginRequest("naoexiste@example.com", "x")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tokenIssuer, never()).issue(anyString(), anyString());
    }
}
