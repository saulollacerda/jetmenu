import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
let productStoreMock: any
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let categoryStoreMock: any
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let anotaAIStoreMock: any

vi.mock('@/stores/productStore', () => ({
  useProductStore: () => productStoreMock,
}))

vi.mock('@/stores/categoryStore', () => ({
  useCategoryStore: () => categoryStoreMock,
}))

vi.mock('@/stores/anotaAIStore', () => ({
  useAnotaAIStore: () => anotaAIStoreMock,
}))

const showToastMock = vi.fn()
vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ showToast: showToastMock }),
}))

import ProductsView from '@/views/ProductsView.vue'

function stubMatchMedia(matches: boolean) {
  window.matchMedia = vi.fn().mockReturnValue({
    matches,
    media: '',
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }) as unknown as typeof window.matchMedia
}

describe('ProductsView', () => {
  beforeEach(() => {
    stubMatchMedia(false)
    productStoreMock = {
      items: [
        {
          id: 'p1',
          name: 'Açaí 330ml',
          price: 20,
          status: 'ACTIVE',
          categoryId: 'cat1',
          categoryName: 'Bebidas',
          targetMarginPct: 55,
        },
      ],
      includes: [],
      loading: false,
      error: null,
      search: '',
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      fetchAll: vi.fn(),
      fetchPage: vi.fn(),
      create: vi.fn().mockResolvedValue({}),
      update: vi.fn().mockResolvedValue({}),
      remove: vi.fn(),
      fetchIncludes: vi.fn(),
      addInclude: vi.fn().mockResolvedValue({}),
      updateInclude: vi.fn().mockResolvedValue({}),
      removeInclude: vi.fn(),
      clearRecipe: vi.fn(),
    }

    categoryStoreMock = {
      items: [
        { id: 'cat1', name: 'Bebidas' },
        { id: 'cat2', name: 'Lanches' },
      ],
      loading: false,
      error: null,
      fetchAll: vi.fn(),
    }

    anotaAIStoreMock = {
      syncingOrders: false,
      syncingCatalog: false,
      lastResult: null,
      error: null,
      syncOrders: vi.fn(),
      syncCatalog: vi.fn(),
      clearResult: vi.fn(),
    }

    showToastMock.mockClear()
  })

  it('should render category column with categoryName for each product', () => {
    const wrapper = mount(ProductsView)
    expect(wrapper.text()).toContain('Açaí 330ml')
    expect(wrapper.text()).toContain('Bebidas')
  })

  it('should submit new product with the selected categoryId', async () => {
    const wrapper = mount(ProductsView)

    await wrapper.get('[data-testid="new-product-button"]').trigger('click')

    await wrapper.get('[data-testid="product-name-input"]').setValue('X-Burguer')
    await wrapper.get('[data-testid="product-price-input"]').setValue('25.9')
    await wrapper.get('[data-testid="product-category-select"]').setValue('cat2')

    await wrapper.get('[data-testid="product-form"]').trigger('submit')
    await flushPromises()

    expect(productStoreMock.create).toHaveBeenCalledWith({
      name: 'X-Burguer',
      price: 25.9,
      categoryId: 'cat2',
      targetMarginPct: null,
    })
  })

  describe('target margin', () => {
    it('should send the target margin when creating a product', async () => {
      const wrapper = mount(ProductsView)

      await wrapper.get('[data-testid="new-product-button"]').trigger('click')
      await wrapper.get('[data-testid="product-name-input"]').setValue('X-Burguer')
      await wrapper.get('[data-testid="product-price-input"]').setValue('25.9')
      await wrapper.get('[data-testid="product-category-select"]').setValue('cat2')
      await wrapper.get('[data-testid="product-target-margin-input"]').setValue('60')

      await wrapper.get('[data-testid="product-form"]').trigger('submit')
      await flushPromises()

      expect(productStoreMock.create).toHaveBeenCalledWith({
        name: 'X-Burguer',
        price: 25.9,
        categoryId: 'cat2',
        targetMarginPct: 60,
      })
    })

    it('should prefill the target margin of the product being edited', async () => {
      const wrapper = mount(ProductsView)

      await wrapper.get('[data-testid="product-p1-edit-button"]').trigger('click')

      const input = wrapper.get('[data-testid="product-target-margin-input"]')
        .element as HTMLInputElement
      expect(input.value).toBe('55')
    })

    it('should preserve the stored target margin when editing an unrelated field', async () => {
      const wrapper = mount(ProductsView)

      await wrapper.get('[data-testid="product-p1-edit-button"]').trigger('click')
      await wrapper.get('[data-testid="product-name-input"]').setValue('Açaí 500ml')

      await wrapper.get('[data-testid="product-form"]').trigger('submit')
      await flushPromises()

      // update() overwrites targetMarginPct unconditionally on the backend, so the
      // payload must always carry it — omitting it would silently clear the target.
      expect(productStoreMock.update).toHaveBeenCalledWith('p1', {
        name: 'Açaí 500ml',
        price: 20,
        categoryId: 'cat1',
        targetMarginPct: 55,
      })
    })

    it('should send null when the merchant clears the target margin', async () => {
      const wrapper = mount(ProductsView)

      await wrapper.get('[data-testid="product-p1-edit-button"]').trigger('click')
      await wrapper.get('[data-testid="product-target-margin-input"]').setValue('')

      await wrapper.get('[data-testid="product-form"]').trigger('submit')
      await flushPromises()

      expect(productStoreMock.update).toHaveBeenCalledWith('p1', {
        name: 'Açaí 330ml',
        price: 20,
        categoryId: 'cat1',
        targetMarginPct: null,
      })
    })

    it('should keep a zero target margin instead of treating it as empty', async () => {
      const wrapper = mount(ProductsView)

      await wrapper.get('[data-testid="product-p1-edit-button"]').trigger('click')
      await wrapper.get('[data-testid="product-target-margin-input"]').setValue('0')

      await wrapper.get('[data-testid="product-form"]').trigger('submit')
      await flushPromises()

      expect(productStoreMock.update.mock.calls[0][1].targetMarginPct).toBe(0)
    })

    it('should not submit when the target margin is above 100', async () => {
      const wrapper = mount(ProductsView)

      await wrapper.get('[data-testid="product-p1-edit-button"]').trigger('click')
      await wrapper.get('[data-testid="product-target-margin-input"]').setValue('120')

      await wrapper.get('[data-testid="product-form"]').trigger('submit')
      await flushPromises()

      expect(productStoreMock.update).not.toHaveBeenCalled()
      expect(wrapper.get('[data-testid="product-target-margin-error"]').text()).toContain(
        'entre 0 e 100',
      )
    })

    it('should not submit when the target margin is negative', async () => {
      const wrapper = mount(ProductsView)

      await wrapper.get('[data-testid="product-p1-edit-button"]').trigger('click')
      await wrapper.get('[data-testid="product-target-margin-input"]').setValue('-5')

      await wrapper.get('[data-testid="product-form"]').trigger('submit')
      await flushPromises()

      expect(productStoreMock.update).not.toHaveBeenCalled()
      expect(wrapper.find('[data-testid="product-target-margin-error"]').exists()).toBe(true)
    })

    it('should start the create form with an empty target margin', async () => {
      const wrapper = mount(ProductsView)

      await wrapper.get('[data-testid="product-p1-edit-button"]').trigger('click')
      await wrapper.get('[data-testid="product-form"]').trigger('submit')
      await flushPromises()

      await wrapper.get('[data-testid="new-product-button"]').trigger('click')

      const input = wrapper.get('[data-testid="product-target-margin-input"]')
        .element as HTMLInputElement
      expect(input.value).toBe('')
    })
  })

  it('should show a success toast after creating a product', async () => {
    const wrapper = mount(ProductsView)

    await wrapper.get('[data-testid="new-product-button"]').trigger('click')
    await wrapper.get('[data-testid="product-name-input"]').setValue('X-Burguer')
    await wrapper.get('[data-testid="product-price-input"]').setValue('25.9')
    await wrapper.get('[data-testid="product-category-select"]').setValue('cat2')

    await wrapper.get('[data-testid="product-form"]').trigger('submit')
    await flushPromises()

    expect(showToastMock).toHaveBeenCalledWith('Produto criado com sucesso!')
  })

  it('should not show a toast when editing an existing product', async () => {
    const wrapper = mount(ProductsView)

    await wrapper.get('[data-testid="product-p1-edit-button"]').trigger('click')
    await wrapper.get('[data-testid="product-form"]').trigger('submit')
    await flushPromises()

    expect(productStoreMock.update).toHaveBeenCalled()
    expect(showToastMock).not.toHaveBeenCalled()
  })

  it('should preselect the current category when editing a product', async () => {
    const wrapper = mount(ProductsView)

    await wrapper.get('[data-testid="product-p1-edit-button"]').trigger('click')

    const select = wrapper.get('[data-testid="product-category-select"]').element as HTMLSelectElement
    expect(select.value).toBe('cat1')
  })

  it('should always add items as PACKAGING kind (no kind selector)', async () => {
    const wrapper = mount(ProductsView)

    await wrapper.get('[data-testid="product-p1-recipe-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="recipe-kind-select"]').exists()).toBe(false)

    await wrapper.get('[data-testid="recipe-name-input"]').setValue('Copo')
    await wrapper.get('[data-testid="recipe-cost-input"]').setValue('0.5')
    await wrapper.get('[data-testid="recipe-quantity-input"]').setValue('1')

    await wrapper.get('[data-testid="recipe-add-form"]').trigger('submit')
    await flushPromises()

    expect(productStoreMock.addInclude).toHaveBeenCalledWith('p1', {
      name: 'Copo',
      cost: 0.5,
      quantity: 1,
      kind: 'PACKAGING',
    })
  })

  it('should render grouped sections when there are PACKAGING items', async () => {
    productStoreMock.includes = [
      { id: 'i1', productId: 'p1', name: 'Açaí Base', cost: 2.5, quantity: 1, totalCost: 2.5, kind: 'INGREDIENT' },
      { id: 'i2', productId: 'p1', name: 'Granola', cost: 0.4, quantity: 1, totalCost: 0.4, kind: 'INGREDIENT' },
      { id: 'i3', productId: 'p1', name: 'Copo', cost: 0.35, quantity: 1, totalCost: 0.35, kind: 'PACKAGING' },
    ]

    const wrapper = mount(ProductsView)
    await wrapper.get('[data-testid="product-p1-recipe-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Ingredientes')
    expect(wrapper.text()).toContain('Embalagens')
    expect(wrapper.text()).toContain('Açaí Base')
    expect(wrapper.text()).toContain('Granola')
    expect(wrapper.text()).toContain('Copo')
  })

  it('should list insumos (PACKAGING) before ingredients (INGREDIENT) in the ficha', async () => {
    productStoreMock.includes = [
      { id: 'i1', productId: 'p1', name: 'Açaí Base', cost: 2.5, quantity: 1, totalCost: 2.5, kind: 'INGREDIENT' },
      { id: 'i2', productId: 'p1', name: 'Granola', cost: 0.4, quantity: 1, totalCost: 0.4, kind: 'INGREDIENT' },
      { id: 'i3', productId: 'p1', name: 'Copo', cost: 0.35, quantity: 1, totalCost: 0.35, kind: 'PACKAGING' },
      { id: 'i4', productId: 'p1', name: 'Tampa', cost: 0.2, quantity: 1, totalCost: 0.2, kind: 'PACKAGING' },
    ]

    const wrapper = mount(ProductsView)
    await wrapper.get('[data-testid="product-p1-recipe-button"]').trigger('click')
    await flushPromises()

    const text = wrapper.text()
    // The Embalagens & Insumos section must render before the Ingredientes section.
    expect(text.indexOf('Embalagens')).toBeLessThan(text.indexOf('Ingredientes'))
    // And individual insumo rows must come before ingredient rows in DOM order.
    expect(text.indexOf('Copo')).toBeLessThan(text.indexOf('Açaí Base'))
    // Stable order is preserved within each group.
    expect(text.indexOf('Copo')).toBeLessThan(text.indexOf('Tampa'))
    expect(text.indexOf('Açaí Base')).toBeLessThan(text.indexOf('Granola'))
  })

  it('should render grouped sections even when there are only PACKAGING items', async () => {
    productStoreMock.includes = [
      { id: 'i3', productId: 'p1', name: 'Copo', cost: 0.35, quantity: 1, totalCost: 0.35, kind: 'PACKAGING' },
    ]

    const wrapper = mount(ProductsView)
    await wrapper.get('[data-testid="product-p1-recipe-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Embalagens')
    expect(wrapper.text()).toContain('Copo')
  })

  it('should render flat list without section headers when all includes have no PACKAGING', async () => {
    productStoreMock.includes = [
      { id: 'i1', productId: 'p1', name: 'Açaí Base', cost: 2.5, quantity: 1, totalCost: 2.5, kind: 'INGREDIENT' },
    ]

    const wrapper = mount(ProductsView)
    await wrapper.get('[data-testid="product-p1-recipe-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Embalagens')
    expect(wrapper.text()).not.toContain('Ingredientes')
  })

  it('should not render the estimated margin card in the recipe panel', async () => {
    productStoreMock.includes = [
      { id: 'i1', productId: 'p1', name: 'Açaí Base', cost: 2.5, quantity: 1, totalCost: 2.5, kind: 'INGREDIENT' },
    ]

    const wrapper = mount(ProductsView)
    await wrapper.get('[data-testid="product-p1-recipe-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Ficha Técnica — Açaí 330ml')
    expect(wrapper.text()).toContain('Custo total')
    expect(wrapper.text()).not.toContain('Margem estimada')
  })

  it('should show edit inputs when edit button is clicked on an include', async () => {
    productStoreMock.includes = [
      { id: 'i1', productId: 'p1', name: 'Copo', cost: 0.5, quantity: 1, totalCost: 0.5, kind: 'PACKAGING' },
    ]

    const wrapper = mount(ProductsView)
    await wrapper.get('[data-testid="product-p1-recipe-button"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="include-i1-edit-button"]').trigger('click')

    expect(wrapper.find('[data-testid="include-i1-name-input"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="include-i1-cost-input"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="include-i1-quantity-input"]').exists()).toBe(true)
  })

  it('should call updateInclude with new values when edit is confirmed', async () => {
    productStoreMock.includes = [
      { id: 'i1', productId: 'p1', name: 'Copo', cost: 0.5, quantity: 1, totalCost: 0.5, kind: 'PACKAGING' },
    ]

    const wrapper = mount(ProductsView)
    await wrapper.get('[data-testid="product-p1-recipe-button"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="include-i1-edit-button"]').trigger('click')

    await wrapper.get('[data-testid="include-i1-name-input"]').setValue('Copo 500ml')
    await wrapper.get('[data-testid="include-i1-cost-input"]').setValue('0.8')
    await wrapper.get('[data-testid="include-i1-quantity-input"]').setValue('2')

    await wrapper.get('[data-testid="include-i1-confirm-button"]').trigger('click')
    await flushPromises()

    expect(productStoreMock.updateInclude).toHaveBeenCalledWith('p1', 'i1', {
      name: 'Copo 500ml',
      cost: 0.8,
      quantity: 2,
      kind: 'PACKAGING',
    })
  })

  it('should cancel edit and restore view mode when cancel is clicked', async () => {
    productStoreMock.includes = [
      { id: 'i1', productId: 'p1', name: 'Copo', cost: 0.5, quantity: 1, totalCost: 0.5, kind: 'PACKAGING' },
    ]

    const wrapper = mount(ProductsView)
    await wrapper.get('[data-testid="product-p1-recipe-button"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="include-i1-edit-button"]').trigger('click')
    await wrapper.get('[data-testid="include-i1-cancel-button"]').trigger('click')

    expect(wrapper.find('[data-testid="include-i1-name-input"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Copo')
  })

  describe('Ficha column (small screens only)', () => {
    it('should show the Abrir button and open the recipe modal when clicked on small screens', async () => {
      stubMatchMedia(true)
      const wrapper = mount(ProductsView)

      expect(wrapper.get('[data-testid="ficha-column-header"]').text()).toBe('Ficha')

      await wrapper.get('[data-testid="product-p1-open-recipe-pill"]').trigger('click')
      await flushPromises()

      expect(productStoreMock.fetchIncludes).toHaveBeenCalledWith('p1')
      expect(wrapper.text()).toContain('Ficha Técnica — Açaí 330ml')
    })

    it('should hide the Ficha column entirely on large screens', () => {
      stubMatchMedia(false)
      const wrapper = mount(ProductsView)

      expect(wrapper.find('[data-testid="ficha-column-header"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="product-p1-open-recipe-pill"]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('Abrir')
    })
  })
})
