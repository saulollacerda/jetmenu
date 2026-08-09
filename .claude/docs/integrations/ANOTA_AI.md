# Anota.AI Integration

JetMenu integrates with Anota.AI to import orders and sync the product catalog.
Authentication: partner token via `Authorization: Bearer <token>` header, configured per merchant.

## Base URLs

| Purpose | Base URL |
|---|---|
| Orders (PDV) | `https://api-parceiros.anota.ai/partnerauth` |
| Menu / Catalog | `https://api-menu.anota.ai/partnerauth` |

## `check` Field — Order Status

| Value | Meaning |
|---|---|
| `0` | Pending — not yet imported |
| `1` | Acknowledged / imported by JetMenu |
| `2` | In preparation / dispatched (iFood flow) |
| `3` | Completed / delivered |

**Import only orders with `check: 0`.**

## Integration Architecture

Orders flow through the `anotaai-adapter` microservice — the backend core has no direct dependency on AnotaAI.

```
AnotaAI ──► POST /webhook/orders/{merchantId} ──► anotaai-adapter ──► RabbitMQ [menubank.external-orders] ──► backend consumer ──► DB
Admin   ──► POST /catalog/sync/{merchantId}   ──► anotaai-adapter ──► AnotaAI API ──► RabbitMQ [menubank.catalog-sync] ──► backend consumer ──► DB
```

The adapter is **multi-tenant**: webhook URL contains the `merchantId`. The adapter fetches the merchant's API key from the core via `GET /api/internal/merchants/{merchantId}/anotaai-key` when needed (catalog sync).

