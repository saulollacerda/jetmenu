export type UserStatus = 'ACTIVE' | 'INACTIVE'

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY'

export interface OpeningHour {
  dayOfWeek: DayOfWeek
  openTime: string | null
  closeTime: string | null
  closed: boolean
}

/**
 * Preferências do merchant (alertas / comportamento de cálculo). O backend grava o objeto
 * inteiro a cada PUT, então quem edita um campo precisa reenviar os outros como vieram.
 */
export interface MerchantPreferences {
  realtimeMarginCalc: boolean
  marginAlertBelow50Pct: boolean
  warnUnregisteredIngredients: boolean
  includePackagingCostInCost: boolean
  /**
   * Margem ideal do pedido inteiro, em %, já descontadas as taxas — mesma base do
   * `marginPct` de cada pedido. `null` = a loja não acompanha, nada é comparado.
   */
  targetOrderMarginPct: number | null
}

export interface UserRequest {
  merchantName: string
  cnpj: string
  email: string
  password: string
  confirmPassword: string
  phone?: string
}

export interface UserResponse {
  id: string
  merchantName: string
  cnpj: string
  email: string
  phone: string | null
  status: UserStatus
  createdAt: string
  anotaAiApiKey?: string | null
  openingHours?: OpeningHour[] | null
}

export interface AnotaAIKeyRequest {
  anotaAiApiKey: string | null
}
