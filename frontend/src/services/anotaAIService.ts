import api from './api'
import type { AnotaAISyncResult, AnotaAIWebhookConfig } from '@/types/AnotaAI'

export const anotaAIService = {
  async syncOrders(): Promise<AnotaAISyncResult> {
    const { data } = await api.post<AnotaAISyncResult>('/integrations/anotaai/orders')
    return data
  },

  async syncCatalog(options: { clearRecipes?: boolean } = {}): Promise<AnotaAISyncResult> {
    const { data } = await api.post<AnotaAISyncResult>('/integrations/anotaai/catalog', null, {
      params: options.clearRecipes ? { clearRecipes: true } : undefined,
    })
    return data
  },

  async getWebhookConfig(): Promise<AnotaAIWebhookConfig> {
    const { data } = await api.get<AnotaAIWebhookConfig>('/merchants/me/anotaai-webhook')
    return data
  },

  /** Gera um segredo novo e invalida o anterior. A URL não muda. */
  async rotateWebhookSecret(): Promise<AnotaAIWebhookConfig> {
    const { data } = await api.post<AnotaAIWebhookConfig>('/merchants/me/anotaai-webhook/rotate')
    return data
  },
}

/**
 * Monta a URL absoluta do webhook a partir do caminho devolvido pelo backend.
 *
 * O backend devolve só o caminho porque quem sabe o host público dele é o frontend, que já
 * fala com ele — evita mais uma variável de ambiente só para montar uma string. Quando a API
 * é servida na mesma origem (o padrão, via proxy), o host é o da própria página.
 */
export function resolveWebhookUrl(webhookPath: string | null | undefined): string {
  if (!webhookPath) return ''
  const baseUrl = api.defaults.baseURL ?? ''
  const origin = /^https?:\/\//i.test(baseUrl)
    ? new URL(baseUrl).origin
    : window.location.origin
  return `${origin}${webhookPath}`
}
