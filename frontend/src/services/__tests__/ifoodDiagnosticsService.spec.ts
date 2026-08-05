import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  ifoodDiagnosticsService,
  diagnosticsErrorMessage,
} from '@/services/ifoodDiagnosticsService'
import api from '@/services/api'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
  },
}))

describe('ifoodDiagnosticsService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('isEnabled should GET /diagnostics/access and return the flag', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { enabled: true } })

    const enabled = await ifoodDiagnosticsService.isEnabled()

    expect(api.get).toHaveBeenCalledWith('/integrations/ifood/diagnostics/access')
    expect(enabled).toBe(true)
  })

  it('isEnabled should return false when the check fails, never throwing', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('network'))

    await expect(ifoodDiagnosticsService.isEnabled()).resolves.toBe(false)
  })

  it('listCatalogs should GET /diagnostics/catalogs and return the raw response', async () => {
    const raw = {
      endpoint: 'https://merchant-api.ifood.com.br/catalog/v2.0/merchants/m1/catalogs',
      status: 200,
      body: '[{"catalogId":"cat-default"}]',
    }
    vi.mocked(api.get).mockResolvedValue({ data: raw })

    const result = await ifoodDiagnosticsService.listCatalogs()

    expect(api.get).toHaveBeenCalledWith('/integrations/ifood/diagnostics/catalogs')
    expect(result).toEqual(raw)
  })

  it('listItems should GET /diagnostics/items without params when no catalog is chosen', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { endpoint: 'x', status: 200, body: '[]' } })

    await ifoodDiagnosticsService.listItems()

    expect(api.get).toHaveBeenCalledWith('/integrations/ifood/diagnostics/items', { params: {} })
  })

  it('listItems should forward the chosen catalogId', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { endpoint: 'x', status: 200, body: '[]' } })

    await ifoodDiagnosticsService.listItems('cat-default')

    expect(api.get).toHaveBeenCalledWith('/integrations/ifood/diagnostics/items', {
      params: { catalogId: 'cat-default' },
    })
  })

  it.each([
    [403, 'O diagnóstico do iFood não está liberado para esta loja.'],
    [409, 'Conecte sua conta do iFood antes de usar o diagnóstico.'],
    [404, 'Nenhum catálogo encontrado no iFood para esta loja.'],
    [503, 'O iFood está indisponível no momento. Tente novamente em instantes.'],
  ])('diagnosticsErrorMessage should map %i to pt-BR copy', (status, expected) => {
    expect(diagnosticsErrorMessage({ response: { status } })).toBe(expected)
  })

  it('diagnosticsErrorMessage should fall back to a generic message', () => {
    expect(diagnosticsErrorMessage(new Error('boom'))).toBe(
      'Não foi possível consultar o iFood. Tente novamente em instantes.',
    )
  })
})
