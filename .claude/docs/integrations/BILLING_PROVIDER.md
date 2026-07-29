# Billing Provider Integration

**Status: no payment provider is integrated.** AbacatePay was removed; the next provider
plugs into the seam described here. JetMenu's own billing domain — plans, subscriptions,
invoices and the activation rules — was left untouched and is ready to be reused on day one.

While no provider exists, `POST /api/subscription/checkout` answers **HTTP 503** with a
pt-BR ProblemDetail, and both plan screens show a "pagamento temporariamente indisponível"
notice instead of a checkout button.

---

## The seam

Everything lives in `backend/src/main/java/com/jetmenu/billing/`.

### 1. `BillingProvider` — the interface to implement

```java
public interface BillingProvider {
    CheckoutResponse createCheckout(UUID merchantId, UUID planId);
}
```

`CheckoutResponse` carries a single field, `url` — where the merchant is sent to pay.

Contract:

| Situation | Expected behaviour |
|---|---|
| Plan does not exist | throw `PlanNotFoundException` → 404 |
| Merchant does not exist | throw `MerchantNotFoundException` → 404 |
| Provider unreachable / not configured | throw `BillingProviderUnavailableException` → 503 |
| Success | return a `CheckoutResponse` with the hosted checkout URL |

The provider is responsible for carrying `merchantId` and `planId` through checkout
(metadata, external id, whatever the platform offers) and decoding them back in its webhook.
AbacatePay did this with an external id shaped `menubank:<merchantId>:<planId>`; that
convention died with it — pick whatever the new platform supports.

`SubscriptionController` injects `BillingProvider` and does nothing else. Do not put
provider logic in the controller.

### 2. `UnavailableBillingProvider` — the current default bean

A `@Service` that logs a warning and throws `BillingProviderUnavailableException`
(pt-BR message, mapped to 503 in `GlobalExceptionHandler`). It exists so the checkout
endpoint fails **explicitly** rather than silently.

When the new provider ships, either delete this class or annotate the new implementation
`@Primary` — two unqualified `BillingProvider` beans will otherwise fail context startup.

### 3. `SubscriptionActivationService` — where activation happens

This is JetMenu domain logic, not provider code. It survived the AbacatePay removal
verbatim; only its signature was translated into JetMenu's own terms.

```java
@Transactional
void activatePaidSubscription(UUID merchantId,
                              UUID planId,
                              BigDecimal amountPaid,          // null → falls back to plan.priceMonthly
                              String externalPaymentReference) // the idempotency key
```

Guarantees (all covered by `SubscriptionActivationServiceTest`):

- **Idempotent.** If an invoice already exists for `externalPaymentReference`, the call is a
  no-op — a provider may safely retry its webhook.
- Subscription is set to `ACTIVE`, `plan` is assigned, and `currentPeriodStart` /
  `currentPeriodEnd` roll forward to `now .. now + 1 month`.
- A `PAID` `Invoice` is written with `paidAt = dueAt = createdAt = now`.
- Amount falls back to the plan's monthly price when the provider reports none.
- Throws `SubscriptionNotFoundException` (merchant has no subscription row) or
  `PlanNotFoundException`. One transaction: either both the subscription and the invoice
  changed, or neither did.

**Do not reimplement any of this inside the provider package.** The webhook decodes the
payload; this service owns what "paid" means.

---

## What was removed and must be re-added

### The webhook endpoint

`AbacatePayWebhookController` (`POST /api/webhooks/abacatepay`) is gone. The new provider
needs its own controller that:

1. authenticates the callback (AbacatePay used a shared secret in a query parameter compared
   with `MessageDigest.isEqual`; prefer a signature header if the platform offers one),
2. ignores events it does not handle, returning `200` so the provider stops retrying,
3. calls `SubscriptionActivationService.activatePaidSubscription(...)`.

### The security entry

