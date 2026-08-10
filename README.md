# JetMenu

A financial management system for delivery restaurants — built as a monorepo with a **Java Spring Boot** backend and a **Vue 3** frontend.

---

## Tech Stack

| Layer          | Technologies                                                    |
| -------------- | --------------------------------------------------------------- |
| **Backend**    | Java 21, Spring Boot 4.0.3, Spring Data JPA, Lombok, Maven     |
| **Frontend**   | Vue 3.5+, TypeScript 5.9+, Pinia 3, Vue Router 5, Vite 7       |
| **Database**   | PostgreSQL 16 (production) · H2 (dev/test)                      |
| **Infra**      | Docker, Docker Compose, Nginx                                   |

---

## Getting Started

### Prerequisites

- [Docker](https://www.docker.com/) & Docker Compose
- (Optional for local dev) Java 21, Node.js ≥20.19.0

### Running with Docker (dev)

One command brings up the database, backend (hot reload via `spring-boot-devtools`), frontend (Vite HMR) and the marketing landing page, all in dev mode:

```bash
docker compose up
```

This expects the `jetmenu-lp` repo — a separate Next.js project, not part of this monorepo — cloned as a sibling of this one, at `../jetmenu-lp`.

| Service         | URL                     |
| ---------------- | ----------------------- |
| Frontend (Vite)  | http://localhost:5173   |
| Landing page     | http://localhost:3000   |
| Backend API      | http://localhost:8080   |
| Database         | localhost:5432           |

Each service also mirrors its stdout/stderr to a plain-text file under `./logs/` (`backend.log`, `frontend.log`, `landing-page.log`), truncated on every `docker compose up` — handy for tailing or grepping without the Docker CLI.

**Backend hot reload caveat:** `spring-boot-devtools` restarts the JVM when `.class` files under `backend/target/classes` change, but it does not recompile `.java` sources by itself. If your IDE is configured to build to `target/classes` automatically (e.g. IntelliJ's "Build Project Automatically" + `compiler.automake.allow.when.app.running=true`), saving a file reloads the backend on its own. Otherwise, run `docker compose exec backend ./mvnw compile` after editing, or restart the `backend` service.

The Caddy reverse proxy for the `*.test` domains (`Caddyfile`) is **not** part of this compose file — it keeps running as its own process on the host, pointed at these same ports.

For a production-like build (Nginx-served frontend, `prod` Spring profile):

```bash
cp .env.example .env
# Edit .env with your database credentials
docker compose -f docker-compose-prod.yaml up --build
```

### Running Locally (without Docker)

```bash
# Backend
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd frontend
npm install
npm run dev
```

---

## Project Structure

```
JetMenu/
├── backend/          # Spring Boot API
│   ├── src/main/java/com/jetmenu/
│   │   ├── order/        # Orders domain
│   │   ├── product/      # Products domain
│   │   ├── category/     # Categories domain
│   │   ├── ingredient/   # Ingredients domain
│   │   ├── customer/     # Customers domain
│   │   └── dashboard/    # Dashboard aggregations
│   └── src/main/resources/
│       ├── application.properties
│       └── application-dev.properties
│
├── frontend/         # Vue 3 SPA
│   └── src/
│       ├── views/        # Page components
│       ├── stores/       # Pinia stores
│       ├── services/     # API call modules
│       ├── types/        # TypeScript interfaces
│       ├── components/   # Reusable UI components
│       └── composables/  # Composition API logic
│
└── docker-compose.yaml
```

---

## Architecture

### Backend — Layered, Package-by-Feature

Each domain follows the pattern:

```
<feature>/
├── FeatureController.java    # @RestController — HTTP layer
├── FeatureService.java       # @Service — Business logic
├── FeatureRepository.java    # JpaRepository — Data access
└── Feature.java              # @Entity — JPA entity
```

**API pattern:** `/api/<feature>` (e.g., `/api/orders`, `/api/products`)

### Frontend — Vue 3 Composition API

- `<script setup lang="ts">` in all components
- Pinia stores with Composition API (`defineStore` + `setup`)
- Vue Router 5 with `createWebHistory`

---

## Pages

| Page            | Route           | Description                                    |
| --------------- | --------------- | ---------------------------------------------- |
| Dashboard       | `/`             | KPIs, sales chart, top 5 products              |
| Orders          | `/orders`       | Order management (CRUD)                        |
| Products        | `/products`     | Products with recipe sheet and cost data       |
| Categories      | `/categories`   | Product categories (CRUD)                      |
| Ingredients     | `/ingredients`  | Ingredients with unit cost tracking            |
| Customers       | `/customers`    | Customer records (CRUD)                        |

---

## Data Model

```
Customer 1 ──── N Order
Order    1 ──── N OrderItem
Product  1 ──── N OrderItem
Product  M ──── N Category        (join table)
Product  1 ──── N RecipeItem
Ingredient 1 ── N RecipeItem
```

---

## Integrations

### Anota.AI

JetMenu imports orders and syncs the product catalog from **Anota.AI** (which also relays iFood orders). Authentication uses a per-merchant partner token via `Authorization: Bearer <token>`.

| Purpose        | Base URL                                      |
| -------------- | --------------------------------------------- |
| Orders (PDV)   | `https://api-parceiros.anota.ai/partnerauth`  |
| Menu / Catalog | `https://api-menu.anota.ai/partnerauth`       |

**Sync flow:** poll `/ping/list` → filter `check: 0` (pending) → fetch each via `/ping/get/{id}` → persist → mark as checked (`check: 1`).

**Two order sources** arrive through the same endpoints, distinguished by `from` / `salesChannel`:

| Aspect          | iFood (`ifood-marketplace`)                    | Anota.AI (`menu-share-adm`)                  |
| --------------- | ---------------------------------------------- | -------------------------------------------- |
| `total`         | `items + delivery − discounts + fees`          | `item.total` (already includes subItems)     |
| `internalId`    | empty — extras resolved by canonical name match | JetMenu product ID                          |
| Fees/discounts  | service fees + iFood promos                    | empty arrays                                  |

See [.claude/docs/integrations/ANOTA_AI.md](.claude/docs/integrations/ANOTA_AI.md) for the full field reference, payload examples, and catalog export details.

---

## Environment Variables

| Variable      | Description              |
| ------------- | ------------------------ |
| `DB_NAME`     | PostgreSQL database name |
| `DB_USER`     | PostgreSQL username      |
| `DB_PASSWORD` | PostgreSQL password      |

---

## License

This project is private and not licensed for public use.
