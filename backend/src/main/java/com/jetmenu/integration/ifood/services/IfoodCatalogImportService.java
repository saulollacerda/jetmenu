package com.jetmenu.integration.ifood.services;

import com.jetmenu.category.CatalogOrigin;
import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.ingredient.IngredientNameNormalizer;
import com.jetmenu.integration.ifood.IfoodCatalogClient;
import com.jetmenu.integration.ifood.IfoodTokenService;
import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogImportResult;
import com.jetmenu.integration.ifood.dto.IfoodCatalogImportResult.ItemOutcome;
import com.jetmenu.integration.ifood.dto.IfoodCatalogImportResult.Outcome;
import com.jetmenu.integration.ifood.dto.IfoodCatalogResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Importa o catálogo do iFood (contexto DEFAULT) como Products e Categories — leitura
 * apenas, nada é escrito de volta no iFood. Regras de merge: produto existente nunca tem
 * campos sobrescritos (preço/nome/categoria são do lojista); no máximo o externalId
 * faltante é preenchido para vincular. Conflitos e itens sem preço viram SKIPPED com
 * motivo no relatório em vez de abortar a transação.
 */
@Service
public class IfoodCatalogImportService {

    private static final Logger log = LoggerFactory.getLogger(IfoodCatalogImportService.class);

    private static final String DEFAULT_CONTEXT = "DEFAULT";

