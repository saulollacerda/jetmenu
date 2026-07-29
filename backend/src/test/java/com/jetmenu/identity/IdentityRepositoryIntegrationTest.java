package com.jetmenu.identity;

import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IdentityRepository — integração com Postgres")
class IdentityRepositoryIntegrationTest extends IntegrationTestBase {

    private static final String PROVIDER = "supabase";

    @Autowired
    private IdentityRepository identityRepository;

    @Test
    @DisplayName("findByProviderAndProviderUserId deve retornar a identity persistida")
    void findByProviderAndProviderUserId_shouldReturnPersistedIdentity() {
        Merchant merchant = createMerchant();
        String providerUserId = UUID.randomUUID().toString();
        identityRepository.save(Identity.builder()
                .merchantId(merchant.getId())
                .provider(PROVIDER)
                .providerUserId(providerUserId)
                .createdAt(LocalDateTime.now())
                .build());

        Optional<Identity> found = identityRepository.findByProviderAndProviderUserId(PROVIDER, providerUserId);

        assertThat(found).isPresent();
        assertThat(found.get().getMerchantId()).isEqualTo(merchant.getId());
    }

    @Test
    @DisplayName("deve violar a unique constraint (provider, provider_user_id) em duplicado")
    void save_shouldRejectDuplicateProviderAndProviderUserId() {
        Merchant merchant = createMerchant();
        String providerUserId = UUID.randomUUID().toString();
        identityRepository.saveAndFlush(Identity.builder()
                .merchantId(merchant.getId())
                .provider(PROVIDER)
                .providerUserId(providerUserId)
                .createdAt(LocalDateTime.now())
                .build());

        assertThatThrownBy(() -> identityRepository.saveAndFlush(Identity.builder()
                .merchantId(merchant.getId())
                .provider(PROVIDER)
                .providerUserId(providerUserId)
                .createdAt(LocalDateTime.now())
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByProviderAndProviderUserId deve retornar vazio quando não existe")
    void findByProviderAndProviderUserId_shouldReturnEmptyWhenMissing() {
        Optional<Identity> found = identityRepository.findByProviderAndProviderUserId(PROVIDER, "nao-existe");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByProviderAndProviderUserId deve refletir a presença da identity")
    void existsByProviderAndProviderUserId_shouldReflectPresence() {
        Merchant merchant = createMerchant();
        String providerUserId = UUID.randomUUID().toString();

        assertThat(identityRepository.existsByProviderAndProviderUserId(PROVIDER, providerUserId)).isFalse();

        identityRepository.save(Identity.builder()
                .merchantId(merchant.getId())
                .provider(PROVIDER)
                .providerUserId(providerUserId)
                .createdAt(LocalDateTime.now())
                .build());

        assertThat(identityRepository.existsByProviderAndProviderUserId(PROVIDER, providerUserId)).isTrue();
    }
}
