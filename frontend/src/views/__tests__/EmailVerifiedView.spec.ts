import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, enableAutoUnmount, RouterLinkStub } from '@vue/test-utils'
import EmailVerifiedView from '@/views/EmailVerifiedView.vue'
import { savePendingPlan } from '@/lib/pendingPlan'

const authState = { isAuthenticated: false }

vi.mock('@/stores/authStore', () => ({ useAuthStore: () => authState }))

enableAutoUnmount(afterEach)

const GLOBAL = { global: { stubs: { RouterLink: RouterLinkStub } } }

beforeEach(() => {
  authState.isAuthenticated = false
})

describe('EmailVerifiedView', () => {
  it('autenticado (link de confirmação já conectou): CTA leva ao painel', () => {
    authState.isAuthenticated = true
    const wrapper = mount(EmailVerifiedView, GLOBAL)

    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/dashboard')
    expect(wrapper.text()).toContain('Ir para o painel')
    expect(wrapper.text()).not.toContain('Faça login')
  })

  it('não autenticado (link aberto em outro navegador): CTA leva ao login', () => {
    const wrapper = mount(EmailVerifiedView, GLOBAL)

    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/login')
    expect(wrapper.text()).toContain('Ir para o login')
  })
})

// The global localStorage in this environment is an unusable stub.
function installStorage() {
  const map = new Map<string, string>()
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: (k: string) => map.get(k) ?? null,
      setItem: (k: string, v: string) => void map.set(k, v),
      removeItem: (k: string) => void map.delete(k),
    } as unknown as Storage,
  })
}

// This is the landing the confirmation link uses, and it carries no query string — the
// only place the chosen plan can come from here is storage.
describe('EmailVerifiedView — plano escolhido antes do cadastro', () => {
  beforeEach(() => {
    installStorage()
    authState.isAuthenticated = false
  })

  it('autenticado com plano guardado: CTA leva ao checkout', () => {
    authState.isAuthenticated = true
    savePendingPlan('basico')

    const wrapper = mount(EmailVerifiedView, GLOBAL)

    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/checkout')
    expect(wrapper.text()).toContain('Continuar para o pagamento')
  })

  it('não autenticado com plano guardado: CTA continua no login', () => {
    savePendingPlan('basico')

    const wrapper = mount(EmailVerifiedView, GLOBAL)

    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/login')
  })
})
