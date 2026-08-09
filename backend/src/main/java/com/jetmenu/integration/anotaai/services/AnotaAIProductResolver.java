package com.jetmenu.integration.anotaai.services;

import com.jetmenu.ingredient.IngredientNameNormalizer;
import com.jetmenu.integration.anotaai.AnotaAIOrderDetailResponse;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolve um {@link Product} para um item de pedido recebido pela Anota.AI.
 *
 * <p>Duas estratégias, nesta ordem:
 * <ol>
 *   <li><b>{@code internalId}</b> — pedidos do canal Anota.AI trazem nele o ID do produto
 *       no JetMenu, o vínculo mais confiável;</li>
 *   <li><b>nome canônico</b> — pedidos do canal iFood chegam com {@code internalId} vazio
 *       em todos os itens, então o nome do produto é o único vínculo disponível. É o mesmo
 *       critério já usado para os extras em
 *       {@link AnotaAIExtraIngredientResolver}.</li>
 * </ol>
 */
public class AnotaAIProductResolver {

    private final ProductRepository productRepository;

    public AnotaAIProductResolver(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Optional<Product> resolve(AnotaAIOrderDetailResponse.AnotaAIOrderItem remoteItem,
                                      UUID merchantId) {
        String internalId = remoteItem.getInternalId();
        if (internalId != null && !internalId.isBlank()) {
            Optional<Product> byInternalId =
                    productRepository.findByExternalIdAndMerchantId(internalId, merchantId);
            if (byInternalId.isPresent()) return byInternalId;
        }

        String canonicalName = IngredientNameNormalizer.normalize(remoteItem.getName());
        if (canonicalName.isEmpty()) return Optional.empty();
        return productRepository.findByCanonicalNameAndMerchantId(canonicalName, merchantId);
    }
}
