-- NotificationType gained ORDER_CANCELLATION_REQUESTED: iFood emits a CANCELLATION_REQUESTED
-- event when the customer (or the platform) asks to cancel an order, and the Order module
-- homologation requires the merchant to be able to accept or reject it. The alert is a
-- distinct type because, unlike ORDER_CANCELLED, nothing changed in the order yet — the
-- merchant still owes an answer.
--
-- The inline enum check constraint last widened in V7 must accept the new value, otherwise
-- the insert fails at runtime. Nothing else changes: no new table, no new column.
alter table notifications drop constraint if exists notifications_type_check;
alter table notifications add constraint notifications_type_check
    check (type in ('MISSING_INGREDIENT','MISSING_PRODUCT','ORDER_CANCELLED',
                    'ORDER_CANCELLATION_REQUESTED'));
