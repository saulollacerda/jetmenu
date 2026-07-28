package com.jetmenu.ingredient;

import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.notification.NotificationService;
import com.jetmenu.product.IncludeRepository;
import com.jetmenu.product.IngredientProductUsageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IngredientService {

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    private final IngredientRepository ingredientRepository;
    private final MerchantRepository merchantRepository;
    private final NotificationService notificationService;
    private final IncludeRepository includeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public IngredientService(IngredientRepository ingredientRepository,
                             MerchantRepository merchantRepository,
                             NotificationService notificationService,
                             IncludeRepository includeRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.ingredientRepository = ingredientRepository;
        this.merchantRepository = merchantRepository;
        this.notificationService = notificationService;
        this.includeRepository = includeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public IngredientResponse create(UUID merchantId, IngredientRequest request) {
        if (ingredientRepository.existsByNameAndMerchantId(request.getName(), merchantId)) {
            throw new DuplicateIngredientException("nome");
        }

        String canonicalName = IngredientNameNormalizer.normalize(request.getName());
        // O match de pedidos importados (iFood/Anota AI) usa o nome canônico como chave;
        // duplicatas canônicas ("Morango" vs "MORANGO ") quebrariam esse lookup.
        if (ingredientRepository.existsByCanonicalNameAndMerchantId(canonicalName, merchantId)) {
            throw new DuplicateIngredientException("nome");
        }
        Integer maxPosition = ingredientRepository.findMaxPositionByMerchantId(merchantId);
        int nextPosition = maxPosition == null ? 0 : maxPosition + 1;

        Ingredient ingredient = Ingredient.builder()
                .merchant(merchantRepository.getReferenceById(merchantId))
                .name(request.getName())
                .canonicalName(canonicalName)
                .unit(request.getUnit())
                .costPerUnit(request.getCostPerUnit())
                .salePrice(request.getSalePrice())
                .defaultQuantity(request.getDefaultQuantity())
                .status(request.getStatus() != null ? request.getStatus() : IngredientStatus.ACTIVE)
                .stockQuantity(request.getStockQuantity())
                .lastReplenishedAt(request.getLastReplenishedAt())
                .lowStockThreshold(request.getLowStockThreshold())
                .createdAt(LocalDateTime.now(BRAZIL_ZONE))
                .position(nextPosition)
                .build();

        Ingredient saved = ingredientRepository.save(ingredient);
        notificationService.deleteMissingIngredient(canonicalName, merchantId);
        eventPublisher.publishEvent(new IngredientCreatedEvent(merchantId, saved.getId(), canonicalName));
        return toResponse(saved);
    }

    public IngredientResponse findById(UUID merchantId, UUID id) {
        Ingredient ingredient = ingredientRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new IngredientNotFoundException(id));
        return toResponse(ingredient);
    }

    public Page<IngredientResponse> findAll(UUID merchantId, String search, Pageable pageable) {
        String term = search == null ? "" : search;
        Page<Ingredient> page = ingredientRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, term, pageable);
        Map<String, Long> usageCounts = fetchUsageCounts(page.getContent(), merchantId);
        return page.map(i -> toResponse(i, (Long) usageCounts.getOrDefault(
                i.getName() == null ? "" : i.getName().toLowerCase(), 0L)));
    }

    private Map<String, Long> fetchUsageCounts(List<Ingredient> ingredients, UUID merchantId) {
        if (ingredients.isEmpty()) {
            return Map.of();
        }
        List<String> names = ingredients.stream()
                .map(Ingredient::getName)
                .filter(java.util.Objects::nonNull)
                .map(String::toLowerCase)
                .distinct()
                .toList();
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : includeRepository.countByLowercaseNameInForMerchant(merchantId, names)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional
    public IngredientResponse update(UUID merchantId, UUID id, IngredientRequest request) {
        Ingredient ingredient = ingredientRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new IngredientNotFoundException(id));

        String canonicalName = IngredientNameNormalizer.normalize(request.getName());
        if (ingredientRepository.existsByCanonicalNameAndMerchantIdAndIdNot(canonicalName, merchantId, id)) {
            throw new DuplicateIngredientException("nome");
        }

        ingredient.setName(request.getName());
        ingredient.setCanonicalName(canonicalName);
        ingredient.setUnit(request.getUnit());
        ingredient.setCostPerUnit(request.getCostPerUnit());
        ingredient.setDefaultQuantity(request.getDefaultQuantity());
        if (request.getSalePrice() != null) {
            ingredient.setSalePrice(request.getSalePrice());
        }
        if (request.getStatus() != null) {
            ingredient.setStatus(request.getStatus());
        }
        if (request.getStockQuantity() != null) {
            ingredient.setStockQuantity(request.getStockQuantity());
        }
        if (request.getLastReplenishedAt() != null) {
            ingredient.setLastReplenishedAt(request.getLastReplenishedAt());
        }
        if (request.getLowStockThreshold() != null) {
            ingredient.setLowStockThreshold(request.getLowStockThreshold());
        }

        return toResponse(ingredientRepository.save(ingredient));
    }

    @Transactional
    public IngredientResponse updateCost(UUID merchantId, UUID id, IngredientCostRequest request) {
        Ingredient ingredient = ingredientRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new IngredientNotFoundException(id));

        ingredient.setCostPerUnit(request.getCostPerUnit());
        if (request.getDefaultQuantity() != null) {
            ingredient.setDefaultQuantity(request.getDefaultQuantity());
        }
        if (request.getUnit() != null && !request.getUnit().isBlank()) {
            ingredient.setUnit(request.getUnit());
        }

        return toResponse(ingredientRepository.save(ingredient));
    }

    @Transactional
    public void delete(UUID merchantId, UUID id) {
        if (!ingredientRepository.existsByIdAndMerchantId(id, merchantId)) {
            throw new IngredientNotFoundException(id);
        }
        ingredientRepository.deleteByIdAndMerchantId(id, merchantId);
    }

    /**
     * Move o ingrediente para uma nova posição global (zero-based) na ordenação padrão do
     * merchant, deslocando os demais registros com um único UPDATE em bloco. A posição
     * informada é limitada ao intervalo válido [0, count-1] — o frontend calcula o índice
     * global (página × tamanho + índice na página), inclusive para movimentos entre páginas.
     */
    @Transactional
    public void reorder(UUID merchantId, UUID id, int newPosition) {
        Ingredient ingredient = ingredientRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new IngredientNotFoundException(id));

        long count = ingredientRepository.countByMerchantId(merchantId);
        int target = Math.max(0, Math.min(newPosition, (int) count - 1));
        // Safety net for legacy rows without a position: treat as if appended at the end.
        int current = ingredient.getPosition() != null ? ingredient.getPosition() : (int) count - 1;

        if (target == current) {
            return;
        }
        if (target > current) {
            ingredientRepository.shiftRangeLeft(merchantId, current, target);
        } else {
            ingredientRepository.shiftRangeRight(merchantId, current, target);
        }
        ingredient.setPosition(target);
        ingredientRepository.save(ingredient);
    }

    /**
     * Retorna os produtos cujas fichas tecnicas (includes) contem este ingrediente
     * (match por nome, case-insensitive).
     */
    public List<IngredientProductUsageResponse> fetchUsages(UUID merchantId, UUID id) {
        Ingredient ingredient = ingredientRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new IngredientNotFoundException(id));

        return includeRepository
                .findByNameIgnoreCaseAndProductMerchantId(ingredient.getName(), merchantId)
                .stream()
                .map(inc -> {
                    BigDecimal cost = inc.getCost() != null ? inc.getCost() : BigDecimal.ZERO;
                    BigDecimal qty = inc.getQuantity() != null ? inc.getQuantity() : BigDecimal.ONE;
                    BigDecimal total = cost.multiply(qty).setScale(4, RoundingMode.HALF_UP);
                    return IngredientProductUsageResponse.builder()
                            .includeId(inc.getId())
                            .productId(inc.getProduct().getId())
                            .productName(inc.getProduct().getName())
                            .quantity(qty)
                            .cost(cost)
                            .totalCost(total)
                            .build();
                })
                .toList();
    }

    private IngredientResponse toResponse(Ingredient ingredient) {
        return toResponse(ingredient, null);
    }

    private IngredientResponse toResponse(Ingredient ingredient, Long usageCount) {
        BigDecimal totalStockCost = null;
        if (ingredient.getStockQuantity() != null && ingredient.getCostPerUnit() != null) {
            totalStockCost = ingredient.getStockQuantity().multiply(ingredient.getCostPerUnit())
                    .setScale(4, RoundingMode.HALF_UP);
        }
        return IngredientResponse.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .unit(ingredient.getUnit())
                .costPerUnit(ingredient.getCostPerUnit())
                .salePrice(ingredient.getSalePrice())
                .defaultQuantity(ingredient.getDefaultQuantity())
                .status(ingredient.getStatus())
                .stockQuantity(ingredient.getStockQuantity())
                .lastReplenishedAt(ingredient.getLastReplenishedAt())
                .lowStockThreshold(ingredient.getLowStockThreshold())
                .totalStockCost(totalStockCost)
                .usageCount(usageCount)
                .createdAt(ingredient.getCreatedAt())
                .position(ingredient.getPosition())
                .build();
    }
}
