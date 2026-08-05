import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import SettingsView from '@/views/SettingsView.vue'
import IfoodConnectModal from '@/components/IfoodConnectModal.vue'
import IfoodCatalogImportModal from '@/components/IfoodCatalogImportModal.vue'
import IfoodOrderSyncModal from '@/components/IfoodOrderSyncModal.vue'
import IfoodCatalogPublishModal from '@/components/IfoodCatalogPublishModal.vue'
import { ifoodAuthService, type IfoodStatusResponse } from '@/services/ifoodAuthService'
import { billingService } from '@/services/billingService'
import type { PlanResponse, SubscriptionResponse } from '@/types/Billing'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { section: 'ints' } }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  onBeforeRouteLeave: vi.fn(),
}))

let mockUser: { anotaAiApiKey: string | null; openingHours: unknown[] }

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    currentUser: mockUser,
    restaurantName: 'Test Burguer',
    loading: false,
    error: null,
    fetchCurrentUser: vi.fn(async () => mockUser),
    updateOpeningHours: vi.fn(),
    updateAnotaAIKey: vi.fn(),
  }),
}))

let anotaAIStoreMock: any

vi.mock('@/stores/anotaAIStore', () => ({
  useAnotaAIStore: () => anotaAIStoreMock,
}))

vi.mock('@/stores/notificationStore', () => ({
  useNotificationStore: () => ({ refreshCount: vi.fn() }),
}))

vi.mock('@/services/ifoodAuthService', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/services/ifoodAuthService')>()
  return {
    ...original,
    ifoodAuthService: {
      status: vi.fn(),
      revoke: vi.fn(),
    },
  }
})

vi.mock('@/services/billingService', () => ({
  billingService: {
    listPlans: vi.fn(),
    getMySubscription: vi.fn(),
    createCheckout: vi.fn(),
  },
}))

const mockedService = vi.mocked(ifoodAuthService)
const mockedBilling = vi.mocked(billingService)

const STUBS = {
  IfoodConnectModal: true,
  IfoodCatalogImportModal: true,
  IfoodCatalogPublishModal: true,
  IfoodOrderSyncModal: true,
}

function statusOf(overrides: Partial<IfoodStatusResponse> = {}): IfoodStatusResponse {
  return {
    connected: false,
    catalogImportedAt: null,
    orderSyncEnabled: false,
    connectionEnabled: true,
    ...overrides,
  }
}

async function mountView(status: IfoodStatusResponse, { expand = true } = {}) {
  mockedService.status.mockResolvedValue(status)
  const wrapper = mount(SettingsView, { global: { stubs: STUBS } })
  await flushPromises()
  if (expand) {
    // Checklists start collapsed; most tests interact with the stages.
    await wrapper.find('[data-testid="ifood-card-toggle"]').trigger('click')
    await wrapper.find('[data-testid="anotaai-card-toggle"]').trigger('click')
  }
  return wrapper
}

enableAutoUnmount(afterEach)

beforeEach(() => {
  mockUser = { anotaAiApiKey: null, openingHours: [] }
  anotaAIStoreMock = {
    syncingOrders: false,
    lastResult: null,
    error: null,
    syncOrders: vi.fn(async () => ({ ordersImported: 2, ordersSkipped: 1 })),
    clearResult: vi.fn(),
  }
})

describe('SettingsView — cards de integração recolhíveis', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  it('as etapas começam recolhidas em ambos os cards', async () => {
    const wrapper = await mountView(statusOf(), { expand: false })

    expect(wrapper.find('[data-testid="ifood-card-toggle"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="anotaai-card-toggle"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="ifood-stage-connect"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="anotaai-stage-connect"]').exists()).toBe(false)
  })

  it('a seta expande e recolhe as etapas do card do iFood', async () => {
    const wrapper = await mountView(statusOf(), { expand: false })

    await wrapper.find('[data-testid="ifood-card-toggle"]').trigger('click')
    expect(wrapper.find('[data-testid="ifood-stage-connect"]').exists()).toBe(true)

    await wrapper.find('[data-testid="ifood-card-toggle"]').trigger('click')
    expect(wrapper.find('[data-testid="ifood-stage-connect"]').exists()).toBe(false)
  })

  it('a seta expande e recolhe as etapas do card do Anota.AI', async () => {
    const wrapper = await mountView(statusOf(), { expand: false })

    await wrapper.find('[data-testid="anotaai-card-toggle"]').trigger('click')
    expect(wrapper.find('[data-testid="anotaai-stage-connect"]').exists()).toBe(true)

    await wrapper.find('[data-testid="anotaai-card-toggle"]').trigger('click')
    expect(wrapper.find('[data-testid="anotaai-stage-connect"]').exists()).toBe(false)
  })
})

