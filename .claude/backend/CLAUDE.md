# Backend — JetMenu

## Tech Stack

| Technology | Version / Details |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.3 |
| Spring Data JPA | `spring-boot-starter-data-jpa` |
| Spring Web MVC | `spring-boot-starter-webmvc` |
| Spring Security | `spring-boot-starter-security` |
| OAuth2 Resource Server | `spring-boot-starter-oauth2-resource-server` |
| OAuth2 Authorization Server | `spring-boot-starter-oauth2-authorization-server` |
| Validation | `spring-boot-starter-validation` |
| Lombok | Annotation processing enabled |
| H2 Database | Runtime (dev/test only) |
| PostgreSQL | 16 (production via Docker) |
| Build Tool | Maven Wrapper (`mvnw`) |
| Test — Slices | `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test` |
| Test — Unit | JUnit 5 (Jupiter), Mockito 5 (`mockito-core`, `mockito-junit-jupiter`) |
| Test — Security | `spring-security-test` |
| Containerization | Docker multi-stage: `eclipse-temurin:21-jdk` → `eclipse-temurin:21-jre` |

## Layer Responsibilities

| Layer | Annotation | Responsibility |
|---|---|---|
| Controller | `@RestController` | Receives HTTP requests, validates input, delegates to service, returns responses |
| Service | `@Service` | Business rules and orchestration logic |
| Repository | `JpaRepository` | Data access, Spring Data JPA auto-implementation |
| Entity | `@Entity` | JPA entity mapping to a PostgreSQL table |

## Conventions

