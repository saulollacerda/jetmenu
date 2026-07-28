-- Descriptive order data required by the iFood Order module homologation: card brand,
-- cash change (troco), coupon value and who sponsors it (iFood or the merchant), item
-- observations, delivery instructions, collection code and the customer's CPF/CNPJ.
--
-- Everything here is display-only. None of these columns takes part in the financial math
-- (total_value, delivery_fee, service_fee, total_cost, estimated_profit) — discounts are
-- recorded to be shown, never deducted.
--
-- Backwards compatible: every column is nullable and the new table starts empty, so orders
-- imported before this migration (and every manual order) simply carry nulls.

alter table orders add column display_id varchar(60);
alter table orders add column order_type varchar(20);
alter table orders add column order_timing varchar(20);
alter table orders add column customer_document varchar(32);

alter table orders add column payment_prepaid_amount numeric(19,4);
alter table orders add column payment_pending_amount numeric(19,4);

alter table orders add column discount_total numeric(19,4);
alter table orders add column discount_ifood_value numeric(19,4);
alter table orders add column discount_merchant_value numeric(19,4);

alter table orders add column delivery_mode varchar(40);
alter table orders add column delivered_by varchar(40);
alter table orders add column delivery_date_time timestamp(6);
alter table orders add column delivery_observations varchar(1024);
alter table orders add column pickup_code varchar(40);

alter table orders add column takeout_mode varchar(40);
alter table orders add column takeout_date_time timestamp(6);

alter table orders
    add constraint orders_order_type_check
    check (order_type is null or order_type in ('DELIVERY','TAKEOUT','DINE_IN'));

alter table orders
    add constraint orders_order_timing_check
    check (order_timing is null or order_timing in ('IMMEDIATE','SCHEDULED'));

-- Special instructions written by the customer for a single item ("sem cebola").
alter table order_items add column observations varchar(1024);

-- One row per payment method of an imported order. A single order can legitimately be
-- split across more than one method (part online, the rest in cash), so this cannot be a
-- set of columns on orders.
create table order_payment_methods (
    id         uuid not null,
    order_id   uuid not null,
    method     varchar(60),
    type       varchar(40),
    card_brand varchar(60),
    value      numeric(19,4),
    currency   varchar(10),
    change_for numeric(19,4),
    primary key (id)
);

alter table if exists order_payment_methods
    add constraint FK_order_payment_methods_order
    foreign key (order_id) references orders;

create index idx_order_payment_methods_order
    on order_payment_methods (order_id);
