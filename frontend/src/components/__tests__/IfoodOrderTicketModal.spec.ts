import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import IfoodOrderTicketModal from '@/components/IfoodOrderTicketModal.vue'
import { ifoodOrderActionService } from '@/services/ifoodOrderActionService'
import type { OrderResponse } from '@/types/Order'

vi.mock('@/services/ifoodOrderActionService', async (importOriginal) => {
  const original =
    await importOriginal<typeof import('@/services/ifoodOrderActionService')>()
  return {
    ...original,
    ifoodOrderActionService: {
      confirm: vi.fn(),
      readyToPickup: vi.fn(),
      dispatch: vi.fn(),
      getConfirmationWindow: vi.fn(),
    },
  }
})

const mockedService = vi.mocked(ifoodOrderActionService)

/** Bare order: every field the homologation adds is absent (manual/legacy order). */
function bareOrder(overrides: Partial<OrderResponse> = {}): OrderResponse {
  return {
    id: 'o1',
    dateTime: '2026-07-01T18:00:00',
    customerId: 'c1',
    customerName: 'Maria',
    status: 'PENDING',
    totalValue: 49.97,
    estimatedProfit: 20,
    origin: 'IFOOD',
    items: [
      {
        id: 'it1',
        productId: 'p1',
        productName: 'Açaí 500ml',
        quantity: 2,
        unitPrice: 24.985,
        unitCost: 8,
        totalCost: 16,
      },
    ],
    ...overrides,
  }
}

/** Full order: everything the iFood homologation requires on the ticket. */
function fullOrder(overrides: Partial<OrderResponse> = {}): OrderResponse {
  return bareOrder({
    displayId: '3421',
    orderType: 'DELIVERY',
    orderTiming: 'IMMEDIATE',
    customerDocument: '12345678909',
    paymentPrepaidAmount: 10,
    paymentPendingAmount: 39.97,
    paymentMethods: [
      {
        id: 'pm1',
        method: 'CASH',
        type: 'OFFLINE',
        cardBrand: null,
        value: 39.97,
        currency: 'BRL',
        changeFor: 50,
        changeAmount: 10.03,
      },
      {
        id: 'pm2',
        method: 'CREDIT',
        type: 'ONLINE',
        cardBrand: 'VISA',
        value: 10,
        currency: 'BRL',
        changeFor: null,
        changeAmount: null,
      },
    ],
    discountTotal: 12,
    discountIfoodValue: 8,
    discountMerchantValue: 4,
    deliveryMode: 'DEFAULT',
    deliveredBy: 'MERCHANT',
    deliveryDateTime: '2026-07-01T18:40:00',
    deliveryObservations: 'Portão azul, tocar a campainha',
    pickupCode: '9182',
    items: [
      {
        id: 'it1',
        productId: 'p1',
        productName: 'Açaí 500ml',
        quantity: 2,
        unitPrice: 24.985,
        unitCost: 8,
        totalCost: 16,
        observations: 'sem granola',
      },
    ],
    ...overrides,
  })
}

const WINDOW_OPEN = {
  orderId: 'o1',
  createdAt: '2026-07-01T18:00:00',
  deadline: '2026-07-01T18:08:00',
  remainingSeconds: 300,
  expired: false,
}

const WINDOW_EXPIRED = { ...WINDOW_OPEN, remainingSeconds: 0, expired: true }

enableAutoUnmount(afterEach)

function mountTicket(order: OrderResponse) {
  return mount(IfoodOrderTicketModal, { props: { order } })
}

