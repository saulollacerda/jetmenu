package com.jetmenu.product;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryNotFoundException;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.ingredient.IngredientNameNormalizer;
import com.jetmenu.merchant.MerchantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MerchantRepository merchantRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          MerchantRepository merchantRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.merchantRepository = merchantRepository;
    }

    public ProductResponse create(UUID merchantId, ProductRequest request) {
        if (productRepository.existsByNameAndMerchantId(request.getName(), merchantId)) {
            throw new DuplicateProductException("nome");
        }

        Category category = categoryRepository.findByIdAndMerchantId(request.getCategoryId(), merchantId)
                .orElseThrow(() -> new CategoryNotFoundException(request.getCategoryId()));

        Product product = Product.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .name(request.getName())
                .canonicalName(IngredientNameNormalizer.normalize(request.getName()))
                .price(request.getPrice())
                .status(request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE)
                .targetMarginPct(request.getTargetMarginPct())
                .category(category)
                .build();

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    public ProductResponse findById(UUID merchantId, UUID id) {
        Product product = productRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return toResponse(product);
    }

    public Page<ProductResponse> findAll(UUID merchantId, String search, Pageable pageable) {
        String term = search == null ? "" : search;
        return productRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, term, pageable)
                .map(this::toResponse);
    }

    public ProductResponse update(UUID merchantId, UUID id, ProductRequest request) {
        Product product = productRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new ProductNotFoundException(id));

        Category category = categoryRepository.findByIdAndMerchantId(request.getCategoryId(), merchantId)
                .orElseThrow(() -> new CategoryNotFoundException(request.getCategoryId()));

        product.setName(request.getName());
        product.setCanonicalName(IngredientNameNormalizer.normalize(request.getName()));
        product.setPrice(request.getPrice());
        // Margem ideal segue o request: ausente = lojista deixou de acompanhar a margem.
        product.setTargetMarginPct(request.getTargetMarginPct());
        product.setCategory(category);
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    public void delete(UUID merchantId, UUID id) {
        if (!productRepository.existsByIdAndMerchantId(id, merchantId)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteByIdAndMerchantId(id, merchantId);
    }

    private ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        BigDecimal unitCost = ProductCostCalculator.computeUnitCost(product.getIncludes());
        BigDecimal marginPct = computeMarginPct(product.getPrice(), unitCost);
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .status(product.getStatus())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .unitCost(unitCost)
                .marginPct(marginPct)
                .targetMarginPct(product.getTargetMarginPct())
                .origin(product.getOrigin())
                .build();
    }

    private BigDecimal computeMarginPct(BigDecimal price, BigDecimal unitCost) {
        if (price == null || price.signum() == 0) {
            return null;
        }
        BigDecimal cost = unitCost != null ? unitCost : BigDecimal.ZERO;
        return price.subtract(cost)
                .divide(price, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
