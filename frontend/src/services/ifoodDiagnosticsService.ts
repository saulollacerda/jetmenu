import api from './api'

/**
 * Resposta do iFood repassada sem interpretação pelo backend — é o que a homologação
 * pede para ver na tela: a URL chamada, o status HTTP e o corpo exatamente como veio.
 */
export interface IfoodRawResponse {
  endpoint: string
  status: number
  body: string
}

/** Maps a /diagnostics failure to user-facing pt-BR copy. */
export function diagnosticsErrorMessage(err: unknown): string {
  const status = (err as { response?: { status?: number } })?.response?.status
  switch (status) {
    case 403:
      return 'O diagnóstico do iFood não está liberado para esta loja.'
    case 409:
      return 'Conecte sua conta do iFood antes de usar o diagnóstico.'
    case 404:
      return 'Nenhum catálogo encontrado no iFood para esta loja.'
    case 503:
      return 'O iFood está indisponível no momento. Tente novamente em instantes.'
    default:
      return 'Não foi possível consultar o iFood. Tente novamente em instantes.'
  }
}

export const ifoodDiagnosticsService = {
  /**
   * Nunca lança: a checagem roda no carregamento da sidebar e uma falha de rede não pode
   * quebrar a navegação — na dúvida, a entrada do diagnóstico simplesmente não aparece.
   */
  async isEnabled(): Promise<boolean> {
    try {
      const { data } = await api.get<{ enabled: boolean }>(
        '/integrations/ifood/diagnostics/access',
      )
      return data.enabled === true
    } catch {
      return false
    }
  },

  async listCatalogs(): Promise<IfoodRawResponse> {
    const { data } = await api.get<IfoodRawResponse>('/integrations/ifood/diagnostics/catalogs')
    return data
  },

  /** Sem `catalogId`, o backend resolve o catálogo DEFAULT da loja. */
  async listItems(catalogId?: string): Promise<IfoodRawResponse> {
    const { data } = await api.get<IfoodRawResponse>('/integrations/ifood/diagnostics/items', {
      params: catalogId ? { catalogId } : {},
    })
    return data
  },
}
