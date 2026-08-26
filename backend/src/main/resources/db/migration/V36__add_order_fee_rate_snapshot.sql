-- V36 — Snapshot da taxa de meio de pagamento (fee.feeRate) vigente na venda
--
-- O lucro estimado do pedido descontava a taxa de meio de pagamento lendo SEMPRE ao vivo
-- fee.feeRate da Taxa associada — mas o resultado (estimated_profit) fica persistido no
-- pedido. Editar o percentual de uma Taxa na tela de Taxas (ex.: "Cartão 10%" → 25%) não
-- tocava no pedido, mas mudava o lucro recalculado ao vivo (tela de pedidos/detalhe) sem
-- mudar a coluna persistida que o dashboard e a exportação somam — as duas telas passavam
-- a divergir para o mesmíssimo pedido.
--
-- A partir de agora o pedido guarda a taxa que valeu NA VENDA: nenhum insumo da fórmula do
-- lucro (totalValue, deliveryFee, serviceFee, totalCost, feeRate) muda fora de OrderService,
-- então a coluna persistida nunca mais fica desatualizada. Editar uma Taxa depois não pode
-- reescrever o lucro de um mês já fechado.
alter table orders add column fee_rate numeric(19, 4);

-- Backfill de fee_rate: um pedido já existente não tem como recuperar a taxa que valeu na
-- venda de verdade — usamos a taxa ATUAL da Fee associada, a única ainda conhecida. Pedido
-- sem fee_id (nunca teve taxa) fica com fee_rate nulo, sem ser tocado.
update orders o
set fee_rate = f.fee_rate
from fees f
where o.fee_id = f.id;

-- Recomputa estimated_profit com a taxa recém-gravada, para que dashboard/exportação (que
-- somam a coluna persistida) batam com a tela de pedidos (que recalcula ao vivo) desde já —
-- sem isso o backfill de fee_rate por si só não resolveria a divergência histórica.
--
-- Espelha OrderCalculations.calculateEstimatedProfit:
--   subtotal   = total_value − delivery_fee − service_fee
--   fee_amount = round(subtotal × fee_rate / 100, 4)   -- arredondado ANTES de subtrair
--   profit     = subtotal − total_cost − fee_amount
-- round(x, 4) no Postgres é HALF_UP em numeric, igual a divide(..., 4, RoundingMode.HALF_UP)
-- no Java. O round(..., 2) mais externo espelha a escala da própria coluna estimated_profit.
--
-- Escopo: fee_id IS NOT NULL (só quem ganhou snapshot acima) AND total_cost IS NOT NULL —
-- pedido com total_cost nulo é um legado onde toResponse cai no fallback de recalcular o
-- custo a partir dos itens; um coalesce(total_cost, 0) aqui gravaria um lucro errado (como se
-- o custo fosse zero) nesses pedidos. Pedido sem taxa (fee_id nulo) não é tocado.
update orders o
set estimated_profit = round(
    (coalesce(o.total_value, 0) - coalesce(o.delivery_fee, 0) - coalesce(o.service_fee, 0))
    - o.total_cost
    - round(
        (coalesce(o.total_value, 0) - coalesce(o.delivery_fee, 0) - coalesce(o.service_fee, 0))
        * o.fee_rate / 100,
        4
      ),
    2
  )
where o.fee_id is not null
  and o.total_cost is not null;
