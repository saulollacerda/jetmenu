import api from './api'
import type {
  IfoodCancellationReason,
  IfoodOrderCancelRequest,
  OrderStatus,
} from '@/types/Order'

const BASE = '/integrations/ifood/orders'

/** Resultado de uma ação de ciclo de vida (confirmar, pronto para retirada, despachar). */
export interface IfoodOrderActionResult {
  orderId: string
  externalOrderId: string
  /** Status local do pedido após a ação. */
  status: OrderStatus
}

/**
 * Janela de confirmação (SLA de 8 minutos que o iFood exige para pedidos DELIVERY e
 * TAKEOUT, independente do `orderTiming`).
 */
export interface IfoodOrderConfirmationWindow {
  orderId: string
  /** ISO local date-time em America/Sao_Paulo. */
  createdAt: string
  /** ISO local date-time em America/Sao_Paulo — `createdAt` + 8 minutos. */
  deadline: string
  remainingSeconds: number
  expired: boolean
}

/**
 * Maps an order action failure to user-facing pt-BR copy. The backend returns an
 * RFC-7807 ProblemDetail whose `detail` is already a ready-to-show pt-BR message
 * (404 pedido inexistente, 409 pedido não-iFood/cancelado/autorização expirada,
 * 400 recusa do iFood), so we surface it and only fall back when it is missing.
 */
export function orderActionErrorMessage(
  err: unknown,
  fallback = 'Não foi possível concluir a operação. Tente novamente em instantes.',
): string {
  const detail = (err as { response?: { data?: { detail?: unknown } } })?.response?.data?.detail
  if (typeof detail === 'string' && detail.trim().length > 0) {
    return detail
  }
  return fallback
}

export const ifoodOrderActionService = {
  /** Confirma o pedido no iFood. Nenhum corpo de requisição. */
  async confirm(orderId: string): Promise<IfoodOrderActionResult> {
    const { data } = await api.post<IfoodOrderActionResult>(`${BASE}/${orderId}/confirm`)
    return data
  },

  /** Avisa o iFood de que um pedido de retirada (TAKEOUT) está pronto. */
  async readyToPickup(orderId: string): Promise<IfoodOrderActionResult> {
    const { data } = await api.post<IfoodOrderActionResult>(`${BASE}/${orderId}/ready-to-pickup`)
    return data
  },

  /** Avisa o iFood de que um pedido de entrega própria (DELIVERY) saiu para entrega. */
  async dispatch(orderId: string): Promise<IfoodOrderActionResult> {
    const { data } = await api.post<IfoodOrderActionResult>(`${BASE}/${orderId}/dispatch`)
    return data
  },

  /**
   * Motivos oficiais de cancelamento aceitos pelo iFood para este pedido. A homologação
   * exige exibi-los ao lojista: ele escolhe um da lista em vez de digitar o motivo.
   */
  async cancellationReasons(orderId: string): Promise<IfoodCancellationReason[]> {
    const { data } = await api.get<IfoodCancellationReason[]>(
      `${BASE}/${orderId}/cancellation-reasons`,
    )
    return data
  },

  /**
   * Cancelamento iniciado pelo lojista. `cancellationCode` é obrigatório (o backend
   * devolve 400 se vier em branco); a observação livre só é enviada quando preenchida.
   */
  async cancel(orderId: string, request: IfoodOrderCancelRequest): Promise<IfoodOrderActionResult> {
    const note = request.reason?.trim()
    const body: IfoodOrderCancelRequest = { cancellationCode: request.cancellationCode }
    if (note) body.reason = note
    const { data } = await api.post<IfoodOrderActionResult>(`${BASE}/${orderId}/cancel`, body)
    return data
  },

  /** Aceita um cancelamento pedido pelo cliente ou pela plataforma: o pedido é cancelado. */
  async acceptCancellationRequest(orderId: string): Promise<IfoodOrderActionResult> {
    const { data } = await api.post<IfoodOrderActionResult>(
      `${BASE}/${orderId}/cancellation-request/accept`,
    )
    return data
  },

  /** Recusa um cancelamento pedido pelo cliente ou pela plataforma: o status não muda. */
  async denyCancellationRequest(orderId: string): Promise<IfoodOrderActionResult> {
    const { data } = await api.post<IfoodOrderActionResult>(
      `${BASE}/${orderId}/cancellation-request/deny`,
    )
    return data
  },

  async getConfirmationWindow(orderId: string): Promise<IfoodOrderConfirmationWindow> {
    const { data } = await api.get<IfoodOrderConfirmationWindow>(
      `${BASE}/${orderId}/confirmation-window`,
    )
    return data
  },
}
