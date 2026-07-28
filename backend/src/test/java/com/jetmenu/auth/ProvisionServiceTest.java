package com.jetmenu.auth;

import com.jetmenu.identity.Identity;
import com.jetmenu.identity.IdentityRepository;
import com.jetmenu.merchant.DuplicateMerchantException;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.merchant.MerchantResponse;
import com.jetmenu.merchant.MerchantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProvisionService")
class ProvisionServiceTest {

    private static final String PROVIDER = "supabase";

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private IdentityRepository identityRepository;

    @InjectMocks
    private ProvisionService provisionService;

    private ProvisionRequest request() {
        return ProvisionRequest.builder()
                .merchantName("Restaurante Novo")
                .cnpj("12345678000195")
                .email("novo@example.com")
                .phone("11999990000")
                .build();
    }

    @Test
    @DisplayName("usuário novo deve criar Merchant (sem senha) + Identity e retornar MerchantResponse")
    void provision_shouldCreateMerchantAndIdentity() {
        String uuid = UUID.randomUUID().toString();
        UUID merchantId = UUID.randomUUID();
        given(identityRepository.findByProviderAndProviderUserId(PROVIDER, uuid)).willReturn(Optional.empty());
        given(merchantRepository.existsByEmail("novo@example.com")).willReturn(false);
        given(merchantRepository.existsByCnpj("12345678000195")).willReturn(false);
        given(merchantRepository.save(any(Merchant.class))).willAnswer(inv -> {
            Merchant m = inv.getArgument(0);
            m.setId(merchantId);
            return m;
        });

        MerchantResponse result = provisionService.provision(uuid, request());

        assertThat(result.getId()).isEqualTo(merchantId);
        assertThat(result.getEmail()).isEqualTo("novo@example.com");
        assertThat(result.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
        verify(merchantRepository).save(any(Merchant.class));
        verify(identityRepository).save(any(Identity.class));
    }

    @Test
    @DisplayName("usuário já provisionado deve ser idempotente (não salva de novo)")
    void provision_shouldBeIdempotent() {
        String uuid = UUID.randomUUID().toString();
        UUID merchantId = UUID.randomUUID();
        given(identityRepository.findByProviderAndProviderUserId(PROVIDER, uuid))
                .willReturn(Optional.of(Identity.builder().merchantId(merchantId).build()));
        given(merchantRepository.findById(merchantId))
                .willReturn(Optional.of(Merchant.builder().id(merchantId).email("existing@example.com").build()));

        MerchantResponse result = provisionService.provision(uuid, request());

        assertThat(result.getId()).isEqualTo(merchantId);
        verify(merchantRepository, never()).save(any(Merchant.class));
        verify(identityRepository, never()).save(any(Identity.class));
    }

    @Test
    @DisplayName("deve lançar DuplicateMerchantException quando email já existe")
    void provision_shouldRejectDuplicateEmail() {
        String uuid = UUID.randomUUID().toString();
        given(identityRepository.findByProviderAndProviderUserId(PROVIDER, uuid)).willReturn(Optional.empty());
        given(merchantRepository.existsByEmail("novo@example.com")).willReturn(true);

        assertThatThrownBy(() -> provisionService.provision(uuid, request()))
                .isInstanceOf(DuplicateMerchantException.class);

        verify(merchantRepository, never()).save(any(Merchant.class));
    }
}
