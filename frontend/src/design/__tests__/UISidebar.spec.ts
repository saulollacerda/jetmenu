import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const routerPush = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/dashboard' }),
  useRouter: () => ({ push: routerPush }),
}))

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({
    restaurantName: 'Açaí do Saulo',
    currentUser: null,
    logout: vi.fn(),
  }),
}))

vi.mock('@/services/ifoodDiagnosticsService', () => ({
  ifoodDiagnosticsService: { isEnabled: vi.fn().mockResolvedValue(false) },
}))

import UISidebar from '@/design/UISidebar.vue'
import { useSidebar } from '@/composables/useSidebar'
import { useIfoodDiagnostics } from '@/composables/useIfoodDiagnostics'
import { ifoodDiagnosticsService } from '@/services/ifoodDiagnosticsService'

describe('UISidebar — drawer mobile', () => {
  beforeEach(() => {
    routerPush.mockClear()
    useSidebar().close()
  })

  it('não tem a classe is-open quando o drawer está fechado', () => {
    const wrapper = mount(UISidebar)
    expect(wrapper.find('aside').classes()).not.toContain('is-open')
  })

  it('recebe a classe is-open quando o drawer abre', async () => {
    const wrapper = mount(UISidebar)
    useSidebar().toggle()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('aside').classes()).toContain('is-open')
  })

  it('renderiza o backdrop apenas com o drawer aberto', async () => {
    const wrapper = mount(UISidebar)
    expect(wrapper.find('[data-testid="sidebar-backdrop"]').exists()).toBe(false)
    useSidebar().toggle()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="sidebar-backdrop"]').exists()).toBe(true)
  })

  it('clicar no backdrop fecha o drawer', async () => {
    const wrapper = mount(UISidebar)
    useSidebar().toggle()
    await wrapper.vm.$nextTick()
    await wrapper.find('[data-testid="sidebar-backdrop"]').trigger('click')
    expect(useSidebar().isOpen.value).toBe(false)
  })

  it('navegar por um item do menu fecha o drawer', async () => {
    const wrapper = mount(UISidebar)
    useSidebar().toggle()
    await wrapper.vm.$nextTick()
    await wrapper.findAll('.ui-nav')[1]!.trigger('click')
    expect(routerPush).toHaveBeenCalled()
    expect(useSidebar().isOpen.value).toBe(false)
  })
})

describe('UISidebar — diagnóstico iFood', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useIfoodDiagnostics().close()
  })

  it('não oferece o diagnóstico para lojas fora da whitelist', async () => {
    vi.mocked(ifoodDiagnosticsService.isEnabled).mockResolvedValue(false)

    const wrapper = mount(UISidebar)
    await flush()

    expect(wrapper.find('[data-testid="sidebar-diagnostics"]').exists()).toBe(false)
  })

  it('mostra o botão </> quando a loja está liberada', async () => {
    vi.mocked(ifoodDiagnosticsService.isEnabled).mockResolvedValue(true)

    const wrapper = mount(UISidebar)
    await flush()

    expect(wrapper.find('[data-testid="sidebar-diagnostics"]').exists()).toBe(true)
  })

  it('clicar no botão abre a janela de diagnóstico', async () => {
    vi.mocked(ifoodDiagnosticsService.isEnabled).mockResolvedValue(true)

    const wrapper = mount(UISidebar)
    await flush()
    await wrapper.find('[data-testid="sidebar-diagnostics"]').trigger('click')

    expect(useIfoodDiagnostics().isOpen.value).toBe(true)
  })
})

/** Deixa a checagem de acesso (assíncrona, no mount) resolver antes do assert. */
async function flush() {
  await new Promise((resolve) => setTimeout(resolve, 0))
}
