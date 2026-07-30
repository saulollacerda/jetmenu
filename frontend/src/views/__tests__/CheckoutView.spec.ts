import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, enableAutoUnmount, RouterLinkStub } from '@vue/test-utils'
import CheckoutView from '@/views/CheckoutView.vue'
import { billingService } from '@/services/billingService'
import type { PlanResponse } from '@/types/Billing'
import { savePendingPlan, peekPendingPlan } from '@/lib/pendingPlan'

enableAutoUnmount(afterEach)

vi.mock('@/services/billingService', () => ({
  billingService: {
    listPlans: vi.fn(),
    createCheckout: vi.fn(),
  },
}))

// The landing page hands the plan over in the query string, so the route is the
// only input this view has.
const routeState = vi.hoisted(() => ({ query: {} as Record<string, string> }))
const replaceMock = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
}))

const authState = vi.hoisted(() => ({ authenticated: true }))
vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    get isAuthenticated() {
      return authState.authenticated
    },
  }),
}))

const mockedBilling = vi.mocked(billingService)

const BASIC_PLAN: PlanResponse = {
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
  authState.authenticated = true
  routeState.query = { plan: 'basico' }
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

describe('CheckoutView — visitante anônimo', () => {
  it('envia para o cadastro levando o plano escolhido', async () => {
    authState.authenticated = false

    mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(replaceMock).toHaveBeenCalledWith({ path: '/register', query: { plan: 'basico' } })
    expect(mockedBilling.createCheckout).not.toHaveBeenCalled()
  })

  it('envia para o cadastro sem plano quando a query vem vazia', async () => {
    authState.authenticated = false
    routeState.query = {}

    mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(replaceMock).toHaveBeenCalledWith({ path: '/register', query: {} })
  })
})

describe('CheckoutView — visitante autenticado', () => {
  it('resolve o plano pelo slug e redireciona para a URL de checkout', async () => {
    mockedBilling.listPlans.mockResolvedValue([BASIC_PLAN])
    mockedBilling.createCheckout.mockResolvedValue({ url: 'https://checkout.stripe.com/s/123' })

    mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(mockedBilling.createCheckout).toHaveBeenCalledWith('plan-1')
    expect(window.location.href).toBe('https://checkout.stripe.com/s/123')
  })

  it('mostra o estado de carregamento em pt-BR enquanto prepara o pagamento', async () => {
    mockedBilling.listPlans.mockReturnValue(new Promise(() => {}))

    const wrapper = mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(wrapper.find('[data-testid="checkout-loading"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Preparando seu pagamento')
  })

  it('usa o único plano disponível quando a query não traz o slug', async () => {
    routeState.query = {}
    mockedBilling.listPlans.mockResolvedValue([BASIC_PLAN])
    mockedBilling.createCheckout.mockResolvedValue({ url: 'https://checkout.stripe.com/s/123' })

    mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(mockedBilling.createCheckout).toHaveBeenCalledWith('plan-1')
  })

  it('avisa em pt-BR quando o slug não corresponde a nenhum plano', async () => {
    routeState.query = { plan: 'inexistente' }
    mockedBilling.listPlans.mockResolvedValue([BASIC_PLAN])

    const wrapper = mount(CheckoutView, GLOBAL)
    await flushPromises()

    const error = wrapper.find('[data-testid="checkout-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('Plano não encontrado')
    expect(mockedBilling.createCheckout).not.toHaveBeenCalled()
  })

  it('avisa que o pagamento está indisponível quando o backend responde 503', async () => {
    mockedBilling.listPlans.mockResolvedValue([BASIC_PLAN])
    mockedBilling.createCheckout.mockRejectedValue({ response: { status: 503 } })

    const wrapper = mount(CheckoutView, GLOBAL)
    await flushPromises()

    const error = wrapper.find('[data-testid="checkout-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('pagamento online está temporariamente indisponível')
    expect(window.location.href).toBe('')
  })

  it('mostra erro genérico em pt-BR e permite tentar novamente', async () => {
    mockedBilling.listPlans.mockResolvedValue([BASIC_PLAN])
    mockedBilling.createCheckout.mockRejectedValueOnce(new Error('network'))

    const wrapper = mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(wrapper.find('[data-testid="checkout-error"]').text()).toContain(
      'Não foi possível iniciar o pagamento',
    )

    mockedBilling.createCheckout.mockResolvedValue({ url: 'https://checkout.stripe.com/s/456' })
    await wrapper.find('[data-testid="checkout-retry"]').trigger('click')
    await flushPromises()

    expect(window.location.href).toBe('https://checkout.stripe.com/s/456')
  })

  it('oferece uma saída para as configurações quando o checkout falha', async () => {
    mockedBilling.listPlans.mockRejectedValue(new Error('boom'))

    const wrapper = mount(CheckoutView, GLOBAL)
    await flushPromises()

    const links = wrapper.findAllComponents(RouterLinkStub)
    expect(links.some((l) => l.props('to') === '/settings?section=billing')).toBe(true)
  })
})

// The plan only survives in the URL while the visitor stays in one tab. With email
// confirmation on, the confirmation link lands on a fixed /email-verificado in a NEW tab
// with no query string — so the slug is also persisted and resumed from storage.
describe('CheckoutView — plano retomado do armazenamento', () => {
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

  beforeEach(() => {
    installStorage()
  })

  it('guarda o plano antes de mandar o visitante anônimo para o cadastro', async () => {
    authState.authenticated = false
    routeState.query = { plan: 'basico' }

    mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(peekPendingPlan()).toBe('basico')
  })

  it('retoma o plano guardado quando volta autenticado sem query', async () => {
    savePendingPlan('basico')
    routeState.query = {}
    mockedBilling.listPlans.mockResolvedValue([BASIC_PLAN])
    mockedBilling.createCheckout.mockResolvedValue({ url: 'https://checkout.stripe.com/s/789' })

    mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(mockedBilling.createCheckout).toHaveBeenCalledWith('plan-1')
    expect(window.location.href).toBe('https://checkout.stripe.com/s/789')
  })

  it('consome o plano guardado, para não voltar ao checkout em todo login', async () => {
    savePendingPlan('basico')
    routeState.query = {}
    mockedBilling.listPlans.mockResolvedValue([BASIC_PLAN])
    mockedBilling.createCheckout.mockResolvedValue({ url: 'https://checkout.stripe.com/s/789' })

    mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(peekPendingPlan()).toBeNull()
  })

  it('descarta em silêncio um plano guardado que não existe mais', async () => {
    savePendingPlan('plano-extinto')
    routeState.query = {}
    mockedBilling.listPlans.mockResolvedValue([BASIC_PLAN])

    const wrapper = mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(replaceMock).toHaveBeenCalledWith('/dashboard')
    expect(wrapper.find('[data-testid="checkout-error"]').exists()).toBe(false)
    expect(peekPendingPlan()).toBeNull()
    expect(mockedBilling.createCheckout).not.toHaveBeenCalled()
  })

  it('a query tem precedência sobre o valor guardado', async () => {
    savePendingPlan('plano-antigo')
    routeState.query = { plan: 'basico' }
    mockedBilling.listPlans.mockResolvedValue([BASIC_PLAN])
    mockedBilling.createCheckout.mockResolvedValue({ url: 'https://checkout.stripe.com/s/1' })

    mount(CheckoutView, GLOBAL)
    await flushPromises()

    expect(mockedBilling.createCheckout).toHaveBeenCalledWith('plan-1')
    expect(peekPendingPlan()).toBeNull()
  })
})
