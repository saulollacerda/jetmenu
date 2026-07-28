import axios from 'axios'
import { AuthError, type AuthProvider, type AuthSession, type SignUpForm } from './authTypes'

const TOKEN_KEY = 'jetmenu.auth.token'

const baseURL = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim() || '/api'
// Bare client (no interceptors) so it never recurses through the app's `api` instance.
const client = axios.create({ baseURL, headers: { 'Content-Type': 'application/json' } })

const listeners: Array<(session: AuthSession | null) => void> = []
function notify(session: AuthSession | null) {
  for (const cb of listeners) cb(session)
}

/** Decodes the JWT payload without verifying the signature; null when unparseable. */
function decodePayload(token: string): Record<string, unknown> | null {
  try {
    const payload = token.split('.')[1] ?? ''
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return null
  }
}

/** Best-effort read of the `email` claim from the JWT payload (display only). */
function decodeEmail(token: string): string {
  return (decodePayload(token)?.email as string) ?? ''
}

/**
 * Reads the persisted token, discarding it when the payload is unreadable or the
 * `exp` claim is in the past — an expired token must not keep the UI "logged in"
 * until the first 401. Tokens without `exp` are accepted (dev-token compat);
 * the backend remains the signature/expiry authority either way.
 */
function readValidToken(): string | null {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return null

  const payload = decodePayload(token)
  const exp = payload?.exp
  const expired = typeof exp === 'number' && exp * 1000 <= Date.now()
  if (!payload || expired) {
    localStorage.removeItem(TOKEN_KEY)
    return null
  }
  return token
}

// Cross-tab sync: sign-in/sign-out in another tab fires a `storage` event here,
// keeping this tab's session state (and router guards) consistent.
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key !== TOKEN_KEY) return
    notify(event.newValue ? sessionFromToken(event.newValue) : null)
  })
}

function sessionFromToken(token: string): AuthSession {
  return { accessToken: token, user: { email: decodeEmail(token), user_metadata: {} } }
}

function persist(token: string): AuthSession {
  localStorage.setItem(TOKEN_KEY, token)
  const session = sessionFromToken(token)
  notify(session)
  return session
}

/**
 * Dev auth backend (no Supabase): talks to /api/auth/dev-login and /api/auth/dev-register,
 * which mint a JWT signed by the backend's local RSA key. The token is persisted in
 * localStorage; the merchant already exists after register, so no provision step is needed.
 */
export const localAuthProvider: AuthProvider = {
  async init() {
    const token = readValidToken()
    return token ? sessionFromToken(token) : null
  },

  onAuthChange(callback) {
    listeners.push(callback)
  },

  async signIn(email, password) {
    try {
      const { data } = await client.post<{ accessToken: string }>('/auth/dev-login', {
        email,
        password,
      })
      return persist(data.accessToken)
    } catch {
      throw new AuthError('invalid_credentials')
    }
  },

  async signUp(form: SignUpForm) {
    try {
      const { data } = await client.post<{ accessToken: string }>('/auth/dev-register', {
        merchantName: form.merchantName,
        cnpj: form.cnpj,
        email: form.email,
        phone: form.phone ?? null,
        password: form.password,
      })
      return { session: persist(data.accessToken) }
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } }).response?.status
      throw new AuthError(status === 409 ? 'email_exists' : 'unknown')
    }
  },

  async signOut() {
    localStorage.removeItem(TOKEN_KEY)
    notify(null)
  },

  async getAccessToken() {
    return readValidToken()
  },

  // No remote refresh concept in local dev — just re-read the persisted valid token.
  async refreshSession() {
    return readValidToken()
  },

  // No email delivery in local dev — failing loudly beats faking a sent email.
  async requestPasswordReset() {
    throw new AuthError('not_supported')
  },

  async updatePassword() {
    throw new AuthError('not_supported')
  },
}
