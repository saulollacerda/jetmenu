import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
let ingredientStoreMock: any
const routeMock = { query: {} as Record<string, string | string[]> }
const routerMock = { replace: vi.fn(), push: vi.fn() }

vi.mock('@/stores/ingredientStore', () => ({
  useIngredientStore: () => ingredientStoreMock,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeMock,
  useRouter: () => routerMock,
}))

vi.mock('@/services/productService', () => ({
  productService: {
    findAll: vi.fn().mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 500,
      first: true,
      last: true,
      empty: true,
    }),
  },
}))

vi.mock('@/services/includeService', () => ({
  includeService: {
    add: vi.fn().mockResolvedValue({}),
    update: vi.fn().mockResolvedValue({}),
    remove: vi.fn().mockResolvedValue(undefined),
  },
}))

vi.mock('@/services/ingredientService', () => ({
  ingredientService: {
    fetchUsages: vi.fn().mockResolvedValue([]),
  },
}))

const showToastMock = vi.fn()
vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ showToast: showToastMock }),
}))

import IngredientsView from '@/views/IngredientsView.vue'
import { ingredientService } from '@/services/ingredientService'
import { includeService } from '@/services/includeService'

describe('IngredientsView', () => {
  beforeEach(() => {
    ingredientStoreMock = {
      items: [],
      loading: false,
      error: null,
      search: '',
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      fetchAll: vi.fn(),
      fetchPage: vi.fn(),
      create: vi.fn().mockResolvedValue({}),
      update: vi.fn(),
      remove: vi.fn(),
      moveWithinPage: vi.fn().mockResolvedValue(undefined),
      moveToPage: vi.fn().mockResolvedValue(undefined),
    }
    routeMock.query = {}
    routerMock.replace.mockClear()
    routerMock.push.mockClear()
    showToastMock.mockClear()
  })

  it('should submit ingredient with default quantity', async () => {
    const wrapper = mount(IngredientsView)

    await wrapper.get('[data-testid="new-ingredient-button"]').trigger('click')

    await wrapper.get('input[placeholder="Nome do ingrediente"]').setValue('Leite Ninho')
    await wrapper.get('input[placeholder="Ex: kg, L, un"]').setValue('g')
    await wrapper.get('[data-testid="ingredient-cost-per-unit-input"]').setValue('0.02')
    await wrapper.get('[data-testid="ingredient-default-quantity-input"]').setValue('20')

    await wrapper.get('form').trigger('submit')

    expect(ingredientStoreMock.create).toHaveBeenCalledWith({
      name: 'Leite Ninho',
      unit: 'g',
      costPerUnit: 0.02,
      defaultQuantity: 20,
    })
  })

  it('should omit default quantity when left blank instead of persisting zero', async () => {
    // Gravar 0 fazia o import de pedidos (iFood/Anota.AI) zerar a gramatura e o custo
    // do complemento. Em branco significa "não configurado", não "zero gramas".
    const wrapper = mount(IngredientsView)

    await wrapper.get('[data-testid="new-ingredient-button"]').trigger('click')

    await wrapper.get('input[placeholder="Nome do ingrediente"]').setValue('Leite Ninho')
    await wrapper.get('input[placeholder="Ex: kg, L, un"]').setValue('g')
    await wrapper.get('[data-testid="ingredient-cost-per-unit-input"]').setValue('0.02')

    await wrapper.get('form').trigger('submit')

    expect(ingredientStoreMock.create).toHaveBeenCalledWith({
      name: 'Leite Ninho',
      unit: 'g',
      costPerUnit: 0.02,
      defaultQuantity: undefined,
    })
  })

  it('should start the default quantity field empty rather than showing zero', async () => {
    const wrapper = mount(IngredientsView)
    await wrapper.get('[data-testid="new-ingredient-button"]').trigger('click')
    const input = wrapper.get('[data-testid="ingredient-default-quantity-input"]')
    expect((input.element as HTMLInputElement).value).toBe('')
  })

  it('should accept cost per unit with four decimal places', async () => {
    const wrapper = mount(IngredientsView)

    await wrapper.get('[data-testid="new-ingredient-button"]').trigger('click')

    await wrapper.get('input[placeholder="Nome do ingrediente"]').setValue('Açúcar refinado')
    await wrapper.get('input[placeholder="Ex: kg, L, un"]').setValue('g')
    await wrapper.get('[data-testid="ingredient-cost-per-unit-input"]').setValue('0.0035')

    await wrapper.get('form').trigger('submit')

    expect(ingredientStoreMock.create).toHaveBeenCalledWith(
      expect.objectContaining({ costPerUnit: 0.0035 }),
    )
  })

  it('should configure cost per unit input with step of 0.0001', async () => {
    const wrapper = mount(IngredientsView)
    await wrapper.get('[data-testid="new-ingredient-button"]').trigger('click')
    const input = wrapper.get('[data-testid="ingredient-cost-per-unit-input"]')
    expect(input.attributes('step')).toBe('0.0001')
  })

  it('should compute costPerUnit from purchase price divided by purchase quantity when auto mode is enabled', async () => {
    const wrapper = mount(IngredientsView)

    await wrapper.get('[data-testid="new-ingredient-button"]').trigger('click')

    await wrapper.get('input[placeholder="Nome do ingrediente"]').setValue('Açaí GOAT')
    await wrapper.get('input[placeholder="Ex: kg, L, un"]').setValue('g')

    await wrapper.get('[data-testid="ingredient-cost-auto-checkbox"]').setValue(true)

    expect(wrapper.find('[data-testid="ingredient-cost-per-unit-input"]').exists()).toBe(false)

    await wrapper.get('[data-testid="ingredient-purchase-price-input"]').setValue('195')
    await wrapper.get('[data-testid="ingredient-purchase-quantity-input"]').setValue('9000')

    const computed = wrapper.get('[data-testid="ingredient-cost-per-unit-computed"]')
    expect(computed.text()).toMatch(/0[,.]0217/)

    await wrapper.get('form').trigger('submit')

    expect(ingredientStoreMock.create).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Açaí GOAT',
        unit: 'g',
        costPerUnit: expect.closeTo(0.02167, 4),
      }),
    )
  })

  it('should not submit when auto mode is enabled and purchase quantity is zero', async () => {
    const wrapper = mount(IngredientsView)

    await wrapper.get('[data-testid="new-ingredient-button"]').trigger('click')

    await wrapper.get('input[placeholder="Nome do ingrediente"]').setValue('Teste')
    await wrapper.get('input[placeholder="Ex: kg, L, un"]').setValue('g')

    await wrapper.get('[data-testid="ingredient-cost-auto-checkbox"]').setValue(true)
    await wrapper.get('[data-testid="ingredient-purchase-price-input"]').setValue('100')
    await wrapper.get('[data-testid="ingredient-purchase-quantity-input"]').setValue('0')

    await wrapper.get('form').trigger('submit')

    expect(ingredientStoreMock.create).not.toHaveBeenCalled()
  })

  it('should pre-fill name and open modal when route has ?createName= query param', async () => {
    routeMock.query = { createName: 'Pistache' }

    const wrapper = mount(IngredientsView)

    // Wait for onMounted + async openCreateModal (loadProducts) to settle
    await flushPromises()

    const nameInput = wrapper.get('[data-testid="ingredient-name-input"]')
    expect((nameInput.element as HTMLInputElement).value).toBe('Pistache')
    // Query is cleared after open so reload doesn't re-trigger
    expect(routerMock.replace).toHaveBeenCalledWith({ query: {} })
  })

  it('should not open modal when route has no createName query', async () => {
    routeMock.query = {}

    const wrapper = mount(IngredientsView)
    await wrapper.vm.$nextTick()

    // The form input is inside the modal — should not exist when modal is closed
    expect(wrapper.find('[data-testid="ingredient-name-input"]').exists()).toBe(false)
  })

  it('should open create modal pre-filled with copied fields when duplicating', async () => {
    ingredientStoreMock.items = [
      {
        id: 'ing-1',
        name: 'Queijo Mussarela',
        unit: 'kg',
        costPerUnit: 32.5,
        defaultQuantity: 5,
        status: 'ACTIVE',
      },
    ]

    const wrapper = mount(IngredientsView)
    await flushPromises()

    await wrapper.get('[data-testid="duplicate-ingredient-button"]').trigger('click')
    await flushPromises()

    // Name is suffixed with "(cópia)" to avoid unique-name conflict
    const nameInput = wrapper.get('[data-testid="ingredient-name-input"]')
    expect((nameInput.element as HTMLInputElement).value).toBe('Queijo Mussarela (cópia)')

    // Unit and cost are copied from the source ingredient
    expect((wrapper.get('input[placeholder="Ex: kg, L, un"]').element as HTMLInputElement).value).toBe('kg')
    expect(
      (wrapper.get('[data-testid="ingredient-cost-per-unit-input"]').element as HTMLInputElement).value,
    ).toBe('32.5')

    // Submitting creates a new ingredient (not an update)
    await wrapper.get('form').trigger('submit')

    expect(ingredientStoreMock.create).toHaveBeenCalledWith({
      name: 'Queijo Mussarela (cópia)',
      unit: 'kg',
      costPerUnit: 32.5,
      defaultQuantity: 5,
    })
    expect(ingredientStoreMock.update).not.toHaveBeenCalled()
  })

  it('should copy product-specific quantities from the source ingredient when duplicating', async () => {
    ingredientStoreMock.items = [
      {
        id: 'ing-1',
        name: 'Queijo Mussarela',
        unit: 'kg',
        costPerUnit: 32.5,
        defaultQuantity: 5,
        status: 'ACTIVE',
      },
    ]
    vi.mocked(ingredientService.fetchUsages).mockResolvedValue([
      {
        includeId: 'inc-1',
        productId: 'p-1',
        productName: 'Pizza Calabresa',
        quantity: 120,
        cost: 32.5,
        totalCost: 3900,
      },
      {
        includeId: 'inc-2',
        productId: 'p-2',
        productName: 'Pizza Portuguesa',
        quantity: 90,
        cost: 32.5,
        totalCost: 2925,
      },
    ])

    const wrapper = mount(IngredientsView)
    await flushPromises()

    await wrapper.get('[data-testid="duplicate-ingredient-button"]').trigger('click')
    await flushPromises()

    // Usages of the source ingredient are fetched and shown in the modal
    expect(ingredientService.fetchUsages).toHaveBeenCalledWith('ing-1')
    expect(wrapper.text()).toContain('Pizza Calabresa')
    expect(wrapper.text()).toContain('Pizza Portuguesa')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    // The copy adds NEW includes on each product; it must never update the
    // source ingredient's existing includes
    expect(includeService.add).toHaveBeenCalledWith('p-1', {
      name: 'Queijo Mussarela (cópia)',
      cost: 32.5,
      quantity: 120,
    })
    expect(includeService.add).toHaveBeenCalledWith('p-2', {
      name: 'Queijo Mussarela (cópia)',
      cost: 32.5,
      quantity: 90,
    })
    expect(includeService.update).not.toHaveBeenCalled()
  })

  it('should show a success toast after creating an ingredient', async () => {
    const wrapper = mount(IngredientsView)

    await wrapper.get('[data-testid="new-ingredient-button"]').trigger('click')
    await wrapper.get('input[placeholder="Nome do ingrediente"]').setValue('Morango')
    await wrapper.get('input[placeholder="Ex: kg, L, un"]').setValue('g')
    await wrapper.get('[data-testid="ingredient-cost-per-unit-input"]').setValue('0.05')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(showToastMock).toHaveBeenCalledWith('Ingrediente criado com sucesso!')
  })

  it('should not show a toast when ingredient creation fails', async () => {
    ingredientStoreMock.create = vi.fn().mockRejectedValue(new Error('boom'))
    const wrapper = mount(IngredientsView)

    await wrapper.get('[data-testid="new-ingredient-button"]').trigger('click')
    await wrapper.get('input[placeholder="Nome do ingrediente"]').setValue('Morango')
    await wrapper.get('input[placeholder="Ex: kg, L, un"]').setValue('g')
    await wrapper.get('[data-testid="ingredient-cost-per-unit-input"]').setValue('0.05')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(showToastMock).not.toHaveBeenCalled()
  })

  describe('cost and sort filters', () => {
    const listItems = [
      { id: '1', name: 'Açúcar', unit: 'g', costPerUnit: 0.02, defaultQuantity: 0, status: 'ACTIVE' },
      { id: '2', name: 'Banana', unit: 'kg', costPerUnit: 5, defaultQuantity: 0, status: 'ACTIVE' },
      { id: '3', name: 'Chocolate', unit: 'g', costPerUnit: 25, defaultQuantity: 0, status: 'ACTIVE' },
    ]

    function rowNames(wrapper: ReturnType<typeof mount>): string[] {
      return wrapper.findAll('.ui-row').map((r) => r.findAll('span')[0]?.text() ?? '')
    }

    it('should filter rows by minimum cost', async () => {
      ingredientStoreMock.items = listItems
      const wrapper = mount(IngredientsView)
      await flushPromises()

      expect(wrapper.findAll('.ui-row')).toHaveLength(3)

      await wrapper.get('[data-testid="ingredient-cost-min"]').setValue('5')
      await flushPromises()

      const names = rowNames(wrapper)
      expect(names).toEqual(['Banana', 'Chocolate'])
    })

    it('should filter rows by maximum cost', async () => {
      ingredientStoreMock.items = listItems
      const wrapper = mount(IngredientsView)
      await flushPromises()

      await wrapper.get('[data-testid="ingredient-cost-max"]').setValue('5')
      await flushPromises()

      expect(rowNames(wrapper)).toEqual(['Açúcar', 'Banana'])
    })

    it('should sort rows by cost descending', async () => {
      ingredientStoreMock.items = listItems
      const wrapper = mount(IngredientsView)
      await flushPromises()

      await wrapper.get('[data-testid="ingredient-sort"]').setValue('cost-desc')
      await flushPromises()

      expect(rowNames(wrapper)).toEqual(['Chocolate', 'Banana', 'Açúcar'])
    })

    it('should filter rows by creation date range and exclude legacy null rows', async () => {
      ingredientStoreMock.items = [
        { id: '1', name: 'Antiga', unit: 'g', costPerUnit: 1, defaultQuantity: 0, status: 'ACTIVE', createdAt: null },
        { id: '2', name: 'Janeiro', unit: 'g', costPerUnit: 1, defaultQuantity: 0, status: 'ACTIVE', createdAt: '2026-01-10T09:00:00' },
        { id: '3', name: 'Fevereiro', unit: 'g', costPerUnit: 1, defaultQuantity: 0, status: 'ACTIVE', createdAt: '2026-02-15T09:00:00' },
      ]
      const wrapper = mount(IngredientsView)
      await flushPromises()

      // No date filter: legacy null row is visible
      expect(rowNames(wrapper)).toEqual(['Antiga', 'Janeiro', 'Fevereiro'])

      await wrapper.get('[data-testid="ingredient-created-from"]').setValue('2026-01-01')
      await wrapper.get('[data-testid="ingredient-created-to"]').setValue('2026-01-31')
      await flushPromises()

      // Legacy null excluded, February out of range
      expect(rowNames(wrapper)).toEqual(['Janeiro'])
    })

    it('should clear active filters via the reset button', async () => {
      ingredientStoreMock.items = listItems
      const wrapper = mount(IngredientsView)
      await flushPromises()

      // Reset button is hidden until a filter is active
      expect(wrapper.find('[data-testid="ingredient-clear-filters"]').exists()).toBe(false)

      await wrapper.get('[data-testid="ingredient-cost-min"]').setValue('5')
      await flushPromises()
      expect(wrapper.find('[data-testid="ingredient-clear-filters"]').exists()).toBe(true)

      await wrapper.get('[data-testid="ingredient-clear-filters"]').trigger('click')
      await flushPromises()

      expect(wrapper.findAll('.ui-row')).toHaveLength(3)
      expect(wrapper.find('[data-testid="ingredient-clear-filters"]').exists()).toBe(false)
    })
  })

  describe('manual drag ordering', () => {
    const listItems = [
      { id: 'a', name: 'Alface', unit: 'un', costPerUnit: 1, defaultQuantity: 0, status: 'ACTIVE' },
      { id: 'b', name: 'Batata', unit: 'kg', costPerUnit: 1, defaultQuantity: 0, status: 'ACTIVE' },
      { id: 'c', name: 'Cenoura', unit: 'kg', costPerUnit: 1, defaultQuantity: 0, status: 'ACTIVE' },
    ]

    it('should show enabled drag handles in default order (no filters/sort)', async () => {
      ingredientStoreMock.items = listItems
      const wrapper = mount(IngredientsView)
      await flushPromises()

      expect(wrapper.findAll('[data-testid="ingredient-drag-handle"]')).toHaveLength(3)
      expect(wrapper.find('[data-testid="ingredient-drag-handle-disabled"]').exists()).toBe(false)
    })

    it('should disable drag handles when a sort is active', async () => {
      ingredientStoreMock.items = listItems
      const wrapper = mount(IngredientsView)
      await flushPromises()

      await wrapper.get('[data-testid="ingredient-sort"]').setValue('name-asc')
      await flushPromises()

      expect(wrapper.find('[data-testid="ingredient-drag-handle"]').exists()).toBe(false)
      expect(wrapper.findAll('[data-testid="ingredient-drag-handle-disabled"]')).toHaveLength(3)
    })

    it('should disable drag handles when a value filter is active', async () => {
      ingredientStoreMock.items = listItems
      const wrapper = mount(IngredientsView)
      await flushPromises()

      await wrapper.get('[data-testid="ingredient-cost-min"]').setValue('5')
      await flushPromises()

      expect(wrapper.find('[data-testid="ingredient-drag-handle"]').exists()).toBe(false)
    })

    it('should move a row within the page with the global position (page × size + index)', async () => {
      ingredientStoreMock.items = listItems
      ingredientStoreMock.page = 0
      ingredientStoreMock.size = 20
      const wrapper = mount(IngredientsView)
      await flushPromises()

      const handles = wrapper.findAll('[data-testid="ingredient-drag-handle"]')
      const rows = wrapper.findAll('.ui-row')

      // Grab row 0 ('a'), drop on row 2 → global position 0*20 + 2 = 2
      await handles[0]!.trigger('dragstart')
      await rows[2]!.trigger('drop')

      expect(ingredientStoreMock.moveWithinPage).toHaveBeenCalledWith('a', 2, 2)
    })

    it('should compute the global position from the current page offset', async () => {
      ingredientStoreMock.items = listItems
      ingredientStoreMock.page = 2
      ingredientStoreMock.size = 20
      ingredientStoreMock.totalPages = 5
      const wrapper = mount(IngredientsView)
      await flushPromises()

      const handles = wrapper.findAll('[data-testid="ingredient-drag-handle"]')
      const rows = wrapper.findAll('.ui-row')

      // Grab row 0, drop on row 1 → global position 2*20 + 1 = 41
      await handles[0]!.trigger('dragstart')
      await rows[1]!.trigger('drop')

      expect(ingredientStoreMock.moveWithinPage).toHaveBeenCalledWith('a', 1, 41)
    })

    it('should move to the START of the next page when dropping on "Próximo"', async () => {
      ingredientStoreMock.items = listItems
      ingredientStoreMock.page = 1
      ingredientStoreMock.size = 20
      ingredientStoreMock.totalPages = 3
      const wrapper = mount(IngredientsView)
      await flushPromises()

      const handles = wrapper.findAll('[data-testid="ingredient-drag-handle"]')
      await handles[0]!.trigger('dragstart')
      await wrapper.get('[data-testid="ingredient-pager-next"]').trigger('drop')

      // start of page 2 = (1+1)*20 = 40
      expect(ingredientStoreMock.moveToPage).toHaveBeenCalledWith('a', 40, 2)
    })

    it('should move to the END of the previous page when dropping on "Anterior"', async () => {
      ingredientStoreMock.items = listItems
      ingredientStoreMock.page = 1
      ingredientStoreMock.size = 20
      ingredientStoreMock.totalPages = 3
      const wrapper = mount(IngredientsView)
      await flushPromises()

      const handles = wrapper.findAll('[data-testid="ingredient-drag-handle"]')
      await handles[0]!.trigger('dragstart')
      await wrapper.get('[data-testid="ingredient-pager-prev"]').trigger('drop')

      // end of page 0 = 1*20 - 1 = 19
      expect(ingredientStoreMock.moveToPage).toHaveBeenCalledWith('a', 19, 0)
    })

    it('should not move across pages when already on the first/last page', async () => {
      ingredientStoreMock.items = listItems
      ingredientStoreMock.page = 0
      ingredientStoreMock.size = 20
      ingredientStoreMock.totalPages = 1
      const wrapper = mount(IngredientsView)
      await flushPromises()

      const handles = wrapper.findAll('[data-testid="ingredient-drag-handle"]')
      await handles[0]!.trigger('dragstart')
      await wrapper.get('[data-testid="ingredient-pager-prev"]').trigger('drop')
      await wrapper.get('[data-testid="ingredient-pager-next"]').trigger('drop')

      expect(ingredientStoreMock.moveToPage).not.toHaveBeenCalled()
    })
  })
})
