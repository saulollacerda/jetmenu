# Frontend — JetMenu

Vue 3 SPA, Composition API, TypeScript.

## Stack

| Tool | Details |
|---|---|
| Framework | Vue 3 — always `<script setup lang="ts">` |
| State | Pinia — `defineStore` + Composition API setup function |
| Router | Vue Router 5 — `createWebHistory` |
| Build | Vite — `@` alias maps to `./src` |
| Formatting | Prettier |
| Linting | ESLint (flat config) + OxLint + Vue + TypeScript rules |
| Testing | Vitest + `@vue/test-utils` |

## Conventions

- Always `<script setup lang="ts">` — no Options API.
- Pinia stores in Composition style, never Options style.
- Strict TypeScript — proper types and interfaces, avoid `any`.
- All UI text in Portuguese (pt-BR).

## Running locally

```bash
cd frontend
npm install
npm run dev
```