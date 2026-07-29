package com.jetmenu.category;

import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.product.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           MerchantRepository merchantRepository,
                           ProductRepository productRepository,
                           OrderRepository orderRepository) {
        this.categoryRepository = categoryRepository;
        this.merchantRepository = merchantRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    public List<CategoryRevenueResponse> revenue(UUID merchantId, LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        return orderRepository.sumRevenueByCategoryForMerchant(merchantId, startDateTime, endDateTime).stream()
                .map(row -> CategoryRevenueResponse.builder()
                        .categoryId((UUID) row[0])
                        .revenue((BigDecimal) row[1])
                        .build())
                .toList();
    }

    public CategoryResponse create(UUID merchantId, CategoryRequest request) {
        if (categoryRepository.existsByNameAndMerchantId(request.getName(), merchantId)) {
            throw new DuplicateCategoryException("nome");
        }

        Category category = Category.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .name(request.getName())
                .colorHex(request.getColorHex())
                .build();

        Category saved = categoryRepository.save(category);
        return toResponseWithCount(saved, merchantId);
    }

    public CategoryResponse findById(UUID merchantId, UUID id) {
        Category category = categoryRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new CategoryNotFoundException(id));
        return toResponseWithCount(category, merchantId);
    }

    public Page<CategoryResponse> findAll(UUID merchantId, String search, Pageable pageable) {
        String term = search == null ? "" : search;
        Page<Category> page = categoryRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, term, pageable);
        List<UUID> ids = page.getContent().stream().map(Category::getId).toList();
        Map<UUID, Long> counts = ids.isEmpty() ? Map.of() :
                productRepository.countByCategoryIdsAndMerchantId(ids, merchantId)
                        .stream()
                        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
        return page.map(c -> toResponse(c, counts.getOrDefault(c.getId(), 0L)));
    }

    public CategoryResponse update(UUID merchantId, UUID id, CategoryRequest request) {
        Category category = categoryRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        category.setName(request.getName());
        if (request.getColorHex() != null) {
            category.setColorHex(request.getColorHex());
        }

        Category saved = categoryRepository.save(category);
        return toResponseWithCount(saved, merchantId);
    }

    @Transactional
    public void delete(UUID merchantId, UUID id) {
        if (!categoryRepository.existsByIdAndMerchantId(id, merchantId)) {
            throw new CategoryNotFoundException(id);
        }
        categoryRepository.deleteByIdAndMerchantId(id, merchantId);
    }

    private CategoryResponse toResponseWithCount(Category category, UUID merchantId) {
        long count = productRepository.countByCategoryIdAndMerchantId(category.getId(), merchantId);
        return toResponse(category, count);
    }

    private CategoryResponse toResponse(Category category, long productCount) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .productCount(productCount)
                .colorHex(category.getColorHex())
                .origin(category.getOrigin())
                .build();
    }
}