- **Package-by-feature** — each domain gets its own package: Controller, Service, Repository, Entity.
- **Lombok** (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`) to reduce boilerplate.
- **DTOs** for all request/response payloads — never expose entities directly.
- **REST endpoints:** `/api/<feature>` (e.g. `/api/orders`, `/api/products`, `/api/dashboard`).
- **Dev profile:** `SPRING_PROFILES_ACTIVE=dev` for local development with H2.

## Data Model

Tenant-scoped multi-tenant: todas as entidades de negócio têm `merchant_id` (FK → `merchants.id`, NOT NULL) extraído do JWT do usuário autenticado via `MerchantContext`.

### Tabelas

| Tabela | Descrição | FKs principais |
|---|---|---|
| `merchants` | Tenant raiz — restaurante/lojista. Auth via email/senha, JWT sub = `merchants.id` | — |
| `customers` | Clientes do restaurante | `merchant_id` |
| `categories` | Categorias do cardápio | `merchant_id` |
| `products` | Itens do cardápio | `merchant_id`, `category_id` |
| `includes` | Ficha técnica do produto — componentes que sempre entram no custo (`name`, `cost`, `quantity` armazenados direto, **sem FK pra `ingredients`**) | `product_id` |
| `ingredients` | Catálogo de ingredientes — usado **apenas** para resolver extras de pedidos (subItems do Anota.AI) via match de nome canônico | `merchant_id` |
| `fees` | Taxas/formas de pagamento | `merchant_id` |
| `orders` | Pedidos | `merchant_id`, `customer_id`, `fee_id` (nullable) |
| `order_items` | Itens do pedido — `unit_price` e `unit_cost` são **snapshots** do momento do pedido | `order_id`, `product_id` |
| `order_item_extra_ingredients` | Extras escolhidos pelo cliente no pedido — `cost_per_unit`, `name`, `unit` são **snapshots** | `order_item_id`, `ingredient_id` |
| `order_item_unmatched_subitems` | SubItems de pedidos externos que ainda **não casaram** com nenhum `ingredient` (guardados para backfill quando o ingrediente for criado) — `canonical_name`, `name`, `quantity`, `price` | `order_item_id` |
| `notifications` | Notificações do sistema (ex.: `MISSING_INGREDIENT`) | `merchant_id` |

### Conceitos-chave

- **`includes` vs `ingredients`** — desacoplados desde o refactor:
  - `includes` = ficha técnica do produto (lojista define `name`, `cost`, `quantity` por produto, sem ligação com catálogo de `ingredients`).
  - `ingredients` = catálogo separado, referenciado **só** por `order_item_extra_ingredients` para tracking de extras dos pedidos.
- **Cálculo de custo**:
  - `product.unitCost = Σ(include.cost × include.quantity)` — via `ProductCostCalculator`.
  - `order.totalCost` = soma de `(item.unitCost × item.quantity) + extras` — via `OrderCostCalculatorService`.
- **Snapshots**: `order_items.unit_cost`, `order_items.unit_price`, e os campos de `order_item_extra_ingredients` (`cost_per_unit`, `name`, `unit`) preservam valores no momento do pedido — alterações posteriores em produtos/ingredientes não afetam o histórico financeiro.
- **Multi-tenant**: `MerchantContext.getMerchantId()` lê o `sub` do JWT. Todo repository tem queries `findByXAndMerchantId(...)` para garantir isolamento de tenant.
- **Match de extras (pedidos externos)**: `IngredientNameNormalizer` normaliza nomes (lowercase, sem acento, espaços colapsados) e busca em `ingredients.canonical_name`. Match não encontrado gera notificação `MISSING_INGREDIENT` **e** persiste o subItem em `order_item_unmatched_subitems`.
- **Backfill de ingredientes**: ao criar um `Ingredient`, `OrderIngredientBackfillService` (acionado por `IngredientCreatedEvent`) busca subItems pendentes em `order_item_unmatched_subitems` por `canonical_name`, cria os `OrderItemExtraIngredient` correspondentes, remove o unmatched e recalcula `total_cost`/`estimated_profit`. **Totalmente interno ao core — não chama nenhuma API externa** (o dado cru já foi persistido na importação).

### Endpoints principais

| Recurso | Endpoint |
|---|---|
| Merchant | `/api/merchants` |
| Customer | `/api/customers` |
| Category | `/api/categories` |
| Product | `/api/products` |
| Ficha técnica do produto | `/api/products/{productId}/includes` |
| Ingredient | `/api/ingredients` |
| "Onde este ingrediente é usado" (match por nome em includes) | `/api/ingredients/{id}/usages` |
| Fee | `/api/fees` |
| Order | `/api/orders` |
| Notification | `/api/notifications` |
| Auth | `/api/auth/login`, `/api/auth/register` |
| Dashboard | `/api/dashboard?from=&to=` |
| Internal — AnotaAI key | `GET /api/internal/merchants/{merchantId}/anotaai-key` (called by adapter) |

## RabbitMQ — External Order Integration

The backend consumes orders published by `anotaai-adapter` (and future adapters). It has **no direct AnotaAI dependency** — it processes platform-agnostic `ExternalOrderMessage` events.

| Queue | Consumer | Purpose |
|---|---|---|
| `menubank.external-orders` | `ExternalOrderConsumer` | Creates orders from external platforms |
| `menubank.catalog-sync` | `ExternalCatalogConsumer` | Syncs categories/products from external catalog |

### Package `integration/external`

| Class | Responsibility |
|---|---|
| `ExternalOrderMessage` | Canonical DTO received from queue |
| `ExternalOrderConsumer` | `@RabbitListener` — orchestrates order import |
| `ExternalOrderImportService` | Creates Order/Customer/Items from the message |
| `ExternalCustomerResolver` | Finds or creates Customer from message data |
| `ExternalProductResolver` | Finds Product by internalId/externalId |
| `ExternalExtraIngredientResolver` | Maps subItems to OrderItemExtraIngredient |
| `ExternalCatalogMessage` | Canonical catalog DTO received from queue |
| `ExternalCatalogConsumer` | `@RabbitListener` — triggers catalog sync |
| `ExternalCatalogSyncService` | Creates/updates Category and Product entities |

### Internal endpoint (called by adapter)

`GET /api/internal/merchants/{merchantId}/anotaai-key` — returns `{ apiKey }` or 404.
Authenticated via `X-Internal-Secret` header (shared secret between adapter and core).

## Running locally

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```