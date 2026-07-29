import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, enableAutoUnmount, RouterLinkStub } from '@vue/test-utils'
import PlansView from '@/views/PlansView.vue'
import { billingService } from '@/services/billingService'
import type { PlanResponse } from '@/types/Billing'

enableAutoUnmount(afterEach)

vi.mock('@/services/billingService', () => ({
  billingService: {
    listPlans: vi.fn(),
    createCheckout: vi.fn(),
  },
}))

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

// Toggle auth state per test via a hoisted holder the mocked store reads from.
const authState = vi.hoisted(() => ({ authenticated: false }))
vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    get isAuthenticated() {
      return authState.authenticated
    },
  }),
}))

const mockedBilling = vi.mocked(billingService)

const PLAN: PlanResponse = {
  id: 'plan-1',
  name: 'Básico',
  minRevenue: 0,
  maxRevenue: null,
  priceMonthly: 50,
  features: {},
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
}

const GLOBAL = { global: { stubs: { RouterLink: RouterLinkStub } } }

let originalLocation: Location

beforeEach(() => {
  vi.clearAllMocks()
  authState.authenticated = false
  originalLocation = window.location
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: { href: '' },
  })
})

afterEach(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: originalLocation,
  })
})

describe('PlansView — carregamento dos planos', () => {
  it('renderiza os planos retornados pelo billingService', async () => {
    mockedBilling.listPlans.mockResolvedValue([PLAN])

    const wrapper = mount(PlansView, GLOBAL)
    await flushPromises()

    expect(mockedBilling.listPlans).toHaveBeenCalledOnce()
    const text = wrapper.text()
    expect(text).toContain('Básico')
    // Intl currency uses a non-breaking space between symbol and amount.
    expect(text).toMatch(/R\$\s*50,00/)
  })

  it('mostra uma mensagem de erro em pt-BR quando a busca falha', async () => {
    mockedBilling.listPlans.mockRejectedValue(new Error('network'))

    const wrapper = mount(PlansView, GLOBAL)
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar os planos')
  })
})

describe('PlansView — assinar', () => {
  it('usuário autenticado: avisa que o pagamento está indisponível, sem chamar createCheckout', async () => {
    authState.authenticated = true
    mockedBilling.listPlans.mockResolvedValue([PLAN])

    const wrapper = mount(PlansView, GLOBAL)
    await flushPromises()

    await wrapper.find('[data-testid="plan-cta"]').trigger('click')
    await flushPromises()

    expect(mockedBilling.createCheckout).not.toHaveBeenCalled()
    expect(window.location.href).toBe('')
    const notice = wrapper.find('[data-testid="plan-unavailable-notice"]')
    expect(notice.exists()).toBe(true)
    expect(notice.text()).toContain('indisponível')
    expect(notice.text()).toContain('contato')
  })

  it('usuário não autenticado: redireciona para /register sem criar checkout', async () => {
    authState.authenticated = false
    mockedBilling.listPlans.mockResolvedValue([PLAN])

    const wrapper = mount(PlansView, GLOBAL)
    await flushPromises()

    await wrapper.find('[data-testid="plan-cta"]').trigger('click')
    await flushPromises()

    expect(mockedBilling.createCheckout).not.toHaveBeenCalled()
    expect(pushMock).toHaveBeenCalledWith('/register')
  })
})

/**
 * The landing page moved to its own domain (jetmenu.com.br), while this app is
 * served from app.jetmenu.com.br. The header links out to it, so they must be
 * real anchors with an absolute URL — a RouterLink would look for a route that
 * no longer exists in this app.
 */
describe('PlansView — links para a landing page externa', () => {
  const LANDING_URL = 'https://jetmenu.com.br'

  it('o wordmark aponta para a landing page externa', async () => {
    mockedBilling.listPlans.mockResolvedValue([PLAN])

    const wrapper = mount(PlansView, GLOBAL)
    await flushPromises()

    const link = wrapper.find('[data-testid="landing-wordmark-link"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe(LANDING_URL)
  })

  it('"Voltar ao início" aponta para a landing page externa', async () => {
    mockedBilling.listPlans.mockResolvedValue([PLAN])

    const wrapper = mount(PlansView, GLOBAL)
    await flushPromises()

    const link = wrapper.find('[data-testid="landing-back-link"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe(LANDING_URL)
  })
})
