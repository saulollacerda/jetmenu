import { describe, it, expect, vi, beforeEach } from 'vitest'

const { post } = vi.hoisted(() => ({ post: vi.fn() }))

vi.mock('axios', () => ({
  default: { create: () => ({ post }) },
}))

import { localAuthProvider } from '@/lib/localAuthProvider'
import { AuthError } from '@/lib/authTypes'

// JWT with payload {"email":"dev@example.com"} (signature irrelevant for the test).
const TOKEN =
  'eyJhbGciOiJSUzI1NiJ9.eyJlbWFpbCI6ImRldkBleGFtcGxlLmNvbSJ9.sig'

const TOKEN_KEY = 'jetmenu.auth.token'

/** Builds an unsigned JWT with the given payload (signature irrelevant for the tests). */
function fakeJwt(payload: Record<string, unknown>): string {
  const body = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `eyJhbGciOiJSUzI1NiJ9.${body}.sig`
}

// This env's global localStorage is an unusable stub; inject a clean in-memory one.
const memoryStore = new Map<string, string>()
vi.stubGlobal('localStorage', {
  getItem: (key: string) => memoryStore.get(key) ?? null,
  setItem: (key: string, value: string) => void memoryStore.set(key, value),
  removeItem: (key: string) => void memoryStore.delete(key),
  clear: () => memoryStore.clear(),
})

beforeEach(() => {
  vi.clearAllMocks()
  memoryStore.clear()
})

