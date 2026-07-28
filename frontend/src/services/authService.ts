import api from './api'
import type { ProvisionRequest } from '@/types/Auth'
import type { UserResponse } from '@/types/User'

export const authService = {
  /**
   * Links the authenticated Supabase user to a JetMenu merchant (just-in-time,
   * idempotent on the backend). Called on first authenticated access.
   */
  async provision(request: ProvisionRequest): Promise<UserResponse> {
    const { data } = await api.post<UserResponse>('/auth/provision', request)
    return data
  },
}
