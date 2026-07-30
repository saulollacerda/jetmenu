-- Margem ideal (target margin) do produto.
--
-- products.target_margin_pct: percentual que o lojista quer atingir no produto. Nullable —
-- null significa "não acompanhada". Produtos existentes ficam null: nada é backfillado.
--
-- order_items.target_margin_pct: SNAPSHOT da margem ideal do produto no momento em que o
-- item do pedido é criado. Fica null nos pedidos já existentes (o produto não tinha margem
-- ideal quando foram feitos) e não é reescrito quando o produto muda depois — o histórico
-- conserva o alvo com que o pedido foi vendido.
alter table products
    add column target_margin_pct numeric(5, 2);

alter table order_items
    add column target_margin_pct numeric(5, 2);