describe('IfoodOrderTicketModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedService.getConfirmationWindow.mockResolvedValue(WINDOW_OPEN)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('homologation fields', () => {
    it('should show the iFood display id of the order', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-display-id"]').text()).toContain('3421')
    })

    it('should show the order type and timing in pt-BR', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-type"]').text()).toBe('Entrega')
      expect(wrapper.get('[data-testid="ifood-ticket-timing"]').text()).toBe('Imediato')
    })

    it('should label a takeout order as Retirada', async () => {
      const wrapper = mountTicket(fullOrder({ orderType: 'TAKEOUT' }))
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-type"]').text()).toBe('Retirada')
    })

    it('should show the customer document formatted as CPF', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      const document = wrapper.get('[data-testid="ifood-ticket-document"]')
      expect(document.text()).toContain('123.456.789-09')
      expect(document.text()).toContain('CPF')
    })

    it('should show the customer document formatted as CNPJ when it has 14 digits', async () => {
      const wrapper = mountTicket(fullOrder({ customerDocument: '12345678000195' }))
      await flushPromises()

      const document = wrapper.get('[data-testid="ifood-ticket-document"]')
      expect(document.text()).toContain('12.345.678/0001-95')
      expect(document.text()).toContain('CNPJ')
    })

    it('should show the card brand of a card payment', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      const card = wrapper.get('[data-testid="ifood-ticket-payment-pm2"]')
      expect(card.text()).toContain('Crédito')
      expect(card.text()).toContain('VISA')
      expect(card.text()).toContain('Pago online')
    })

    it('should show the cash change exactly as computed by the backend', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      const cash = wrapper.get('[data-testid="ifood-ticket-payment-pm1"]')
      expect(cash.text()).toContain('Dinheiro')
      // troco = changeAmount, rendered as-is (never recomputed here)
      expect(wrapper.get('[data-testid="ifood-ticket-change-pm1"]').text()).toContain('R$ 10,03')
      expect(wrapper.get('[data-testid="ifood-ticket-change-pm1"]').text()).toContain('R$ 50,00')
      expect(wrapper.get('[data-testid="ifood-ticket-change-pm1"]').text()).toContain('Troco')
    })

    it('should not render a change line for a payment without change', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-change-pm2"]').exists()).toBe(false)
    })

    it('should show the prepaid and pending payment amounts', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-prepaid"]').text()).toContain('R$ 10,00')
      expect(wrapper.get('[data-testid="ifood-ticket-pending"]').text()).toContain('R$ 39,97')
    })

    it('should show the coupon value and who pays for each share', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-discount-total"]').text()).toContain(
        'R$ 12,00',
      )
      const ifoodShare = wrapper.get('[data-testid="ifood-ticket-discount-ifood"]')
      expect(ifoodShare.text()).toContain('iFood')
      expect(ifoodShare.text()).toContain('R$ 8,00')
      const merchantShare = wrapper.get('[data-testid="ifood-ticket-discount-merchant"]')
      expect(merchantShare.text()).toContain('Loja')
      expect(merchantShare.text()).toContain('R$ 4,00')
      // split is exhaustive here — no residual line
      expect(wrapper.find('[data-testid="ifood-ticket-discount-other"]').exists()).toBe(false)
    })

    it('should show the unattributed remainder when the sponsors do not cover the total', async () => {
      const wrapper = mountTicket(
        fullOrder({ discountTotal: 12, discountIfoodValue: 5, discountMerchantValue: 4 }),
      )
      await flushPromises()

      const other = wrapper.get('[data-testid="ifood-ticket-discount-other"]')
      expect(other.text()).toContain('R$ 3,00')
    })

    it('should show the item observations', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-item-it1-observations"]').text()).toContain(
        'sem granola',
      )
    })

    it('should show the collection code', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-pickup-code"]').text()).toContain('9182')
    })

    it('should show the delivery observations and who delivers', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-delivery-observations"]').text()).toContain(
        'Portão azul, tocar a campainha',
      )
      expect(wrapper.get('[data-testid="ifood-ticket-delivered-by"]').text()).toContain(
        'Entrega própria',
      )
    })

    it('should show an unknown deliveredBy value verbatim', async () => {
      const wrapper = mountTicket(fullOrder({ deliveredBy: 'PARTNER' }))
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-delivered-by"]').text()).toContain('PARTNER')
    })

    it('should show the takeout schedule for a takeout order', async () => {
      const wrapper = mountTicket(
        fullOrder({
          orderType: 'TAKEOUT',
          takeoutMode: 'DEFAULT',
          takeoutDateTime: '2026-07-01T18:30:00',
        }),
      )
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-takeout"]').exists()).toBe(true)
    })
  })

  describe('null safety', () => {
    it('should not render the payment, coupon, delivery or takeout sections without data', async () => {
      const wrapper = mountTicket(bareOrder({ paymentMethods: [] }))
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-payments"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ifood-ticket-discounts"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ifood-ticket-delivery"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ifood-ticket-takeout"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ifood-ticket-document"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ifood-ticket-pickup-code"]').exists()).toBe(false)
    })

    it('should never render a missing amount as R$ 0,00', async () => {
      const wrapper = mountTicket(bareOrder())
      await flushPromises()

      expect(wrapper.text()).not.toContain('R$ 0,00')
    })

    it('should tolerate paymentMethods being absent from the payload', async () => {
      const wrapper = mountTicket(bareOrder())
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-payments"]').exists()).toBe(false)
    })

    it('should not render an observations line for an item without observations', async () => {
      const wrapper = mountTicket(bareOrder())
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-item-it1-observations"]').exists()).toBe(
        false,
      )
    })

    it('should render the delivery section when only the observations are present', async () => {
      const wrapper = mountTicket(bareOrder({ deliveryObservations: 'Deixar na portaria' }))
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-delivery-observations"]').text()).toContain(
        'Deixar na portaria',
      )
    })

    it('should show a placeholder instead of an empty display id', async () => {
      const wrapper = mountTicket(bareOrder())
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-display-id"]').exists()).toBe(false)
    })
  })

  describe('confirmation SLA', () => {
    it('should fetch the confirmation window once for an iFood order', async () => {
      mountTicket(fullOrder())
      await flushPromises()

      expect(mockedService.getConfirmationWindow).toHaveBeenCalledTimes(1)
      expect(mockedService.getConfirmationWindow).toHaveBeenCalledWith('o1')
    })

    it('should show the remaining time as mm:ss', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-countdown"]').text()).toContain('05:00')
    })

    it('should count down locally without calling the endpoint again', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-countdown"]').text()).toContain('05:00')

      vi.advanceTimersByTime(30_000)
      await nextTick()

      expect(wrapper.get('[data-testid="ifood-ticket-countdown"]').text()).toContain('04:30')
      expect(mockedService.getConfirmationWindow).toHaveBeenCalledTimes(1)
    })

    it('should make the expiry obvious when the window is already expired', async () => {
      mockedService.getConfirmationWindow.mockResolvedValue(WINDOW_EXPIRED)
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-countdown"]').text()).toContain('expirado')
    })

    it('should reach the expired state when the countdown runs out locally', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      vi.advanceTimersByTime(301_000)
      await nextTick()

      expect(wrapper.get('[data-testid="ifood-ticket-countdown"]').text()).toContain('expirado')
    })

    it('should never confirm the order on its own', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
      mountTicket(fullOrder())
      await flushPromises()

      vi.advanceTimersByTime(601_000)
      await nextTick()

      expect(mockedService.confirm).not.toHaveBeenCalled()
    })

    it('should not fetch the confirmation window for a non-iFood order', async () => {
      mountTicket(bareOrder({ origin: 'MENUBANK' }))
      await flushPromises()

      expect(mockedService.getConfirmationWindow).not.toHaveBeenCalled()
    })

    it('should not fetch the confirmation window for a cancelled order', async () => {
      mountTicket(fullOrder({ status: 'CANCELLED' }))
      await flushPromises()

      expect(mockedService.getConfirmationWindow).not.toHaveBeenCalled()
    })

    it('should still render the ticket when the confirmation window call fails', async () => {
      mockedService.getConfirmationWindow.mockRejectedValue(new Error('boom'))
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-countdown"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="ifood-ticket-display-id"]').text()).toContain('3421')
    })
  })

  describe('lifecycle actions', () => {
    it('should offer Despachar for a delivery order', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-dispatch"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="ifood-ticket-ready"]').exists()).toBe(false)
    })

    it('should offer Pronto para retirada for a takeout order', async () => {
      const wrapper = mountTicket(fullOrder({ orderType: 'TAKEOUT' }))
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-ready"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="ifood-ticket-dispatch"]').exists()).toBe(false)
    })

    it('should offer both actions with a warning when the order type is unknown', async () => {
      const wrapper = mountTicket(fullOrder({ orderType: null }))
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-ready"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="ifood-ticket-dispatch"]').exists()).toBe(true)
      const warning = wrapper.get('[data-testid="ifood-ticket-unknown-type-warning"]')
      expect(warning.text()).toContain('não foi informado')
      expect(warning.text()).toContain('entrega')
      expect(warning.text()).toContain('retirada')
    })

    it('should offer no action for a dine-in order', async () => {
      const wrapper = mountTicket(fullOrder({ orderType: 'DINE_IN' }))
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-ready"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ifood-ticket-dispatch"]').exists()).toBe(false)
    })

    it('should offer no action for a non-iFood order', async () => {
      const wrapper = mountTicket(bareOrder({ origin: 'MENUBANK', orderType: 'DELIVERY' }))
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-dispatch"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ifood-ticket-confirm"]').exists()).toBe(false)
    })

    it('should offer no action for a cancelled order', async () => {
      const wrapper = mountTicket(fullOrder({ status: 'CANCELLED' }))
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-dispatch"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ifood-ticket-confirm"]').exists()).toBe(false)
    })

    it('should offer no action for a test order', async () => {
      const wrapper = mountTicket(fullOrder({ status: 'TEST' }))
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-dispatch"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="ifood-ticket-confirm"]').exists()).toBe(false)
    })

    it('should confirm the order and reflect the returned status', async () => {
      mockedService.confirm.mockResolvedValue({
        orderId: 'o1',
        externalOrderId: 'ext-1',
        status: 'PENDING',
      })
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      await wrapper.get('[data-testid="ifood-ticket-confirm"]').trigger('click')
      await flushPromises()

      expect(mockedService.confirm).toHaveBeenCalledWith('o1')
      expect(wrapper.get('[data-testid="ifood-ticket-status"]').text()).toContain('Pendente')
      expect(wrapper.emitted('updated')?.[0]?.[0]).toMatchObject({
        orderId: 'o1',
        status: 'PENDING',
      })
    })

    it('should dispatch the order and reflect the returned status', async () => {
      mockedService.dispatch.mockResolvedValue({
        orderId: 'o1',
        externalOrderId: 'ext-1',
        status: 'DELIVERED',
      })
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      await wrapper.get('[data-testid="ifood-ticket-dispatch"]').trigger('click')
      await flushPromises()

      expect(mockedService.dispatch).toHaveBeenCalledWith('o1')
      expect(wrapper.get('[data-testid="ifood-ticket-status"]').text()).toContain('Entregue')
    })

    it('should mark the order ready to pickup and reflect the returned status', async () => {
      mockedService.readyToPickup.mockResolvedValue({
        orderId: 'o1',
        externalOrderId: 'ext-1',
        status: 'READY',
      })
      const wrapper = mountTicket(fullOrder({ orderType: 'TAKEOUT' }))
      await flushPromises()

      await wrapper.get('[data-testid="ifood-ticket-ready"]').trigger('click')
      await flushPromises()

      expect(mockedService.readyToPickup).toHaveBeenCalledWith('o1')
      expect(wrapper.get('[data-testid="ifood-ticket-status"]').text()).toContain('Pronto')
    })

    it('should show the backend pt-BR detail when an action fails', async () => {
      mockedService.dispatch.mockRejectedValue({
        response: { status: 409, data: { detail: 'Pedido do iFood sem identificador externo.' } },
      })
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      await wrapper.get('[data-testid="ifood-ticket-dispatch"]').trigger('click')
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-error"]').text()).toContain(
        'Pedido do iFood sem identificador externo.',
      )
    })

    it('should show generic pt-BR copy when the failure carries no detail', async () => {
      mockedService.confirm.mockRejectedValue(new Error('network'))
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      await wrapper.get('[data-testid="ifood-ticket-confirm"]').trigger('click')
      await flushPromises()

      expect(wrapper.get('[data-testid="ifood-ticket-error"]').text()).toContain(
        'Não foi possível',
      )
    })

    it('should emit close when the modal is dismissed', async () => {
      const wrapper = mountTicket(fullOrder())
      await flushPromises()

      await wrapper.get('[data-testid="ui-modal-close"]').trigger('click')

      expect(wrapper.emitted('close')).toBeTruthy()
    })
  })
})
