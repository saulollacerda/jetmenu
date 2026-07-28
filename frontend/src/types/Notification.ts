export type NotificationType =
  | 'MISSING_INGREDIENT'
  | 'MISSING_PRODUCT'
  | 'ORDER_CANCELLED'
  /** O cliente (ou o iFood) pediu o cancelamento e espera a resposta do lojista. */
  | 'ORDER_CANCELLATION_REQUESTED'

export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  MISSING_INGREDIENT: 'Ingrediente não cadastrado',
  MISSING_PRODUCT: 'Produto não cadastrado',
  ORDER_CANCELLED: 'Pedido cancelado',
  ORDER_CANCELLATION_REQUESTED: 'Solicitação de cancelamento',
}

/**
 * `referenceData` de uma notificação `ORDER_CANCELLATION_REQUESTED`: o id **local** do
 * pedido (UUID), e não o id do iFood — é por ele que a comanda descobre que há uma
 * solicitação pendente e chama os endpoints de aceitar/recusar.
 */
export const CANCELLATION_REQUESTED_TYPE: NotificationType = 'ORDER_CANCELLATION_REQUESTED'

export type NotificationStatus = 'UNREAD' | 'READ' | 'RESOLVED'

export interface NotificationResponse {
  id: string
  type: NotificationType
  title: string
  message: string
  referenceData: string | null
  referenceDisplay: string | null
  status: NotificationStatus
  createdAt: string
  resolvedAt: string | null
}

export interface UnreadCountResponse {
  count: number
}
