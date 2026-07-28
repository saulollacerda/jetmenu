package com.jetmenu.order;

import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientNotFoundException;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.merchant.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ficha do PEDIDO: insumos consumidos uma única vez por pedido, independentemente de
 * quantos itens ele tenha (sacola de entrega, guardanapo, cupom).
 *
 * <p>Complementa a ficha técnica do produto, que é por item e multiplicada pela quantidade
 * — correto para copo/colher (2 copos consomem 2 copos), errado para a sacola (2 copos
 * saem numa sacola só).
 *
 * <p><b>Não há migração automática:</b> insumos de pedido que hoje estejam na ficha do
 * produto continuam lá, contando por item. Mover a sacola do produto para cá é decisão do
 * lojista — adivinhar quais insumos são "de pedido" reescreveria o custo dele sem pedir.
 */
@Service
public class OrderFichaService {

    private final OrderFichaLineRepository orderFichaLineRepository;
    private final IngredientRepository ingredientRepository;
    private final MerchantRepository merchantRepository;

    public OrderFichaService(OrderFichaLineRepository orderFichaLineRepository,
                             IngredientRepository ingredientRepository,
                             MerchantRepository merchantRepository) {
        this.orderFichaLineRepository = orderFichaLineRepository;
        this.ingredientRepository = ingredientRepository;
        this.merchantRepository = merchantRepository;
    }

    @Transactional(readOnly = true)
    public OrderFichaResponse findByMerchant(UUID merchantId) {
        return toResponse(orderFichaLineRepository.findAllByMerchantIdOrderBySortOrderAsc(merchantId));
    }

    /**
     * Substitui a ficha do pedido inteira. Lista vazia limpa a ficha e o custo volta a zero.
     * Só afeta pedidos criados/importados DEPOIS: pedidos existentes guardam o próprio
     * snapshot ({@link OrderFichaIngredient}) e não são recalculados.
     */
    @Transactional
    public OrderFichaResponse replace(UUID merchantId, OrderFichaRequest request) {
        List<OrderFichaLineRequest> requestedLines =
                request.getLines() != null ? request.getLines() : List.of();

        // Resolve tudo antes de apagar: uma linha inválida não pode zerar a ficha atual.
        List<OrderFichaLine> lines = new ArrayList<>();
        Set<UUID> seenIngredientIds = new HashSet<>();
        int sortOrder = 0;
        for (OrderFichaLineRequest lineRequest : requestedLines) {
            Ingredient ingredient = ingredientRepository
                    .findByIdAndMerchantId(lineRequest.getIngredientId(), merchantId)
                    .orElseThrow(() -> new IngredientNotFoundException(lineRequest.getIngredientId()));

            if (!seenIngredientIds.add(ingredient.getId())) {
                throw new DuplicateOrderFichaIngredientException(ingredient.getName());
            }

            lines.add(OrderFichaLine.builder()
                    .merchant(merchantRepository.getReferenceById(merchantId))
                    .ingredient(ingredient)
                    .quantity(lineRequest.getQuantity())
                    .sortOrder(sortOrder++)
                    .build());
        }

        orderFichaLineRepository.deleteAllByMerchantId(merchantId);
        if (lines.isEmpty()) {
            return toResponse(List.of());
        }
        return toResponse(orderFichaLineRepository.saveAll(lines));
    }

    /**
     * Copia a ficha configurada para linhas de snapshot prontas para serem penduradas num
     * pedido. O {@code order} de cada linha é responsabilidade do chamador.
     *
     * <p>Copia nome/unidade/custo por valor: é o que congela o custo histórico do pedido
     * quando o lojista editar a ficha ou o custo do ingrediente depois.
     *
     * @return lista vazia quando o lojista não configurou ficha do pedido (custo zero).
     */
    @Transactional(readOnly = true)
    public List<OrderFichaIngredient> buildSnapshot(UUID merchantId) {
        return orderFichaLineRepository.findAllByMerchantIdOrderBySortOrderAsc(merchantId).stream()
                .map(line -> {
                    Ingredient ingredient = line.getIngredient();
                    return OrderFichaIngredient.builder()
                            .ingredient(ingredient)
                            .quantity(line.getQuantity())
                            .costPerUnit(ingredient.getCostPerUnit())
                            .ingredientName(ingredient.getName())
                            .ingredientUnit(ingredient.getUnit())
                            .build();
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private OrderFichaResponse toResponse(List<OrderFichaLine> lines) {
        List<OrderFichaLineResponse> lineResponses = lines.stream()
                .map(this::toLineResponse)
                .toList();
        BigDecimal totalCost = lineResponses.stream()
                .map(OrderFichaLineResponse::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return OrderFichaResponse.builder()
                .lines(lineResponses)
                .totalCost(totalCost)
                .build();
    }

    private OrderFichaLineResponse toLineResponse(OrderFichaLine line) {
        Ingredient ingredient = line.getIngredient();
        BigDecimal quantity = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
        BigDecimal costPerUnit = ingredient.getCostPerUnit() != null
                ? ingredient.getCostPerUnit() : BigDecimal.ZERO;
        return OrderFichaLineResponse.builder()
                .id(line.getId())
                .ingredientId(ingredient.getId())
                .ingredientName(ingredient.getName())
                .ingredientUnit(ingredient.getUnit())
                .quantity(quantity)
                .costPerUnit(costPerUnit)
                .totalCost(quantity.multiply(costPerUnit))
                .build();
    }
}
