import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import NotificationBell from '@/components/NotificationBell.vue'
import { notificationService } from '@/services/notificationService'
import type { NotificationResponse } from '@/types/Notification'
import { NOTIFICATION_TYPE_LABELS } from '@/types/Notification'
import { UIIcon } from '@/design'

vi.mock('@/services/notificationService')

const mockedService = vi.mocked(notificationService)

function buildNotification(overrides: Partial<NotificationResponse> = {}): NotificationResponse {
  return {
    id: 'n-1',
    type: 'MISSING_INGREDIENT',
    title: 'Ingrediente não cadastrado',
    message: "O ingrediente 'Pistache' apareceu em um pedido mas não está cadastrado.",
    referenceData: 'pistache',
    referenceDisplay: 'Pistache',
    status: 'UNREAD',
    createdAt: '2026-05-22T12:00:00Z',
    resolvedAt: null,
    ...overrides,
  }
}

function asPage<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    number: 0,
    size: 20,
    first: true,
    last: true,
    empty: content.length === 0,
  }
}

async function mountAndOpen(items: NotificationResponse[]) {
  mockedService.findAll.mockResolvedValue(asPage(items))
  mockedService.unreadCount.mockResolvedValue(items.length)

  const wrapper = mount(NotificationBell)
  await wrapper.find('[data-testid="notification-bell-button"]').trigger('click')
  await flushPromises()
  return wrapper
}

function typeIconOf(wrapper: VueWrapper, id: string) {
  return wrapper.find(`[data-testid="notification-${id}-type-icon"]`)
}

function iconNameInside(wrapper: VueWrapper, container: Element): string | undefined {
  const icon = wrapper.findAllComponents(UIIcon).find((c) => container.contains(c.element))
  return icon?.props('name')
}

describe('NotificationBell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('exporta label pt-BR para cada tipo de notificação', () => {
    expect(NOTIFICATION_TYPE_LABELS.MISSING_INGREDIENT).toBe('Ingrediente não cadastrado')
    expect(NOTIFICATION_TYPE_LABELS.MISSING_PRODUCT).toBe('Produto não cadastrado')
    expect(NOTIFICATION_TYPE_LABELS.ORDER_CANCELLED).toBe('Pedido cancelado')
    expect(NOTIFICATION_TYPE_LABELS.ORDER_CANCELLATION_REQUESTED).toBe(
      'Solicitação de cancelamento',
    )
  })

  it('MISSING_INGREDIENT não resolvida mostra ações "Cadastrar ingrediente" e "Descartar"', async () => {
    const wrapper = await mountAndOpen([buildNotification({ id: 'n-1', type: 'MISSING_INGREDIENT' })])

    expect(wrapper.find('[data-testid="notification-n-1-action-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="notification-n-1-dismiss-button"]').exists()).toBe(true)
  })

  it('MISSING_PRODUCT não resolvida mostra "Descartar" mas não "Cadastrar ingrediente"', async () => {
    const wrapper = await mountAndOpen([
      buildNotification({
        id: 'n-2',
        type: 'MISSING_PRODUCT',
        title: 'Produto não cadastrado',
        message: "O produto 'Produto Fantasma' apareceu em um pedido mas não está cadastrado.",
        referenceData: 'produto fantasma',
        referenceDisplay: 'Produto Fantasma',
      }),
    ])

    expect(wrapper.find('[data-testid="notification-n-2-action-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="notification-n-2-dismiss-button"]').exists()).toBe(true)
  })

  it('ORDER_CANCELLED não resolvida mostra "Descartar" mas não ação primária', async () => {
    const wrapper = await mountAndOpen([
      buildNotification({
        id: 'n-3',
        type: 'ORDER_CANCELLED',
        title: 'Pedido cancelado',
        message: 'O pedido ord-1 foi cancelado no iFood e removido dos ganhos.',
        referenceData: 'ord-1',
        referenceDisplay: 'ord-1',
      }),
    ])

    expect(wrapper.find('[data-testid="notification-n-3-action-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="notification-n-3-dismiss-button"]').exists()).toBe(true)
  })

  it('exibe ícone "x" com label pt-BR para notificação ORDER_CANCELLED', async () => {
    const wrapper = await mountAndOpen([
      buildNotification({ id: 'n-3', type: 'ORDER_CANCELLED', title: 'Pedido cancelado' }),
    ])

    const iconBox = typeIconOf(wrapper, 'n-3')
    expect(iconBox.exists()).toBe(true)
    expect(iconBox.attributes('aria-label')).toBe('Pedido cancelado')
    expect(iconNameInside(wrapper, iconBox.element)).toBe('x')
  })

  it('ORDER_CANCELLATION_REQUESTED aponta a comanda como lugar de responder', async () => {
    const wrapper = await mountAndOpen([
      buildNotification({
        id: 'n-4',
        type: 'ORDER_CANCELLATION_REQUESTED',
        title: 'Solicitação de cancelamento',
        message: 'O cliente solicitou o cancelamento do pedido ext-1.',
        referenceData: 'ord-uuid-1',
        referenceDisplay: 'ext-1',
      }),
    ])

    // Sem ação primária: aceitar/recusar acontece na comanda, com o pedido à vista.
    expect(wrapper.find('[data-testid="notification-n-4-action-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="notification-n-4-dismiss-button"]').exists()).toBe(true)

    const iconBox = typeIconOf(wrapper, 'n-4')
    expect(iconBox.attributes('aria-label')).toBe('Solicitação de cancelamento')
    expect(iconNameInside(wrapper, iconBox.element)).toBe('alert')
  })

  it('mantém ícone "alert" com label pt-BR para MISSING_INGREDIENT', async () => {
    const wrapper = await mountAndOpen([buildNotification()])

    const iconBox = typeIconOf(wrapper, 'n-1')
    expect(iconBox.exists()).toBe(true)
    expect(iconBox.attributes('aria-label')).toBe('Ingrediente não cadastrado')
    expect(iconNameInside(wrapper, iconBox.element)).toBe('alert')
  })
})
