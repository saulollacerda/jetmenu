import api from './api'
import type {
  AnotaAIKeyRequest,
  MerchantPreferences,
  OpeningHour,
  UserResponse,
} from '@/types/User'

export const userService = {
  /** Current authenticated merchant (resolved server-side from the token). */
  async getMe(): Promise<UserResponse> {
    const { data } = await api.get<UserResponse>('/merchants/me')
    return data
  },

  async updateAnotaAIKey(request: AnotaAIKeyRequest): Promise<UserResponse> {
    const { data } = await api.put<UserResponse>('/merchants/me/anota-ai-key', request)
    return data
  },

  async updateMe(payload: { openingHours: OpeningHour[] }): Promise<UserResponse> {
    const { data } = await api.put<UserResponse>('/merchants/me', payload)
    return data
  },

  async getPreferences(): Promise<MerchantPreferences> {
    const { data } = await api.get<MerchantPreferences>('/merchants/me/preferences')
    return data
  },

  /** O backend sobrescreve as preferências inteiras — envie o objeto completo. */
  async updatePreferences(preferences: MerchantPreferences): Promise<MerchantPreferences> {
    const { data } = await api.put<MerchantPreferences>('/merchants/me/preferences', preferences)
    return data
  },
}
