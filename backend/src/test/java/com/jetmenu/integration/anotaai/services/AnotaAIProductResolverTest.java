package com.jetmenu.integration.anotaai.services;

import com.jetmenu.integration.anotaai.AnotaAIOrderDetailResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnotaAIProductResolver")
class AnotaAIProductResolverTest {

    @Mock private ProductRepository productRepository;

    private AnotaAIProductResolver resolver;
    private UUID merchantId;

    @BeforeEach
    void setUp() {
        resolver = new AnotaAIProductResolver(productRepository);
        merchantId = UUID.randomUUID();
    }

    private AnotaAIOrderDetailResponse.AnotaAIOrderItem item(String internalId, String name) {
        AnotaAIOrderDetailResponse.AnotaAIOrderItem remoteItem =
                new AnotaAIOrderDetailResponse.AnotaAIOrderItem();
        remoteItem.setInternalId(internalId);
        remoteItem.setName(name);
        return remoteItem;
    }

    private Product product(String name) {
        return Product.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .name(name)
                .status(ProductStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("deve resolver por internalId quando presente — sem consultar por nome")
    void shouldResolveByInternalIdWhenPresent() {
        Product expected = product("Açaí 330 ml");
        given(productRepository.findByExternalIdAndMerchantId("internal-1", merchantId))
                .willReturn(Optional.of(expected));

        Optional<Product> result = resolver.resolve(item("internal-1", "Açaí 330 ml"), merchantId);

        assertThat(result).contains(expected);
        verify(productRepository, never()).findByCanonicalNameAndMerchantId(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("deve resolver por nome canônico quando internalId vem vazio (pedidos do canal iFood)")
    void shouldResolveByCanonicalNameWhenInternalIdIsBlank() {
        // Pedidos com salesChannel=ifood chegam com internalId="" em todos os itens —
        // o único vínculo com o catálogo do JetMenu é o nome do produto.
        Product expected = product("Açaí 330 ml");
        given(productRepository.findByCanonicalNameAndMerchantId("acai 330 ml", merchantId))
                .willReturn(Optional.of(expected));

        Optional<Product> result = resolver.resolve(item("", "Açaí 330 ml"), merchantId);

        assertThat(result).contains(expected);
    }

    @Test
    @DisplayName("deve resolver por nome canônico quando internalId é nulo")
    void shouldResolveByCanonicalNameWhenInternalIdIsNull() {
        Product expected = product("Açaí 330 ml");
        given(productRepository.findByCanonicalNameAndMerchantId("acai 330 ml", merchantId))
                .willReturn(Optional.of(expected));

        Optional<Product> result = resolver.resolve(item(null, "Açaí 330 ml"), merchantId);

        assertThat(result).contains(expected);
    }

    @Test
    @DisplayName("deve cair para o nome canônico quando o internalId não casa com nenhum produto")
    void shouldFallBackToCanonicalNameWhenInternalIdDoesNotMatch() {
        Product expected = product("Açaí 330 ml");
        given(productRepository.findByExternalIdAndMerchantId("internal-desconhecido", merchantId))
                .willReturn(Optional.empty());
        given(productRepository.findByCanonicalNameAndMerchantId("acai 330 ml", merchantId))
                .willReturn(Optional.of(expected));

        Optional<Product> result = resolver.resolve(
                item("internal-desconhecido", "Açaí 330 ml"), merchantId);

        assertThat(result).contains(expected);
    }

    @Test
    @DisplayName("deve retornar vazio quando não há internalId nem nome")
    void shouldReturnEmptyWhenNoInternalIdAndNoName() {
        Optional<Product> result = resolver.resolve(item("", "  "), merchantId);

        assertThat(result).isEmpty();
        verify(productRepository, never()).findByCanonicalNameAndMerchantId(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("deve retornar vazio quando nem o internalId nem o nome casam")
    void shouldReturnEmptyWhenNeitherMatches() {
        given(productRepository.findByCanonicalNameAndMerchantId("produto inexistente", merchantId))
                .willReturn(Optional.empty());

        Optional<Product> result = resolver.resolve(item("", "Produto Inexistente"), merchantId);

        assertThat(result).isEmpty();
    }
}