The pull-based sync (`AnotaAIController`, `AnotaAISyncService`, `OrderSyncScheduler`) is **still
present and active** in the backend — see [Pull-based Sync](#pull-based-sync-still-in-the-backend)
below. It is the path the iFood order preview flag hooks into.

## Webhook — Incoming Order Payload

AnotaAI sends the full order detail directly in the webhook body (same structure as the old `/ping/get/{id}` response). The adapter maps it to an `ExternalOrderMessage` and publishes to the queue.

**Webhook URL registered in AnotaAI:** `POST https://<adapter-host>/webhook/orders/{merchantId}`
**Security:** `X-Webhook-Secret` header validated against `WEBHOOK_SECRET` env var.

## Canonical Message Format (`ExternalOrderMessage`)

```json
{
  "externalOrderId": "6a0e094aa2335ae5e05c5eae",
  "merchantId": "uuid",
  "origin": "ANOTA_AI",
  "createdAt": "2026-05-20T19:19:38.368Z",
  "paymentName": "ifood-online-pix-payin",
  "deliveryFee": 0.0,
  "total": 25.99,
  "customer": { "name": "Maria Santos", "phone": "11912345678", "taxId": null },
  "items": [
    {
      "internalId": "66c3adfc0fae7c422a4a6c9a",
      "externalId": "",
      "name": "Açaí 500 ml",
      "quantity": 1,
      "price": 21.99,
      "subItems": [
        { "name": "Açaí Zero", "quantity": 1, "price": 0.0, "internalId": "679ab6c7207b65c7415b6614" }
      ]
    }
  ]
}
```

## Pull-based Sync (still in the backend)

Alongside the adapter, the backend still runs the original pull-based sync — it is what
`POST /api/anotaai/orders` and `OrderSyncScheduler` (every 10 min, for open merchants with an
API key) drive:

```
poll /ping/list → filter by salesChannel → fetch each via /ping/get/{id} → persist locally
```

Entry points: `AnotaAIController`, `OrderSyncScheduler` → `AnotaAISyncService.syncOrders`.

## iFood Order Preview — temporary feature flag

`anotaai.import-ifood-orders-enabled` (env `ANOTAAI_IMPORT_IFOOD_ORDERS_ENABLED`, **default
`false`**).

Anota.AI's `/ping/list` also returns the orders the merchant received through iFood
(`salesChannel: "ifood"`). Normally `syncOrders` skips them. With the flag on they are
imported and stored with `origin = IFOOD`, so merchants get a taste of the iFood numbers
inside JetMenu before the direct iFood integration ships.

How these orders differ at import time:

| Aspect | Handling |
|---|---|
| `items[].internalId` | Empty — the product is resolved by **canonical name** (`AnotaAIProductResolver` falls back to `products.canonical_name`) |
| `items[].subItems[].internalId` | Empty — extras already resolve by canonical name, unchanged |
| `total` | Used as-is: already net of `discounts` and inclusive of `deliveryFee` + `additionalFees` |
| `additionalFees` | Summed into `serviceFee` and excluded from profit, same as the Anota.AI path |
| iFood ticket ("Comanda") | **Not offered** — these orders have no `displayId` and don't exist in the iFood API, so its confirm/dispatch/cancel actions would fail |

> **Turn this off when the direct iFood integration takes over these orders.** The external ids
> differ (Anota.AI `_id` vs iFood order id), so the duplicate check would not catch the same
> order arriving through both paths.

Because product matching is by name, `products.canonical_name` must be populated — the catalog
sync now stamps it, and `V33__backfill_product_canonical_name.sql` fills existing rows.

---

## Endpoints

### List orders — `GET /ping/list`

```json
{
  "success": true,
  "info": {
    "docs": [
      { "_id": "6a0e28468e8bf5733592fb7a", "check": 2, "from": "ifood-marketplace", "salesChannel": "ifood", "updatedAt": "2026-05-20T20:57:32.968Z" },
      { "_id": "6a0e094aa2335ae5e05c5eae", "check": 3, "from": "menu-share-adm",    "salesChannel": "anotaai", "updatedAt": "2026-05-20T20:41:00.884Z" }
    ],
    "count": 4,
    "limit": 100,
    "currentpage": 1
  }
}
```

### Get order detail — `GET /ping/get/{orderId}`

---

### iFood order — `from: "ifood-marketplace"` / `salesChannel: "ifood"`

```json
{
  "success": true,
  "info": {
    "_id": "6a0e28468e8bf5733592fb7a",
    "check": 2, "type": "DELIVERY",
    "salesChannel": "ifood", "from": "ifood-marketplace",
    "shortReference": 7452,
    "createdAt": "2026-05-20T21:31:50.786Z",
    "total": 14.48, "deliveryFee": 4,
    "additionalFees": [{ "type": "RESTAURANT_SERVICE_FEE_...", "description": "Taxa de serviço", "value": 0.99 }],
    "discounts": [{ "amount": 9, "tag": "Total de descontos - iFood", "target": "CART" }],
    "customer": { "id": null, "name": "João Silva", "phone": "11987654321", "localizer": "12345678" },
    "deliveryAddress": {
      "formattedAddress": "Rua das Flores, 123, Apto 4",
      "city": "São Paulo", "state": "SP", "postalCode": "01310100",
      "neighborhood": "Vila Madalena",
      "coordinates": { "latitude": -23.550520, "longitude": -46.633308 },
      "ifood_pickup_code": "4321"
    },
    "items": [{
      "_id": "...", "id": 0,
      "name": "Açaí 330 ml", "quantity": 1, "price": 18.49, "total": 18.49,
      "externalId": "", "internalId": "",
      "subItems": [
        { "name": "Açaí Zero", "quantity": 1, "price": 0, "total": 0, "externalId": "", "internalId": "" },
        { "name": "Banana",    "quantity": 1, "price": 0, "total": 0, "externalId": "", "internalId": "" }
      ]
    }],
    "payments": [{ "name": "pix-ifood", "code": "pix-ifood", "value": "14.48", "prepaid": true }],
    "merchant": { "name": "Restaurante Exemplo", "id": "aabb1122...", "unit": "ccdd3344..." },
    "ifood_order_integration": { "order_v4": false },
    "observation": ""
  }
}
```

**iFood total formula:**
```
total = sum(items[].price × quantity) + deliveryFee
      - sum(discounts[].amount)
      + sum(additionalFees[].value)
// Example: 18.49 + 4.00 - 9.00 + 0.99 = 14.48
```

---

### Anota.AI order — `from: "menu-share-adm"` / `salesChannel: "anotaai"`

```json
{
  "success": true,
  "info": {
    "_id": "6a0e094aa2335ae5e05c5eae",
    "check": 3, "type": "DELIVERY",
    "salesChannel": "anotaai", "from": "menu-share-adm",
    "shortReference": 5173,
    "createdAt": "2026-05-20T19:19:38.368Z",
    "total": 25.99, "deliveryFee": 0,
    "additionalFees": [], "discounts": [],
    "customer": { "id": "aabbcc112233...", "name": "Maria Santos", "phone": "11912345678", "taxPayerIdentificationNumber": null },
    "deliveryAddress": {
      "formattedAddress": "Rua das Palmeiras, 45, Mocambinho II, Nº 45",
      "city": "São Paulo", "state": "SP", "postalCode": "01310-100",
      "neighborhood": "Vila Madalena",
      "coordinates": { "latitude": -23.561684, "longitude": -46.655981 },
      "reference": "Próximo à escola", "complement": "Próximo à escola"
    },
    "items": [{
      "_id": "...", "id": 0,
      "name": "Açaí 500 ml", "quantity": 1, "price": 21.99, "total": 25.99,
      "internalId": "66c3adfc0fae7c422a4a6c9a",
      "backoffice_id": "1968578289",
      "subItems": [
        { "name": "Açaí Zero", "quantity": 1, "price": 0,   "total": 0,   "internalId": "679ab6c7207b65c7415b6614" },
        { "name": "Pistache",  "quantity": 1, "price": 1.5, "total": 1.5, "internalId": "693de3b9d994310a1d532f45" },
        { "name": "Morango",   "quantity": 1, "price": 1.5, "total": 1.5, "internalId": "6871b1adcc7d0f025fde518a" },
        { "name": "Cereja",    "quantity": 1, "price": 1.0, "total": 1.0, "internalId": "6871b1adcc7d0f025fde524b" }
      ]
    }],
    "payments": [{ "name": "ifood-online-pix-payin", "code": "ifood-online-pix-payin", "value": "25.99", "prepaid": true }],
    "merchant": { "name": "Restaurante Exemplo", "id": "aabb1122...", "unit": "ccdd3344..." },
    "ifood_order_integration": { "order_v4": true, "merchant_id": "dddd-eeee-ffff-..." },
    "observation": null
  }
}
```

**Anota.AI total formula:**
```
total = item.total (already includes subItems — use directly)
// Example: 21.99 + 0 + 1.5 + 1.5 + 1.0 = 25.99
```

---

## iFood vs Anota.AI — Key Differences

| Field | iFood (`ifood-marketplace`) | Anota.AI (`menu-share-adm`) |
|---|---|---|
| `additionalFees` | Has service fees | Empty array |
| `discounts` | Has iFood promotional discounts | Empty array |
| `items[].internalId` | Empty string | JetMenu product ID |
| `items[].subItems[].internalId` | Empty string | Anota.AI complement ID (extras são resolvidos por **match de nome canônico** contra a tabela `ingredients`, não pelo internalId) |
| `items[].backoffice_id` | Empty | Anota.AI backoffice numeric ID |
| `customer.localizer` | Present (pickup code) | Absent |
| `deliveryAddress.ifood_pickup_code` | Present | Absent |
| `ifood_order_integration.order_v4` | `false` | `true` (includes `merchant_id`) |
| `total` composition | items + delivery - discounts + fees | `item.total` already includes subItems |
| `observation` | `""` (empty string) | `null` |

---

## Catalog Sync

### Export — `GET /v2/nm-category/rest/simple-item/export/v2`

Returns full menu as a **flat `data[]` array** — both product categories (`is_additional: false`) and complement groups (`is_additional: true`). No nesting; relationships expressed via IDs.

Key fields per category:

| Field | Description |
|---|---|
| `id` | Anota.AI category ID |
| `title` | Category name |
| `is_additional` | `true` = complement group, `false` = product |
| `itens[].id` | Anota.AI item ID |
| `itens[].title` | Item name |
| `itens[].week_prices` | Array of `{ price, short_name }` (sun–sat) |
| `itens[].next_steps` | Linked complement categories |
| `itens[].out` | `true` = out of stock |

### Integration Notes

- Item price = current day-of-week from `week_prices`.
- `internalId` on order items maps to JetMenu product ID; `externalId` is the Anota.AI reference.
- The `/ping/get/{id}` response already contains resolved `subItems` with calculated prices — no need to re-resolve `next_steps` at import time.