describe('localAuthProvider', () => {
  it('signUp posta em /auth/dev-register, persiste o token e expõe a sessão', async () => {
    post.mockResolvedValue({ data: { accessToken: TOKEN } })

    const result = await localAuthProvider.signUp({
      email: 'dev@example.com',
      password: 'senha123',
      merchantName: 'Loja',
      cnpj: '123',
      phone: '9',
    })

    expect(post).toHaveBeenCalledWith(
      '/auth/dev-register',
      expect.objectContaining({ email: 'dev@example.com', merchantName: 'Loja', cnpj: '123' }),
    )
    expect(result.session?.accessToken).toBe(TOKEN)
    expect(result.session?.user.email).toBe('dev@example.com')
    expect(await localAuthProvider.getAccessToken()).toBe(TOKEN)
  })

  it('signIn posta em /auth/dev-login e persiste o token', async () => {
    post.mockResolvedValue({ data: { accessToken: TOKEN } })

    const session = await localAuthProvider.signIn('dev@example.com', 'senha123')

    expect(post).toHaveBeenCalledWith('/auth/dev-login', {
      email: 'dev@example.com',
      password: 'senha123',
    })
    expect(session.accessToken).toBe(TOKEN)
    expect(await localAuthProvider.getAccessToken()).toBe(TOKEN)
  })

  it('signIn com falha lança AuthError invalid_credentials', async () => {
    post.mockRejectedValue({ response: { status: 401 } })

    await expect(localAuthProvider.signIn('a@b.com', 'x')).rejects.toMatchObject({
      code: 'invalid_credentials',
    })
    expect(await localAuthProvider.getAccessToken()).toBeNull()
  })

  it('signUp com 409 lança AuthError email_exists', async () => {
    post.mockRejectedValue({ response: { status: 409 } })

    await expect(
      localAuthProvider.signUp({
        email: 'a@b.com',
        password: 'x',
        merchantName: 'L',
        cnpj: '1',
      }),
    ).rejects.toBeInstanceOf(AuthError)
  })

  it('signOut limpa o token', async () => {
    post.mockResolvedValue({ data: { accessToken: TOKEN } })
    await localAuthProvider.signIn('dev@example.com', 'senha123')

    await localAuthProvider.signOut()

    expect(await localAuthProvider.getAccessToken()).toBeNull()
    expect(await localAuthProvider.init()).toBeNull()
  })

  describe('refreshSession', () => {
    it('retorna o token válido armazenado (não há refresh remoto no modo local)', async () => {
      const valid = fakeJwt({ email: 'dev@example.com', exp: Math.floor(Date.now() / 1000) + 3600 })
      memoryStore.set(TOKEN_KEY, valid)

      expect(await localAuthProvider.refreshSession()).toBe(valid)
    })

    it('retorna null quando não há token armazenado', async () => {
      expect(await localAuthProvider.refreshSession()).toBeNull()
    })

    it('retorna null e remove um token expirado', async () => {
      const expired = fakeJwt({ email: 'dev@example.com', exp: Math.floor(Date.now() / 1000) - 60 })
      memoryStore.set(TOKEN_KEY, expired)

      expect(await localAuthProvider.refreshSession()).toBeNull()
    })
  })

  describe('recuperação de senha (não suportada no modo local)', () => {
    it('requestPasswordReset lança AuthError not_supported', async () => {
      await expect(localAuthProvider.requestPasswordReset('a@b.com')).rejects.toMatchObject({
        code: 'not_supported',
      })
    })

    it('updatePassword lança AuthError not_supported', async () => {
      await expect(localAuthProvider.updatePassword('novaSenha1')).rejects.toMatchObject({
        code: 'not_supported',
      })
    })
  })

  describe('validação de expiração (exp)', () => {
    it('init retorna null e remove do storage um token expirado', async () => {
      const expired = fakeJwt({ email: 'dev@example.com', exp: Math.floor(Date.now() / 1000) - 60 })
      memoryStore.set(TOKEN_KEY, expired)

      expect(await localAuthProvider.init()).toBeNull()
      expect(memoryStore.has(TOKEN_KEY)).toBe(false)
    })

    it('init retorna null e remove do storage um token com payload ilegível', async () => {
      memoryStore.set(TOKEN_KEY, 'garbage-not-a-jwt')

      expect(await localAuthProvider.init()).toBeNull()
      expect(memoryStore.has(TOKEN_KEY)).toBe(false)
    })

    it('init retorna a sessão para token com exp no futuro', async () => {
      const valid = fakeJwt({ email: 'dev@example.com', exp: Math.floor(Date.now() / 1000) + 3600 })
      memoryStore.set(TOKEN_KEY, valid)

      const session = await localAuthProvider.init()

      expect(session?.accessToken).toBe(valid)
      expect(session?.user.email).toBe('dev@example.com')
    })

    it('getAccessToken retorna null para token expirado', async () => {
      const expired = fakeJwt({ email: 'dev@example.com', exp: Math.floor(Date.now() / 1000) - 60 })
      memoryStore.set(TOKEN_KEY, expired)

      expect(await localAuthProvider.getAccessToken()).toBeNull()
    })

    it('token sem claim exp continua aceito (compat com tokens de dev)', async () => {
      memoryStore.set(TOKEN_KEY, TOKEN)

      const session = await localAuthProvider.init()

      expect(session?.accessToken).toBe(TOKEN)
      expect(await localAuthProvider.getAccessToken()).toBe(TOKEN)
    })
  })

  describe('sincronização entre abas (storage event)', () => {
    it('notifica listeners com null quando o token é removido em outra aba', async () => {
      const callback = vi.fn()
      localAuthProvider.onAuthChange(callback)

      window.dispatchEvent(
        new StorageEvent('storage', { key: TOKEN_KEY, newValue: null }),
      )

      expect(callback).toHaveBeenCalledWith(null)
    })

    it('notifica listeners com a nova sessão quando o token muda em outra aba', async () => {
      const callback = vi.fn()
      localAuthProvider.onAuthChange(callback)

      window.dispatchEvent(
        new StorageEvent('storage', { key: TOKEN_KEY, newValue: TOKEN }),
      )

      expect(callback).toHaveBeenCalledWith(
        expect.objectContaining({ accessToken: TOKEN }),
      )
    })

    it('ignora storage events de outras chaves', async () => {
      const callback = vi.fn()
      localAuthProvider.onAuthChange(callback)

      window.dispatchEvent(
        new StorageEvent('storage', { key: 'outra.chave', newValue: 'x' }),
      )

      expect(callback).not.toHaveBeenCalled()
    })
  })
})