describe('SettingsView — checklist iFood', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  it('mostra as 3 etapas com estado correto quando tudo está ativo', async () => {
    const wrapper = await mountView(
      statusOf({ connected: true, catalogImportedAt: '2026-07-01T10:00:00', orderSyncEnabled: true }),
    )

    expect(wrapper.find('[data-testid="ifood-stage-connect"]').text()).toContain('Conectado')
    expect(wrapper.find('[data-testid="ifood-stage-catalog"]').text()).toContain('Importado')
    expect(wrapper.find('[data-testid="ifood-stage-sync"]').text()).toContain('Ativa')
  })

  it('desconectado: etapas 2 e 3 ficam pendentes com ações desabilitadas', async () => {
    const wrapper = await mountView(statusOf())

    expect(wrapper.find('[data-testid="ifood-stage-connect"]').text()).toContain('Pendente')
    expect(wrapper.find('[data-testid="ifood-stage-catalog"]').text()).toContain('Pendente')
    expect(wrapper.find('[data-testid="ifood-stage-sync"]').text()).toContain('Inativa')
    expect(
      wrapper.find('[data-testid="ifood-stage-catalog-action"]').attributes('disabled'),
    ).toBeDefined()
    expect(
      wrapper.find('[data-testid="ifood-stage-sync-action"]').attributes('disabled'),
    ).toBeDefined()
  })

  it('clicar na ação da etapa 1 abre o modal de conexão', async () => {
    const wrapper = await mountView(statusOf())

    await wrapper.find('[data-testid="ifood-stage-connect-action"]').trigger('click')

    expect(wrapper.findComponent(IfoodConnectModal).exists()).toBe(true)
  })

  it('clicar na ação da etapa 2 abre o modal de importação de cardápio', async () => {
    const wrapper = await mountView(statusOf({ connected: true }))

    await wrapper.find('[data-testid="ifood-stage-catalog-action"]').trigger('click')

    expect(wrapper.findComponent(IfoodCatalogImportModal).exists()).toBe(true)
  })

  it('a etapa de publicação fica desabilitada enquanto a conta não estiver conectada', async () => {
    const wrapper = await mountView(statusOf())

    const stage = wrapper.find('[data-testid="ifood-stage-publish"]')
    expect(stage.exists()).toBe(true)
    expect(stage.text()).toContain('Cardápio Digital')
    expect(
      wrapper.find('[data-testid="ifood-stage-publish-action"]').attributes('disabled'),
    ).toBeDefined()
  })

  it('clicar na ação de publicação abre o modal de publicação de cardápio', async () => {
    const wrapper = await mountView(statusOf({ connected: true }))

    await wrapper.find('[data-testid="ifood-stage-publish-action"]').trigger('click')

    expect(wrapper.findComponent(IfoodCatalogPublishModal).exists()).toBe(true)
  })

  it('clicar na ação da etapa 3 abre o modal de sincronia', async () => {
    const wrapper = await mountView(statusOf({ connected: true }))

    await wrapper.find('[data-testid="ifood-stage-sync-action"]').trigger('click')

    expect(wrapper.findComponent(IfoodOrderSyncModal).exists()).toBe(true)
  })

  it('evento updated do modal de sincronia atualiza o estado da etapa 3', async () => {
    const wrapper = await mountView(statusOf({ connected: true }))
    await wrapper.find('[data-testid="ifood-stage-sync-action"]').trigger('click')

    wrapper.findComponent(IfoodOrderSyncModal).vm.$emit(
      'updated',
      statusOf({ connected: true, orderSyncEnabled: true }),
    )
    await flushPromises()

    expect(wrapper.find('[data-testid="ifood-stage-sync"]').text()).toContain('Ativa')
  })

  it('importação concluída recarrega o status do checklist', async () => {
    const wrapper = await mountView(statusOf({ connected: true }))
    await wrapper.find('[data-testid="ifood-stage-catalog-action"]').trigger('click')
    mockedService.status.mockResolvedValue(
      statusOf({ connected: true, catalogImportedAt: '2026-07-06T09:00:00' }),
    )

    wrapper.findComponent(IfoodCatalogImportModal).vm.$emit('imported', {
      importedProducts: 1, linkedProducts: 0, skippedProducts: 0,
      importedCategories: 1, linkedCategories: 0, items: [],
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="ifood-stage-catalog"]').text()).toContain('Importado')
  })

  it('conexão em homologação: botão Conectar desabilitado com aviso', async () => {
    const wrapper = await mountView(statusOf({ connectionEnabled: false }))

    const action = wrapper.find('[data-testid="ifood-stage-connect-action"]')
    expect(action.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="ifood-stage-connect"]').text().toLowerCase())
      .toContain('homologação')
  })

  it('conexão em homologação: clicar em Conectar não abre o modal', async () => {
    const wrapper = await mountView(statusOf({ connectionEnabled: false }))

    await wrapper.find('[data-testid="ifood-stage-connect-action"]').trigger('click')

    expect(wrapper.findComponent(IfoodConnectModal).exists()).toBe(false)
  })

  it('conexão em homologação mas já conectado: Desconectar continua habilitado', async () => {
    const wrapper = await mountView(statusOf({ connected: true, connectionEnabled: false }))

    const action = wrapper.find('[data-testid="ifood-stage-connect-action"]')
    expect(action.text()).toContain('Desconectar')
    expect(action.attributes('disabled')).toBeUndefined()
  })

  it('sincronia ativa sem catálogo importado mostra indicador de aviso na etapa 3', async () => {
    const wrapper = await mountView(
      statusOf({ connected: true, catalogImportedAt: null, orderSyncEnabled: true }),
    )

    expect(wrapper.find('[data-testid="ifood-stage-sync-warning"]').exists()).toBe(true)
  })
})

