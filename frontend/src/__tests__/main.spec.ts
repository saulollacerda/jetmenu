import { describe, it, expect, vi } from 'vitest'

/**
 * Regression guard for the blank-screen bug: the entry module must finish evaluating
 * without suspending on the auth restore.
 *
 * `authProvider.init()` lazily `import()`s the Supabase provider chunk, and that chunk
 * statically imports the entry chunk back (Rollup hoists the shared modules into the
 * entry). A top-level `await` here suspends the entry mid-evaluation, so the lazily
 * imported chunk can never finish linking — and the promise the entry is awaiting can
 * never settle. Neither side errors: the app just never mounts and the page stays blank.
 *
 * So: evaluating `main.ts` must complete while `init()` is still pending. Mounting still
 * waits for the restore, which is what keeps the first paint free of a login-layout flash.
 */

const mounted = vi.hoisted(() => ({ mount: vi.fn(), use: vi.fn() }))
const auth = vi.hoisted(() => {
  let resolveInit!: () => void
  const initPromise = new Promise<void>((resolve) => {
    resolveInit = resolve
  })
  return { init: vi.fn(() => initPromise), resolveInit: () => resolveInit() }
})

vi.mock('vue', () => ({
  createApp: () => ({ use: mounted.use, mount: mounted.mount }),
}))
vi.mock('pinia', () => ({ createPinia: () => ({}) }))
vi.mock('../App.vue', () => ({ default: {} }))
vi.mock('../router', () => ({ default: {} }))
vi.mock('@/stores/authStore', () => ({ useAuthStore: () => ({ init: auth.init }) }))

describe('main entry', () => {
  it('finishes evaluating while the auth restore is still pending, then mounts', async () => {
    // Would hang forever if main.ts kept the top-level await — vitest fails on timeout.
    await import('../main')

    expect(auth.init).toHaveBeenCalled()
    expect(mounted.mount).not.toHaveBeenCalled()

    auth.resolveInit()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(mounted.mount).toHaveBeenCalledWith('#app')
  })
})
