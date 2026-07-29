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

## Coding Guidelines

@docs/CODING_GUIDELINES.md