describe('SettingsView — checklist Anota.AI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  it('sem token: etapa 1 pendente e importação manual desabilitada', async () => {
    const wrapper = await mountView(statusOf())

    expect(wrapper.find('[data-testid="anotaai-stage-connect"]').text()).toContain('Pendente')
    expect(wrapper.find('[data-testid="anotaai-stage-hours"]').text()).toContain('Pendente')
    expect(
      wrapper.find('[data-testid="anotaai-stage-import-action"]').attributes('disabled'),
    ).toBeDefined()
  })

  it('com token e horários: etapas conectada e configurada, importação habilitada', async () => {
    mockUser = {
      anotaAiApiKey: 'token-123',
      openingHours: [{ dayOfWeek: 'MONDAY', openTime: '11:00', closeTime: '22:00', closed: false }],
    }
    const wrapper = await mountView(statusOf())

    expect(wrapper.find('[data-testid="anotaai-stage-connect"]').text()).toContain('Conectado')
    expect(wrapper.find('[data-testid="anotaai-stage-hours"]').text()).toContain('Configurado')
    expect(
      wrapper.find('[data-testid="anotaai-stage-import-action"]').attributes('disabled'),
    ).toBeUndefined()
  })

  it('ação da etapa de horários leva à seção de horários de funcionamento', async () => {
    const wrapper = await mountView(statusOf())

    await wrapper.find('[data-testid="anotaai-stage-hours-action"]').trigger('click')

    expect(wrapper.text()).toContain('Horários de funcionamento')
  })

  it('importar agora chama a sincronização de pedidos do Anota.AI', async () => {
    mockUser = { anotaAiApiKey: 'token-123', openingHours: [] }
    const wrapper = await mountView(statusOf())

    await wrapper.find('[data-testid="anotaai-stage-import-action"]').trigger('click')

    expect(anotaAIStoreMock.syncOrders).toHaveBeenCalled()
  })

  it('exibe o resultado da última importação dentro do card', async () => {
    mockUser = { anotaAiApiKey: 'token-123', openingHours: [] }
    anotaAIStoreMock.lastResult = { ordersImported: 3, ordersSkipped: 2, missingIngredientNames: [] }
    const wrapper = await mountView(statusOf())

    expect(wrapper.text()).toContain('3 pedido(s) importado(s)')
    expect(wrapper.text()).toContain('2 já existente(s)')
  })
})

