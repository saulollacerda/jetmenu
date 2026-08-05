package com.jetmenu.integration.ifood.services;

import com.jetmenu.integration.ifood.IfoodCatalogClient;
import com.jetmenu.integration.ifood.IfoodDiagnosticsNotAllowedException;
import com.jetmenu.integration.ifood.IfoodResourceNotFoundException;
import com.jetmenu.integration.ifood.IfoodTokenService;
import com.jetmenu.integration.ifood.dto.IfoodCatalogResponse;
import com.jetmenu.integration.ifood.dto.IfoodRawResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("IfoodDiagnosticsService")
class IfoodDiagnosticsServiceTest {

    @Mock private IfoodCatalogClient catalogClient;
    @Mock private IfoodTokenService tokenService;
    @Mock private MerchantRepository merchantRepository;

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        merchant = Merchant.builder().id(merchantId).build();
        merchant.setIfoodMerchantId("ifood-m1");
        lenient().when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        lenient().when(tokenService.getAccessToken()).thenReturn("token-1");
    }

    private IfoodDiagnosticsService serviceWithWhitelist(String whitelist) {
        return new IfoodDiagnosticsService(catalogClient, tokenService, merchantRepository, whitelist);
    }

    private static IfoodCatalogResponse catalog(String catalogId, String... contexts) {
        IfoodCatalogResponse response = new IfoodCatalogResponse();
        response.setCatalogId(catalogId);
        response.setContext(List.of(contexts));
        return response;
    }

    // ── Whitelist ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("desligado por padrão: whitelist vazia não libera ninguém")
    void isEnabledFor_shouldBeFalseWhenWhitelistIsEmpty() {
        assertThat(serviceWithWhitelist("").isEnabledFor(merchantId)).isFalse();
    }

    @Test
    @DisplayName("libera quando o merchantId do JetMenu está na whitelist")
    void isEnabledFor_shouldAcceptJetMenuMerchantId() {
        assertThat(serviceWithWhitelist(merchantId.toString()).isEnabledFor(merchantId)).isTrue();
    }

    @Test
    @DisplayName("libera quando o merchantId do iFood está na whitelist")
    void isEnabledFor_shouldAcceptIfoodMerchantId() {
        assertThat(serviceWithWhitelist("ifood-m1").isEnabledFor(merchantId)).isTrue();
    }

    @Test
    @DisplayName("ignora espaços e diferenças de caixa entre os ids da whitelist")
    void isEnabledFor_shouldTolerateSpacingAndCase() {
        String whitelist = " outro-merchant , " + merchantId.toString().toUpperCase() + " ,, ";

        assertThat(serviceWithWhitelist(whitelist).isEnabledFor(merchantId)).isTrue();
    }

    @Test
    @DisplayName("não libera merchant fora da whitelist")
    void isEnabledFor_shouldRejectMerchantOutsideWhitelist() {
        assertThat(serviceWithWhitelist("outro-merchant").isEnabledFor(merchantId)).isFalse();
    }

    @Test
    @DisplayName("merchant inexistente não é liberado")
    void isEnabledFor_shouldRejectUnknownMerchant() {
        UUID unknown = UUID.randomUUID();
        given(merchantRepository.findById(unknown)).willReturn(Optional.empty());

        assertThat(serviceWithWhitelist(unknown.toString()).isEnabledFor(unknown)).isFalse();
    }

    // ── Listagem crua ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("listCatalogs devolve a resposta crua do iFood")
    void listCatalogs_shouldReturnRawResponse() {
        IfoodRawResponse raw = new IfoodRawResponse("https://ifood/catalogs", 200, "[]");
        given(catalogClient.rawCatalogs("token-1", "ifood-m1")).willReturn(raw);

        IfoodRawResponse result = serviceWithWhitelist("ifood-m1").listCatalogs(merchantId);

        assertThat(result).isEqualTo(raw);
    }

    @Test
    @DisplayName("listCatalogs recusa merchant fora da whitelist sem chamar o iFood")
    void listCatalogs_shouldRejectMerchantOutsideWhitelist() {
        IfoodDiagnosticsService service = serviceWithWhitelist("outro-merchant");

        assertThatThrownBy(() -> service.listCatalogs(merchantId))
                .isInstanceOf(IfoodDiagnosticsNotAllowedException.class);
        then(catalogClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("listCatalogs falha quando o merchant não está conectado ao iFood")
    void listCatalogs_shouldFailWhenNotConnected() {
        merchant.setIfoodMerchantId(null);
        IfoodDiagnosticsService service = serviceWithWhitelist(merchantId.toString());

        assertThatThrownBy(() -> service.listCatalogs(merchantId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("listItems resolve o catálogo DEFAULT antes de pedir as categorias com itens")
    void listItems_shouldResolveDefaultCatalog() {
        given(catalogClient.listCatalogs("token-1", "ifood-m1"))
                .willReturn(List.of(catalog("cat-indoor", "INDOOR"), catalog("cat-default", "DEFAULT")));
        IfoodRawResponse raw = new IfoodRawResponse("https://ifood/categories", 200, "[]");
        given(catalogClient.rawCategories("token-1", "ifood-m1", "cat-default")).willReturn(raw);

        IfoodRawResponse result = serviceWithWhitelist("ifood-m1").listItems(merchantId, null);

        assertThat(result).isEqualTo(raw);
    }

    @Test
    @DisplayName("listItems usa o catálogo informado sem consultar a lista de catálogos")
    void listItems_shouldUseGivenCatalogId() {
        IfoodRawResponse raw = new IfoodRawResponse("https://ifood/categories", 200, "[]");
        given(catalogClient.rawCategories("token-1", "ifood-m1", "cat-escolhido")).willReturn(raw);

        IfoodRawResponse result =
                serviceWithWhitelist("ifood-m1").listItems(merchantId, "cat-escolhido");

        assertThat(result).isEqualTo(raw);
        then(catalogClient).should(never()).listCatalogs("token-1", "ifood-m1");
    }

    @Test
    @DisplayName("listItems falha quando o merchant não tem catálogo no iFood")
    void listItems_shouldFailWhenNoCatalogAvailable() {
        given(catalogClient.listCatalogs("token-1", "ifood-m1")).willReturn(List.of());
        IfoodDiagnosticsService service = serviceWithWhitelist("ifood-m1");

        assertThatThrownBy(() -> service.listItems(merchantId, null))
                .isInstanceOf(IfoodResourceNotFoundException.class);
    }

    @Test
    @DisplayName("listItems recusa merchant fora da whitelist sem chamar o iFood")
    void listItems_shouldRejectMerchantOutsideWhitelist() {
        IfoodDiagnosticsService service = serviceWithWhitelist("");

        assertThatThrownBy(() -> service.listItems(merchantId, null))
                .isInstanceOf(IfoodDiagnosticsNotAllowedException.class);
        then(catalogClient).shouldHaveNoInteractions();
    }
}
