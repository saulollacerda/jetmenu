import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const supabaseModuleLoaded = vi.fn()
const getSession = vi.fn()
const onAuthStateChange = vi.fn()

// This env's global localStorage is an unusable stub; inject a clean in-memory one
// (localAuthProvider reads it on getAccessToken).
const memoryStore = new Map<string, string>()
vi.stubGlobal('localStorage', {
  getItem: (key: string) => memoryStore.get(key) ?? null,
  setItem: (key: string, value: string) => void memoryStore.set(key, value),
  removeItem: (key: string) => void memoryStore.delete(key),
  clear: () => memoryStore.clear(),
})

/**
 * Loads `@/lib/authProvider` fresh under the given VITE_AUTH_PROVIDER.
 *
 * The Supabase client module is mocked with a factory that bumps `supabaseModuleLoaded`,
 * so the counter is a faithful probe for "did anything in the graph import Supabase?".
 * `@/lib/supabaseAuthProvider` is deliberately NOT mocked — it is the module that imports
 * the client, so leaving it real is what makes the probe meaningful.
 *
 * Uses `vi.doMock` (not `vi.mock`) because `vi.resetModules()` does not re-run cached
 * hoisted `vi.mock` factories, which would silently zero the counter after the first test.
 */
async function loadAuthProvider(provider: string) {
  vi.resetModules()
  supabaseModuleLoaded.mockClear()
  getSession.mockClear()
  onAuthStateChange.mockClear()
  getSession.mockResolvedValue({ data: { session: null } })

  vi.stubEnv('VITE_AUTH_PROVIDER', provider)
  vi.doMock('@/lib/supabase', () => {
    supabaseModuleLoaded()
    return { supabase: { auth: { getSession, onAuthStateChange } } }
  })

  const { authProvider } = await import('@/lib/authProvider')
  const { localAuthProvider } = await import('@/lib/localAuthProvider')
  return { authProvider, localAuthProvider }
}

beforeEach(() => {
  memoryStore.clear()
})

afterEach(() => {
  vi.unstubAllEnvs()
  vi.doUnmock('@/lib/supabase')
})

describe('authProvider', () => {
  describe('VITE_AUTH_PROVIDER=local', () => {
    it('não importa o módulo do Supabase ao carregar', async () => {
      await loadAuthProvider('local')

      expect(supabaseModuleLoaded).not.toHaveBeenCalled()
    })

    it('não importa o módulo do Supabase ao usar o provider', async () => {
      const { authProvider } = await loadAuthProvider('local')

      await authProvider.getAccessToken()
      await authProvider.init()

      expect(supabaseModuleLoaded).not.toHaveBeenCalled()
      expect(getSession).not.toHaveBeenCalled()
    })

    it('usa o localAuthProvider', async () => {
      const { authProvider, localAuthProvider } = await loadAuthProvider('local')

      expect(authProvider).toBe(localAuthProvider)
    })
  })

  describe('VITE_AUTH_PROVIDER=supabase', () => {
    it('delega ao supabaseAuthProvider de verdade', async () => {
      const { authProvider } = await loadAuthProvider('supabase')

      await authProvider.getAccessToken()

      expect(supabaseModuleLoaded).toHaveBeenCalled()
      expect(getSession).toHaveBeenCalled()
    })

    it('registra o listener de onAuthChange no Supabase', async () => {
      const { authProvider } = await loadAuthProvider('supabase')

      authProvider.onAuthChange(vi.fn())

      await vi.waitFor(() => expect(onAuthStateChange).toHaveBeenCalled())
    })

    it('é o padrão quando VITE_AUTH_PROVIDER está vazio', async () => {
      const { authProvider, localAuthProvider } = await loadAuthProvider('')

      await authProvider.getAccessToken()

      expect(authProvider).not.toBe(localAuthProvider)
      expect(getSession).toHaveBeenCalled()
    })
  })
})
