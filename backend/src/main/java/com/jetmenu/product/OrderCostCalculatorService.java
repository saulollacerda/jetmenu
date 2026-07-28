package com.jetmenu.product;

import com.jetmenu.order.Order;
import com.jetmenu.order.OrderFichaIngredient;
import com.jetmenu.order.OrderItem;
import com.jetmenu.order.OrderItemExtraIngredient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Calcula o custo total estimado de um {@link Order} no modelo aditivo:
 * <pre>
 *   item.unitCost   = ficha técnica (mandatory base) + sum(extras.quantity × extras.costPerUnit)
 *   item.totalCost  = item.unitCost × item.quantity
 *   order.totalCost = sum(item.totalCost) + fichaDoPedido
 *   fichaDoPedido   = sum(orderFicha.quantity × orderFicha.costPerUnit)   // UMA vez por pedido
 * </pre>
 * O {@code OrderItem.unitCost} é gravado por {@link ProductCostCalculator} no momento
 * em que o pedido é criado/importado — este service não consulta {@code ProductIngredient}.
 *
 * <p>A ficha do PEDIDO ({@link OrderFichaIngredient}) entra uma única vez, fora do laço dos
 * itens: são insumos consumidos por pedido e não por item (sacola, guardanapo). Ela é lida
 * do snapshot gravado no pedido, nunca da configuração viva — pedidos já fechados mantêm o
 * custo com que foram calculados.
 */
@Service
public class OrderCostCalculatorService {

    public OrderCostCalculatorService() {
    }

    public BigDecimal computeOrderTotalCost(Order order) {
        if (order == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal itemsCost = order.getItems() == null || order.getItems().isEmpty()
                ? BigDecimal.ZERO
                : order.getItems().stream()
                        .map(item -> computeItemTotalCost(item, order.getMerchant().getId()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return itemsCost.add(computeOrderFichaCost(order));
    }

    /**
     * Custo da ficha do PEDIDO: soma {@code quantity × costPerUnit} do snapshot
     * ({@link OrderFichaIngredient}) gravado no pedido.
     */
    public BigDecimal computeOrderFichaCost(Order order) {
        if (order == null || order.getOrderFicha() == null || order.getOrderFicha().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return order.getOrderFicha().stream()
                .map(line -> {
                    BigDecimal q = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
                    BigDecimal c = line.getCostPerUnit() != null ? line.getCostPerUnit() : BigDecimal.ZERO;
                    return q.multiply(c);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Custo unitário do item = base da ficha técnica ({@code item.unitCost}) +
     * soma dos extras (quantity × costPerUnit). NÃO multiplica pela quantidade do item.
     * Parâmetro {@code merchantId} reservado para evoluções futuras (snapshot externo).
     */
    public BigDecimal computeItemUnitCost(OrderItem item, UUID merchantId) {
        BigDecimal baseCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;
        BigDecimal extrasPerUnit = BigDecimal.ZERO;
        if (item.getExtraIngredients() != null) {
            for (OrderItemExtraIngredient extra : item.getExtraIngredients()) {
                BigDecimal q = extra.getQuantity() != null ? extra.getQuantity() : BigDecimal.ZERO;
                BigDecimal c = extra.getCostPerUnit() != null ? extra.getCostPerUnit() : BigDecimal.ZERO;
                extrasPerUnit = extrasPerUnit.add(q.multiply(c));
            }
        }
        return baseCost.add(extrasPerUnit);
    }

    private BigDecimal computeItemTotalCost(OrderItem item, UUID merchantId) {
        return computeItemUnitCost(item, merchantId)
                .multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}
