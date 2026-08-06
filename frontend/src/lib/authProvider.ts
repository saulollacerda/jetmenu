import type { AuthProvider } from './authTypes'
import { localAuthProvider } from './localAuthProvider'

export * from './authTypes'

const providerName = (import.meta.env.VITE_AUTH_PROVIDER as string | undefined)?.trim() || 'supabase'

// Resolved on first use and cached, so concurrent callers share a single import.
let pendingSupabaseProvider: Promise<AuthProvider> | null = null

function resolveSupabaseProvider(): Promise<AuthProvider> {
  pendingSupabaseProvider ??= import('./supabaseAuthProvider').then((m) => m.supabaseAuthProvider)
  return pendingSupabaseProvider
}

/**
 * Loads `./supabaseAuthProvider` (and with it the Supabase SDK) only on first use.
 *
 * A static import would evaluate `./supabase` at module load even in `local` mode, which
 * instantiates a client and requires VITE_SUPABASE_URL/VITE_SUPABASE_ANON_KEY — so dev
 * without a Supabase project got a console error and a pointless SDK dependency. The
 * dynamic import keeps `local` mode free of Supabase entirely, while `supabase` mode
 * behaves as before (one extra microtask before the first call reaches the SDK).
 */
const lazySupabaseAuthProvider: AuthProvider = {
  async init() {
    return (await resolveSupabaseProvider()).init()
  },

  onAuthChange(callback) {
    // The only entry point that is not async: register as soon as the module resolves.
    void resolveSupabaseProvider().then((provider) => provider.onAuthChange(callback))
  },

  async signIn(email, password) {
    return (await resolveSupabaseProvider()).signIn(email, password)
  },

  async signUp(form) {
    return (await resolveSupabaseProvider()).signUp(form)
  },

  async signOut() {
    return (await resolveSupabaseProvider()).signOut()
  },

  async getAccessToken() {
    return (await resolveSupabaseProvider()).getAccessToken()
  },

  async refreshSession() {
    return (await resolveSupabaseProvider()).refreshSession()
  },

  async requestPasswordReset(email) {
    return (await resolveSupabaseProvider()).requestPasswordReset(email)
  },

  async updatePassword(password) {
    return (await resolveSupabaseProvider()).updatePassword(password)
  },
}

/** Active auth backend. `local` for dev without Supabase; `supabase` otherwise (default). */
export const authProvider: AuthProvider =
  providerName === 'local' ? localAuthProvider : lazySupabaseAuthProvider
