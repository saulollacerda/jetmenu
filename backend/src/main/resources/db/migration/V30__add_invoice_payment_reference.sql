-- V30 — Provider-agnostic external payment reference on invoices
--
-- Until now invoices.abacatepay_billing_id doubled as THE external payment reference:
-- Invoice.getExternalPaymentReference() aliased it and
-- InvoiceRepository.findByExternalPaymentReference() queried it. That was a stopgap left
-- behind when AbacatePay was removed. Stripe now writes its own references (Checkout
-- Session ids, cs_…) and they must never land in AbacatePay reconciliation data.
--
-- invoices.abacatepay_billing_id STAYS exactly as it is. Historical invoices link to real
-- AbacatePay payments through it and accounting reconciles against that link; V13 is
-- applied history and is not edited.
--
-- ⚠️ The UPDATE below is the step an EMPTY database cannot validate. The activation
-- idempotency guard now reads payment_reference only. Without copying the legacy values
-- across, every historical AbacatePay reference becomes invisible to that guard and a
-- replayed legacy webhook would activate a second subscription period for a payment that
-- was already settled. On an empty database the UPDATE matches zero rows and the omission
-- would look identical to a correct migration — which is exactly how V29 took production
-- down. Validate this against a database that actually contains invoices.

alter table invoices add column payment_reference varchar(255);

update invoices
   set payment_reference = abacatepay_billing_id
 where abacatepay_billing_id is not null;

-- Unique so the idempotency guard cannot be defeated by a duplicated reference.
-- Postgres allows many NULLs under a unique constraint, so invoices with no external
-- payment (manual/legacy rows) are unaffected. The constraint also creates the index the
-- lookup needs, so no separate CREATE INDEX is required.
alter table invoices
    add constraint invoices_payment_reference_key unique (payment_reference);
