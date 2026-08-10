import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useIngredientStore } from '@/stores/ingredientStore'
import { ingredientService } from '@/services/ingredientService'

vi.mock('@/services/ingredientService')

const mockedService = vi.mocked(ingredientService)

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

describe('ingredientStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchPage should populate items and pagination state', async () => {
    const mockData = [
      {
        id: '1',
        name: 'Farinha',
        unit: 'kg',
        costPerUnit: 5.0,
        status: 'ACTIVE' as const,
      },
    ]
    mockedService.findAll.mockResolvedValue(asPage(mockData))

    const store = useIngredientStore()
    await store.fetchPage({ search: 'farinha' })

    expect(store.items).toEqual(mockData)
    expect(store.search).toBe('farinha')
    expect(store.loading).toBe(false)
  })

  it('create should refetch the current page', async () => {
    const created = {
      id: '1',
      name: 'Farinha',
      unit: 'kg',
      costPerUnit: 5.0,
      status: 'ACTIVE' as const,
    }
    mockedService.create.mockResolvedValue(created)
    mockedService.findAll.mockResolvedValue(asPage([created]))

    const store = useIngredientStore()
    await store.create({ name: 'Farinha', unit: 'kg', costPerUnit: 5.0 })

    expect(store.items).toContainEqual(created)
  })

  it('remove should call service and refetch the current page', async () => {
    const remaining = {
      id: '2',
      name: 'Açúcar',
      unit: 'kg',
      costPerUnit: 3.0,
      status: 'ACTIVE' as const,
    }
    mockedService.remove.mockResolvedValue()
    mockedService.findAll.mockResolvedValue(asPage([remaining]))

    const store = useIngredientStore()
    await store.remove('1')

    expect(mockedService.remove).toHaveBeenCalledWith('1')
    expect(store.items).toEqual([remaining])
  })

  describe('reorder', () => {
    const rows = [
      { id: 'a', name: 'A', unit: 'g', costPerUnit: 1, status: 'ACTIVE' as const },
      { id: 'b', name: 'B', unit: 'g', costPerUnit: 1, status: 'ACTIVE' as const },
      { id: 'c', name: 'C', unit: 'g', costPerUnit: 1, status: 'ACTIVE' as const },
    ]

    it('moveWithinPage should optimistically reorder items and call updatePosition', async () => {
      mockedService.updatePosition.mockResolvedValue()
      const store = useIngredientStore()
      store.items = [...rows]

      // move 'a' (index 0) to index 2 on page 0 → global position 2
      await store.moveWithinPage('a', 2, 2)

      expect(store.items.map((i) => i.id)).toEqual(['b', 'c', 'a'])
      expect(mockedService.updatePosition).toHaveBeenCalledWith('a', 2)
    })

    it('moveWithinPage should roll back and set error when the request fails', async () => {
      mockedService.updatePosition.mockRejectedValue(new Error('boom'))
      const store = useIngredientStore()
      store.items = [...rows]

      await expect(store.moveWithinPage('a', 2, 2)).rejects.toThrow()

      // original order restored
      expect(store.items.map((i) => i.id)).toEqual(['a', 'b', 'c'])
      expect(store.error).toBe('Erro ao reordenar ingrediente')
    })

    it('moveToPage should call updatePosition then navigate to the target page', async () => {
      mockedService.updatePosition.mockResolvedValue()
      mockedService.findAll.mockResolvedValue(asPage(rows))
      const store = useIngredientStore()
      store.items = [...rows]

      await store.moveToPage('a', 40, 2)

      expect(mockedService.updatePosition).toHaveBeenCalledWith('a', 40)
      expect(mockedService.findAll).toHaveBeenCalledWith(
        expect.objectContaining({ page: 2 }),
      )
    })

    it('moveToPage should set error and not navigate when the request fails', async () => {
      mockedService.updatePosition.mockRejectedValue(new Error('boom'))
      const store = useIngredientStore()
      store.items = [...rows]

      await expect(store.moveToPage('a', 40, 2)).rejects.toThrow()

      expect(store.error).toBe('Erro ao reordenar ingrediente')
      expect(mockedService.findAll).not.toHaveBeenCalled()
    })
  })

  describe('concurrent searches', () => {
    it('should expose the search term synchronously, before the request resolves', async () => {
      let release!: (v: unknown) => void
      mockedService.findAll.mockReturnValue(
        new Promise((resolve) => {
          release = resolve
        }) as never,
      )

      const store = useIngredientStore()
      const pending = store.fetchPage({ search: 'leite' })

      // O campo de busca é controlado por `search`; ele não pode esperar a rede.
      expect(store.search).toBe('leite')

      release(asPage([]))
      await pending
    })

    it('should not revert search to a stale term when an earlier response lands late', async () => {
      const resolvers: Array<(v: unknown) => void> = []
      mockedService.findAll.mockImplementation(
        () => new Promise((resolve) => resolvers.push(resolve)) as never,
      )

      const store = useIngredientStore()
      const first = store.fetchPage({ search: 'lei' })
      const second = store.fetchPage({ search: 'leite ninho' })

      // A resposta da busca antiga chega depois da nova (out-of-order).
      resolvers[1]!(asPage([{ id: '2', name: 'Leite Ninho' }]))
      resolvers[0]!(asPage([{ id: '1', name: 'Leite' }]))
      await Promise.all([first, second])

      expect(store.search).toBe('leite ninho')
      expect(store.items).toEqual([{ id: '2', name: 'Leite Ninho' }])
    })

    it('should keep loading true while a newer request is still in flight', async () => {
      const resolvers: Array<(v: unknown) => void> = []
      mockedService.findAll.mockImplementation(
        () => new Promise((resolve) => resolvers.push(resolve)) as never,
      )

      const store = useIngredientStore()
      const first = store.fetchPage({ search: 'lei' })
      const second = store.fetchPage({ search: 'leite' })

      resolvers[0]!(asPage([]))
      await first

      expect(store.loading).toBe(true)

      resolvers[1]!(asPage([]))
      await second

      expect(store.loading).toBe(false)
    })
  })
})