    private final IfoodCatalogClient catalogClient;
    private final IfoodTokenService tokenService;
    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public IfoodCatalogImportService(IfoodCatalogClient catalogClient,
                                     IfoodTokenService tokenService,
                                     MerchantRepository merchantRepository,
                                     ProductRepository productRepository,
                                     CategoryRepository categoryRepository) {
        this.catalogClient = catalogClient;
        this.tokenService = tokenService;
        this.merchantRepository = merchantRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public IfoodCatalogImportResult importCatalog(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .filter(m -> m.getIfoodMerchantId() != null)
                .orElseThrow(() -> new IllegalStateException(
                        "Merchant " + merchantId + " is not connected to iFood"));
        String ifoodMerchantId = merchant.getIfoodMerchantId();

        AtomicReference<String> token = new AtomicReference<>(tokenService.getAccessToken());
        List<IfoodCatalogResponse> catalogs =
                withRetryOn401(token, t -> catalogClient.listCatalogs(t, ifoodMerchantId));

        List<ItemOutcome> outcomes = new ArrayList<>();
        int importedCategories = 0;
        int linkedCategories = 0;

        Optional<String> catalogId = pickCatalogId(catalogs);
        if (catalogId.isPresent()) {
            List<IfoodCatalogCategoryResponse> remoteCategories = withRetryOn401(token,
                    t -> catalogClient.listCategories(t, ifoodMerchantId, catalogId.get()));

            for (IfoodCatalogCategoryResponse remoteCategory : remoteCategories) {
                CategoryResolution resolution = resolveCategory(merchant, remoteCategory);
                switch (resolution.outcome()) {
                    case IMPORTED -> importedCategories++;
                    case LINKED -> linkedCategories++;
                    case SKIPPED -> { /* categoria sem nome — itens seguem sem categoria */ }
                }
                importItems(merchant, remoteCategory, resolution.category(), outcomes);
            }
        } else {
            log.warn("[iFood] importação de catálogo sem catálogos disponíveis — merchant {}", merchantId);
        }

        merchant.setIfoodCatalogImportedAt(LocalDateTime.now());
        merchantRepository.save(merchant);

        IfoodCatalogImportResult result =
                IfoodCatalogImportResult.of(outcomes, importedCategories, linkedCategories);
        log.info("[iFood] catálogo importado — merchant {}: {} importados, {} vinculados, {} ignorados",
                merchantId, result.getImportedProducts(), result.getLinkedProducts(),
                result.getSkippedProducts());
        return result;
    }

    /** Pedidos vêm do canal de entrega, então o catálogo do contexto DEFAULT é o alvo. */
    private static Optional<String> pickCatalogId(List<IfoodCatalogResponse> catalogs) {
        return catalogs.stream()
                .filter(c -> c.getContext() != null && c.getContext().contains(DEFAULT_CONTEXT))
                .map(IfoodCatalogResponse::getCatalogId)
                .findFirst()
                .or(() -> catalogs.stream().map(IfoodCatalogResponse::getCatalogId).findFirst());
    }

    private record CategoryResolution(Category category, Outcome outcome) {}

    private CategoryResolution resolveCategory(Merchant merchant,
                                               IfoodCatalogCategoryResponse remoteCategory) {
        String name = remoteCategory.getName();
        if (name == null || name.isBlank()) {
            return new CategoryResolution(null, Outcome.SKIPPED);
        }

        Optional<Category> byExternalId = remoteCategory.getId() != null
                ? categoryRepository.findByExternalIdAndMerchantId(remoteCategory.getId(), merchant.getId())
                : Optional.empty();
        if (byExternalId.isPresent()) {
            return new CategoryResolution(byExternalId.get(), Outcome.LINKED);
        }

        Optional<Category> byName = categoryRepository.findByNameAndMerchantId(name, merchant.getId());
        if (byName.isPresent()) {
            Category category = byName.get();
            category.setExternalId(remoteCategory.getId());
            return new CategoryResolution(categoryRepository.save(category), Outcome.LINKED);
        }

        Category created = categoryRepository.save(Category.builder()
                .merchant(merchant)
                .name(name)
                .externalId(remoteCategory.getId())
                .origin(CatalogOrigin.IFOOD)
                .build());
        return new CategoryResolution(created, Outcome.IMPORTED);
    }

    private void importItems(Merchant merchant,
                             IfoodCatalogCategoryResponse remoteCategory,
                             Category category,
                             List<ItemOutcome> outcomes) {
        if (remoteCategory.getItems() == null) return;

        for (IfoodCatalogCategoryResponse.Item remoteItem : remoteCategory.getItems()) {
            outcomes.add(importItem(merchant, remoteItem, category));
        }
    }

    private ItemOutcome importItem(Merchant merchant,
                                   IfoodCatalogCategoryResponse.Item remoteItem,
                                   Category category) {
        String name = remoteItem.getName();
        String externalCode = effectiveExternalCode(remoteItem);
        BigDecimal price = effectivePrice(remoteItem);

        if (name == null || name.isBlank()) {
            return new ItemOutcome(name, externalCode, Outcome.SKIPPED, "Item sem nome no catálogo");
        }
        if (price == null) {
            return new ItemOutcome(name, externalCode, Outcome.SKIPPED,
                    "Item sem preço no catálogo (ex.: pizza/combo precificado por opções)");
        }

        if (externalCode != null && !externalCode.isBlank()) {
            Optional<Product> byExternalId =
                    productRepository.findByExternalIdAndMerchantId(externalCode, merchant.getId());
            if (byExternalId.isPresent()) {
                return new ItemOutcome(name, externalCode, Outcome.LINKED, null);
            }
        }

        String canonicalName = IngredientNameNormalizer.normalize(name);
        Optional<Product> byCanonicalName =
                productRepository.findByCanonicalNameAndMerchantId(canonicalName, merchant.getId());
        if (byCanonicalName.isPresent()) {
            Product existing = byCanonicalName.get();
            boolean hasIncomingCode = externalCode != null && !externalCode.isBlank();
            if (existing.getExternalId() == null || existing.getExternalId().isBlank()) {
                if (hasIncomingCode) {
                    existing.setExternalId(externalCode);
                    productRepository.save(existing);
                }
                return new ItemOutcome(name, externalCode, Outcome.LINKED, null);
            }
            if (!hasIncomingCode || existing.getExternalId().equals(externalCode)) {
                return new ItemOutcome(name, externalCode, Outcome.LINKED, null);
            }
            return new ItemOutcome(name, externalCode, Outcome.SKIPPED,
                    "Produto '" + existing.getName() + "' já está vinculado ao código '"
                            + existing.getExternalId() + "'");
        }

        // canonical lookup missed but the raw name is taken (e.g. legacy null canonicalName):
        // creating would violate the per-merchant name uniqueness enforced by ProductService
        if (productRepository.existsByNameAndMerchantId(name, merchant.getId())) {
            return new ItemOutcome(name, externalCode, Outcome.SKIPPED,
                    "Já existe um produto com esse nome");
        }

        productRepository.save(Product.builder()
                .merchant(merchant)
                .externalId(externalCode != null && !externalCode.isBlank() ? externalCode : null)
                .name(name)
                .canonicalName(canonicalName)
                .price(price)
                .status(ProductStatus.ACTIVE)
                .category(category)
                .origin(CatalogOrigin.IFOOD)
                .build());
        return new ItemOutcome(name, externalCode, Outcome.IMPORTED, null);
    }

    private static BigDecimal effectivePrice(IfoodCatalogCategoryResponse.Item item) {
        IfoodCatalogCategoryResponse.Price price = item.getPrice();
        IfoodCatalogCategoryResponse.ContextModifier modifier = defaultModifier(item);
        if (modifier != null && modifier.getPrice() != null && modifier.getPrice().getValue() != null) {
            price = modifier.getPrice();
        }
        return price != null ? price.getValue() : null;
    }

    private static String effectiveExternalCode(IfoodCatalogCategoryResponse.Item item) {
        IfoodCatalogCategoryResponse.ContextModifier modifier = defaultModifier(item);
        if (modifier != null && modifier.getExternalCode() != null
                && !modifier.getExternalCode().isBlank()) {
            return modifier.getExternalCode();
        }
        return item.getExternalCode();
    }

    private static IfoodCatalogCategoryResponse.ContextModifier defaultModifier(
            IfoodCatalogCategoryResponse.Item item) {
        if (item.getContextModifiers() == null) return null;
        return item.getContextModifiers().stream()
                .filter(m -> DEFAULT_CONTEXT.equalsIgnoreCase(m.getCatalogContext()))
                .findFirst()
                .orElse(null);
    }

    private <T> T withRetryOn401(AtomicReference<String> token, Function<String, T> call) {
        try {
            return call.apply(token.get());
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("[iFood] 401 recebido — forçando refresh do token e repetindo a chamada");
            token.set(tokenService.handleUnauthorized());
            return call.apply(token.get());
        }
    }
}
