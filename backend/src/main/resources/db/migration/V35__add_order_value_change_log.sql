-- Trilha de auditoria das mudanças de valor financeiro do pedido — não para o lojista,
-- para o desenvolvedor debugar quando um pedido aparece com valor/lucro errado.
--
-- Cada correção manual (updateValues/restoreValues), edição de itens (update) ou
-- recomputo assíncrono de custo (OrderIngredientBackfillService) que efetivamente muda
-- totalValue/totalCost/estimatedProfit grava uma linha aqui com o valor ANTES e DEPOIS.
-- Nenhuma linha é gravada quando a operação não muda nada (ex.: editar itens sem alterar
-- o total) — um log cheio de linhas no-op atrapalharia mais do que ajudaria a debugar.
create table order_value_changes (
    id                     uuid not null,
    order_id               uuid not null,
    merchant_id            uuid not null,
    old_total_value        numeric(19,4),
    new_total_value        numeric(19,4),
    old_total_cost         numeric(19,4),
    new_total_cost         numeric(19,4),
    old_estimated_profit   numeric(19,4),
    new_estimated_profit   numeric(19,4),
    changed_at             timestamp not null,
    source                 varchar(30) not null,
    primary key (id)
);

alter table if exists order_value_changes
    add constraint FK_order_value_changes_order foreign key (order_id) references orders;
alter table if exists order_value_changes
    add constraint FK_order_value_changes_merchant foreign key (merchant_id) references merchants;

alter table order_value_changes
    add constraint order_value_changes_source_check
        check (source in ('MANUAL_OVERRIDE', 'RESTORE', 'ITEM_EDIT', 'INGREDIENT_BACKFILL'));

-- A pergunta de debug é sempre "tudo que aconteceu com este pedido, em ordem".
create index idx_order_value_changes_order_changed_at on order_value_changes (order_id, changed_at);
