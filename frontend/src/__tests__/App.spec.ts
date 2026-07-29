import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createPinia } from 'pinia'
import App from '../App.vue'

// Toggle auth state per test via a hoisted holder the mocked store reads from.
const authState = vi.hoisted(() => ({ authenticated: true }))

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    get isAuthenticated() {
      return authState.authenticated
    },
    restaurantName: '',
    currentUser: null,
    logout: vi.fn(),
  }),
}))

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: { template: '<div>Dashboard</div>' } },
    { path: '/login', component: { template: '<div>Login</div>' }, meta: { public: true } },
    {
      path: '/register',
      component: { template: '<div>Register</div>' },
      meta: { public: true },
    },
    {
      path: '/email-verificado',
      component: { template: '<div>Email verificado</div>' },
      meta: { public: true, allowAuthenticated: true },
    },
    {
      path: '/checkout',
      component: { template: '<div>Checkout</div>' },
      meta: { public: true, allowAuthenticated: true },
    },
    { path: '/orders', component: { template: '<div>Orders</div>' } },
    { path: '/products', component: { template: '<div>Products</div>' } },
    { path: '/ingredients', component: { template: '<div>Ingredients</div>' } },
    { path: '/customers', component: { template: '<div>Customers</div>' } },
  ],
})

describe('App', () => {
  beforeEach(() => {
    authState.authenticated = true
    vi.clearAllMocks()
  })

  it('renders the sidebar with navigation links when authenticated', async () => {
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.text()).toContain('JetMenu')
    expect(wrapper.text()).toContain('Dashboard')
    expect(wrapper.text()).toContain('Pedidos')
    expect(wrapper.text()).toContain('Produtos')
    expect(wrapper.text()).toContain('Ingredientes')
    expect(wrapper.text()).toContain('Clientes')
    expect(wrapper.text()).toContain('Taxas')
    expect(wrapper.text()).toContain('Configurações')
  })

  it('does not render a Categorias navigation link', async () => {
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.text()).not.toContain('Categorias')
    expect(wrapper.find('a[href="/categories"]').exists()).toBe(false)
  })

  it('has the correct layout structure when authenticated', async () => {
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.find('.app-layout').exists()).toBe(true)
    expect(wrapper.find('aside').exists()).toBe(true)
    expect(wrapper.find('.main-content').exists()).toBe(true)
  })

  it('does not render sidebar when not authenticated', async () => {
    authState.authenticated = false
    router.push('/login')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.find('.app-layout').exists()).toBe(false)
    expect(wrapper.find('aside').exists()).toBe(false)
  })

  it('does not render sidebar on /email-verificado even when authenticated', async () => {
    authState.authenticated = true
    router.push('/email-verificado')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.find('.app-layout').exists()).toBe(false)
    expect(wrapper.find('aside').exists()).toBe(false)
  })

  it('does not render sidebar on checkout even when authenticated', async () => {
    authState.authenticated = true
    router.push('/checkout')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    expect(wrapper.find('.app-layout').exists()).toBe(false)
    expect(wrapper.find('aside').exists()).toBe(false)
  })
})
