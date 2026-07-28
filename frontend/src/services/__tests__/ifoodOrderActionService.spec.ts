import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  ifoodOrderActionService,
  orderActionErrorMessage,
} from '@/services/ifoodOrderActionService'
import api from '@/services/api'

vi.mock('@/services/api', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
  },
}))

const ACTION_RESULT = {
  orderId: 'o1',
  externalOrderId: 'ext-1',
  status: 'PENDING',
}

describe('ifoodOrderActionService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('confirm should POST /integrations/ifood/orders/{id}/confirm without a body', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: ACTION_RESULT })

    const result = await ifoodOrderActionService.confirm('o1')

    expect(api.post).toHaveBeenCalledWith('/integrations/ifood/orders/o1/confirm')
    expect(result.status).toBe('PENDING')
    expect(result.externalOrderId).toBe('ext-1')
  })

  it('readyToPickup should POST /integrations/ifood/orders/{id}/ready-to-pickup', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { ...ACTION_RESULT, status: 'READY' } })

    const result = await ifoodOrderActionService.readyToPickup('o1')

    expect(api.post).toHaveBeenCalledWith('/integrations/ifood/orders/o1/ready-to-pickup')
    expect(result.status).toBe('READY')
  })

  it('dispatch should POST /integrations/ifood/orders/{id}/dispatch', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { ...ACTION_RESULT, status: 'DELIVERED' } })

    const result = await ifoodOrderActionService.dispatch('o1')

    expect(api.post).toHaveBeenCalledWith('/integrations/ifood/orders/o1/dispatch')
    expect(result.status).toBe('DELIVERED')
  })

  it('getConfirmationWindow should GET the confirmation window of the order', async () => {
    const window = {
      orderId: 'o1',
      createdAt: '2026-07-01T18:00:00',
      deadline: '2026-07-01T18:08:00',
      remainingSeconds: 300,
      expired: false,
    }
    vi.mocked(api.get).mockResolvedValue({ data: window })

    const result = await ifoodOrderActionService.getConfirmationWindow('o1')

    expect(api.get).toHaveBeenCalledWith('/integrations/ifood/orders/o1/confirmation-window')
    expect(result.remainingSeconds).toBe(300)
    expect(result.expired).toBe(false)
  })

  describe('orderActionErrorMessage', () => {
    it('should surface the pt-BR ProblemDetail message returned by the backend', () => {
      const err = { response: { status: 409, data: { detail: 'Pedido já foi cancelado.' } } }

      expect(orderActionErrorMessage(err)).toBe('Pedido já foi cancelado.')
    })

    it('should fall back to generic pt-BR copy when there is no detail', () => {
      expect(orderActionErrorMessage(new Error('boom'))).toBe(
        'Não foi possível concluir a operação. Tente novamente em instantes.',
      )
    })

    it('should ignore a blank detail', () => {
      const err = { response: { data: { detail: '   ' } } }

      expect(orderActionErrorMessage(err)).toBe(
        'Não foi possível concluir a operação. Tente novamente em instantes.',
      )
    })

    it('should accept a custom fallback', () => {
      expect(orderActionErrorMessage(undefined, 'Falha ao confirmar o pedido.')).toBe(
        'Falha ao confirmar o pedido.',
      )
    })
  })
})
