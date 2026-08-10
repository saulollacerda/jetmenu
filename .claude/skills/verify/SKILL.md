---
name: verify
description: Build, launch, and drive JetMenu locally to verify changes at the real surface (browser at localhost:5173)
---

# Verifying JetMenu locally

## Stack pieces (all three needed for auth/data flows)

**Default: one command.** `docker compose up` at repo root starts Postgres, backend, frontend, and the landing page together on a shared Docker network (requires the `jetmenu-lp` repo cloned as a sibling at `../jetmenu-lp`). The backend talks to Postgres via the `db` service name, not `localhost`, so the native-Postgres-port-conflict gotcha below doesn't apply here. If Docker Desktop is *paused*, `docker desktop restart` clears it (there is no CLI unpause).

- Backend: `http://localhost:8080` (`/actuator/health` answers 401 when up — that still means "alive")
- Frontend: `http://localhost:5173`
- Landing page: `http://localhost:3000`
- Postgres: `localhost:5432` (DB `jetmenu_dev`, user/pass `jetmenu`/`jetmenu`)

Each service also mirrors its stdout to a plain-text file — `logs/backend.log`, `logs/frontend.log`, `logs/landing-page.log` — read or grep those directly instead of `docker compose logs`.

Backend hot reload: `spring-boot-devtools` restarts the JVM when `.class` files under `backend/target/classes` change, but it does not recompile `.java` by itself. Works automatically only if the IDE is set to build to `target/classes` on save; otherwise run `docker compose exec backend ./mvnw compile` after editing backend code, then re-check the log.

### Running natively instead (e.g. to attach a debugger)

1. **Postgres** — `docker compose up -d db` (service `jetmenu_db`). Note: on this machine the native Postgres on `127.0.0.1:5432` wins over the Docker container for `localhost` connections, so that's the instance a natively-run backend actually talks to.
2. **Backend** — `cd backend && ./mvnw spring-boot:run` (port 8080; `/actuator/health` answers 401 when up — that still means "alive").
3. **Frontend** — `cd frontend && npm run dev` (Vite, port 5173).

## Auth in dev

`frontend/.env` sets `VITE_AUTH_PROVIDER=local` → login goes through the backend's `/api/auth/dev-login` (JWT in localStorage under a `jetmenu.*` key), NOT Supabase. Registering through the UI (`/register`) creates a real merchant in the dev DB and signs you in immediately (no email confirmation locally).

Known dev user: `guard-test-0715@menubank.dev` / `senha123` (created 2026-07-15, before the rebrand — email domain wasn't retroactively changed).

Inspect dev data: `psql -h localhost -U jetmenu -d jetmenu_dev -c "..."` (table is `merchants`, plural).

## Driving the UI

Use the Maestri portal ("Portal" on the canvas) against `http://localhost:5173`:
- `maestri portal navigate/snapshot/fill/click/screenshot "Portal" ...`
- **Gotchas:** `portal evaluate` is blocked by the app's CSP (`script-src 'self'`) — use snapshots/screenshots instead. Snapshots show input *placeholders*, not filled values — screenshot to confirm form state. After `navigate`, sleep 2–4s before `snapshot` or you read the previous page.

## Useful flows

- Session persistence: log in, then `navigate` straight to `/dashboard` (a full page load = F5) — must stay on `/dashboard`, not bounce to `/login`.
- Logout: the "Sair" button is the icon at the sidebar bottom; lands on `/login`.
