-- V34 — Snapshot dos valores originais antes de uma correção manual do pedido
--
-- O lojista pode corrigir manualmente o valor final (total_value) e o custo total
-- (total_cost) de um pedido — desconto dado por telefone, insumo perdido, reembolso ao
-- entregador — casos que o cálculo automático a partir dos itens não tem como prever.
--
-- Antes da primeira correção, o sistema tira um snapshot dos valores calculados
-- (original_total_value, original_total_cost, original_estimated_profit) e marca
-- values_overridden_at. O snapshot é gravado uma única vez: correções manuais seguintes
-- não o sobrescrevem, preservando o valor original de verdade.
--
-- Todas as quatro colunas são nullable e ficam nulas em todo pedido nunca corrigido
-- manualmente — inclusive os já existentes.
alter table orders add column original_total_value numeric(19, 4);
alter table orders add column original_total_cost numeric(19, 4);
alter table orders add column original_estimated_profit numeric(19, 4);
alter table orders add column values_overridden_at timestamp;
