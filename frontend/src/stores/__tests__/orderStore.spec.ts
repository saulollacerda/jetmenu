import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useOrderStore } from '@/stores/orderStore'
import { orderService } from '@/services/orderService'
import type { OrderResponse } from '@/types/Order'

vi.mock('@/services/orderService')

const mockedService = vi.mocked(orderService)

function asPage<T>(content: T[], size = 20) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    number: 0,
    size,
    first: true,
    last: true,
    empty: content.length === 0,
  }
}

describe('orderStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchPage should populate items and pagination state', async () => {
    const mockData = [
      {
        id: '1',
        dateTime: '2026-03-24T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PENDING' as const,
        totalValue: 50.0,
        estimatedProfit: 20.0,
        items: [],
      },
    ]
    mockedService.findAll.mockResolvedValue(asPage(mockData))

    const store = useOrderStore()
    await store.fetchPage({ search: 'joão' })

    expect(store.items).toEqual(mockData)
    expect(store.search).toBe('joão')
    expect(store.loading).toBe(false)
  })

  it('create should refetch the current page', async () => {
    const created = {
      id: '1',
      dateTime: '2026-03-24T10:00:00',
      customerId: 'c1',
      customerName: 'João',
      status: 'PENDING' as const,
      totalValue: 50.0,
      estimatedProfit: 20.0,
      items: [],
    }
    mockedService.create.mockResolvedValue(created)
    mockedService.findAll.mockResolvedValue(asPage([created]))

    const store = useOrderStore()
    await store.create({
      customerId: 'c1',
      items: [
        {
          productId: 'p1',
          quantity: 2,
          extraIngredients: [{ ingredientId: 'i1', quantity: 1.5 }],
        },
      ],
    })

    expect(store.items).toContainEqual(created)
  })

  it('create should accept a quick-create payload with customerName only', async () => {
    const created = {
      id: '1',
      dateTime: '2026-03-24T10:00:00',
      customerId: 'c9',
      customerName: 'Maria',
      status: 'PAID' as const,
      totalValue: 30.0,
      estimatedProfit: 10.0,
      items: [],
    }
    mockedService.create.mockResolvedValue(created)
    mockedService.findAll.mockResolvedValue(asPage([created]))

    const store = useOrderStore()
    await store.create({
      customerName: 'Maria',
      items: [{ productId: 'p1', quantity: 1 }],
    })

    expect(mockedService.create).toHaveBeenCalledWith({
      customerName: 'Maria',
      items: [{ productId: 'p1', quantity: 1 }],
    })
  })

  it('create should surface the backend ProblemDetail message on failure', async () => {
    mockedService.create.mockRejectedValue({
      response: { data: { detail: 'Cliente é obrigatório' } },
    })

    const store = useOrderStore()
    await expect(
      store.create({ customerId: 'c1', items: [{ productId: 'p1', quantity: 1 }] }),
    ).rejects.toBeTruthy()

    expect(store.error).toBe('Cliente é obrigatório')
  })

  it('create should fall back to a generic message when no detail is present', async () => {
    mockedService.create.mockRejectedValue(new Error('network'))

    const store = useOrderStore()
    await expect(
      store.create({ customerId: 'c1', items: [{ productId: 'p1', quantity: 1 }] }),
    ).rejects.toBeTruthy()

    expect(store.error).toBe('Erro ao criar pedido')
  })

  it('update should surface the backend ProblemDetail message on failure', async () => {
    mockedService.update.mockRejectedValue({
      response: { data: { detail: 'Cliente com ID x não encontrado' } },
    })

    const store = useOrderStore()
    await expect(
      store.update('1', { customerId: 'c1', items: [{ productId: 'p1', quantity: 1 }] }),
    ).rejects.toBeTruthy()

    expect(store.error).toBe('Cliente com ID x não encontrado')
  })

  it('keeps items and uses refreshing (not loading) during a silent background refetch', async () => {
    vi.useFakeTimers()
    try {
      const first = {
        id: '1',
        dateTime: '2026-03-24T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PENDING' as const,
        totalValue: 50.0,
        estimatedProfit: 20.0,
        items: [],
      }
      mockedService.findAll.mockResolvedValueOnce(asPage([first]))

      const store = useOrderStore()
      await store.fetchPage({ page: 0, search: '' })
      expect(store.items).toEqual([first])

      // Jump past the window so the silent poll actually issues a request.
      vi.advanceTimersByTime(10 * 60 * 1000 + 1)

      let resolveSecond!: (v: ReturnType<typeof asPage<OrderResponse>>) => void
      mockedService.findAll.mockReturnValueOnce(
        new Promise((r) => {
          resolveSecond = r
        }),
      )
      const pending = store.fetchPage({}, true)
      await Promise.resolve()

      // Background poll must not clear the list or flip the full-view loading flag.
      expect(store.loading).toBe(false)
      expect(store.refreshing).toBe(true)
      expect(store.items).toEqual([first])

      resolveSecond(asPage([]))
      await pending

      expect(store.refreshing).toBe(false)
      expect(store.loading).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('does not refetch the same page within the 10-minute window and refetches after it', async () => {
    vi.useFakeTimers()
    try {
      mockedService.findAll.mockResolvedValue(asPage([]))

      const store = useOrderStore()
      await store.fetchPage({ page: 0, search: '' })
      expect(mockedService.findAll).toHaveBeenCalledTimes(1)

      // Same params within the window: served from cache, no new request.
      await store.fetchPage({ page: 0, search: '' })
      expect(mockedService.findAll).toHaveBeenCalledTimes(1)

      vi.advanceTimersByTime(10 * 60 * 1000 + 1)
      await store.fetchPage({}, true)
      expect(mockedService.findAll).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('a page change bypasses the cache window even inside it', async () => {
    vi.useFakeTimers()
    try {
      mockedService.findAll.mockResolvedValue(asPage([]))

      const store = useOrderStore()
      await store.fetchPage({ page: 0 })
      expect(mockedService.findAll).toHaveBeenCalledTimes(1)

      await store.fetchPage({ page: 1 })
      expect(mockedService.findAll).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('create refetches the current page even inside the cache window', async () => {
    vi.useFakeTimers()
    try {
      const created = {
        id: '1',
        dateTime: '2026-03-24T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PENDING' as const,
        totalValue: 50.0,
        estimatedProfit: 20.0,
        items: [],
      }
      mockedService.findAll.mockResolvedValue(asPage([created]))
      mockedService.create.mockResolvedValue(created)

      const store = useOrderStore()
      await store.fetchPage({ page: 0, search: '' })
      expect(mockedService.findAll).toHaveBeenCalledTimes(1)

      await store.create({ customerId: 'c1', items: [{ productId: 'p1', quantity: 1 }] })
      // Mutation must force a refetch despite the fresh cache.
      expect(mockedService.findAll).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('remove should call service and refetch the current page', async () => {
    mockedService.remove.mockResolvedValue()
    mockedService.findAll.mockResolvedValue(asPage([]))

    const store = useOrderStore()
    await store.remove('1')

    expect(mockedService.remove).toHaveBeenCalledWith('1')
    expect(store.items).toHaveLength(0)
  })

  it('updateValues should call the service and replace the order in the current page', async () => {
    const original = {
      id: '1',
      dateTime: '2026-03-24T10:00:00',
      customerId: 'c1',
      customerName: 'João',
      status: 'PAID' as const,
      totalValue: 50.0,
      estimatedProfit: 20.0,
      items: [],
    }
    const updated = {
      ...original,
      totalValue: 45.0,
      totalCost: 18.0,
      estimatedProfit: 27.0,
      originalTotalValue: 50.0,
      originalTotalCost: 20.0,
      originalEstimatedProfit: 20.0,
      valuesOverriddenAt: '2026-08-20T12:00:00',
    }
    mockedService.findAll.mockResolvedValue(asPage([original]))
    mockedService.updateValues.mockResolvedValue(updated)

    const store = useOrderStore()
    await store.fetchPage({})
    const result = await store.updateValues('1', { totalValue: 45.0, totalCost: 18.0 })

    expect(mockedService.updateValues).toHaveBeenCalledWith('1', { totalValue: 45.0, totalCost: 18.0 })
    expect(result).toEqual(updated)
    expect(store.items).toContainEqual(updated)
  })

  it('updateValues should surface the backend ProblemDetail message on failure', async () => {
    mockedService.updateValues.mockRejectedValue({
      response: { data: { detail: 'Valor total não pode ser negativo' } },
    })

    const store = useOrderStore()
    await expect(
      store.updateValues('1', { totalValue: -1, totalCost: 0 }),
    ).rejects.toBeTruthy()

    expect(store.error).toBe('Valor total não pode ser negativo')
  })

  it('restoreValues should call the service and replace the order in the current page', async () => {
    const overridden = {
      id: '1',
      dateTime: '2026-03-24T10:00:00',
      customerId: 'c1',
      customerName: 'João',
      status: 'PAID' as const,
      totalValue: 45.0,
      estimatedProfit: 27.0,
      valuesOverriddenAt: '2026-08-20T12:00:00',
      items: [],
    }
    const restored = {
      ...overridden,
      totalValue: 50.0,
      estimatedProfit: 20.0,
      originalTotalValue: null,
      originalTotalCost: null,
      originalEstimatedProfit: null,
      valuesOverriddenAt: null,
    }
    mockedService.findAll.mockResolvedValue(asPage([overridden]))
    mockedService.restoreValues.mockResolvedValue(restored)

    const store = useOrderStore()
    await store.fetchPage({})
    const result = await store.restoreValues('1')

    expect(mockedService.restoreValues).toHaveBeenCalledWith('1')
    expect(result).toEqual(restored)
    expect(store.items).toContainEqual(restored)
  })

  it('restoreValues should fall back to a generic message when no detail is present', async () => {
    mockedService.restoreValues.mockRejectedValue(new Error('network'))

    const store = useOrderStore()
    await expect(store.restoreValues('1')).rejects.toBeTruthy()

    expect(store.error).toBe('Erro ao restaurar valores originais do pedido')
  })
})
