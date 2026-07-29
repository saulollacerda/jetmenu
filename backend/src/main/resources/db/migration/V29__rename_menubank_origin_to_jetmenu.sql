-- V29 — Rename MENUBANK origin value to JETMENU (product rebrand)
update orders set origin = 'JETMENU' where origin = 'MENUBANK';
update products set origin = 'JETMENU' where origin = 'MENUBANK';
update categories set origin = 'JETMENU' where origin = 'MENUBANK';

alter table orders drop constraint orders_origin_check;
alter table orders add constraint orders_origin_check
    check (origin in ('JETMENU', 'ANOTA_AI', 'IFOOD'));

alter table products drop constraint products_origin_check;
alter table products add constraint products_origin_check
    check (origin in ('JETMENU', 'ANOTA_AI', 'IFOOD'));
alter table products alter column origin set default 'JETMENU';

alter table categories drop constraint categories_origin_check;
alter table categories add constraint categories_origin_check
    check (origin in ('JETMENU', 'ANOTA_AI', 'IFOOD'));
alter table categories alter column origin set default 'JETMENU';
