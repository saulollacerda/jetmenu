import { describe, it, expect, vi, beforeEach } from 'vitest'
import { billingService } from '@/services/billingService'
import api from '@/services/api'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('billingService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listPlans should GET /plans and return active plans', async () => {
    const plans = [
      {
        id: 'plan-1',
        name: 'Básico',
        minRevenue: 0,
        maxRevenue: null,
        priceMonthly: 50.0,
        features: { allFeatures: true },
        active: true,
        createdAt: '2026-07-10T10:00:00',
      },
    ]
    vi.mocked(api.get).mockResolvedValue({ data: plans })

    const result = await billingService.listPlans()

    expect(api.get).toHaveBeenCalledWith('/plans')
    expect(result).toEqual(plans)
  })

  it('getMySubscription should GET /subscription/me and return the subscription', async () => {
    const subscription = {
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
    }
    vi.mocked(api.get).mockResolvedValue({ data: subscription })

    const result = await billingService.getMySubscription()

    expect(api.get).toHaveBeenCalledWith('/subscription/me')
    expect(result).toEqual(subscription)
  })

  // Kept intact although no view calls it today: the next payment provider only has to
  // restore the call site, not this service.
  it('createCheckout should POST /subscription/checkout with planId and return the payment URL', async () => {
    vi.mocked(api.post).mockResolvedValue({
      data: { url: 'https://checkout.example/pay_123' },
    })

    const result = await billingService.createCheckout('plan-1')

    expect(api.post).toHaveBeenCalledWith('/subscription/checkout', { planId: 'plan-1' })
    expect(result.url).toBe('https://checkout.example/pay_123')
  })
})
