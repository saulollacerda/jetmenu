# JetMenu

Financial management system for delivery restaurants.
Monorepo: Java Spring Boot backend + Vue 3 frontend.

## Language Rule

- **Code:** English — class names, variables, methods, endpoints, comments, commit messages.
- **UI:** Portuguese (pt-BR) — labels, buttons, messages, tooltips, error messages.

## Git & Branch Strategy

| Branch | Pattern | Purpose |
|--------|---------|---------|
| `main` | — | Production only |
| `develop` | — | Always most up-to-date |
| Feature | `feature/<description>` | Created from develop |
| Fix | `fix/<description>` | Created from develop |

Merge flow: `feature/*` → `develop` → `main`.

## Running the App (Dev)

`docker compose up` from the repo root starts everything at once: Postgres, backend (hot reload via `spring-boot-devtools`), frontend (Vite), and the landing page. Requires the `jetmenu-lp` repo cloned as a sibling directory (`../jetmenu-lp`) — it's a separate Next.js project, not part of this monorepo. See [skills/verify/SKILL.md](skills/verify/SKILL.md) for details, gotchas, and how to drive the UI for verification.

## Coding Guidelines

@docs/CODING_GUIDELINES.md