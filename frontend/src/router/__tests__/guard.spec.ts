import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'

// Simulates the boot race: the store starts without a session and init() only
// restores it when awaited (like the async Supabase getSession on page refresh).
const authState = vi.hoisted(() => ({
  authenticated: false,
  sessionAfterInit: false,
  initCalls: 0,
}))

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    async init() {
      authState.initCalls++
      await new Promise((resolve) => setTimeout(resolve, 0))
      if (authState.sessionAfterInit) authState.authenticated = true
    },
    get isAuthenticated() {
      return authState.authenticated
    },
  }),
}))

import { authGuard } from '@/router'

function route(overrides: Partial<RouteLocationNormalized> = {}): RouteLocationNormalized {
  return { name: 'dashboard', meta: {}, ...overrides } as RouteLocationNormalized
}

beforeEach(() => {
  authState.authenticated = false
  authState.sessionAfterInit = false
  authState.initCalls = 0
})

describe('authGuard', () => {
  it('aguarda a restauração da sessão antes de decidir (refresh em rota privada)', async () => {
    // Page refresh on /dashboard: token in storage, session restored async by init().
    authState.sessionAfterInit = true

    const result = await authGuard(route({ name: 'dashboard', meta: {} }))

    expect(authState.initCalls).toBe(1)
    expect(result).toBeUndefined()
  })

  it('sem sessão após a restauração redireciona para login', async () => {
    authState.sessionAfterInit = false

    const result = await authGuard(route({ name: 'dashboard', meta: {} }))

    expect(result).toEqual({ name: 'login' })
  })

  it('autenticado em rota pública sem allowAuthenticated redireciona para dashboard', async () => {
    authState.sessionAfterInit = true

    const result = await authGuard(route({ name: 'login', meta: { public: true } }))

    expect(result).toEqual({ name: 'dashboard' })
  })

  it('rota pública com allowAuthenticated permanece acessível autenticado', async () => {
    authState.sessionAfterInit = true

    const result = await authGuard(
      route({ name: 'checkout', meta: { public: true, allowAuthenticated: true } }),
    )

    expect(result).toBeUndefined()
  })
})
