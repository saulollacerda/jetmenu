/**
 * O que o lojista precisa cadastrar no painel da Anota.AI para receber os pedidos por
 * webhook. O cadastro lá é manual, loja por loja, então os dois valores são exibidos
 * prontos para copiar.
 */
export interface AnotaAIWebhookConfig {
  merchantId: string
  /** Caminho da rota; a URL absoluta é montada no cliente por `resolveWebhookUrl`. */
  webhookPath: string
  /** O "Token Externo" a colar no painel. `null` enquanto nenhum foi gerado. */
  webhookSecret: string | null
  /** Loja vinculada do lado da Anota.AI. `null` até a primeira entrega chegar. */
  anotaAiMerchantId: string | null
}

export interface AnotaAISyncResult {
  ordersImported: number
  ordersSkipped: number
  categoriesCreated: number
  categoriesUpdated: number
  productsCreated: number
  productsUpdated: number
  /**
   * Names of ingredients referenced by imported orders that are not registered
   * locally. Surfaced in the UI as a temporary alert; persistent tracking lives
   * in the notifications inbox.
   */
  missingIngredientNames?: string[]
  errors: string[]
}
