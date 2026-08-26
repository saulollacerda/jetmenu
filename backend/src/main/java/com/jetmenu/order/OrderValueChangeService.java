package com.jetmenu.order;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Grava a trilha de auditoria ({@link OrderValueChange}) das mudanças em
 * {@code totalValue}/{@code totalCost}/{@code estimatedProfit} de um pedido já existente.
 * Usado por {@link OrderService} (edição de itens, correção manual, restauração) e por
 * {@code OrderIngredientBackfillService} (recomputo assíncrono de custo) — os quatro únicos
 * pontos do sistema que alteram esses valores num pedido já persistido.
 */
@Service
public class OrderValueChangeService {

    // Mesma zona usada pelo restante do fluxo de pedidos (OrderService) — não a do
    // servidor, que em prod/Railway é UTC e adiantaria o horário em 3h.
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    private final OrderValueChangeRepository orderValueChangeRepository;

    public OrderValueChangeService(OrderValueChangeRepository orderValueChangeRepository) {
        this.orderValueChangeRepository = orderValueChangeRepository;
    }

    /**
     * Grava uma linha só quando ao menos um dos três valores realmente muda. Um log cheio
     * de linhas idênticas (ex.: editar itens sem mudar o total) atrapalharia mais do que
     * ajudaria a debugar — por isso o no-op nunca é gravado.
     */
    public void recordIfChanged(Order order, OrderValueChangeSource source,
                                 BigDecimal oldTotalValue, BigDecimal oldTotalCost, BigDecimal oldEstimatedProfit,
                                 BigDecimal newTotalValue, BigDecimal newTotalCost, BigDecimal newEstimatedProfit) {
        boolean unchanged = isUnchanged(oldTotalValue, newTotalValue)
                && isUnchanged(oldTotalCost, newTotalCost)
                && isUnchanged(oldEstimatedProfit, newEstimatedProfit);
        if (unchanged) {
            return;
        }

        OrderValueChange change = OrderValueChange.builder()
                .order(order)
                .merchant(order.getMerchant())
                .oldTotalValue(oldTotalValue)
                .newTotalValue(newTotalValue)
                .oldTotalCost(oldTotalCost)
                .newTotalCost(newTotalCost)
                .oldEstimatedProfit(oldEstimatedProfit)
                .newEstimatedProfit(newEstimatedProfit)
                .changedAt(LocalDateTime.now(BRAZIL_ZONE))
                .source(source)
                .build();
        orderValueChangeRepository.save(change);
    }

    private boolean isUnchanged(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null || newValue == null) {
            return oldValue == newValue;
        }
        return oldValue.compareTo(newValue) == 0;
    }
}
