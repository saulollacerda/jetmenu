package com.jetmenu.integration.anotaai.services;

import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientNameNormalizer;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.integration.anotaai.AnotaAIOrderDetailResponse;
import com.jetmenu.notification.NotificationService;
import com.jetmenu.order.OrderItemExtraIngredient;
import com.jetmenu.order.OrderItemUnmatchedSubItem;
import com.jetmenu.order.ResolvedSubItems;
import com.jetmenu.product.Include;
import com.jetmenu.product.IncludeKind;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Resolve os subItems de um pedido em {@link OrderItemExtraIngredient}s.
 *
 * <p><b>Sem deduplicação:</b> cada subItem vira um {@code OrderItemExtraIngredient}
 * independente, mesmo que tenha o mesmo nome de outro subItem. Isso reflete combos
 * (e.g. Combo Casal) onde o mesmo ingrediente aparece uma vez por produto dentro
 * do pedido e deve ser contabilizado separadamente.
 *
 * <p><b>Apenas PACKAGING é autoritativo:</b> se o subItem casa com um {@link Include}
 * do tipo {@link IncludeKind#PACKAGING} (copo, embalagem...), ele já está na base do
 * produto — <b>nenhum {@code OrderItemExtraIngredient} é criado</b>, evitando
 * double-counting. Includes do tipo {@code INGREDIENT} (ou legados sem kind) NÃO entram
 * na base: são opções de personalização, então quando o cliente os pede o subItem vira
 * um extra normalmente.
 *
 * <p>Para todo subItem que não casa com um PACKAGING, o extra usa
 * {@code Ingredient.defaultQuantity} (qty global) × {@code subItem.quantity} e o
 * {@code Ingredient.costPerUnit} global.
 *
 * <p><b>Preço × custo são independentes:</b> o custo vem sempre do catálogo local
 * ({@code Ingredient.costPerUnit}); o preço pago pelo cliente ({@code salePricePerUnit} /
 * {@code salePriceTotal}) vem literalmente do payload. Um subItem com preço {@code 0.0} é
 * complemento base — incluso no produto, mas ainda assim com custo de produção.
 */
@Component
public class AnotaAIExtraIngredientResolver {

    private final IngredientRepository ingredientRepository;
    private final NotificationService notificationService;

    public AnotaAIExtraIngredientResolver(IngredientRepository ingredientRepository,
                                           NotificationService notificationService) {
        this.ingredientRepository = ingredientRepository;
        this.notificationService = notificationService;
    }

    public ResolvedSubItems resolve(
            List<AnotaAIOrderDetailResponse.AnotaAISubItem> subItems,
            List<Include> productIncludes,
            UUID merchantId,
            Set<String> missingIngredientNames) {
        if (subItems == null || subItems.isEmpty()) {
            return new ResolvedSubItems(new ArrayList<>(), new ArrayList<>());
        }

        List<OrderItemExtraIngredient> extras = new ArrayList<>();
        List<OrderItemUnmatchedSubItem> unmatched = new ArrayList<>();
        Set<String> notifiedMissing = new java.util.HashSet<>();

        for (AnotaAIOrderDetailResponse.AnotaAISubItem subItem : subItems) {
            String rawName = subItem.getName();
            if (rawName == null || rawName.isBlank()) continue;
            String canonical = IngredientNameNormalizer.normalize(rawName);

            // Apenas PACKAGING é autoritativo: se o subItem casa com uma embalagem da
            // ficha técnica, ele já está na base do produto e NÃO vira extra. Um match
            // com INGREDIENT não pula — ingredientes só contam quando pedidos (via extra).
            if (findMatchingPackagingInclude(productIncludes, canonical).isPresent()) {
                continue;
            }

            Optional<Ingredient> match = ingredientRepository
                    .findFirstByCanonicalNameAndMerchantIdOrderByIdAsc(canonical, merchantId);
            if (match.isEmpty()) {
                missingIngredientNames.add(rawName);
                if (notifiedMissing.add(canonical)) {
                    notificationService.createMissingIngredient(rawName, canonical, merchantId);
                }
                // Grava o subItem não-casado para aparecer no detalhe do pedido com um botão
                // de cadastro, em vez de sumir. Preço/qtd copiados literalmente do payload.
                unmatched.add(OrderItemUnmatchedSubItem.builder()
                        .rawName(rawName)
                        .canonicalName(canonical)
                        .quantity(subItem.getQuantity())
                        .salePricePerUnit(BigDecimal.valueOf(subItem.getPrice()))
                        .salePriceTotal(BigDecimal.valueOf(subItem.getTotal()))
                        .build());
                continue;
            }
            Ingredient ingredient = match.get();
            BigDecimal customerQuantity = BigDecimal.valueOf(subItem.getQuantity());
            BigDecimal perUnitQuantity = resolveQuantityForProduct(productIncludes, canonical, ingredient);
            BigDecimal costPerUnit = ingredient.getCostPerUnit();

            // Preço pago: copiado literalmente do payload. NÃO derivar de `quantity`
            // (gramatura) — isso multiplicaria reais por gramas. Ver javadoc de
            // OrderItemExtraIngredient.salePriceTotal.
            extras.add(OrderItemExtraIngredient.builder()
                    .ingredient(ingredient)
                    .quantity(perUnitQuantity.multiply(customerQuantity))
                    .costPerUnit(costPerUnit)
                    .salePricePerUnit(BigDecimal.valueOf(subItem.getPrice()))
                    .salePriceTotal(BigDecimal.valueOf(subItem.getTotal()))
                    .ingredientName(ingredient.getName())
                    .ingredientUnit(ingredient.getUnit())
                    .build());
        }
        return new ResolvedSubItems(extras, unmatched);
    }

    /**
     * Retorna a quantidade específica do produto para o ingrediente, se houver um
     * {@link Include} não-PACKAGING com nome canônico correspondente. Caso contrário,
     * usa {@code ingredient.defaultQuantity} ou {@link BigDecimal#ONE} como fallback.
     *
     * <p><b>Gramatura zero é tratada como "não configurada", nunca como zero literal.</b>
     * A tela de ingredientes grava {@code defaultQuantity = 0} quando o lojista não informa
     * a gramatura, e linhas legadas da ficha técnica podem ter {@code quantity = 0}. Aceitar
     * esse zero zerava a quantidade — e, por consequência, o custo — do extra no pedido,
     * mesmo com o match por nome funcionando (bug reportado em pedidos do iFood, cujos
     * combos trazem muitos complementos fora da ficha técnica do produto).
     */
    private BigDecimal resolveQuantityForProduct(List<Include> productIncludes,
                                                  String canonical,
                                                  com.jetmenu.ingredient.Ingredient ingredient) {
        if (productIncludes != null) {
            for (Include inc : productIncludes) {
                if (inc.getKind() == IncludeKind.PACKAGING) continue;
                if (inc.getName() == null || !isPositive(inc.getQuantity())) continue;
                if (IngredientNameNormalizer.normalize(inc.getName()).equals(canonical)) {
                    return inc.getQuantity();
                }
            }
        }
        return isPositive(ingredient.getDefaultQuantity())
                ? ingredient.getDefaultQuantity()
                : BigDecimal.ONE;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Procura, entre os Includes {@link IncludeKind#PACKAGING} da ficha técnica de um
     * produto, um cujo nome normalizado case com o nome canônico do subItem. Includes
     * de outros tipos (INGREDIENT ou legados sem kind) são ignorados aqui.
     */
    private Optional<Include> findMatchingPackagingInclude(List<Include> productIncludes,
                                                           String canonicalSubItemName) {
        if (productIncludes == null || productIncludes.isEmpty() || canonicalSubItemName == null) {
            return Optional.empty();
        }
        return productIncludes.stream()
                .filter(inc -> inc.getKind() == IncludeKind.PACKAGING)
                .filter(inc -> inc.getName() != null
                        && IngredientNameNormalizer.normalize(inc.getName()).equals(canonicalSubItemName))
                .findFirst();
    }
}
