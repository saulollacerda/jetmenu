import { describe, it, expect, vi, beforeEach } from 'vitest'
import { userService } from '@/services/userService'
import api from '@/services/api'
import type { MerchantPreferences } from '@/types/User'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
  },
}))

const PREFERENCES: MerchantPreferences = {
  realtimeMarginCalc: true,
  marginAlertBelow50Pct: false,
  warnUnregisteredIngredients: true,
  includePackagingCostInCost: true,
  targetOrderMarginPct: 30,
}

describe('userService — preferências do merchant', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getPreferences should GET /merchants/me/preferences', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: PREFERENCES })

    const result = await userService.getPreferences()

    expect(api.get).toHaveBeenCalledWith('/merchants/me/preferences')
    expect(result.targetOrderMarginPct).toBe(30)
  })

  it('updatePreferences should PUT the whole preferences object', async () => {
    const updated = { ...PREFERENCES, targetOrderMarginPct: 25 }
    vi.mocked(api.put).mockResolvedValue({ data: updated })

    const result = await userService.updatePreferences(updated)

    // o backend sobrescreve as prefs inteiras — mandar só um campo zeraria os outros
    expect(api.put).toHaveBeenCalledWith('/merchants/me/preferences', updated)
    expect(result.targetOrderMarginPct).toBe(25)
  })

  it('updatePreferences should accept clearing the target order margin', async () => {
    const cleared = { ...PREFERENCES, targetOrderMarginPct: null }
    vi.mocked(api.put).mockResolvedValue({ data: cleared })

    const result = await userService.updatePreferences(cleared)

    expect(result.targetOrderMarginPct).toBeNull()
  })
})
