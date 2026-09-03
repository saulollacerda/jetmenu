package com.jetmenu.merchant;

import com.jetmenu.billing.SubscriptionService;
import com.jetmenu.integration.anotaai.AnotaAIWebhookTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * O segredo do webhook é gerado pelo JetMenu, nunca escolhido pelo lojista — e como a
 * Anota.AI não assina as entregas, ele é a única credencial do endpoint.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantService — configuração do webhook da Anota.AI")
class AnotaAIWebhookConfigServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SubscriptionService subscriptionService;

    private MerchantService service;

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        service = new MerchantService(merchantRepository, passwordEncoder, subscriptionService,
                new AnotaAIWebhookTokenService());
        merchantId = UUID.randomUUID();
        merchant = Merchant.builder().id(merchantId).build();
    }

    @Test
    @DisplayName("rotacionar gera um segredo novo e persiste no merchant")
    void shouldGenerateAndPersistSecret() {
        given(merchantRepository.findByIdWithAnotaAiIntegration(merchantId))
                .willReturn(Optional.of(merchant));

        AnotaAIWebhookConfigResponse response = service.rotateAnotaAiWebhookSecret(merchantId);

        assertThat(response.getWebhookSecret()).isNotBlank();
        assertThat(merchant.getAnotaAiWebhookSecret()).isEqualTo(response.getWebhookSecret());
        then(merchantRepository).should().save(merchant);
    }

    @Test
    @DisplayName("rotacionar troca o segredo anterior")
    void shouldReplacePreviousSecret() {
        merchant.setAnotaAiWebhookSecret("segredo-antigo");
        given(merchantRepository.findByIdWithAnotaAiIntegration(merchantId))
                .willReturn(Optional.of(merchant));

        AnotaAIWebhookConfigResponse response = service.rotateAnotaAiWebhookSecret(merchantId);

        assertThat(response.getWebhookSecret()).isNotEqualTo("segredo-antigo");
    }

    /**
     * Rotacionar o segredo <b>não</b> muda a URL: o lojista troca um campo no painel da
     * Anota.AI, não recadastra o endpoint inteiro. Foi por isso que o merchantId ficou cru no
     * path, em vez de um token opaco.
     */
    @Test
    @DisplayName("a URL do webhook não muda quando o segredo é rotacionado")
    void shouldKeepWebhookPathStableAcrossRotations() {
        given(merchantRepository.findByIdWithAnotaAiIntegration(merchantId))
                .willReturn(Optional.of(merchant));

        String first = service.rotateAnotaAiWebhookSecret(merchantId).getWebhookPath();
        String second = service.rotateAnotaAiWebhookSecret(merchantId).getWebhookPath();

        assertThat(first).isEqualTo(second)
                .isEqualTo("/api/webhooks/anotaai/" + merchantId);
    }

    @Test
    @DisplayName("consultar devolve o segredo atual para o lojista recopiar")
    void shouldReturnCurrentConfig() {
        merchant.setAnotaAiWebhookSecret("segredo-atual");
        merchant.setAnotaAiMerchantId("66c3ada81acfe90018b7ca85");
        given(merchantRepository.findByIdWithAnotaAiIntegration(merchantId))
                .willReturn(Optional.of(merchant));

        AnotaAIWebhookConfigResponse response = service.getAnotaAiWebhookConfig(merchantId);

        assertThat(response.getWebhookSecret()).isEqualTo("segredo-atual");
        assertThat(response.getAnotaAiMerchantId()).isEqualTo("66c3ada81acfe90018b7ca85");
        assertThat(response.getWebhookPath()).isEqualTo("/api/webhooks/anotaai/" + merchantId);
        then(merchantRepository).should(org.mockito.Mockito.never()).save(merchant);
    }

    @Test
    @DisplayName("merchant inexistente é 404")
    void shouldRejectUnknownMerchant() {
        given(merchantRepository.findByIdWithAnotaAiIntegration(merchantId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotateAnotaAiWebhookSecret(merchantId))
                .isInstanceOf(MerchantNotFoundException.class);
    }
}
