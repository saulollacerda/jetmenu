import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import IfoodDiagnosticsWindow from '@/components/IfoodDiagnosticsWindow.vue'
import { ifoodDiagnosticsService } from '@/services/ifoodDiagnosticsService'

vi.mock('@/services/ifoodDiagnosticsService', async () => {
  const actual = await vi.importActual<typeof import('@/services/ifoodDiagnosticsService')>(
    '@/services/ifoodDiagnosticsService',
  )
  return {
    ...actual,
    ifoodDiagnosticsService: {
      isEnabled: vi.fn(),
      listCatalogs: vi.fn(),
      listItems: vi.fn(),
    },
  }
})

const CATALOGS_RAW = {
  endpoint: 'https://merchant-api.ifood.com.br/catalog/v2.0/merchants/m1/catalogs',
  status: 200,
  body: '[{"catalogId":"cat-default","context":["DEFAULT"]}]',
}

describe('IfoodDiagnosticsWindow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('começa sem resultado, apenas com as ações disponíveis', () => {
    const wrapper = mount(IfoodDiagnosticsWindow)

    expect(wrapper.find('[data-testid="diag-list-catalogs"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="diag-list-items"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="diag-result-body"]').exists()).toBe(false)
  })

  it('"Listar catálogos" chama GET /catalogs e mostra endpoint, status e corpo cru', async () => {
    vi.mocked(ifoodDiagnosticsService.listCatalogs).mockResolvedValue(CATALOGS_RAW)
    const wrapper = mount(IfoodDiagnosticsWindow)

    await wrapper.find('[data-testid="diag-list-catalogs"]').trigger('click')
    await flush()

    expect(ifoodDiagnosticsService.listCatalogs).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="diag-result-endpoint"]').text()).toContain(
      '/merchants/m1/catalogs',
    )
    expect(wrapper.find('[data-testid="diag-result-status"]').text()).toContain('200')
    expect(wrapper.find('[data-testid="diag-result-body"]').text()).toContain('cat-default')
  })

  it('formata o JSON da resposta para leitura na reunião de homologação', async () => {
    vi.mocked(ifoodDiagnosticsService.listCatalogs).mockResolvedValue(CATALOGS_RAW)
    const wrapper = mount(IfoodDiagnosticsWindow)

    await wrapper.find('[data-testid="diag-list-catalogs"]').trigger('click')
    await flush()

    // indentado, não a linha única que veio do iFood
    expect(wrapper.find('[data-testid="diag-result-body"]').text()).toContain('\n  ')
  })

  it('mostra o corpo como veio quando não é JSON', async () => {
    vi.mocked(ifoodDiagnosticsService.listCatalogs).mockResolvedValue({
      endpoint: 'https://ifood/catalogs',
      status: 502,
      body: 'Bad Gateway',
    })
    const wrapper = mount(IfoodDiagnosticsWindow)

    await wrapper.find('[data-testid="diag-list-catalogs"]').trigger('click')
    await flush()

    expect(wrapper.find('[data-testid="diag-result-body"]').text()).toContain('Bad Gateway')
    expect(wrapper.find('[data-testid="diag-result-status"]').text()).toContain('502')
  })

  it('"Listar itens" chama GET /items', async () => {
    vi.mocked(ifoodDiagnosticsService.listItems).mockResolvedValue({
      endpoint: 'https://ifood/categories?includeItems=true',
      status: 200,
      body: '[]',
    })
    const wrapper = mount(IfoodDiagnosticsWindow)

    await wrapper.find('[data-testid="diag-list-items"]').trigger('click')
    await flush()

    expect(ifoodDiagnosticsService.listItems).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="diag-result-endpoint"]').text()).toContain('includeItems=true')
  })

  it('exibe mensagem em pt-BR quando a chamada falha', async () => {
    vi.mocked(ifoodDiagnosticsService.listCatalogs).mockRejectedValue({ response: { status: 409 } })
    const wrapper = mount(IfoodDiagnosticsWindow)

    await wrapper.find('[data-testid="diag-list-catalogs"]').trigger('click')
    await flush()

    expect(wrapper.find('[data-testid="diag-error"]').text()).toBe(
      'Conecte sua conta do iFood antes de usar o diagnóstico.',
    )
    expect(wrapper.find('[data-testid="diag-result-body"]').exists()).toBe(false)
  })

  it('limpa o erro anterior ao repetir a consulta com sucesso', async () => {
    vi.mocked(ifoodDiagnosticsService.listCatalogs)
      .mockRejectedValueOnce({ response: { status: 503 } })
      .mockResolvedValueOnce(CATALOGS_RAW)
    const wrapper = mount(IfoodDiagnosticsWindow)

    await wrapper.find('[data-testid="diag-list-catalogs"]').trigger('click')
    await flush()
    expect(wrapper.find('[data-testid="diag-error"]').exists()).toBe(true)

    await wrapper.find('[data-testid="diag-list-catalogs"]').trigger('click')
    await flush()

    expect(wrapper.find('[data-testid="diag-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="diag-result-body"]').exists()).toBe(true)
  })

  it('expande e restaura a janela', async () => {
    const wrapper = mount(IfoodDiagnosticsWindow)
    const window = () => wrapper.find('[data-testid="ifood-diagnostics-window"]')

    expect(window().attributes('data-expanded')).toBe('false')

    await wrapper.find('[data-testid="diag-expand"]').trigger('click')
    expect(window().attributes('data-expanded')).toBe('true')

    await wrapper.find('[data-testid="diag-expand"]').trigger('click')
    expect(window().attributes('data-expanded')).toBe('false')
  })

  it('emite close no botão de fechar', async () => {
    const wrapper = mount(IfoodDiagnosticsWindow)

    await wrapper.find('[data-testid="diag-close"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})

/** Deixa a microtask da chamada e o re-render acontecerem. */
async function flush() {
  await new Promise((resolve) => setTimeout(resolve, 0))
}
