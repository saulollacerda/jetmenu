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