describe('SettingsView — Plano e pagamento', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  const basicPlan: PlanResponse = {
    id: 'plan-1',
    name: 'Básico',
    minRevenue: 0,
    maxRevenue: null,
    priceMonthly: 50,
    features: { allFeatures: true, description: 'Acesso a todas as funcionalidades' },
    active: true,
    createdAt: '2026-07-10T10:00:00',
  }

  function subscriptionOf(overrides: Partial<SubscriptionResponse> = {}): SubscriptionResponse {
    return {
      id: 'sub-1',
      merchantId: 'm-1',
      planId: null,
      planName: null,
      status: 'TRIAL',
      trialEndsAt: '2026-07-17T10:00:00',
      currentPeriodStart: null,
      currentPeriodEnd: null,
      createdAt: '2026-07-10T10:00:00',
      updatedAt: '2026-07-10T10:00:00',
      ...overrides,
    }
  }

  async function openBilling(subscription: SubscriptionResponse, plans: PlanResponse[]) {
    mockedBilling.getMySubscription.mockResolvedValue(subscription)
    mockedBilling.listPlans.mockResolvedValue(plans)
    const wrapper = await mountView(statusOf())
    const item = wrapper
      .findAll('.settings-subnav-item')
      .find((el) => el.text().includes('Plano e pagamento'))
    await item!.trigger('click')
    await flushPromises()
    return wrapper
  }

  it('exibe o período de teste e o plano Básico com preço mensal', async () => {
    const wrapper = await openBilling(subscriptionOf(), [basicPlan])

    expect(mockedBilling.getMySubscription).toHaveBeenCalled()
    expect(mockedBilling.listPlans).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="billing-status"]').text()).toContain('Período de teste')

    const planCard = wrapper.find('[data-testid="billing-plan-card"]')
    expect(planCard.text()).toContain('Básico')
    expect(planCard.text()).toContain('50,00')
    expect(planCard.text()).toContain('todas as funcionalidades')
  })

  it('assinatura ativa mostra o plano atual e a vigência', async () => {
    const wrapper = await openBilling(
      subscriptionOf({
        status: 'ACTIVE',
        planId: 'plan-1',
        planName: 'Básico',
        trialEndsAt: null,
        currentPeriodStart: '2026-07-10T10:00:00',
        currentPeriodEnd: '2026-08-10T10:00:00',
      }),
      [basicPlan],
    )

    const status = wrapper.find('[data-testid="billing-status"]')
    expect(status.text()).toContain('Ativa')
    expect(status.text()).toContain('Básico')
  })

  it('avisa em pt-BR que o pagamento está indisponível e não oferece botão de assinar', async () => {
    const wrapper = await openBilling(subscriptionOf(), [basicPlan])

    const notice = wrapper.find('[data-testid="billing-unavailable-notice"]')
    expect(notice.exists()).toBe(true)
    expect(notice.text()).toContain('indisponível')
    expect(notice.text()).toContain('contato')
    // No dead button: nothing may look clickable while there is no provider.
    expect(wrapper.find('[data-testid="billing-subscribe-action"]').exists()).toBe(false)
    expect(mockedBilling.createCheckout).not.toHaveBeenCalled()
  })

  it('mostra os planos mesmo quando o merchant ainda não tem assinatura registrada', async () => {
    mockedBilling.getMySubscription.mockRejectedValue({ response: { status: 404 } })
    mockedBilling.listPlans.mockResolvedValue([basicPlan])
    const wrapper = await mountView(statusOf())
    const item = wrapper
      .findAll('.settings-subnav-item')
      .find((el) => el.text().includes('Plano e pagamento'))
    await item!.trigger('click')
    await flushPromises()

    const planCard = wrapper.find('[data-testid="billing-plan-card"]')
    expect(planCard.exists()).toBe(true)
    expect(planCard.text()).toContain('Básico')
    expect(wrapper.find('[data-testid="billing-unavailable-notice"]').exists()).toBe(true)
  })
})
