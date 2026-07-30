# Billing Provider Integration

**Status: Stripe is integrated** (2026-07-29). It plugs into the seam described below;
JetMenu's own billing domain — plans, subscriptions, invoices and the activation rules — was
reused untouched. AbacatePay, the previous provider, is gone; the history it left behind is
described under "The two legacy columns".

`POST /api/subscription/checkout` creates a **Stripe hosted Checkout Session in subscription
mode** and returns its URL. Sandbox and production run the same code and differ only in env
var values (test-mode vs live-mode keys and price ids) — see "Config".

When `STRIPE_API_KEY` is empty the endpoint still answers **HTTP 503** with a pt-BR
ProblemDetail, so an unconfigured environment fails explicitly instead of throwing 500s.

**Known gap:** only `checkout.session.completed` is handled. Stripe will keep charging on
renewal, but JetMenu's `currentPeriodEnd` does not roll forward on its own — recurring
`invoice.paid` handling still needs to be written and verified against the sandbox.

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

## What a provider must supply — all implemented by Stripe

| Requirement | Stripe implementation |
|---|---|
| Checkout | `StripeBillingProvider` + `StripeCheckoutGateway` (`mode=subscription`, pt-BR locale) |
| Plan → price mapping | `StripePriceResolver`, config-driven (`stripe.price-ids.<slug>`) |
| Callback authentication | `StripeEventVerifier` — HMAC `Stripe-Signature` vs `STRIPE_WEBHOOK_SECRET` |
| Webhook | `StripeWebhookController` → `StripeWebhookService` → `SubscriptionActivationService` |
| Security entry | `.requestMatchers("/api/webhooks/stripe").permitAll()` |
| Payment reference column | `V30`, `invoices.payment_reference` |

`UnavailableBillingProvider` was **deleted** rather than kept behind `@Primary`:
`StripeBillingProvider` answers the same 503 when unconfigured, so keeping both would have
left dead code plus a two-unqualified-bean startup hazard.

The subsections below record what the AbacatePay removal took out and therefore what Stripe
had to restore — they are the rationale for the shape above, not open work.

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

✅ **Resolved by `V30__add_invoice_payment_reference.sql`.** The stopgap that had the
activation idempotency guard reading `invoices.abacatepay_billing_id` is gone:

- `invoices.payment_reference` (`varchar(255)`, unique) is now the external-payment-reference
  column. It holds the Stripe Checkout Session id (`cs_…`).
- `Invoice.getExternalPaymentReference()` / `setExternalPaymentReference()` are `@Transient`
  aliases over `paymentReference`, and
  `InvoiceRepository.findByExternalPaymentReference(...)` queries that column.
- V30 backfills `payment_reference` from `abacatepay_billing_id` where present, so historical
  AbacatePay references stay visible to the idempotency guard. Without that copy, a replayed
  legacy webhook would activate a second period for an already-settled payment.
- `abacatepay_billing_id` is now **frozen history**: nothing writes it.

V30 was validated against a *populated* Postgres (rows seeded before the migration, backfill
and the untouched legacy column asserted after), not just an empty one — an empty database
cannot exercise the `UPDATE`, which is precisely how V29 reached production broken.

Note: `invoices.stripe_invoice_id` also exists and predates AbacatePay. It is dead too.

---

## Config

| Env var | Sandbox | Production |
|---|---|---|
| `STRIPE_API_KEY` | `sk_test_…` / `rk_test_…` | `sk_live_…` / `rk_live_…` |
| `STRIPE_WEBHOOK_SECRET` | `whsec_…` (test endpoint or `stripe listen`) | `whsec_…` (live endpoint) |
| `STRIPE_PRICE_BASICO` | `price_…` created in test mode | `price_…` created in live mode |
| `APP_FRONTEND_BASE_URL` | `http://localhost:5173` | `https://app.jetmenu.com.br` |

All default to empty, in `application-dev.properties`, `application-prod.properties`,
`backend/.env.example` and `backend/src/test/resources/application.properties`. **Same code
in both modes — only the values differ.** Register the webhook at `POST /api/webhooks/stripe`
for `checkout.session.completed`.

Price ids live in config, not in a `plans` column, so a new environment needs no database
rows. Adding a plan means adding a `stripe.price-ids.<slug>` entry; a plan with no configured
price id fails with 503 rather than silently succeeding.

## State of the integration

Done: Stripe SDK, `BillingProvider` implementation, `V30` + repointed accessors, webhook
controller with signature verification, `SecurityConfig` entry, config/env vars, and the
frontend call site restored in `SettingsView.vue`. Written test-first — 35 new tests, full
backend suite 1063 passing.

Note the in-app pricing page (`PlansView.vue`) no longer exists: pricing moved to the
landing page, which links to `/checkout?plan=<slug>`. `SettingsView.vue` is now the only
in-app checkout entry point, and it is the renew/upgrade path for a lapsed subscription.

Remaining: recurring `invoice.paid` handling, and end-to-end verification against the Stripe
sandbox (nothing here has run against a real Stripe account).
