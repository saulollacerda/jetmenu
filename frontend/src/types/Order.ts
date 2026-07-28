import type { OrderFichaIngredientResponse } from './OrderFicha'

export type OrderStatus = 'PENDING' | 'READY' | 'DELIVERED' | 'PAID' | 'CANCELLED' | 'TEST'
export type OrderOrigin = 'MENUBANK' | 'ANOTA_AI' | 'IFOOD'

/**
 * Modalidade do pedido no iFood. Null para pedidos manuais, importados antes desta
 * feature, ou quando o iFood envia um valor que o backend ainda não reconhece — nunca
 * chega como string crua.
 */
export type OrderType = 'DELIVERY' | 'TAKEOUT' | 'DINE_IN'

/** Imediato ou agendado. Mesmas regras de nulidade de `OrderType`. */
export type OrderTiming = 'IMMEDIATE' | 'SCHEDULED'

/**
 * Uma forma de pagamento do pedido, como exibida na comanda. Todo campo escalar pode
 * ser nulo: o bloco de pagamento pode não vir no payload do iFood.
 */
export interface OrderPaymentMethodResponse {
  id: string
  /** CREDIT, DEBIT, CASH, PIX, MEAL_VOUCHER, ... Valor cru do iFood. */
  method?: string | null
  /** ONLINE (já pago) ou OFFLINE (recebido pela loja). Valor cru do iFood. */
  type?: string | null
  /** VISA, MASTERCARD, ELO, ... Null quando a forma de pagamento não é cartão. */
  cardBrand?: string | null
  value?: number | null
  currency?: string | null
  /** Cédula que o cliente entrega no pagamento em dinheiro. Null quando não há troco. */
  changeFor?: number | null
  /**
   * Troco devido ao cliente, já calculado pelo backend. Exibir como veio — nunca
   * recalcular no frontend, para que todos os clientes mostrem o mesmo número.
   */
  changeAmount?: number | null
}

export interface OrderItemExtraIngredientRequest {
  ingredientId: string
  quantity: number
}

export interface OrderItemExtraIngredientResponse {
  id: string
  ingredientId: string
  ingredientName: string
  ingredientUnit: string
  quantity: number
  costPerUnit: number
  totalCost: number
  /**
   * Preço unitário pago pelo cliente por este adicional. `0` = complemento base.
   * Ausente para extras sem preço conhecido (pedido manual/iFood ou importado antes da V16).
   */
  salePricePerUnit?: number | null
  /** Valor total pago pelo cliente por este adicional. Não confundir com `totalCost` (custo). */
  salePriceTotal?: number | null
}

export interface OrderItemRequest {
  productId: string
  quantity: number
  extraIngredients?: OrderItemExtraIngredientRequest[]
  /** Ids dos includes da ficha técnica desmarcados neste item. Vazio = ficha completa. */
  excludedIncludeIds?: string[]
}

export interface OrderItemUnmatchedSubItemResponse {
  id: string
  /** Nome exato do subItem no payload — usado para pré-preencher o cadastro do ingrediente. */
  rawName: string
  quantity: number
  /** Preço unitário pago pelo cliente. Pode ser `0`/ausente para complemento base. */
  salePricePerUnit?: number | null
  /** Valor total pago pelo cliente. Pode ser `0`/ausente para complemento base. */
  salePriceTotal?: number | null
}

export interface OrderItemInsumoResponse {
  id: string
  productId: string
  name: string
  cost: number
  quantity: number
  totalCost: number
}

export interface OrderItemResponse {
  id: string
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  unitCost: number
  totalCost: number
  insumos?: OrderItemInsumoResponse[]
  extraIngredients?: OrderItemExtraIngredientResponse[]
  /**
   * SubItems importados que não casaram com nenhum ingrediente cadastrado. Cada um é exibido
   * com um botão para cadastrar o ingrediente faltante; some assim que o ingrediente existe.
   */
  unmatchedSubItems?: OrderItemUnmatchedSubItemResponse[]
  /** Ids dos includes da ficha técnica desmarcados neste item (pedido manual). */
  excludedIncludeIds?: string[]
  /** Observação do cliente para este item ("sem granola"). Null quando não há. */
  observations?: string | null
}

export interface OrderRequest {
  /** Cliente existente. Opcional quando customerName é informado. */
  customerId?: string
  /** Fluxo rápido: o backend reutiliza um cliente com o mesmo nome ou cria um novo. */
  customerName?: string
  status?: OrderStatus
  feeId?: string
  origin?: OrderOrigin
  items: OrderItemRequest[]
}

export interface OrderResponse {
  id: string
  dateTime: string
  customerId: string
  customerName: string
  status: OrderStatus
  totalValue: number
  estimatedProfit: number
  /** Margem (%) apurada pelo backend sobre (totalValue - deliveryFee). Null quando o subtotal é zero. */
  marginPct?: number | null
  deliveryFee?: number
  totalCost?: number
  feeId?: string
  feeName?: string
  feeRate?: number
  items: OrderItemResponse[]
  origin?: OrderOrigin
  /**
   * Insumos cobrados uma vez neste pedido (ficha do pedido), como gravados na criação.
   * Vazio para lojistas sem ficha configurada e para pedidos anteriores à V17.
   */
  orderFicha?: OrderFichaIngredientResponse[]
  /** Parcela do `totalCost` que veio da ficha do pedido. Zero quando não há ficha. */
  orderFichaCost?: number

  // --- Dados exigidos pela homologação de Order do iFood ---
  // Todos nulos/ausentes em pedidos manuais e em pedidos importados antes desta feature.

  /** Número curto do pedido exibido ao cliente e ao entregador ("3421"). */
  displayId?: string | null
  orderType?: OrderType | null
  orderTiming?: OrderTiming | null
  /** CPF ou CNPJ informado para a nota fiscal, somente dígitos. */
  customerDocument?: string | null
  /** Valor já pago online (iFood). */
  paymentPrepaidAmount?: number | null
  /** Valor a receber na entrega/retirada. */
  paymentPendingAmount?: number | null
  /** Nunca nulo no backend, mas ausente em respostas anteriores a esta feature. */
  paymentMethods?: OrderPaymentMethodResponse[]
  /** Valor total do cupom aplicado ao pedido. */
  discountTotal?: number | null
  /** Parcela do cupom bancada pelo iFood. */
  discountIfoodValue?: number | null
  /** Parcela do cupom bancada pela loja. */
  discountMerchantValue?: number | null
  deliveryMode?: string | null
  /** String crua (`IFOOD`, `MERCHANT`, ...) — o iFood pode adicionar novos valores. */
  deliveredBy?: string | null
  deliveryDateTime?: string | null
  /** Observação de entrega ("portão azul"). Exigida na comanda pela homologação. */
  deliveryObservations?: string | null
  /** Código de coleta informado pelo cliente na retirada. */
  pickupCode?: string | null
  takeoutMode?: string | null
  takeoutDateTime?: string | null
}

