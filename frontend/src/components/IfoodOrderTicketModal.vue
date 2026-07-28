<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { UI, UIBtn, UIIcon, UIModal, UIPill, brl } from '@/design'
import {
  ifoodOrderActionService,
  orderActionErrorMessage,
  type IfoodOrderConfirmationWindow,
} from '@/services/ifoodOrderActionService'
import type { OrderPaymentMethodResponse, OrderResponse, OrderStatus } from '@/types/Order'

const props = defineProps<{ order: OrderResponse }>()

const emit = defineEmits<{
  (e: 'updated', payload: { orderId: string; status: OrderStatus }): void
  (e: 'close'): void
}>()

type PillColor = 'gray' | 'emerald' | 'blue' | 'amber' | 'rose' | 'violet'

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: 'Pendente',
  READY: 'Pronto',
  DELIVERED: 'Entregue',
  PAID: 'Pago',
  CANCELLED: 'Cancelado',
  TEST: 'Teste',
}

const STATUS_COLOR: Record<OrderStatus, PillColor> = {
  PENDING: 'amber',
  READY: 'blue',
  DELIVERED: 'emerald',
  PAID: 'emerald',
  CANCELLED: 'rose',
  TEST: 'gray',
}

const PAYMENT_METHOD_LABEL: Record<string, string> = {
  CREDIT: 'Crédito',
  DEBIT: 'Débito',
  CASH: 'Dinheiro',
  PIX: 'Pix',
  MEAL_VOUCHER: 'Vale-refeição',
  FOOD_VOUCHER: 'Vale-alimentação',
  DIGITAL_WALLET: 'Carteira digital',
  GIFT_CARD: 'Vale-presente',
  OTHER: 'Outra forma',
}

/** Local mirror of the order status: an action may change it while the ticket is open. */
const currentStatus = ref<OrderStatus>(props.order.status)
const confirmationWindow = ref<IfoodOrderConfirmationWindow | null>(null)
const remainingSeconds = ref<number | null>(null)
const runningAction = ref<'confirm' | 'ready' | 'dispatch' | null>(null)
const doneActions = ref<string[]>([])
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

// The countdown is anchored on `remainingSeconds` (timezone-free) rather than parsed
// from `deadline`, which is a São Paulo local date-time and would drift on a browser
// in another timezone. `deadline` is still shown so the merchant sees the SLA time.
let anchorAt = 0
let anchorRemaining = 0
let ticker: ReturnType<typeof setInterval> | null = null

/** Only iFood orders act on the iFood API, and never cancelled or test orders. */
const actionable = computed(
  () =>
    props.order.origin === 'IFOOD' &&
    currentStatus.value !== 'CANCELLED' &&
    currentStatus.value !== 'TEST',
)

const orderTypeLabel = computed(() => {
  if (props.order.orderType === 'DELIVERY') return 'Entrega'
  if (props.order.orderType === 'TAKEOUT') return 'Retirada'
  if (props.order.orderType === 'DINE_IN') return 'Consumo no local'
  return null
})

const orderTimingLabel = computed(() => {
  if (props.order.orderTiming === 'IMMEDIATE') return 'Imediato'
  if (props.order.orderTiming === 'SCHEDULED') return 'Agendado'
  return null
})

const deliveredByLabel = computed(() => {
  const raw = props.order.deliveredBy
  if (!raw) return null
  if (raw === 'MERCHANT') return 'Entrega própria'
  if (raw === 'IFOOD') return 'Entregador iFood'
  // deliveredBy is a raw string — iFood may add values we do not know yet.
  return raw
})

