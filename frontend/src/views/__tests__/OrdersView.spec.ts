import { describe, it, expect, vi, beforeEach } from 'vitest'
import { reactive } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

let orderStoreMock: any
let customerStoreMock: any
let productStoreMock: any
let ingredientStoreMock: any
let feeStoreMock: any
let notificationStoreMock: any

vi.mock('@/stores/orderStore', () => ({
  useOrderStore: () => orderStoreMock,
}))

vi.mock('@/stores/customerStore', () => ({
  useCustomerStore: () => customerStoreMock,
}))

vi.mock('@/stores/productStore', () => ({
  useProductStore: () => productStoreMock,
}))

vi.mock('@/stores/ingredientStore', () => ({
  useIngredientStore: () => ingredientStoreMock,
}))

vi.mock('@/stores/feeStore', () => ({
  useFeeStore: () => feeStoreMock,
}))

vi.mock('@/stores/notificationStore', () => ({
  useNotificationStore: () => notificationStoreMock,
}))

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({ currentUser: null }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))

let includesByProductMock: Record<string, unknown[]>

vi.mock('@/services/includeService', () => ({
  includeService: {
    findByProductId: vi.fn(async (productId: string) => includesByProductMock[productId] ?? []),
  },
}))

const orderFichaFindMock = vi.fn()
const orderFichaReplaceMock = vi.fn()

vi.mock('@/services/orderFichaService', () => ({
  orderFichaService: {
    find: (...args: unknown[]) => orderFichaFindMock(...args),
    replace: (...args: unknown[]) => orderFichaReplaceMock(...args),
  },
}))

const ifoodOrderActionMock = {
  confirm: vi.fn(),
  readyToPickup: vi.fn(),
  dispatch: vi.fn(),
  getConfirmationWindow: vi.fn(),
}

vi.mock('@/services/ifoodOrderActionService', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/services/ifoodOrderActionService')>()
  return {
    ...original,
    ifoodOrderActionService: {
      confirm: (...args: [string]) => ifoodOrderActionMock.confirm(...args),
      readyToPickup: (...args: [string]) => ifoodOrderActionMock.readyToPickup(...args),
      dispatch: (...args: [string]) => ifoodOrderActionMock.dispatch(...args),
      getConfirmationWindow: (...args: [string]) =>
        ifoodOrderActionMock.getConfirmationWindow(...args),
    },
  }
})

import OrdersView from '@/views/OrdersView.vue'
import { UI } from '@/design'

const showToastMock = vi.fn()
vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ showToast: showToastMock }),
}))

