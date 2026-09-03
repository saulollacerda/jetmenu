import { describe, it, expect, vi, beforeEach } from 'vitest'
import { anotaAIService, resolveWebhookUrl } from '@/services/anotaAIService'
import api from '@/services/api'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    defaults: { baseURL: '/api' },
  },
}))

const config = {
  merchantId: '3f2504e0-4f89-11d3-9a0c-0305e82c3301',
  webhookPath: '/api/webhooks/anotaai/3f2504e0-4f89-11d3-9a0c-0305e82c3301',
  webhookSecret: 'IqLp0Zt5xw',
  anotaAiMerchantId: '66c3ada81acfe90018b7ca85',
}

describe('anotaAIService — webhook', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.defaults.baseURL = '/api'
  })

  it('getWebhookConfig should GET /merchants/me/anotaai-webhook', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: config })

    const result = await anotaAIService.getWebhookConfig()

    expect(api.get).toHaveBeenCalledWith('/merchants/me/anotaai-webhook')
    expect(result).toEqual(config)
  })

  it('rotateWebhookSecret should POST /merchants/me/anotaai-webhook/rotate', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { ...config, webhookSecret: 'novo' } })

    const result = await anotaAIService.rotateWebhookSecret()

    expect(api.post).toHaveBeenCalledWith('/merchants/me/anotaai-webhook/rotate')
    expect(result.webhookSecret).toBe('novo')
  })
})

describe('resolveWebhookUrl', () => {
  beforeEach(() => {
    api.defaults.baseURL = '/api'
  })

  // O lojista cola essa URL no painel da Anota.AI: precisa ser absoluta, ou não chega
  // em lugar nenhum.
  it('should build an absolute URL from the page origin when the API is same-origin', () => {
    expect(resolveWebhookUrl(config.webhookPath)).toBe(
      `${window.location.origin}${config.webhookPath}`,
    )
  })

  it('should use the API host when the API lives somewhere else', () => {
    api.defaults.baseURL = 'https://api.jetmenu.com.br/api'

    expect(resolveWebhookUrl(config.webhookPath)).toBe(
      `https://api.jetmenu.com.br${config.webhookPath}`,
    )
  })

  it('should return empty for an empty path', () => {
    expect(resolveWebhookUrl('')).toBe('')
    expect(resolveWebhookUrl(null)).toBe('')
  })
})