`SecurityConfig` had `.requestMatchers("/api/webhooks/abacatepay").permitAll()`. **It was
removed.** Without an equivalent `permitAll()` for the new webhook path, the provider's
server-to-server callbacks are rejected with **401** and payments will never activate a
subscription. A comment in `SecurityConfig` marks the spot.

### Config

All `abacatepay.*` properties and `ABACATEPAY_*` env vars were deleted from
`application-dev.properties`, `application-prod.properties`, `backend/.env.example` and
`backend/src/test/resources/application.properties`.

`app.frontend-base-url` (`APP_FRONTEND_BASE_URL`) was **kept** — it is provider-neutral and
the next integration will need it to build return/completion URLs. Nothing reads it today.

### Frontend

`frontend/src/services/billingService.ts` is **intact**, including `createCheckout(planId)`
→ `POST /subscription/checkout`. Only the call sites were removed:

- `frontend/src/views/SettingsView.vue` — the "Assinar com Pix" button was replaced by a
  notice (`data-testid="billing-unavailable-notice"`). Plan cards and subscription status
  still render.
- `frontend/src/views/PlansView.vue` — the public pricing page. Anonymous visitors still go
  to `/register` (sign-up is unaffected); authenticated visitors get a notice
  (`data-testid="plan-unavailable-notice"`).

Both files carry a `NOTE:` comment naming the exact call to restore.

**The billing gate is enforced in the frontend and keys off subscription `status`, never off
the plan.** Removing the provider did not change that, and a new provider must not change it
either — granting a plan does not unblock anything; only `status = ACTIVE` does.

---

## The two legacy columns — do not drop, do not reuse

`V13__add_abacatepay_billing_columns.sql` created:

| Table | Column | JPA field |
|---|---|---|
| `plans` | `abacatepay_product_id` | `Plan.abacatepayProductId` |
| `invoices` | `abacatepay_billing_id` | `Invoice.abacatepayBillingId` |

They **stay**. Historical invoices link to real payments at AbacatePay through them and
accounting reconciles against that link. `V13` is applied migration history — never edit it,
and write no migration to drop or rename these.

Nothing writes `plans.abacatepay_product_id` any more; the field is retained for
reconciliation only.

⚠️ **One thing the next session must fix.** `invoices.abacatepay_billing_id` is currently the
only external-payment-reference column in the schema, so the activation idempotency guard is
temporarily wired to it:

- `Invoice.getExternalPaymentReference()` / `setExternalPaymentReference()` are `@Transient`
  aliases over `abacatepayBillingId`.
- `InvoiceRepository.findByExternalPaymentReference(...)` is an explicit `@Query` over the
  same column.

This is a stopgap so the guard stays functional and tested with no schema change. **The new
provider must add its own column** (e.g. `invoices.payment_reference`) via a new migration
and repoint those two accessors, so new payments never mix with AbacatePay reconciliation
data. Everything else in the activation service is already provider-agnostic — that is the
only edit.

Note: `invoices.stripe_invoice_id` also exists and predates AbacatePay. It is dead too.

---

## Checklist for the next integration

1. Add the SDK/HTTP client under `backend/.../integration/<provider>/`.
2. Implement `BillingProvider`; make it `@Primary` or delete `UnavailableBillingProvider`.
3. Add a migration for the provider's own payment-reference column on `invoices`, and
   repoint `Invoice.getExternalPaymentReference()` and
   `InvoiceRepository.findByExternalPaymentReference(...)` to it.
4. Add the webhook controller; call `SubscriptionActivationService.activatePaidSubscription`.
5. Add `.requestMatchers("/api/webhooks/<provider>").permitAll()` to `SecurityConfig`.
6. Add config properties + env vars (dev, prod, `.env.example`, test properties).
7. Frontend: restore `billingService.createCheckout(plan.id)` in `SettingsView.vue` and
   `PlansView.vue`, and remove the two unavailability notices.
8. TDD throughout — see `.claude/docs/CODING_GUIDELINES.md`.
