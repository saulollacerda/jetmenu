package com.jetmenu.merchant;

import com.jetmenu.common.ForbiddenException;
import com.jetmenu.integration.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MerchantService — integração com Postgres")
class MerchantServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private MerchantService merchantService;

    private static final AtomicLong CNPJ_SEQ = new AtomicLong(20_000_000_000_000L);

    @Test
    @DisplayName("create deve persistir merchant com password encoded")
    void create_shouldPersistMerchantWithEncodedPassword() {
        MerchantRequest request = newRequest("Restaurante Teste");

        MerchantResponse response = merchantService.create(request);

        Merchant persisted = merchantRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getMerchantName()).isEqualTo("Restaurante Teste");
        // Senha não é texto puro
        assertThat(persisted.getPassword()).isNotEqualTo("password123");
        assertThat(persisted.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
    }

    @Test
    @DisplayName("create deve rejeitar email duplicado")
    void create_shouldRejectDuplicateEmail() {
        MerchantRequest first = newRequest("A");
        merchantService.create(first);

        MerchantRequest dup = newRequest("B");
        dup.setEmail(first.getEmail()); // mesmo email

        assertThatThrownBy(() -> merchantService.create(dup))
                .isInstanceOf(DuplicateMerchantException.class);
    }

    @Test
    @DisplayName("create deve rejeitar CNPJ duplicado")
    void create_shouldRejectDuplicateCnpj() {
        MerchantRequest first = newRequest("A");
        merchantService.create(first);

        MerchantRequest dup = newRequest("B");
        dup.setCnpj(first.getCnpj()); // mesmo CNPJ

        assertThatThrownBy(() -> merchantService.create(dup))
                .isInstanceOf(DuplicateMerchantException.class);
    }

    @Test
    @DisplayName("updateAnotaAIKey deve atualizar a API key do merchant autenticado")
    void updateAnotaAIKey_shouldUpdateKey() {
        Merchant merchant = createMerchantAndAuthenticate();

        merchantService.updateAnotaAIKey(merchant.getId(), AnotaAIKeyRequest.builder()
                .anotaAiApiKey("new-key-xyz").build());

        Merchant reloaded = merchantRepository.findById(merchant.getId()).orElseThrow();
        assertThat(reloaded.getAnotaAiApiKey()).isEqualTo("new-key-xyz");
    }

    @Test
    @DisplayName("findById deve falhar quando o id é diferente do merchant autenticado")
    void findById_shouldBlockCrossMerchantAccess() {
        Merchant authenticated = createMerchantAndAuthenticate();
        Merchant other = createMerchant("Outro");

        assertThatThrownBy(() -> merchantService.findById(authenticated.getId(), other.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("delete deve remover o merchant autenticado")
    void delete_shouldRemove() {
        Merchant merchant = createMerchantAndAuthenticate();

        merchantService.delete(merchant.getId(), merchant.getId());

        assertThat(merchantRepository.findById(merchant.getId())).isEmpty();
    }

    private MerchantRequest newRequest(String name) {
        long cnpjN = CNPJ_SEQ.incrementAndGet();
        return MerchantRequest.builder()
                .merchantName(name)
                .cnpj(String.valueOf(cnpjN))
                .email(UUID.randomUUID() + "@example.com")
                .password("password123")
                .confirmPassword("password123")
                .phone("11999990000")
                .build();
    }
}