describe('OrdersView', () => {
  beforeEach(() => {
    showToastMock.mockClear()
    ifoodOrderActionMock.confirm.mockReset()
    ifoodOrderActionMock.readyToPickup.mockReset()
    ifoodOrderActionMock.dispatch.mockReset()
    ifoodOrderActionMock.getConfirmationWindow.mockReset()
    ifoodOrderActionMock.getConfirmationWindow.mockResolvedValue({
      orderId: 'o1',
      createdAt: '2026-05-14T10:00:00',
      deadline: '2026-05-14T10:08:00',
      remainingSeconds: 300,
      expired: false,
    })
    orderFichaFindMock.mockReset()
    orderFichaReplaceMock.mockReset()
    orderFichaFindMock.mockResolvedValue({ lines: [], totalCost: 0 })
    orderFichaReplaceMock.mockImplementation(async (req: { lines: { ingredientId: string; quantity: number }[] }) => ({
      lines: req.lines.map((l, idx) => ({
        id: `l${idx}`,
        ingredientId: l.ingredientId,
        ingredientName: l.ingredientId === 'i1' ? 'Granola' : 'Leite Ninho',
        ingredientUnit: 'g',
        quantity: l.quantity,
        costPerUnit: 0.05,
        totalCost: l.quantity * 0.05,
      })),
      totalCost: req.lines.reduce((acc, l) => acc + l.quantity * 0.05, 0),
    }))
    orderStoreMock = {
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
      findById: vi.fn(async (id: string) =>
        orderStoreMock.items.find((o: { id: string }) => o.id === id),
      ),
      create: vi.fn().mockResolvedValue({}),
      update: vi.fn(),
      remove: vi.fn(),
    }

    customerStoreMock = {
      items: [{ id: 'c1', name: 'João' }],
      loading: false,
      error: null,
      fetchAll: vi.fn(),
    }

    productStoreMock = {
      items: [{ id: 'p1', name: 'Açaí 330ml', price: 20 }],
      loading: false,
      error: null,
      fetchAll: vi.fn(),
    }

    ingredientStoreMock = {
      // reactive so store mutations (e.g. inline create) propagate to computeds
      items: reactive([
        { id: 'i1', name: 'Granola', unit: 'g', costPerUnit: 0.05, status: 'ACTIVE' },
        {
          id: 'i2',
          name: 'Leite Ninho',
          unit: 'g',
          costPerUnit: 0.0035,
          defaultQuantity: 20,
          status: 'ACTIVE',
        },
      ]),
      loading: false,
      error: null,
      fetchAll: vi.fn(),
      create: vi.fn(),
    }

    feeStoreMock = {
      items: [],
      loading: false,
      error: null,
      fetchAll: vi.fn(),
    }

    notificationStoreMock = {
      items: [],
      unreadCount: 0,
      loading: false,
      error: null,
      fetchAll: vi.fn(),
      refreshCount: vi.fn(),
      markRead: vi.fn(),
      dismiss: vi.fn(),
    }

    includesByProductMock = {
      p1: [
        { id: 'inc1', productId: 'p1', name: 'Copo', cost: 0.1, quantity: 1, totalCost: 0.1, kind: 'PACKAGING' },
        { id: 'inc2', productId: 'p1', name: 'Açaí base', cost: 0.02, quantity: 150, totalCost: 3, kind: null },
        { id: 'inc3', productId: 'p1', name: 'Granola', cost: 0.05, quantity: 40, totalCost: 2, kind: 'INGREDIENT' },
      ],
    }
  })

  it('should not render the Anota.AI import button (moved to Settings > Integrações)', () => {
    const wrapper = mount(OrdersView)
    expect(wrapper.text()).not.toContain('Importar do Anota.AI')
  })

  it('should submit order with extra ingredients', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')

    await wrapper.get('[data-testid="order-customer-input"]').setValue('jo')
    await wrapper.get('[data-testid="combo-option-c1"]').trigger('click')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')
    await wrapper.get('[data-testid="order-item-0-quantity-input"]').setValue('2')

    await wrapper.get('[data-testid="order-item-0-add-extra-button"]').trigger('click')
    await wrapper
      .get('[data-testid="order-item-0-extra-0-ingredient-select"]')
      .setValue('i1')
    await wrapper.get('[data-testid="order-item-0-extra-0-quantity-input"]').setValue('1.5')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(orderStoreMock.create).toHaveBeenCalledWith({
      customerId: 'c1',
      origin: 'JETMENU',
      items: [
        {
          productId: 'p1',
          quantity: 2,
          extraIngredients: [{ ingredientId: 'i1', quantity: 1.5 }],
          excludedIncludeIds: [],
        },
      ],
    })
    const payload = orderStoreMock.create.mock.calls[0][0]
    expect(payload.customerName).toBeUndefined()
  })

  it('should show a success toast after creating an order', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-customer-input"]').setValue('jo')
    await wrapper.get('[data-testid="combo-option-c1"]').trigger('click')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')
    await wrapper.get('[data-testid="order-item-0-quantity-input"]').setValue('1')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(orderStoreMock.create).toHaveBeenCalledTimes(1)
    expect(showToastMock).toHaveBeenCalledWith('Pedido criado com sucesso!')
  })

  it('should not show a toast when order creation fails', async () => {
    orderStoreMock.create = vi.fn().mockRejectedValue(new Error('boom'))
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-customer-input"]').setValue('jo')
    await wrapper.get('[data-testid="combo-option-c1"]').trigger('click')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(showToastMock).not.toHaveBeenCalled()
  })

  it('should list the product ficha técnica as checked insumos when a product is selected', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')
    await flushPromises()

    const copo = wrapper.get('[data-testid="order-item-0-insumo-inc1-checkbox"]')
    const acai = wrapper.get('[data-testid="order-item-0-insumo-inc2-checkbox"]')
    expect((copo.element as HTMLInputElement).checked).toBe(true)
    expect((acai.element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.html()).toContain('Copo')
    expect(wrapper.html()).toContain('Açaí base')
  })

  it('should not pull INGREDIENT-kind includes as insumos of the order item', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')
    await flushPromises()

    expect(wrapper.find('[data-testid="order-item-0-insumo-inc3-checkbox"]').exists()).toBe(false)
  })

  it('should send excludedIncludeIds when an insumo is unchecked', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-customer-input"]').setValue('jo')
    await wrapper.get('[data-testid="combo-option-c1"]').trigger('click')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')
    await flushPromises()

    await wrapper.get('[data-testid="order-item-0-insumo-inc1-checkbox"]').setValue(false)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(orderStoreMock.create).toHaveBeenCalledTimes(1)
    const payload = orderStoreMock.create.mock.calls[0][0]
    expect(payload.items[0].excludedIncludeIds).toEqual(['inc1'])
  })

  it('should re-include the insumo cost when the checkbox is checked back', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-customer-input"]').setValue('jo')
    await wrapper.get('[data-testid="combo-option-c1"]').trigger('click')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')
    await flushPromises()

    await wrapper.get('[data-testid="order-item-0-insumo-inc1-checkbox"]').setValue(false)
    await wrapper.get('[data-testid="order-item-0-insumo-inc1-checkbox"]').setValue(true)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const payload = orderStoreMock.create.mock.calls[0][0]
    expect(payload.items[0].excludedIncludeIds).toEqual([])
  })

  it('should show a validation error and keep the modal open when no customer is given', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="order-customer-error"]').text()).toBe(
      'Cliente é obrigatório',
    )
    expect(orderStoreMock.create).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="order-form-title"]').exists()).toBe(true)
  })

  it('should treat a whitespace-only customer name as missing', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-customer-input"]').setValue('   ')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="order-customer-error"]').text()).toBe(
      'Cliente é obrigatório',
    )
    expect(orderStoreMock.create).not.toHaveBeenCalled()
  })

  it('should clear the validation error when the user types a customer name', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.find('[data-testid="order-customer-error"]').exists()).toBe(true)

    await wrapper.get('[data-testid="order-customer-input"]').setValue('Ma')

    expect(wrapper.find('[data-testid="order-customer-error"]').exists()).toBe(false)
  })

  it('should quick-create: submit customerName without customerId when typing a new name', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-customer-input"]').setValue('  Maria ')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(orderStoreMock.create).toHaveBeenCalledTimes(1)
    const payload = orderStoreMock.create.mock.calls[0][0]
    expect(payload.customerName).toBe('Maria')
    expect(payload.customerId).toBeUndefined()
  })

  it('should show the backend error inside the modal and keep it open on failure', async () => {
    orderStoreMock.create = vi.fn().mockImplementation(() => {
      orderStoreMock.error = 'Cliente é obrigatório'
      return Promise.reject(new Error('bad request'))
    })

    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-customer-input"]').setValue('Maria')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="order-modal-error"]').text()).toContain(
      'Cliente é obrigatório',
    )
    expect(wrapper.find('[data-testid="order-form-title"]').exists()).toBe(true)
  })

  it('should auto-fill extra ingredient quantity with ingredient defaultQuantity', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')
    await wrapper.get('[data-testid="order-item-0-add-extra-button"]').trigger('click')

    const quantityInput = wrapper.get(
      '[data-testid="order-item-0-extra-0-quantity-input"]',
    )
    expect((quantityInput.element as HTMLInputElement).value).toBe('1')

    await wrapper
      .get('[data-testid="order-item-0-extra-0-ingredient-select"]')
      .setValue('i2')

    expect((quantityInput.element as HTMLInputElement).value).toBe('20')
  })

  it('should render order details with extra ingredients, costs and profit breakdown', async () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 60,
        estimatedProfit: 26,
        // sem taxa de entrega: 26 / 60 = 43,33% (como o backend envia)
        marginPct: 43.33,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Hambúrguer',
            quantity: 2,
            unitPrice: 30,
            unitCost: 17,
            totalCost: 34,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Bacon',
                ingredientUnit: 'g',
                quantity: 50,
                costPerUnit: 0.1,
                totalCost: 10,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')
    const html = detail.html()
    expect(html).toContain('Bacon')
    expect(html).toContain('50')
    expect(html).toMatch(/Custo/i)
    expect(detail.get('[data-testid="order-detail-total-cost"]').text()).toMatch(/34/)
    expect(detail.get('[data-testid="order-detail-estimated-profit"]').text()).toMatch(/26/)
    expect(detail.get('[data-testid="order-detail-margin"]').text()).toMatch(/43[,.]/)
  })

  it('should render the price paid for a subitem alongside its production cost', async () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 21.99,
        estimatedProfit: 10,
        marginPct: 45.0,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Açaí 500ml',
            quantity: 1,
            unitPrice: 21.99,
            unitCost: 10,
            totalCost: 10,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Pistache',
                ingredientUnit: 'g',
                quantity: 30,
                costPerUnit: 0.05,
                totalCost: 1.5,
                salePricePerUnit: 1.5,
                salePriceTotal: 1.5,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)
    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')
    const paid = detail.get('[data-testid="extra-oei1-paid"]')

    // Valor pago pelo cliente pelo adicional.
    expect(paid.text()).toMatch(/1,50/)
    // O custo de produção continua visível e separado.
    expect(detail.get('[data-testid="extra-oei1-cost"]').text()).toMatch(/1,50/)
  })

  it('should show the item value as base price plus the value paid for its extras', async () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 65,
        estimatedProfit: 20,
        marginPct: 30.0,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Hambúrguer',
            quantity: 2,
            unitPrice: 30,
            unitCost: 17,
            totalCost: 34,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Bacon',
                ingredientUnit: 'g',
                quantity: 50,
                costPerUnit: 0.1,
                totalCost: 5,
                salePricePerUnit: 5,
                salePriceTotal: 5,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)
    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')
    // Base 30 × 2 = 60, somado ao adicional pago (5) = 65.
    expect(detail.get('[data-testid="item-oi1-value"]').text()).toContain('65,00')
  })

  it('should show only the base price when the extras are zero-priced complements', async () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 15,
        estimatedProfit: 5,
        marginPct: 33.33,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Açaí 330ml',
            quantity: 1,
            unitPrice: 15,
            unitCost: 10,
            totalCost: 11,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Leite Ninho',
                ingredientUnit: 'g',
                quantity: 50,
                costPerUnit: 0.02,
                totalCost: 1,
                salePricePerUnit: 0,
                salePriceTotal: 0,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)
    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')
    expect(detail.get('[data-testid="item-oi1-value"]').text()).toContain('15,00')
  })

  it('should show only the base price when the extras have no known price (legacy)', async () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 30,
        estimatedProfit: 10,
        marginPct: 33.33,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Hambúrguer',
            quantity: 1,
            unitPrice: 30,
            unitCost: 20,
            totalCost: 20,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Bacon',
                ingredientUnit: 'g',
                quantity: 50,
                costPerUnit: 0.2,
                totalCost: 10,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)
    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')
    expect(detail.get('[data-testid="item-oi1-value"]').text()).toContain('30,00')
  })

  it('should mark a zero-priced subitem as a base complement, not a paid add-on', async () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 15,
        estimatedProfit: 5,
        marginPct: 33.33,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Açaí 330ml',
            quantity: 1,
            unitPrice: 15,
            unitCost: 10,
            totalCost: 10,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Leite Ninho',
                ingredientUnit: 'g',
                quantity: 50,
                costPerUnit: 0.02,
                totalCost: 1,
                salePricePerUnit: 0,
                salePriceTotal: 0,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)
    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')

    // Complemento base: incluso, sem valor agregado.
    expect(detail.get('[data-testid="extra-oei1-paid"]').text()).toMatch(/Incluso/i)
  })

  it('should omit the paid value for extras without a known price', async () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 60,
        estimatedProfit: 26,
        marginPct: 43.33,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Hambúrguer',
            quantity: 2,
            unitPrice: 30,
            unitCost: 17,
            totalCost: 34,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Bacon',
                ingredientUnit: 'g',
                quantity: 50,
                costPerUnit: 0.1,
                totalCost: 10,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)
    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')

    // Sem preço conhecido: nada de "R$ 0,00" enganoso, e o custo segue visível.
    expect(detail.get('[data-testid="extra-oei1-paid"]').text()).toBe('—')
    expect(detail.get('[data-testid="extra-oei1-cost"]').text()).toMatch(/10,00/)
  })

  it('should keep the "(extra)" suffix for an extra the customer paid for', async () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 23.49,
        estimatedProfit: 10,
        marginPct: 45.0,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Açaí 500ml',
            quantity: 1,
            unitPrice: 21.99,
            unitCost: 10,
            totalCost: 10,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Pistache',
                ingredientUnit: 'g',
                quantity: 30,
                costPerUnit: 0.05,
                totalCost: 1.5,
                salePricePerUnit: 1.5,
                salePriceTotal: 1.5,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)
    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')

    // Adicional pago mantém o sufixo "(extra)".
    expect(detail.get('[data-testid="extra-oei1-name"]').text()).toMatch(/Pistache \(extra\)/)
  })

  it('should drop the "(extra)" suffix for a zero-priced base complement', async () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 15,
        estimatedProfit: 5,
        marginPct: 33.33,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Açaí 330ml',
            quantity: 1,
            unitPrice: 15,
            unitCost: 10,
            totalCost: 10,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Leite Ninho',
                ingredientUnit: 'g',
                quantity: 50,
                costPerUnit: 0.02,
                totalCost: 1,
                salePricePerUnit: 0,
                salePriceTotal: 0,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)
    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')
    const name = detail.get('[data-testid="extra-oei1-name"]').text()

    // Complemento base (preço 0): só o nome, sem "(extra)".
    expect(name).toMatch(/Leite Ninho/)
    expect(name).not.toMatch(/\(extra\)/)
  })

  it('should drop the "(extra)" suffix when the sale price is unknown', async () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 60,
        estimatedProfit: 26,
        marginPct: 43.33,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Hambúrguer',
            quantity: 2,
            unitPrice: 30,
            unitCost: 17,
            totalCost: 34,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Bacon',
                ingredientUnit: 'g',
                quantity: 50,
                costPerUnit: 0.1,
                totalCost: 10,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)
    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')
    const name = detail.get('[data-testid="extra-oei1-name"]').text()

    // Preço desconhecido: sem sufixo enganoso de "(extra)".
    expect(name).toMatch(/Bacon/)
    expect(name).not.toMatch(/\(extra\)/)
  })

  it('should render the margin from the backend marginPct, excluding the delivery fee', async () => {
    // Backend: subtotal = 60 - 10 = 50 | profit = 29 | marginPct = 29/50 = 58.00%
    // Recalcular no front sobre o totalValue daria 29/60 = 48,3% (errado).
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 60,
        deliveryFee: 10,
        estimatedProfit: 29,
        marginPct: 58,
        items: [],
      },
    ]

    const wrapper = mount(OrdersView)

    expect(wrapper.get('[data-testid="order-o1-margin"]').text()).toBe('58,0%')

    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')
    expect(detail.get('[data-testid="order-detail-margin"]').text()).toBe('58,0%')
  })

  it('should render a dash instead of 0% when marginPct is not available', async () => {
    // totalValue == deliveryFee -> backend não consegue apurar a margem (marginPct null)
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 15,
        deliveryFee: 15,
        estimatedProfit: -5,
        marginPct: null,
        items: [],
      },
    ]

    const wrapper = mount(OrdersView)

    expect(wrapper.get('[data-testid="order-o1-margin"]').text()).toBe('—')

    await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')

    const detail = wrapper.get('[data-testid="order-detail-modal"]')
    expect(detail.get('[data-testid="order-detail-margin"]').text()).toBe('—')
  })

  it('should render TEST order status with pt-BR label "Teste"', () => {
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-07-01T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'TEST',
        totalValue: 30,
        estimatedProfit: 10,
        items: [],
      },
    ]

    const wrapper = mount(OrdersView)

    expect(wrapper.html()).toContain('Teste')
    expect(wrapper.html()).not.toContain('>TEST<')
  })

  it('should populate form with existing order and call update on submit when editing', async () => {
    orderStoreMock.update = vi.fn().mockResolvedValue({})
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 60,
        estimatedProfit: 26,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Açaí 330ml',
            quantity: 2,
            unitPrice: 20,
            unitCost: 10,
            totalCost: 20,
            extraIngredients: [
              {
                id: 'oei1',
                ingredientId: 'i1',
                ingredientName: 'Granola',
                ingredientUnit: 'g',
                quantity: 30,
                costPerUnit: 0.05,
                totalCost: 3,
              },
            ],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="order-o1-edit-button"]').trigger('click')

    expect(wrapper.get('[data-testid="order-form-title"]').text()).toBe('Editar Pedido')
    expect(
      (wrapper.get('[data-testid="order-customer-input"]').element as HTMLInputElement).value,
    ).toBe('João')
    expect(
      (wrapper.get('[data-testid="order-item-0-product-select"]').element as HTMLSelectElement).value,
    ).toBe('p1')
    expect(
      (wrapper.get('[data-testid="order-item-0-quantity-input"]').element as HTMLInputElement).value,
    ).toBe('2')
    expect(
      (wrapper.get('[data-testid="order-item-0-extra-0-ingredient-select"]').element as HTMLSelectElement)
        .value,
    ).toBe('i1')
    expect(
      (wrapper.get('[data-testid="order-item-0-extra-0-quantity-input"]').element as HTMLInputElement)
        .value,
    ).toBe('30')

    await wrapper.get('[data-testid="order-item-0-quantity-input"]').setValue('3')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(orderStoreMock.update).toHaveBeenCalledWith(
      'o1',
      expect.objectContaining({
        customerId: 'c1',
        status: 'PAID',
        items: [
          expect.objectContaining({
            productId: 'p1',
            quantity: 3,
            extraIngredients: [{ ingredientId: 'i1', quantity: 30 }],
          }),
        ],
      }),
    )
    expect(orderStoreMock.create).not.toHaveBeenCalled()
  })

  it('should restore unchecked insumos when editing an order with exclusions', async () => {
    orderStoreMock.update = vi.fn().mockResolvedValue({})
    orderStoreMock.items = [
      {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 40,
        estimatedProfit: 20,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Açaí 330ml',
            quantity: 1,
            unitPrice: 20,
            unitCost: 0.1,
            totalCost: 0.1,
            extraIngredients: [],
            excludedIncludeIds: ['inc2'],
          },
        ],
      },
    ]

    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="order-o1-edit-button"]').trigger('click')
    await flushPromises()

    const copo = wrapper.get('[data-testid="order-item-0-insumo-inc1-checkbox"]')
    const acai = wrapper.get('[data-testid="order-item-0-insumo-inc2-checkbox"]')
    expect((copo.element as HTMLInputElement).checked).toBe(true)
    expect((acai.element as HTMLInputElement).checked).toBe(false)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const payload = orderStoreMock.update.mock.calls[0][1]
    expect(payload.items[0].excludedIncludeIds).toEqual(['inc2'])
  })

  it('should fall back to quantity 1 when selected ingredient has no defaultQuantity', async () => {
    const wrapper = mount(OrdersView)

    await wrapper.get('[data-testid="new-order-button"]').trigger('click')
    await wrapper.get('[data-testid="order-item-0-product-select"]').setValue('p1')
    await wrapper.get('[data-testid="order-item-0-add-extra-button"]').trigger('click')

    await wrapper
      .get('[data-testid="order-item-0-extra-0-ingredient-select"]')
      .setValue('i1')

    const quantityInput = wrapper.get(
      '[data-testid="order-item-0-extra-0-quantity-input"]',
    )
    expect((quantityInput.element as HTMLInputElement).value).toBe('1')
  })

  // -------------------------------------------------------------------------
  // SubItems não-casados — cadastrar ingrediente faltante
  // -------------------------------------------------------------------------

  describe('Unmatched subitems', () => {
    function orderWithUnmatched(overrides: Record<string, unknown> = {}) {
      return {
        id: 'o1',
        dateTime: '2026-07-15T12:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 30,
        estimatedProfit: 10,
        origin: 'ANOTA_AI',
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Açaí 500ml',
            quantity: 1,
            unitPrice: 30,
            unitCost: 10,
            totalCost: 10,
            extraIngredients: [],
            unmatchedSubItems: [
              {
                id: 'u1',
                rawName: 'Nutella',
                quantity: 2,
                salePricePerUnit: 3,
                salePriceTotal: 6,
              },
            ],
          },
        ],
        ...overrides,
      }
    }

    it('should render an unmatched subitem with a create-ingredient button', async () => {
      orderStoreMock.items = [orderWithUnmatched()]
      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')
      await flushPromises()

      const detail = wrapper.get('[data-testid="order-detail-modal"]')
      expect(detail.html()).toContain('Nutella')
      expect(detail.find('[data-testid="unmatched-u1-create-button"]').exists()).toBe(true)
    })

    it('should create the missing ingredient prefilled with the raw name and hide the button after refetch', async () => {
      ingredientStoreMock.create = vi.fn().mockResolvedValue({ id: 'new1' })

      const withUnmatched = orderWithUnmatched()
      const withoutUnmatched = orderWithUnmatched({
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Açaí 500ml',
            quantity: 1,
            unitPrice: 30,
            unitCost: 10,
            totalCost: 10,
            extraIngredients: [],
            unmatchedSubItems: [],
          },
        ],
      })
      orderStoreMock.items = [withUnmatched]
      orderStoreMock.findById = vi
        .fn()
        .mockResolvedValueOnce(withUnmatched)
        .mockResolvedValueOnce(withoutUnmatched)

      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')
      await flushPromises()

      const detail = wrapper.get('[data-testid="order-detail-modal"]')
      await detail.get('[data-testid="unmatched-u1-create-button"]').trigger('click')

      const nameInput = detail.get('[data-testid="unmatched-u1-name-input"]')
      expect((nameInput.element as HTMLInputElement).value).toBe('Nutella')

      await detail.get('[data-testid="unmatched-u1-unit-input"]').setValue('g')
      await detail.get('[data-testid="unmatched-u1-cost-input"]').setValue('0.05')
      await detail.get('[data-testid="unmatched-u1-save-button"]').trigger('click')
      await flushPromises()

      expect(ingredientStoreMock.create).toHaveBeenCalledWith({
        name: 'Nutella',
        unit: 'g',
        costPerUnit: 0.05,
      })
      // Depois de criar, o pedido é recarregado e o botão some.
      expect(wrapper.find('[data-testid="unmatched-u1-create-button"]').exists()).toBe(false)
    })
  })

  // -------------------------------------------------------------------------
  // Detalhe do pedido — troca rápida entre pedidos
  // -------------------------------------------------------------------------

  describe('order detail switching', () => {
    function makeOrder(id: string, customerName: string, productName: string) {
      return {
        id,
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName,
        status: 'PAID',
        totalValue: 60,
        estimatedProfit: 26,
        marginPct: 43.33,
        items: [
          {
            id: `oi-${id}`,
            productId: 'p1',
            productName,
            quantity: 1,
            unitPrice: 60,
            unitCost: 34,
            totalCost: 34,
            extraIngredients: [],
          },
        ],
      }
    }

    it('should ignore a stale response that resolves after another order was opened', async () => {
      const first = makeOrder('o1', 'João', 'Hambúrguer')
      const second = makeOrder('o2', 'Maria', 'Açaí 500ml')
      orderStoreMock.items = [first, second]

      let resolveFirst: (value: unknown) => void = () => {}
      orderStoreMock.findById = vi.fn((id: string) =>
        id === 'o1'
          ? new Promise((resolve) => {
              resolveFirst = resolve
            })
          : Promise.resolve(second),
      )

      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')
      await wrapper.get('[data-testid="order-o2-detail-button"]').trigger('click')
      await flushPromises()

      // A resposta lenta do primeiro pedido chega depois: não pode sobrescrever.
      resolveFirst(first)
      await flushPromises()

      const detail = wrapper.get('[data-testid="order-detail-modal"]')
      expect(detail.html()).toContain('Maria')
      expect(detail.html()).toContain('Açaí 500ml')
      expect(detail.html()).not.toContain('João')
      expect(detail.html()).not.toContain('Hambúrguer')
    })

    it('should show the clicked order right away, without blanking to a loading state', async () => {
      const first = makeOrder('o1', 'João', 'Hambúrguer')
      const second = makeOrder('o2', 'Maria', 'Açaí 500ml')
      orderStoreMock.items = [first, second]

      orderStoreMock.findById = vi.fn((id: string) =>
        id === 'o1' ? Promise.resolve(first) : new Promise(() => {}),
      )

      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')
      await flushPromises()

      // Troca para o segundo pedido: enquanto o detalhe não chega, a linha da
      // lista já é exibida — sem spinner e sem o pedido anterior.
      await wrapper.get('[data-testid="order-o2-detail-button"]').trigger('click')

      const detail = wrapper.get('[data-testid="order-detail-modal"]')
      expect(detail.html()).toContain('Maria')
      expect(detail.html()).toContain('Açaí 500ml')
      expect(detail.html()).not.toContain('João')
      expect(detail.html()).not.toContain('Hambúrguer')
      expect(detail.text()).not.toContain('Carregando')
    })

    it('should not repopulate a closed modal with a pending response', async () => {
      const first = makeOrder('o1', 'João', 'Hambúrguer')
      orderStoreMock.items = [first]

      let resolveFirst: (value: unknown) => void = () => {}
      orderStoreMock.findById = vi.fn(
        () =>
          new Promise((resolve) => {
            resolveFirst = resolve
          }),
      )

      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')
      await wrapper
        .get('[data-testid="order-detail-modal"] [data-testid="ui-modal-close"]')
        .trigger('click')
      await flushPromises()

      resolveFirst(first)
      await flushPromises()

      expect(wrapper.find('[data-testid="order-detail-modal"]').exists()).toBe(false)
    })
  })

  // -------------------------------------------------------------------------
  // Configurar pedidos — ficha do pedido (insumos cobrados uma vez por pedido)
  // -------------------------------------------------------------------------

  describe('Configurar pedidos', () => {
    it('should render the Configurar pedidos button next to Novo Pedido', () => {
      const wrapper = mount(OrdersView)
      expect(wrapper.find('[data-testid="configure-orders-button"]').exists()).toBe(true)
      expect(wrapper.get('[data-testid="configure-orders-button"]').text()).toContain(
        'Configurar pedidos',
      )
    })

    it('should open the modal and load the current ficha', async () => {
      orderFichaFindMock.mockResolvedValue({
        lines: [
          {
            id: 'l1',
            ingredientId: 'i1',
            ingredientName: 'Granola',
            ingredientUnit: 'g',
            quantity: 2,
            costPerUnit: 0.05,
            totalCost: 0.1,
          },
        ],
        totalCost: 0.1,
      })

      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      expect(orderFichaFindMock).toHaveBeenCalled()
      expect(wrapper.find('[data-testid="order-ficha-modal"]').exists()).toBe(true)
      const qty = wrapper.get('[data-testid="order-ficha-line-0-quantity-input"]')
      expect((qty.element as HTMLInputElement).value).toBe('2')
    })

    it('should show the resulting per-order cost', async () => {
      orderFichaFindMock.mockResolvedValue({
        lines: [
          {
            id: 'l1',
            ingredientId: 'i1',
            ingredientName: 'Granola',
            ingredientUnit: 'g',
            quantity: 2,
            costPerUnit: 0.05,
            totalCost: 0.1,
          },
        ],
        totalCost: 0.1,
      })

      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      expect(wrapper.get('[data-testid="order-ficha-total-cost"]').text()).toContain('0,10')
    })

    it('should add a line and save it', async () => {
      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      await wrapper.get('[data-testid="order-ficha-add-line-button"]').trigger('click')
      await wrapper.get('[data-testid="order-ficha-line-0-ingredient-select"]').setValue('i1')
      await wrapper.get('[data-testid="order-ficha-line-0-quantity-input"]').setValue('3')

      await wrapper.get('[data-testid="order-ficha-save-button"]').trigger('click')
      await flushPromises()

      expect(orderFichaReplaceMock).toHaveBeenCalledWith({
        lines: [{ ingredientId: 'i1', quantity: 3 }],
      })
    })

    it('should remove a line', async () => {
      orderFichaFindMock.mockResolvedValue({
        lines: [
          {
            id: 'l1',
            ingredientId: 'i1',
            ingredientName: 'Granola',
            ingredientUnit: 'g',
            quantity: 2,
            costPerUnit: 0.05,
            totalCost: 0.1,
          },
        ],
        totalCost: 0.1,
      })

      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      await wrapper.get('[data-testid="order-ficha-line-0-remove-button"]').trigger('click')
      expect(wrapper.find('[data-testid="order-ficha-line-0-quantity-input"]').exists()).toBe(false)

      await wrapper.get('[data-testid="order-ficha-save-button"]').trigger('click')
      await flushPromises()

      // lista vazia limpa a ficha — custo volta a zero
      expect(orderFichaReplaceMock).toHaveBeenCalledWith({ lines: [] })
    })

    it('should not save a line without an ingredient selected', async () => {
      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      await wrapper.get('[data-testid="order-ficha-add-line-button"]').trigger('click')
      await wrapper.get('[data-testid="order-ficha-save-button"]').trigger('click')
      await flushPromises()

      expect(orderFichaReplaceMock).not.toHaveBeenCalled()
      expect(wrapper.get('[data-testid="order-ficha-error"]').text()).toBeTruthy()
    })

    it('should reject the same ingredient twice', async () => {
      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      await wrapper.get('[data-testid="order-ficha-add-line-button"]').trigger('click')
      await wrapper.get('[data-testid="order-ficha-line-0-ingredient-select"]').setValue('i1')
      await wrapper.get('[data-testid="order-ficha-add-line-button"]').trigger('click')
      await wrapper.get('[data-testid="order-ficha-line-1-ingredient-select"]').setValue('i1')

      await wrapper.get('[data-testid="order-ficha-save-button"]').trigger('click')
      await flushPromises()

      expect(orderFichaReplaceMock).not.toHaveBeenCalled()
      expect(wrapper.get('[data-testid="order-ficha-error"]').text()).toBeTruthy()
    })

    it('should show the order ficha cost in the order detail', async () => {
      orderStoreMock.items = [
        {
          id: 'o1',
          dateTime: '2026-07-15T12:00:00',
          customerId: 'c1',
          customerName: 'João',
          status: 'PAID',
          totalValue: 40,
          estimatedProfit: 30,
          totalCost: 6.86,
          orderFichaCost: 0.86,
          orderFicha: [
            {
              id: 'f1',
              ingredientId: 'i1',
              ingredientName: 'Sacola',
              ingredientUnit: 'un',
              quantity: 1,
              costPerUnit: 0.8,
              totalCost: 0.8,
            },
          ],
          items: [],
          origin: 'JETMENU',
        },
      ]

      const wrapper = mount(OrdersView)
      await flushPromises()
      await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')
      await flushPromises()

      const ficha = wrapper.get('[data-testid="order-detail-ficha"]')
      expect(ficha.text()).toContain('Sacola')
      expect(ficha.text()).toContain('0,86')
    })

    it('should show a short one-sentence intro without the long detail', async () => {
      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      const intro = wrapper.get('[data-testid="order-ficha-intro"]')
      // Keeps the core meaning...
      expect(intro.text()).toContain('uma vez por pedido')
      // ...but drops the long secondary paragraph.
      expect(intro.text()).not.toContain('guardanapo')
      expect(intro.text()).not.toContain('mova-a')
    })

    it('should reveal an inline insumo creation form from a row', async () => {
      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      await wrapper.get('[data-testid="order-ficha-add-line-button"]').trigger('click')
      expect(wrapper.find('[data-testid="order-ficha-create-name"]').exists()).toBe(false)

      await wrapper.get('[data-testid="order-ficha-line-0-create-toggle"]').trigger('click')
      expect(wrapper.find('[data-testid="order-ficha-create-name"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="order-ficha-create-unit"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="order-ficha-create-cost"]').exists()).toBe(true)
    })

    it('should create a new insumo inline and auto-select it in the row', async () => {
      ingredientStoreMock.create = vi.fn(async (req: { name: string; unit: string; costPerUnit: number }) => {
        const created = { id: 'i9', name: req.name, unit: req.unit, costPerUnit: req.costPerUnit, status: 'ACTIVE' }
        ingredientStoreMock.items.push(created)
        return created
      })

      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      await wrapper.get('[data-testid="order-ficha-add-line-button"]').trigger('click')
      await wrapper.get('[data-testid="order-ficha-line-0-create-toggle"]').trigger('click')
      await wrapper.get('[data-testid="order-ficha-create-name"]').setValue('Sacola')
      await wrapper.get('[data-testid="order-ficha-create-unit"]').setValue('un')
      await wrapper.get('[data-testid="order-ficha-create-cost"]').setValue('0.8')
      await wrapper.get('[data-testid="order-ficha-create-submit"]').trigger('click')
      await flushPromises()

      expect(ingredientStoreMock.create).toHaveBeenCalledWith({
        name: 'Sacola',
        unit: 'un',
        costPerUnit: 0.8,
      })
      const select = wrapper.get('[data-testid="order-ficha-line-0-ingredient-select"]')
      expect((select.element as HTMLSelectElement).value).toBe('i9')
      // form closes after a successful creation
      expect(wrapper.find('[data-testid="order-ficha-create-name"]').exists()).toBe(false)
    })

    it('should show a pt-BR error and keep the row empty when inline creation fails', async () => {
      ingredientStoreMock.create = vi.fn().mockRejectedValue(new Error('boom'))

      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      await wrapper.get('[data-testid="order-ficha-add-line-button"]').trigger('click')
      await wrapper.get('[data-testid="order-ficha-line-0-create-toggle"]').trigger('click')
      await wrapper.get('[data-testid="order-ficha-create-name"]').setValue('Sacola')
      await wrapper.get('[data-testid="order-ficha-create-unit"]').setValue('un')
      await wrapper.get('[data-testid="order-ficha-create-cost"]').setValue('0.8')
      await wrapper.get('[data-testid="order-ficha-create-submit"]').trigger('click')
      await flushPromises()

      expect(wrapper.get('[data-testid="order-ficha-create-error"]').text()).toBeTruthy()
      const select = wrapper.get('[data-testid="order-ficha-line-0-ingredient-select"]')
      expect((select.element as HTMLSelectElement).value).toBe('')
    })

    it('should validate the inline insumo form before calling the store', async () => {
      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="configure-orders-button"]').trigger('click')
      await flushPromises()

      await wrapper.get('[data-testid="order-ficha-add-line-button"]').trigger('click')
      await wrapper.get('[data-testid="order-ficha-line-0-create-toggle"]').trigger('click')
      // name/unit left blank
      await wrapper.get('[data-testid="order-ficha-create-submit"]').trigger('click')
      await flushPromises()

      expect(ingredientStoreMock.create).not.toHaveBeenCalled()
      expect(wrapper.get('[data-testid="order-ficha-create-error"]').text()).toBeTruthy()
    })
  })

  describe('comanda do iFood', () => {
    function ifoodOrder(overrides: Record<string, unknown> = {}) {
      return {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PENDING',
        totalValue: 60,
        estimatedProfit: 26,
        origin: 'IFOOD',
        displayId: '3421',
        orderType: 'DELIVERY',
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Hambúrguer',
            quantity: 2,
            unitPrice: 30,
            unitCost: 17,
            totalCost: 34,
          },
        ],
        ...overrides,
      }
    }

    it('should open the ticket for an iFood order', async () => {
      orderStoreMock.items = [ifoodOrder()]
      const wrapper = mount(OrdersView)

      await wrapper.get('[data-testid="order-o1-ticket-button"]').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="ifood-ticket-modal-title"]').exists()).toBe(true)
      expect(wrapper.text()).toContain('3421')
    })

    it('should not offer the ticket for a non-iFood order', () => {
      orderStoreMock.items = [ifoodOrder({ origin: 'JETMENU' })]
      const wrapper = mount(OrdersView)

      expect(wrapper.find('[data-testid="order-o1-ticket-button"]').exists()).toBe(false)
    })

    it('should refresh the list when the ticket reports a new status', async () => {
      orderStoreMock.items = [ifoodOrder()]
      ifoodOrderActionMock.dispatch.mockResolvedValue({
        orderId: 'o1',
        externalOrderId: 'ext-1',
        status: 'DELIVERED',
      })
      const wrapper = mount(OrdersView)

      await wrapper.get('[data-testid="order-o1-ticket-button"]').trigger('click')
      await flushPromises()
      orderStoreMock.fetchPage.mockClear()

      await wrapper.get('[data-testid="ifood-ticket-dispatch"]').trigger('click')
      await flushPromises()

      expect(orderStoreMock.fetchPage).toHaveBeenCalled()
    })
  })

  describe('item target margin indicator', () => {
    // O style inline é normalizado para rgb() pelo jsdom.
    function rgb(hex: string): string {
      const n = parseInt(hex.slice(1), 16)
      return `rgb(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255})`
    }

    function orderWithItem(item: Record<string, unknown>) {
      return {
        id: 'o1',
        dateTime: '2026-05-14T10:00:00',
        customerId: 'c1',
        customerName: 'João',
        status: 'PAID',
        totalValue: 30,
        estimatedProfit: 13,
        marginPct: 43.3,
        items: [
          {
            id: 'oi1',
            productId: 'p1',
            productName: 'Hambúrguer',
            quantity: 1,
            unitPrice: 30,
            unitCost: 17,
            totalCost: 17,
            ...item,
          },
        ],
      }
    }

    async function openDetail(order: Record<string, unknown>) {
      orderStoreMock.items = [order]
      const wrapper = mount(OrdersView)
      await wrapper.get('[data-testid="order-o1-detail-button"]').trigger('click')
      await flushPromises()
      return wrapper.get('[data-testid="order-detail-modal"]')
    }

    it('should render nothing when the item has no target margin snapshot', async () => {
      const detail = await openDetail(orderWithItem({ targetMarginPct: null, marginPct: 43.3 }))

      expect(detail.find('[data-testid="item-oi1-margin"]').exists()).toBe(false)
    })

    it('should render nothing when the backend could not compute the realised margin', async () => {
      const detail = await openDetail(orderWithItem({ targetMarginPct: 60, marginPct: null }))

      expect(detail.find('[data-testid="item-oi1-margin"]').exists()).toBe(false)
    })

    it('should show the realised margin next to the target', async () => {
      const detail = await openDetail(orderWithItem({ targetMarginPct: 60, marginPct: 43.33 }))

      expect(detail.get('[data-testid="item-oi1-margin"]').text()).toBe('Margem 43,3% · meta 60,0%')
    })

    it('should paint the indicator red when the item came out below its target', async () => {
      const detail = await openDetail(orderWithItem({ targetMarginPct: 60, marginPct: 43.3 }))

      expect(detail.get('[data-testid="item-oi1-margin"]').attributes('style')).toContain(
        `color: ${rgb(UI.rose)}`,
      )
    })

    it('should paint the indicator green when the item came out above its target', async () => {
      const detail = await openDetail(orderWithItem({ targetMarginPct: 40, marginPct: 43.3 }))

      expect(detail.get('[data-testid="item-oi1-margin"]').attributes('style')).toContain(
        `color: ${rgb(UI.emerald2)}`,
      )
    })

    it('should paint the indicator neutral grey when the item hit its target exactly', async () => {
      const detail = await openDetail(orderWithItem({ targetMarginPct: 43.3, marginPct: 43.3 }))

      expect(detail.get('[data-testid="item-oi1-margin"]').attributes('style')).toContain(
        `color: ${rgb(UI.textMute)}`,
      )
    })
  })
})
