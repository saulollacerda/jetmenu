package com.jetmenu.integration.anotaai.services;

import com.jetmenu.category.CatalogOrigin;
import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.ingredient.IngredientNameNormalizer;
import com.jetmenu.integration.anotaai.AnotaAICatalogResponse;
import com.jetmenu.integration.anotaai.AnotaAIClient;
import com.jetmenu.integration.anotaai.AnotaAISyncResult;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.product.IncludeRepository;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sincroniza o catálogo (categorias + produtos) do Anota.AI com o banco do JetMenu.
 *
 * <p>Ignora categorias com {@code is_additional: true} — esses complementos são
 * gerenciados manualmente como {@link com.jetmenu.ingredient.Ingredient}.
 */
public class AnotaAICatalogSyncService {

    private static final Logger log = LoggerFactory.getLogger(AnotaAICatalogSyncService.class);

    private final AnotaAIClient anotaAIClient;
    private final MerchantRepository merchantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final IncludeRepository includeRepository;

    public AnotaAICatalogSyncService(AnotaAIClient anotaAIClient,
                                      MerchantRepository merchantRepository,
                                      CategoryRepository categoryRepository,
                                      ProductRepository productRepository,
                                      IncludeRepository includeRepository) {
        this.anotaAIClient = anotaAIClient;
        this.merchantRepository = merchantRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.includeRepository = includeRepository;
    }

    public AnotaAISyncResult sync(UUID merchantId, String apiKey, boolean clearRecipes) {
        AnotaAICatalogResponse catalog = anotaAIClient.getCatalog(apiKey);

        int categoriesCreated = 0, categoriesUpdated = 0;
        int productsCreated = 0, productsUpdated = 0;
        List<String> errors = new ArrayList<>();

        if (catalog == null || catalog.getCategories() == null) {
            return AnotaAISyncResult.builder().errors(errors).build();
        }

        List<Category> categories = categoryRepository.findAll();

        for (AnotaAICatalogResponse.AnotaAICategory remoteCategory : catalog.getCategories()) {
            // Ingredients are managed manually by the merchant — skip is_additional=true categories.
            if (remoteCategory.isAdditional()) continue;

            Optional<Category> existingCategoryOpt = categoryRepository
                    .findByExternalIdAndMerchantId(remoteCategory.getId(), merchantId);

            Category category;
            if (existingCategoryOpt.isPresent()) {
                category = existingCategoryOpt.get();
                category.setName(remoteCategory.getTitle());
                category = categoryRepository.save(category);
                categoriesUpdated++;
            } else {
                category = Category.builder()
                        .merchant(merchantRepository.getReferenceById(merchantId))
                        .name(remoteCategory.getTitle())
                        .externalId(remoteCategory.getId())
                        .origin(CatalogOrigin.ANOTA_AI)
                        .build();
                category = categoryRepository.save(category);
                categoriesCreated++;
            }

            if (remoteCategory.getItens() == null) continue;

            for (AnotaAICatalogResponse.AnotaAIItem remoteItem : remoteCategory.getItens()) {
                Optional<Product> existingProductOpt = productRepository
                        .findByExternalIdAndMerchantId(remoteItem.getId(), merchantId);

                BigDecimal price = BigDecimal.valueOf(remoteItem.getPrice());
                ProductStatus status = remoteItem.isOut() ? ProductStatus.INACTIVE : ProductStatus.ACTIVE;

                if (existingProductOpt.isPresent()) {
                    Product product = existingProductOpt.get();
                    if (clearRecipes) {
                        includeRepository.deleteAllByProductIdAndProductMerchantId(product.getId(), merchantId);
                    }
                    product.setName(remoteItem.getTitle());
                    // Recalculado junto com o nome: é a chave usada para casar itens de pedido
                    // que chegam sem internalId (canal iFood via Anota.AI).
                    product.setCanonicalName(IngredientNameNormalizer.normalize(remoteItem.getTitle()));
                    product.setPrice(price);
                    product.setStatus(status);
                    product.setCategory(category);
                    productRepository.save(product);
                    productsUpdated++;
                } else {
                    Product product = Product.builder()
                            .merchant(merchantRepository.getReferenceById(merchantId))
                            .name(remoteItem.getTitle())
                            .canonicalName(IngredientNameNormalizer.normalize(remoteItem.getTitle()))
                            .price(price)
                            .status(status)
                            .externalId(remoteItem.getId())
                            .category(category)
                            .origin(CatalogOrigin.ANOTA_AI)
                            .build();
                    productRepository.save(product);
                    productsCreated++;
                }
            }
        }

        return AnotaAISyncResult.builder()
                .categoriesCreated(categoriesCreated)
                .categoriesUpdated(categoriesUpdated)
                .productsCreated(productsCreated)
                .productsUpdated(productsUpdated)
                .errors(errors)
                .build();
    }
}
