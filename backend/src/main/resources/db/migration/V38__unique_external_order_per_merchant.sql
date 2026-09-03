-- Deduplicação de pedidos externos no banco, não só em código.
--
-- Até aqui a única proteção era o read-then-write de
-- OrderRepository.existsByExternalOrderIdAndMerchantId. Com reentrega de webhook e várias
-- instâncias atendendo em paralelo, essa corrida deixa de ser teórica: duas entregas
-- simultâneas do mesmo pedido leem "não existe" antes de qualquer uma gravar.
--
-- Índice PARCIAL: pedidos manuais têm external_order_id nulo e precisam continuar podendo
-- se repetir à vontade.
--
-- ANTES DE APLICAR, conferir que não há duplicata pré-existente (a criação do índice falha
-- e trava o deploy se houver):
--
--   SELECT merchant_id, external_order_id, count(*)
--     FROM orders
--    WHERE external_order_id IS NOT NULL
--    GROUP BY merchant_id, external_order_id
--   HAVING count(*) > 1;
CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_merchant_external_order
    ON orders (merchant_id, external_order_id)
    WHERE external_order_id IS NOT NULL;
