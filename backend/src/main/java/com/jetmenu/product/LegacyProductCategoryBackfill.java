package com.jetmenu.product;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.merchant.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dá categoria aos produtos legados que ficaram sem uma.
 *
 * <p><b>Suprimido por {@code app.startup.backfills-enabled=false}</b>, que a produção usa: é
 * uma migração de uma vez só que roda a cada boot, e no Cloud Run isso vira a cada instância
 * nova. O default é ligado — desligar é decisão de quem já migrou.
 */
@Component
@ConditionalOnProperty(name = "app.startup.backfills-enabled", havingValue = "true",
        matchIfMissing = true)
class LegacyProductCategoryBackfill implements CommandLineRunner {

    private static final String DEFAULT_NAME = "Sem categoria";
    private static final Logger log = LoggerFactory.getLogger(LegacyProductCategoryBackfill.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MerchantRepository merchantRepository;

    LegacyProductCategoryBackfill(ProductRepository productRepository,
                                  CategoryRepository categoryRepository,
                                  MerchantRepository merchantRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.merchantRepository = merchantRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Product> orphans = productRepository.findAllByCategoryIsNull();
        if (orphans.isEmpty()) {
            return;
        }

        Map<UUID, List<Product>> byOwner = orphans.stream()
                .collect(Collectors.groupingBy(p -> p.getMerchant().getId()));

        byOwner.forEach((merchantId, products) -> {
            Category defaultCategory = categoryRepository
                    .findByNameAndMerchantId(DEFAULT_NAME, merchantId)
                    .orElseGet(() -> categoryRepository.save(Category.builder()
                            .merchant(merchantRepository.getReferenceById(merchantId))
                            .name(DEFAULT_NAME)
                            .build()));

            products.forEach(p -> p.setCategory(defaultCategory));
            productRepository.saveAll(products);

            log.info("Backfill: {} produtos legados atribuídos a '{}' para owner {}",
                    products.size(), DEFAULT_NAME, merchantId);
        });
    }
}