const document = computed(() => {
  const raw = props.order.customerDocument
  if (!raw) return null
  const digits = raw.replace(/\D/g, '')
  if (digits.length === 11) {
    return {
      label: 'CPF',
      value: digits.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4'),
    }
  }
  if (digits.length === 14) {
    return {
      label: 'CNPJ',
      value: digits.replace(/(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, '$1.$2.$3/$4-$5'),
    }
  }
  return { label: 'Documento', value: raw }
})

const payments = computed<OrderPaymentMethodResponse[]>(() => props.order.paymentMethods ?? [])

const hasPaymentInfo = computed(
  () =>
    payments.value.length > 0 ||
    props.order.paymentPrepaidAmount != null ||
    props.order.paymentPendingAmount != null,
)

const hasDiscount = computed(
  () =>
    props.order.discountTotal != null ||
    props.order.discountIfoodValue != null ||
    props.order.discountMerchantValue != null,
)

/**
 * The sponsor split is not guaranteed to be exhaustive: a coupon with an unrecognised
 * sponsor leaves a remainder. We show it as its own line instead of pretending the
 * two known shares add up to the total.
 */
const discountOther = computed(() => {
  const total = props.order.discountTotal
  if (total == null) return null
  const known = Number(props.order.discountIfoodValue ?? 0) + Number(props.order.discountMerchantValue ?? 0)
  const rest = Number(total) - known
  return rest > 0.005 ? rest : null
})

const deliveryModeLabel = computed(() => modeLabel(props.order.deliveryMode))
const takeoutModeLabel = computed(() => modeLabel(props.order.takeoutMode))

const hasDeliveryInfo = computed(
  () =>
    !!props.order.deliveryObservations ||
    !!props.order.deliveredBy ||
    !!props.order.deliveryDateTime ||
    !!props.order.deliveryMode,
)

const hasTakeoutInfo = computed(
  () => !!props.order.takeoutMode || !!props.order.takeoutDateTime,
)

const canDispatch = computed(
  () => actionable.value && (props.order.orderType === 'DELIVERY' || props.order.orderType == null),
)

const canReadyToPickup = computed(
  () => actionable.value && (props.order.orderType === 'TAKEOUT' || props.order.orderType == null),
)

const unknownType = computed(() => actionable.value && props.order.orderType == null)

const expired = computed(
  () =>
    !!confirmationWindow.value &&
    (confirmationWindow.value.expired || (remainingSeconds.value ?? 0) <= 0),
)

const countdownLabel = computed(() => {
  const total = remainingSeconds.value
  if (total == null) return ''
  const minutes = String(Math.floor(total / 60)).padStart(2, '0')
  const seconds = String(total % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
})

function modeLabel(raw: string | null | undefined): string | null {
  if (!raw) return null
  return raw === 'DEFAULT' ? 'Padrão' : raw
}

function methodLabel(method: string | null | undefined): string {
  if (!method) return 'Forma de pagamento não informada'
  return PAYMENT_METHOD_LABEL[method] ?? method
}

function paymentTypeLabel(type: string | null | undefined): string | null {
  if (type === 'ONLINE') return 'Pago online (iFood)'
  if (type === 'OFFLINE') return 'A receber pela loja'
  return type ?? null
}

/** Formats an amount, or returns null so the caller can omit the line entirely. */
function amount(value: number | null | undefined): string | null {
  return value == null ? null : brl(Number(value))
}

function formatDateTime(value: string | null | undefined): string | null {
  if (!value) return null
  return new Date(value).toLocaleString('pt-BR')
}

function itemTotal(unitPrice: number, quantity: number): string {
  return brl(Number(unitPrice) * Number(quantity))
}

function tick() {
  const elapsed = Math.floor((Date.now() - anchorAt) / 1000)
  remainingSeconds.value = Math.max(0, anchorRemaining - elapsed)
  if (remainingSeconds.value === 0) stopTicker()
}

function stopTicker() {
  if (ticker) {
    clearInterval(ticker)
    ticker = null
  }
}

onMounted(async () => {
  if (!actionable.value) return
  try {
    const window = await ifoodOrderActionService.getConfirmationWindow(props.order.id)
    confirmationWindow.value = window
    anchorAt = Date.now()
    anchorRemaining = Math.max(0, window.remainingSeconds)
    remainingSeconds.value = anchorRemaining
    // Counted down locally — the endpoint is called once and never polled.
    if (anchorRemaining > 0) ticker = setInterval(tick, 1000)
  } catch {
    // The ticket must still render every homologation field without the SLA window.
    confirmationWindow.value = null
  }
})

onUnmounted(stopTicker)

async function runAction(
  key: 'confirm' | 'ready' | 'dispatch',
  call: () => Promise<{ status: OrderStatus }>,
  successMessage: string,
) {
  runningAction.value = key
  actionError.value = null
  actionSuccess.value = null
  try {
    const result = await call()
    currentStatus.value = result.status
    doneActions.value = [...doneActions.value, key]
    actionSuccess.value = successMessage
    emit('updated', { orderId: props.order.id, status: result.status })
  } catch (e: unknown) {
    actionError.value = orderActionErrorMessage(e)
  } finally {
    runningAction.value = null
  }
}

function confirmOrder() {
  // Confirming is always the merchant's decision — nothing here auto-confirms.
  return runAction(
    'confirm',
    () => ifoodOrderActionService.confirm(props.order.id),
    'Pedido confirmado no iFood.',
  )
}

function readyToPickup() {
  return runAction(
    'ready',
    () => ifoodOrderActionService.readyToPickup(props.order.id),
    'Cliente avisado de que o pedido está pronto para retirada.',
  )
}

function dispatchOrder() {
  return runAction(
    'dispatch',
    () => ifoodOrderActionService.dispatch(props.order.id),
    'Pedido despachado para entrega.',
  )
}

const sectionTitle = {
  fontSize: '11px',
  fontWeight: 700,
  color: UI.textMute,
  letterSpacing: '0.6px',
  textTransform: 'uppercase' as const,
  marginBottom: '8px',
}

const block = {
  border: `1px solid ${UI.border}`,
  borderRadius: '10px',
  padding: '12px 14px',
  marginBottom: '12px',
}

const rowStyle = {
  display: 'flex',
  alignItems: 'baseline',
  justifyContent: 'space-between',
  gap: '12px',
  fontSize: '12.5px',
  color: UI.text,
  padding: '3px 0',
}

const labelStyle = { color: UI.textSub, fontSize: '12px' }
</script>

<template>
  <UIModal
    title="Comanda do pedido"
    :subtitle="`${order.customerName} · ${formatDateTime(order.dateTime)}`"
    :width="620"
    title-test-id="ifood-ticket-modal-title"
    @close="emit('close')"
  >
    <!-- Cabeçalho: identificação e situação -->
    <div :style="{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap', marginBottom: '14px' }">
      <span
        v-if="order.displayId"
        data-testid="ifood-ticket-display-id"
        :style="{
          fontSize: '20px',
          fontWeight: 700,
          color: UI.text,
          fontVariantNumeric: 'tabular-nums',
        }"
      >
        #{{ order.displayId }}
      </span>
      <UIPill v-if="orderTypeLabel" color="violet" size="sm">
        <span data-testid="ifood-ticket-type">{{ orderTypeLabel }}</span>
      </UIPill>
      <UIPill v-if="orderTimingLabel" color="blue" size="sm">
        <span data-testid="ifood-ticket-timing">{{ orderTimingLabel }}</span>
      </UIPill>
      <UIPill :color="STATUS_COLOR[currentStatus]" size="sm" dot>
        <span data-testid="ifood-ticket-status">{{ STATUS_LABEL[currentStatus] }}</span>
      </UIPill>
    </div>

    <!-- SLA de confirmação (8 minutos) -->
    <div
      v-if="confirmationWindow"
      data-testid="ifood-ticket-countdown"
      :style="{
        ...block,
        background: expired ? UI.roseBg : UI.amberBg,
        borderColor: expired ? UI.roseBg : UI.amberBg,
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
      }"
    >
      <UIIcon name="clock" :size="16" :style="{ color: expired ? UI.rose : UI.amber2, flexShrink: 0 }" />
      <div>
        <div :style="{ fontSize: '13px', fontWeight: 700, color: expired ? UI.rose2 : UI.amber2 }">
          <template v-if="expired">Prazo de confirmação expirado</template>
          <template v-else>Confirme em {{ countdownLabel }}</template>
        </div>
        <div :style="{ fontSize: '11.5px', color: UI.textSub, marginTop: '2px' }">
          <template v-if="expired">
            O iFood pode recusar a confirmação deste pedido. Limite:
            {{ formatDateTime(confirmationWindow.deadline) }}.
          </template>
          <template v-else>
            O iFood exige a confirmação em até 8 minutos. Limite:
            {{ formatDateTime(confirmationWindow.deadline) }}.
          </template>
        </div>
      </div>
    </div>

    <!-- Cliente -->
    <div :style="block">
      <div :style="sectionTitle">Cliente</div>
      <div :style="rowStyle">
        <span :style="labelStyle">Nome</span>
        <span :style="{ fontWeight: 600 }">{{ order.customerName }}</span>
      </div>
      <div v-if="document" data-testid="ifood-ticket-document" :style="rowStyle">
        <span :style="labelStyle">{{ document.label }}</span>
        <span :style="{ fontWeight: 600 }">{{ document.value }}</span>
      </div>
    </div>

    <!-- Código de coleta -->
    <div
      v-if="order.pickupCode"
      data-testid="ifood-ticket-pickup-code"
      :style="{ ...block, background: UI.blueBg, borderColor: UI.blueBg }"
    >
      <div :style="{ fontSize: '11.5px', color: UI.blue2, fontWeight: 600 }">Código de coleta</div>
      <div
        :style="{
          fontSize: '22px',
          fontWeight: 700,
          color: UI.blue2,
          letterSpacing: '2px',
          fontVariantNumeric: 'tabular-nums',
        }"
      >
        {{ order.pickupCode }}
      </div>
    </div>

    <!-- Itens e observações -->
    <div :style="block">
      <div :style="sectionTitle">Itens</div>
      <div v-for="item in order.items" :key="item.id" :style="{ padding: '4px 0' }">
        <div :style="rowStyle">
          <span>
            <strong>{{ item.quantity }}×</strong>
            {{ item.productName }}
          </span>
          <span :style="{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }">
            {{ itemTotal(item.unitPrice, item.quantity) }}
          </span>
        </div>
        <div
          v-for="extra in item.extraIngredients ?? []"
          :key="extra.id"
          :style="{ fontSize: '12px', color: UI.textSub, paddingLeft: '14px' }"
        >
          + {{ extra.ingredientName }}
        </div>
        <div
          v-if="item.observations"
          :data-testid="`ifood-ticket-item-${item.id}-observations`"
          :style="{
            fontSize: '12px',
            color: UI.amber2,
            background: UI.amberBg,
            borderRadius: '7px',
            padding: '5px 9px',
            marginTop: '4px',
          }"
        >
          Obs.: {{ item.observations }}
        </div>
      </div>
      <div
        :style="{
          ...rowStyle,
          borderTop: `1px solid ${UI.borderSub}`,
          marginTop: '6px',
          paddingTop: '8px',
        }"
      >
        <span :style="labelStyle">Total do pedido</span>
        <span :style="{ fontWeight: 700 }">{{ brl(Number(order.totalValue)) }}</span>
      </div>
    </div>

    <!-- Pagamento -->
    <div v-if="hasPaymentInfo" data-testid="ifood-ticket-payments" :style="block">
      <div :style="sectionTitle">Pagamento</div>
      <div
        v-for="payment in payments"
        :key="payment.id"
        :data-testid="`ifood-ticket-payment-${payment.id}`"
        :style="{ padding: '4px 0' }"
      >
        <div :style="rowStyle">
          <span>
            {{ methodLabel(payment.method) }}
            <UIPill v-if="payment.cardBrand" color="gray" size="sm">{{ payment.cardBrand }}</UIPill>
          </span>
          <span v-if="amount(payment.value)" :style="{ fontWeight: 600 }">
            {{ amount(payment.value) }}
          </span>
        </div>
        <div
          v-if="paymentTypeLabel(payment.type)"
          :style="{ fontSize: '11.5px', color: UI.textSub }"
        >
          {{ paymentTypeLabel(payment.type) }}
        </div>
        <!-- Troco: valor calculado pelo backend, exibido como veio. -->
        <div
          v-if="payment.changeAmount != null"
          :data-testid="`ifood-ticket-change-${payment.id}`"
          :style="{
            fontSize: '12px',
            color: UI.emerald2,
            background: UI.emeraldBg,
            borderRadius: '7px',
            padding: '5px 9px',
            marginTop: '4px',
          }"
        >
          Troco para {{ amount(payment.changeFor) }}: <strong>{{ amount(payment.changeAmount) }}</strong>
        </div>
      </div>

      <div v-if="amount(order.paymentPrepaidAmount)" data-testid="ifood-ticket-prepaid" :style="rowStyle">
        <span :style="labelStyle">Já pago online</span>
        <span :style="{ fontWeight: 600 }">{{ amount(order.paymentPrepaidAmount) }}</span>
      </div>
      <div v-if="amount(order.paymentPendingAmount)" data-testid="ifood-ticket-pending" :style="rowStyle">
        <span :style="labelStyle">A receber na entrega/retirada</span>
        <span :style="{ fontWeight: 600 }">{{ amount(order.paymentPendingAmount) }}</span>
      </div>
    </div>

    <!-- Cupom e descontos -->
    <div v-if="hasDiscount" data-testid="ifood-ticket-discounts" :style="block">
      <div :style="sectionTitle">Cupom</div>
      <div
        v-if="amount(order.discountTotal)"
        data-testid="ifood-ticket-discount-total"
        :style="rowStyle"
      >
        <span :style="labelStyle">Valor do cupom</span>
        <span :style="{ fontWeight: 700 }">{{ amount(order.discountTotal) }}</span>
      </div>
      <div
        v-if="order.discountIfoodValue != null && Number(order.discountIfoodValue) > 0"
        data-testid="ifood-ticket-discount-ifood"
        :style="rowStyle"
      >
        <span :style="labelStyle">Bancado pelo iFood</span>
        <span>{{ amount(order.discountIfoodValue) }}</span>
      </div>
      <div
        v-if="order.discountMerchantValue != null && Number(order.discountMerchantValue) > 0"
        data-testid="ifood-ticket-discount-merchant"
        :style="rowStyle"
      >
        <span :style="labelStyle">Bancado pela Loja</span>
        <span>{{ amount(order.discountMerchantValue) }}</span>
      </div>
      <div v-if="discountOther != null" data-testid="ifood-ticket-discount-other" :style="rowStyle">
        <span :style="labelStyle">Patrocinador não identificado</span>
        <span>{{ amount(discountOther) }}</span>
      </div>
    </div>

    <!-- Entrega -->
    <div v-if="hasDeliveryInfo" data-testid="ifood-ticket-delivery" :style="block">
      <div :style="sectionTitle">Entrega</div>
      <div v-if="deliveredByLabel" data-testid="ifood-ticket-delivered-by" :style="rowStyle">
        <span :style="labelStyle">Responsável</span>
        <span :style="{ fontWeight: 600 }">{{ deliveredByLabel }}</span>
      </div>
      <div v-if="deliveryModeLabel" :style="rowStyle">
        <span :style="labelStyle">Modalidade</span>
        <span>{{ deliveryModeLabel }}</span>
      </div>
      <div v-if="formatDateTime(order.deliveryDateTime)" data-testid="ifood-ticket-delivery-datetime" :style="rowStyle">
        <span :style="labelStyle">Previsão</span>
        <span>{{ formatDateTime(order.deliveryDateTime) }}</span>
      </div>
      <div
        v-if="order.deliveryObservations"
        data-testid="ifood-ticket-delivery-observations"
        :style="{
          display: 'flex',
          gap: '8px',
          alignItems: 'flex-start',
          marginTop: '6px',
          padding: '8px 10px',
          borderRadius: '8px',
          background: UI.amberBg,
        }"
      >
        <UIIcon name="alert" :size="14" :style="{ color: UI.amber2, flexShrink: 0, marginTop: '2px' }" />
        <span :style="{ fontSize: '12.5px', color: UI.amber2, lineHeight: 1.5 }">
          <strong>Observações de entrega:</strong> {{ order.deliveryObservations }}
        </span>
      </div>
    </div>

    <!-- Retirada -->
    <div v-if="hasTakeoutInfo" data-testid="ifood-ticket-takeout" :style="block">
      <div :style="sectionTitle">Retirada</div>
      <div v-if="takeoutModeLabel" :style="rowStyle">
        <span :style="labelStyle">Modalidade</span>
        <span>{{ takeoutModeLabel }}</span>
      </div>
      <div v-if="formatDateTime(order.takeoutDateTime)" data-testid="ifood-ticket-takeout-datetime" :style="rowStyle">
        <span :style="labelStyle">Previsão</span>
        <span>{{ formatDateTime(order.takeoutDateTime) }}</span>
      </div>
    </div>

    <div
      v-if="unknownType"
      data-testid="ifood-ticket-unknown-type-warning"
      :style="{
        display: 'flex',
        gap: '8px',
        alignItems: 'flex-start',
        padding: '10px 12px',
        borderRadius: '9px',
        background: UI.bg,
      }"
    >
      <UIIcon name="info" :size="14" :style="{ color: UI.textSub, flexShrink: 0, marginTop: '2px' }" />
      <span :style="{ fontSize: '12.5px', color: UI.textSub, lineHeight: 1.5 }">
        O tipo deste pedido não foi informado pelo iFood (pedido importado antes desta versão).
        Confira no app do iFood se ele é de <strong>entrega</strong> ou de <strong>retirada</strong>
        antes de escolher a ação.
      </span>
    </div>

    <div
      v-if="actionSuccess"
      data-testid="ifood-ticket-success"
      :style="{ fontSize: '12.5px', color: UI.emerald2, marginTop: '12px' }"
    >
      {{ actionSuccess }}
    </div>
    <div
      v-if="actionError"
      data-testid="ifood-ticket-error"
      :style="{ fontSize: '12.5px', color: UI.rose, marginTop: '12px' }"
    >
      {{ actionError }}
    </div>

    <template #footer>
      <UIBtn variant="ghost" data-testid="ifood-ticket-close" @click="emit('close')">Fechar</UIBtn>
      <UIBtn
        v-if="actionable"
        variant="secondary"
        data-testid="ifood-ticket-confirm"
        :disabled="runningAction !== null || doneActions.includes('confirm')"
        @click="confirmOrder"
      >
        {{ runningAction === 'confirm' ? 'Confirmando…' : 'Confirmar pedido' }}
      </UIBtn>
      <UIBtn
        v-if="canReadyToPickup"
        variant="primary"
        data-testid="ifood-ticket-ready"
        :disabled="runningAction !== null || doneActions.includes('ready')"
        @click="readyToPickup"
      >
        {{ runningAction === 'ready' ? 'Enviando…' : 'Pronto para retirada' }}
      </UIBtn>
      <UIBtn
        v-if="canDispatch"
        variant="primary"
        data-testid="ifood-ticket-dispatch"
        :disabled="runningAction !== null || doneActions.includes('dispatch')"
        @click="dispatchOrder"
      >
        {{ runningAction === 'dispatch' ? 'Despachando…' : 'Despachar' }}
      </UIBtn>
    </template>
  </UIModal>
</template>
