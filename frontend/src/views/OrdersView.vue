<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { usePolling } from '@/composables/usePolling'
import { REFRESH_INTERVAL_MS } from '@/utils/refresh'
import { useAuthStore } from '@/stores/authStore'
import { useOrderStore } from '@/stores/orderStore'
import { useCustomerStore } from '@/stores/customerStore'
import { useProductStore } from '@/stores/productStore'
import { useIngredientStore } from '@/stores/ingredientStore'
import { useFeeStore } from '@/stores/feeStore'
import {
  UI,
  UITopbar,
  UIBtn,
  UIPill,
  UISearch,
  UIField,
  UIInput,
  UISelect,
  UICombobox,
  UIModal,
  UIIcon,
  UIRowAction,
  brl,
} from '@/design'
import type {
  OrderRequest,
  OrderResponse,
  OrderItemRequest,
  OrderItemResponse,
  OrderItemExtraIngredientRequest,
  OrderItemExtraIngredientResponse,
  OrderItemUnmatchedSubItemResponse,
  OrderOrigin,
} from '@/types/Order'
import type { IngredientRequest } from '@/types/Ingredient'
import type { IncludeResponse } from '@/types/Product'
import type { OrderFichaLineRequest } from '@/types/OrderFicha'
import { includeService } from '@/services/includeService'
import { orderFichaService } from '@/services/orderFichaService'
import { useToast } from '@/composables/useToast'
import IfoodOrderTicketModal from '@/components/IfoodOrderTicketModal.vue'

const router = useRouter()
const { showToast } = useToast()
const authStore = useAuthStore()
const orderStore = useOrderStore()
const customerStore = useCustomerStore()
const productStore = useProductStore()
const ingredientStore = useIngredientStore()
const feeStore = useFeeStore()

const showHoursBanner = computed(() => {
  const u = authStore.currentUser
  return !!u?.anotaAiApiKey && (!u.openingHours || u.openingHours.length === 0)
})

function goToHoursSettings() {
  router.push({ path: '/settings', query: { section: 'horario' } })
}

const showModal = ref(false)
const showDetailModal = ref(false)
const loadingDetail = ref(false)
// Background refresh of the detail: never replaces content already on screen.
const detailPending = ref(false)
const selectedOrder = ref<OrderResponse | null>(null)
// Detail request sequence, guarding against out-of-order responses.
let detailRequestToken = 0
const confirmDeleteId = ref<string | null>(null)
const editingOrderId = ref<string | null>(null)

const form = ref<OrderRequest>({
  customerId: '',
  customerName: '',
  origin: 'MENUBANK',
  items: [{ productId: '', quantity: 1, extraIngredients: [] }],
})

// Erros do modal de pedido: validação client-side do cliente + falha do submit.
const customerError = ref<string | null>(null)
const submitError = ref<string | null>(null)

const customerOptions = computed(() =>
  customerStore.items.map((c) => ({ id: c.id, label: c.name })),
)

// Ficha técnica por produto (cache local): os insumos (PACKAGING + legados sem kind)
// acompanham o item do pedido por padrão e o operador pode desmarcar os que ficaram
// de fora. INGREDIENT não é puxado — só entra no custo quando pedido como extra.
const includesByProduct = ref<Record<string, IncludeResponse[]>>({})

async function loadIncludes(productId: string) {
  if (!productId || includesByProduct.value[productId]) return
  try {
    includesByProduct.value[productId] = await includeService.findByProductId(productId)
  } catch {
    /* ficha indisponível: o pedido segue sem a lista de insumos */
  }
}

watch(
  () => form.value.items.map((item) => item.productId),
  (ids) => ids.forEach((id) => void loadIncludes(id)),
  { immediate: true },
)

function insumosOf(item: OrderItemRequest): IncludeResponse[] {
  return (includesByProduct.value[item.productId] ?? []).filter(
    (inc) => inc.kind !== 'INGREDIENT',
  )
}

function isInsumoIncluded(item: OrderItemRequest, includeId: string): boolean {
  return !(item.excludedIncludeIds ?? []).includes(includeId)
}

function toggleInsumo(item: OrderItemRequest, includeId: string, included: boolean) {
  const excluded = item.excludedIncludeIds ?? (item.excludedIncludeIds = [])
  if (included) {
    item.excludedIncludeIds = excluded.filter((id) => id !== includeId)
  } else if (!excluded.includes(includeId)) {
    excluded.push(includeId)
  }
}

function ensureExtras(item: OrderItemRequest): OrderItemExtraIngredientRequest[] {
  if (!item.extraIngredients) item.extraIngredients = []
  return item.extraIngredients
}

// ---------------------------------------------------------------------------
// Configurar pedidos — ficha do pedido
//
// Insumos cobrados UMA vez por pedido (sacola, guardanapo), independentemente de
// quantos itens ele tenha. A ficha técnica do produto continua sendo por item e
// multiplicada pela quantidade — correto para copo/colher, errado para a sacola.
// Mover a sacola da ficha do produto para cá é decisão do lojista: nada é migrado
// automaticamente, senão o custo dele seria reescrito sem pedir.
// ---------------------------------------------------------------------------
const showFichaModal = ref(false)
const loadingFicha = ref(false)
const savingFicha = ref(false)
const fichaError = ref<string | null>(null)
const fichaLines = ref<OrderFichaLineRequest[]>([])

const ingredientOptions = computed(() =>
  ingredientStore.items.map((i) => ({ id: i.id, label: `${i.name} (${i.unit})` })),
)

function ingredientById(id: string) {
  return ingredientStore.items.find((i) => i.id === id)
}

/** Custo por pedido calculado ao vivo, para o lojista ver o efeito antes de salvar. */
const fichaTotalCost = computed(() =>
  fichaLines.value.reduce((acc, line) => {
    const ingredient = ingredientById(line.ingredientId)
    return acc + (ingredient ? Number(ingredient.costPerUnit) * Number(line.quantity || 0) : 0)
  }, 0),
)

async function openFichaModal() {
  showFichaModal.value = true
  fichaError.value = null
  loadingFicha.value = true
  try {
    const ficha = await orderFichaService.find()
    fichaLines.value = ficha.lines.map((l) => ({
      ingredientId: l.ingredientId,
      quantity: Number(l.quantity),
    }))
  } catch {
    fichaError.value = 'Não foi possível carregar a ficha do pedido.'
  } finally {
    loadingFicha.value = false
  }
}

function closeFichaModal() {
  showFichaModal.value = false
  fichaError.value = null
  closeCreateInsumo()
}

function addFichaLine() {
  fichaLines.value.push({ ingredientId: '', quantity: 1 })
}

function removeFichaLine(index: number) {
  fichaLines.value.splice(index, 1)
}

/** Preenche a quantidade sugerida do ingrediente ao selecioná-lo. */
function onFichaIngredientChange(index: number) {
  const line = fichaLines.value[index]
  if (!line) return
  const ingredient = ingredientById(line.ingredientId)
  if (ingredient?.defaultQuantity) line.quantity = Number(ingredient.defaultQuantity)
}

// ---------------------------------------------------------------------------
// Criação de insumo sem sair do modal
//
// Quando o insumo ainda não existe, o lojista pode criá-lo direto na linha:
// um formulário curto (nome, unidade, custo) reaproveita a mesma action do
// cadastro de ingredientes. Ao criar, o insumo já entra selecionado na linha.
// Só um formulário fica aberto por vez (creatingLineIndex).
// ---------------------------------------------------------------------------
const creatingLineIndex = ref<number | null>(null)
const creatingInsumo = ref(false)
const newInsumoError = ref<string | null>(null)
const newInsumo = ref<IngredientRequest>({ name: '', unit: '', costPerUnit: 0 })

function toggleCreateInsumo(index: number) {
  if (creatingLineIndex.value === index) {
    closeCreateInsumo()
    return
  }
  creatingLineIndex.value = index
  newInsumoError.value = null
  newInsumo.value = { name: '', unit: '', costPerUnit: 0 }
}

function closeCreateInsumo() {
  creatingLineIndex.value = null
  newInsumoError.value = null
}

async function submitNewInsumo() {
  const index = creatingLineIndex.value
  if (index === null) return
  newInsumoError.value = null

  const name = newInsumo.value.name.trim()
  const unit = newInsumo.value.unit.trim()
  const costPerUnit = Number(newInsumo.value.costPerUnit)
  if (!name || !unit) {
    newInsumoError.value = 'Informe o nome e a unidade do insumo.'
    return
  }
  if (!(costPerUnit >= 0)) {
    newInsumoError.value = 'Informe um custo válido.'
    return
  }

  creatingInsumo.value = true
  try {
    const created = await ingredientStore.create({ name, unit, costPerUnit })
    const line = fichaLines.value[index]
    if (line) {
      line.ingredientId = created.id
      onFichaIngredientChange(index)
    }
    closeCreateInsumo()
  } catch {
    newInsumoError.value = 'Não foi possível criar o insumo.'
  } finally {
    creatingInsumo.value = false
  }
}

async function saveFicha() {
  fichaError.value = null

  if (fichaLines.value.some((l) => !l.ingredientId)) {
    fichaError.value = 'Selecione o insumo em todas as linhas.'
    return
  }
  if (fichaLines.value.some((l) => !(Number(l.quantity) > 0))) {
    fichaError.value = 'A quantidade deve ser maior que zero.'
    return
  }
  const ids = fichaLines.value.map((l) => l.ingredientId)
  if (new Set(ids).size !== ids.length) {
    fichaError.value = 'Cada insumo pode aparecer só uma vez — some a quantidade numa linha só.'
    return
  }

  savingFicha.value = true
  try {
    await orderFichaService.replace({
      lines: fichaLines.value.map((l) => ({
        ingredientId: l.ingredientId,
        quantity: Number(l.quantity),
      })),
    })
    showFichaModal.value = false
    showToast('Ficha do pedido salva. Vale para os próximos pedidos.', 'success')
  } catch {
    fichaError.value = 'Não foi possível salvar a ficha do pedido.'
  } finally {
    savingFicha.value = false
  }
}

function formatDateTime(s: string): string {
  return new Date(s).toLocaleString('pt-BR')
}
function timeOf(iso: string): string {
  const d = new Date(iso)
  const date = d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
  const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  return `${date} ${time}`
}
/**
 * Margem do pedido. A fonte da verdade é o marginPct do backend, apurado sobre
 * (totalValue - deliveryFee) — a mesma base do lucro. Null quando o backend não
 * consegue apurá-la (subtotal zero); nesse caso não exibimos 0%, que enganaria.
 */
function marginLabel(o: OrderResponse): string {
  if (o.marginPct == null) return '—'
  return Number(o.marginPct).toFixed(1).replace('.', ',') + '%'
}

/**
 * Valor pago pelo cliente por um adicional — não confundir com o custo de produção.
 * Nulo/ausente = preço desconhecido (pedido manual/iFood ou importado antes da V16);
 * 0 = complemento base, incluso no produto sem valor agregado.
 */
function paidLabel(ex: OrderItemExtraIngredientResponse): string {
  if (ex.salePriceTotal == null) return '—'
  const paid = Number(ex.salePriceTotal)
  if (paid === 0) return 'Incluso'
  return brl(paid)
}

/**
 * Rótulo do adicional na composição do item. O sufixo "(extra)" só faz sentido
 * quando o cliente pagou a mais por ele: usa o preço de venda persistido
 * (`salePriceTotal`), nunca o custo de produção. Preço 0/nulo (complemento base,
 * ou pedido sem preço conhecido) mostra apenas o nome do ingrediente.
 */
function extraLabel(ex: OrderItemExtraIngredientResponse): string {
  const paid = ex.salePriceTotal
  if (paid == null || Number(paid) <= 0) return ex.ingredientName
  return `${ex.ingredientName} (extra)`
}

/**
 * Valor cobrado do cliente por este item: o preço do produto (unitPrice × quantidade)
 * somado ao que foi pago pelos adicionais. Só somam os extras com preço de venda
 * conhecido e positivo (`salePriceTotal > 0`); complementos base (preço 0) e extras
 * sem preço (nulo/legado) não agregam valor, mantendo o item no preço base.
 */
function itemChargedValue(item: OrderItemResponse): number {
  const base = Number(item.unitPrice) * Number(item.quantity)
  const paidExtras = (item.extraIngredients ?? []).reduce((sum, ex) => {
    const paid = Number(ex.salePriceTotal)
    return ex.salePriceTotal != null && paid > 0 ? sum + paid : sum
  }, 0)
  return base + paidExtras
}

const STATUS_PILL: Record<string, { color: 'amber' | 'emerald' | 'rose' | 'blue' | 'gray'; label: string }> = {
  PENDING: { color: 'amber', label: 'Pendente' },
  READY: { color: 'blue', label: 'Pronto' },
  DELIVERED: { color: 'emerald', label: 'Entregue' },
  PAID: { color: 'emerald', label: 'Pago' },
  CANCELLED: { color: 'rose', label: 'Cancelado' },
  TEST: { color: 'gray', label: 'Teste' },
}

function originLabel(o: OrderOrigin | undefined): string {
  if (o === 'ANOTA_AI') return 'Anota.AI'
  if (o === 'IFOOD') return 'iFood'
  return 'MenuBank'
}
function originColor(o: OrderOrigin | undefined): 'blue' | 'rose' | 'violet' {
  if (o === 'ANOTA_AI') return 'blue'
  if (o === 'IFOOD') return 'rose'
  return 'violet'
}

function orderTotalCost(o: OrderResponse): number {
  return o.items.reduce((s, it) => s + (Number(it.totalCost) || 0), 0)
}

const counts = computed(() => {
  const items = orderStore.items
  return {
    total: orderStore.totalElements,
    pending: items.filter((o) => o.status === 'PENDING').length,
    paid: items.filter((o) => o.status === 'PAID').length,
    cancelled: items.filter((o) => o.status === 'CANCELLED').length,
  }
})

const filterStatus = ref<'todos' | 'PENDING' | 'PAID' | 'CANCELLED'>('todos')
const filteredItems = computed(() => {
  if (filterStatus.value === 'todos') return orderStore.items
  return orderStore.items.filter((o) => o.status === filterStatus.value)
})

let searchDebounce: ReturnType<typeof setTimeout> | null = null
function onSearchInput(value: string) {
  orderStore.search = value
  if (searchDebounce) clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => {
    orderStore.fetchPage({ search: value, page: 0 })
  }, 300)
}
function onSortChange(value: string) {
  orderStore.fetchPage({ sort: value, page: 0 })
}
function onPageChange(p: number) {
  if (p < 0 || p >= orderStore.totalPages) return
  orderStore.fetchPage({ page: p })
}

function openCreate() {
  editingOrderId.value = null
  customerError.value = null
  submitError.value = null
  form.value = {
    customerId: '',
    customerName: '',
    origin: 'MENUBANK',
    feeId: '',
    items: [{ productId: '', quantity: 1, extraIngredients: [], excludedIncludeIds: [] }],
  }
  showModal.value = true
}
function openEdit(o: OrderResponse) {
  editingOrderId.value = o.id
  customerError.value = null
  submitError.value = null
  form.value = {
    customerId: o.customerId,
    customerName: o.customerName,
    status: o.status,
    feeId: o.feeId ?? '',
    origin: o.origin,
    items: o.items.map((item) => ({
      productId: item.productId,
      quantity: item.quantity,
      extraIngredients: (item.extraIngredients ?? []).map((extra) => ({
        ingredientId: extra.ingredientId,
        quantity: extra.quantity,
      })),
      excludedIncludeIds: [...(item.excludedIncludeIds ?? [])],
    })),
  }
  showModal.value = true
}
function closeModal() {
  showModal.value = false
  editingOrderId.value = null
  customerError.value = null
  submitError.value = null
}
function addItem() {
  form.value.items.push({ productId: '', quantity: 1, extraIngredients: [], excludedIncludeIds: [] })
}
function removeItem(i: number) {
  if (form.value.items.length > 1) form.value.items.splice(i, 1)
}
function addExtra(i: number) {
  ensureExtras(form.value.items[i]!).push({ ingredientId: '', quantity: 1 })
}
function removeExtra(i: number, j: number) {
  ensureExtras(form.value.items[i]!).splice(j, 1)
}
function onExtraChange(extra: OrderItemExtraIngredientRequest, id: string) {
  extra.ingredientId = id
  const ing = ingredientStore.items.find((x) => x.id === id)
  extra.quantity = ing?.defaultQuantity ?? 1
}
async function handleSubmit() {
  submitError.value = null
  const customerName = (form.value.customerName ?? '').trim()
  if (!form.value.customerId && !customerName) {
    customerError.value = 'Cliente é obrigatório'
    return
  }
  customerError.value = null
  try {
    // Envia exatamente uma referência de cliente: id (selecionado) ou nome (fluxo rápido).
    const payload = {
      ...form.value,
      customerId: form.value.customerId || undefined,
      customerName: form.value.customerId ? undefined : customerName,
      feeId: form.value.feeId || undefined,
    }
    if (editingOrderId.value) {
      await orderStore.update(editingOrderId.value, payload)
    } else {
      await orderStore.create(payload)
      showToast('Pedido criado com sucesso!')
    }
    closeModal()
  } catch {
    submitError.value = orderStore.error ?? 'Erro ao salvar o pedido'
  }
}
async function viewDetail(o: OrderResponse) {
  // Monotonic token: only the newest request may write to the modal. Without it
  // a slow response for the previous order overwrites the current one.
  const token = ++detailRequestToken
  showDetailModal.value = true
  cancelCreateIngredient()
  // The list row is already a complete OrderResponse: render it at once and let
  // the fetched detail replace it, so switching orders never flashes a spinner.
  selectedOrder.value = o
  loadingDetail.value = false
  detailPending.value = true
  try {
    const detail = await orderStore.findById(o.id)
    if (token !== detailRequestToken) return
    selectedOrder.value = detail
  } catch {
    if (token !== detailRequestToken) return
    showDetailModal.value = false
    selectedOrder.value = null
  } finally {
    if (token === detailRequestToken) detailPending.value = false
  }
}
// --- Comanda do iFood ----------------------------------------------------
// A comanda vive em componente próprio (IfoodOrderTicketModal); aqui só abrimos,
// fechamos e recarregamos a lista quando uma ação muda o status do pedido.
const ticketOrder = ref<OrderResponse | null>(null)

function openTicket(o: OrderResponse) {
  ticketOrder.value = o
}
function closeTicket() {
  ticketOrder.value = null
}
function onTicketUpdated() {
  orderStore.fetchPage({}, true).catch(() => {})
}

function closeDetail() {
  // Invalidate the in-flight request: it must not reopen or repopulate the modal.
  detailRequestToken++
  detailPending.value = false
  loadingDetail.value = false
  showDetailModal.value = false
  selectedOrder.value = null
  cancelCreateIngredient()
}

// --- Cadastro de ingrediente faltante (subItem não-casado) ---------------
const creatingIngredientFor = ref<string | null>(null)
const savingIngredient = ref(false)
const creatingIngredientError = ref<string | null>(null)
const ingredientForm = ref<{
  name: string
  unit: string
  costPerUnit: number | null
  defaultQuantity: number | null
}>({ name: '', unit: '', costPerUnit: null, defaultQuantity: null })

function startCreateIngredient(u: OrderItemUnmatchedSubItemResponse) {
  creatingIngredientFor.value = u.id
  creatingIngredientError.value = null
  ingredientForm.value = { name: u.rawName, unit: '', costPerUnit: null, defaultQuantity: null }
}

function cancelCreateIngredient() {
  creatingIngredientFor.value = null
  creatingIngredientError.value = null
}

async function submitCreateIngredient() {
  const name = ingredientForm.value.name.trim()
  const unit = ingredientForm.value.unit.trim()
  const cost = ingredientForm.value.costPerUnit
  if (!name || !unit || cost == null || Number.isNaN(Number(cost))) {
    creatingIngredientError.value = 'Preencha nome, unidade e custo.'
    return
  }
  const request: IngredientRequest = { name, unit, costPerUnit: Number(cost) }
  const qty = ingredientForm.value.defaultQuantity
  if (qty != null && Number(qty) > 0) request.defaultQuantity = Number(qty)

  savingIngredient.value = true
  try {
    await ingredientStore.create(request)
    creatingIngredientFor.value = null
    // Recarrega o pedido: o backend filtra o subItem já casado e o botão some.
    if (selectedOrder.value) {
      selectedOrder.value = await orderStore.findById(selectedOrder.value.id)
    }
    // Mantém os dropdowns de ingredientes atualizados para novos pedidos.
    void ingredientStore.fetchAll(true)
    showToast('Ingrediente cadastrado com sucesso!')
  } catch {
    creatingIngredientError.value = ingredientStore.error ?? 'Erro ao cadastrar ingrediente'
  } finally {
    savingIngredient.value = false
  }
}
async function handleDelete() {
  if (!confirmDeleteId.value) return
  try {
    await orderStore.remove(confirmDeleteId.value)
  } catch {
    /* store has error */
  }
  confirmDeleteId.value = null
}

const cols = '70px 1.5fr 100px 110px 110px 110px 90px 130px'
// Fixed columns + gaps + room for the fr column; below this the table scrolls horizontally.
const tableMinWidth = '920px'

const statusPills = computed(() => [
  { id: 'todos', label: 'Todos', count: counts.value.total, dot: undefined },
  { id: 'PENDING', label: 'Pendentes', count: counts.value.pending, dot: UI.amber },
  { id: 'PAID', label: 'Pagos', count: counts.value.paid, dot: UI.emerald2 },
  { id: 'CANCELLED', label: 'Cancelados', count: counts.value.cancelled, dot: UI.rose },
])

onMounted(() => {
  orderStore.fetchPage({ page: 0, search: '' })
  customerStore.fetchAll()
  productStore.fetchAll()
  ingredientStore.fetchAll()
  feeStore.fetchAll()
})

// Atualização automática a cada 10 minutos, em segundo plano (silent) para não piscar.
usePolling(() => { orderStore.fetchPage({}, true).catch(() => {}) }, REFRESH_INTERVAL_MS)
</script>

<template>
  <div style="display: flex; flex-direction: column; flex: 1">
    <UITopbar
      title="Pedidos"
      :subtitle="`${counts.total} pedidos no total`"
    >
      <template #actions>
        <UIBtn
          icon="gear"
          variant="secondary"
          data-testid="configure-orders-button"
          @click="openFichaModal"
        >
          Configurar pedidos
        </UIBtn>
        <UIBtn icon="plus" variant="dark" data-testid="new-order-button" @click="openCreate">
          Novo Pedido
        </UIBtn>
      </template>
    </UITopbar>

    <div class="view-content">
      <!-- Opening hours onboarding banner -->
      <div
        v-if="showHoursBanner"
        :style="{
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          padding: '12px 16px',
          background: UI.amberBg,
          color: UI.amber2,
          borderRadius: '10px',
          fontSize: '13px',
          marginBottom: '12px',
        }"
      >
        <UIIcon name="clock" :size="16" style="flex-shrink: 0" />
        <span style="flex: 1">
          Configure os horários de funcionamento para ativar a importação automática de pedidos.
        </span>
        <button
          :style="{
            background: UI.amber2,
            color: UI.amberBg,
            border: 'none',
            borderRadius: '7px',
            padding: '5px 12px',
            fontSize: '12.5px',
            fontWeight: 600,
            cursor: 'pointer',
            whiteSpace: 'nowrap',
          }"
          @click="goToHoursSettings"
        >
          Configurar horários
        </button>
      </div>

      <div
        v-if="orderStore.error"
        :style="{
          padding: '10px 14px',
          background: UI.roseBg,
          color: UI.rose2,
          borderRadius: '10px',
          fontSize: '13px',
          marginBottom: '12px',
        }"
      >
        {{ orderStore.error }}
      </div>

      <!-- Filter row -->
      <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 18px; flex-wrap: wrap">
        <div
          :style="{
            display: 'flex',
            background: UI.panel,
            border: `1px solid ${UI.border}`,
            borderRadius: '10px',
            padding: '4px',
            gap: '2px',
          }"
        >
          <div
            v-for="s in statusPills"
            :key="s.id"
            :style="{
              display: 'flex',
              alignItems: 'center',
              gap: '7px',
              padding: '6px 12px',
              borderRadius: '7px',
              fontSize: '12.5px',
              fontWeight: 500,
              background: filterStatus === s.id ? UI.bg : 'transparent',
              color: filterStatus === s.id ? UI.text : UI.textSub,
              cursor: 'pointer',
            }"
            @click="filterStatus = (s.id as typeof filterStatus)"
          >
            <span
              v-if="s.dot"
              :style="{ width: '6px', height: '6px', borderRadius: '3px', background: s.dot }"
            />
            {{ s.label }}
            <span
              :style="{
                fontSize: '10.5px',
                padding: '1px 6px',
                borderRadius: '4px',
                background: filterStatus === s.id ? UI.panel : UI.bg,
                color: UI.textMute,
                fontWeight: 600,
              }"
            >
              {{ s.count }}
            </span>
          </div>
        </div>
        <div style="flex: 1" />
        <UISearch
          :model-value="orderStore.search"
          placeholder="Buscar por cliente…"
          :width="260"
          @update:model-value="onSearchInput"
        />
        <UISelect
          :model-value="orderStore.sort"
          :width="220"
          @update:model-value="onSortChange"
        >
          <option value="dateTime,desc">Data: mais recentes</option>
          <option value="dateTime,asc">Data: mais antigos</option>
          <option value="totalValue,desc">Valor: maior → menor</option>
          <option value="totalValue,asc">Valor: menor → maior</option>
        </UISelect>
      </div>

      <!-- Table -->
      <div
        :style="{
          background: UI.panel,
          border: `1px solid ${UI.border}`,
          borderRadius: '14px',
          overflow: 'hidden',
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          minHeight: 0,
        }"
      >
        <div class="table-scroll">
        <div :style="{ minWidth: tableMinWidth }">
        <div
          class="table-sticky-header"
          :style="{
            display: 'grid',
            gridTemplateColumns: cols,
            gap: '12px',
            padding: '12px 18px',
            background: UI.bgSoft,
            borderBottom: `1px solid ${UI.border}`,
            fontSize: '10.5px',
            color: UI.textSub,
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: '0.5px',
            flexShrink: 0,
          }"
        >
          <span>Hora</span>
          <span>Cliente</span>
          <span>Canal</span>
          <span>Status</span>
          <span style="text-align: right">Valor</span>
          <span style="text-align: right">Lucro</span>
          <span style="text-align: right">Margem</span>
          <span style="text-align: right">Ações</span>
        </div>

        <div>
          <div
            v-if="orderStore.loading"
            :style="{
              padding: '32px',
              textAlign: 'center',
              color: UI.textMute,
            }"
          >
            Carregando…
          </div>
          <div
            v-else-if="!filteredItems.length"
            :style="{
              padding: '60px 32px',
              textAlign: 'center',
              color: UI.textMute,
              fontSize: '13px',
            }"
          >
            Nenhum pedido encontrado.
          </div>
          <div
            v-for="(o, i) in filteredItems"
            v-else
            :key="o.id"
            class="ui-row"
            :style="{
              display: 'grid',
              gridTemplateColumns: cols,
              gap: '12px',
              padding: '12px 18px',
              borderBottom: i === filteredItems.length - 1 ? 'none' : `1px solid ${UI.borderSub}`,
              fontSize: '13px',
              color: UI.text,
              alignItems: 'center',
            }"
          >
            <span :style="{ color: UI.textSub, fontVariantNumeric: 'tabular-nums' }">{{ timeOf(o.dateTime) }}</span>
            <span style="min-width: 0">
              <span
                :style="{
                  display: 'block',
                  fontWeight: 600,
                  color: UI.text,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }"
              >
                {{ o.customerName }}
              </span>
              <span :style="{ display: 'block', fontSize: '11px', color: UI.textMute }">
                {{ o.items.length }} {{ o.items.length === 1 ? 'item' : 'itens' }}
              </span>
            </span>
            <span>
              <UIPill :color="originColor(o.origin)" size="sm">{{ originLabel(o.origin) }}</UIPill>
            </span>
            <span>
              <UIPill :color="STATUS_PILL[o.status]?.color ?? 'gray'" dot>
                {{ STATUS_PILL[o.status]?.label ?? o.status }}
              </UIPill>
            </span>
            <span
              :style="{
                textAlign: 'right',
                fontWeight: 600,
                color: UI.text,
                fontVariantNumeric: 'tabular-nums',
              }"
            >
              {{ brl(Number(o.totalValue)) }}
            </span>
            <span
              :style="{
                textAlign: 'right',
                color: UI.emerald2,
                fontWeight: 600,
                fontVariantNumeric: 'tabular-nums',
              }"
            >
              {{ brl(Number(o.estimatedProfit)) }}
            </span>
            <span
              :data-testid="`order-${o.id}-margin`"
              :style="{
                textAlign: 'right',
                color: UI.textSub,
                fontVariantNumeric: 'tabular-nums',
              }"
            >
              {{ marginLabel(o) }}
            </span>
            <span style="display: flex; gap: 5px; justify-content: flex-end">
              <UIRowAction
                v-if="o.origin === 'IFOOD'"
                icon="receipt"
                color="emerald"
                label="Comanda"
                :data-testid="`order-${o.id}-ticket-button`"
                @click="openTicket(o)"
              />
              <UIRowAction
                icon="eye"
                color="gray"
                label="Detalhes"
                :data-testid="`order-${o.id}-detail-button`"
                @click="viewDetail(o)"
              />
              <UIRowAction
                icon="edit"
                color="blue"
                label="Editar"
                :data-testid="`order-${o.id}-edit-button`"
                @click="openEdit(o)"
              />
              <UIRowAction
                icon="trash"
                color="rose"
                label="Excluir"
                :data-testid="`order-${o.id}-delete-button`"
                @click="confirmDeleteId = o.id"
              />
            </span>
          </div>
        </div>
        </div>
        </div>

        <div
          :style="{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '12px 18px',
            borderTop: `1px solid ${UI.border}`,
            background: UI.bgSoft,
            fontSize: '12px',
            color: UI.textSub,
            flexShrink: 0,
          }"
        >
          <span>
            Página {{ orderStore.page + 1 }} de {{ Math.max(orderStore.totalPages, 1) }}
            · {{ orderStore.totalElements }} pedidos
          </span>
          <div style="display: flex; gap: 6px; align-items: center">
            <UIBtn
              size="sm"
              icon="chevLeft"
              variant="secondary"
              :disabled="orderStore.page === 0 || orderStore.loading"
              @click="onPageChange(orderStore.page - 1)"
            >
              Anterior
            </UIBtn>
            <span
              :style="{
                padding: '5px 10px',
                background: UI.text,
                color: '#fff',
                borderRadius: '6px',
                fontSize: '11.5px',
                fontWeight: 600,
              }"
            >
              {{ orderStore.page + 1 }}
            </span>
            <UIBtn
              size="sm"
              icon="chevRight"
              variant="secondary"
              :disabled="orderStore.page >= orderStore.totalPages - 1 || orderStore.loading"
              @click="onPageChange(orderStore.page + 1)"
            >
              Próximo
            </UIBtn>
          </div>
        </div>
      </div>
    </div>

    <!-- Configurar pedidos — ficha do pedido (insumos cobrados uma vez por pedido) -->
    <UIModal
      v-if="showFichaModal"
      title="Configurar pedidos"
      subtitle="Insumos cobrados uma vez por pedido, não por item"
      :width="640"
      data-testid="order-ficha-modal"
      @close="closeFichaModal"
    >
      <div v-if="loadingFicha" style="padding: 40px; text-align: center; color: #94a3b8">
        Carregando…
      </div>
      <div v-else style="display: flex; flex-direction: column; gap: 16px">
        <div
          data-testid="order-ficha-intro"
          :style="{
            background: UI.bg,
            border: `1px solid ${UI.border}`,
            borderRadius: '10px',
            padding: '12px',
            fontSize: '12.5px',
            color: UI.textSub,
            display: 'flex',
            gap: '10px',
          }"
        >
          <UIIcon name="info" :size="16" />
          <div>
            Insumos cobrados <strong>uma vez por pedido</strong> (ex.: sacola de entrega),
            somados ao custo de todo pedido.
          </div>
        </div>

        <div
          v-if="fichaError"
          data-testid="order-ficha-error"
          :style="{
            background: UI.roseBg,
            color: UI.rose2,
            border: `1px solid ${UI.rose}22`,
            borderRadius: '9px',
            padding: '10px 12px',
            fontSize: '12.5px',
          }"
        >
          {{ fichaError }}
        </div>

        <div style="display: flex; flex-direction: column; gap: 8px">
          <div
            v-for="(line, i) in fichaLines"
            :key="i"
            style="display: flex; flex-direction: column; gap: 8px"
          >
            <div style="display: flex; gap: 8px; align-items: center">
              <UISelect
                :model-value="line.ingredientId"
                width="100%"
                :data-testid="`order-ficha-line-${i}-ingredient-select`"
                @update:model-value="
                  (v) => {
                    line.ingredientId = v as string
                    onFichaIngredientChange(i)
                  }
                "
              >
                <option value="">Selecione o insumo…</option>
                <option v-for="opt in ingredientOptions" :key="opt.id" :value="opt.id">
                  {{ opt.label }}
                </option>
              </UISelect>
              <UIInput
                v-model.number="line.quantity"
                type="number"
                step="any"
                :width="90"
                :data-testid="`order-ficha-line-${i}-quantity-input`"
              />
              <span
                :style="{ fontSize: '12px', color: UI.textMute, minWidth: '64px', textAlign: 'right' }"
                :data-testid="`order-ficha-line-${i}-cost`"
              >
                {{
                  brl(
                    Number(ingredientById(line.ingredientId)?.costPerUnit ?? 0) *
                      Number(line.quantity || 0),
                  )
                }}
              </span>
              <div
                :style="{
                  width: '30px',
                  height: '30px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  color: creatingLineIndex === i ? UI.text : UI.textMute,
                }"
                title="Criar novo insumo"
                :data-testid="`order-ficha-line-${i}-create-toggle`"
                @click="toggleCreateInsumo(i)"
              >
                <UIIcon name="plus" :size="15" />
              </div>
              <div
                :style="{
                  width: '30px',
                  height: '30px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  color: UI.rose2,
                }"
                :data-testid="`order-ficha-line-${i}-remove-button`"
                @click="removeFichaLine(i)"
              >
                <UIIcon name="trash" :size="15" />
              </div>
            </div>

            <!-- Formulário inline: cria um insumo e o seleciona nesta linha. -->
            <div
              v-if="creatingLineIndex === i"
              :style="{
                display: 'flex',
                flexDirection: 'column',
                gap: '8px',
                padding: '10px',
                background: UI.bg,
                border: `1px solid ${UI.border}`,
                borderRadius: '10px',
              }"
            >
              <span :style="{ fontSize: '12px', color: UI.textSub, fontWeight: 600 }">
                Novo insumo
              </span>
              <div style="display: flex; gap: 8px; align-items: center">
                <UIInput
                  v-model="newInsumo.name"
                  placeholder="Nome (ex.: Sacola)"
                  width="100%"
                  data-testid="order-ficha-create-name"
                />
                <UIInput
                  v-model="newInsumo.unit"
                  placeholder="Un."
                  :width="80"
                  data-testid="order-ficha-create-unit"
                />
                <UIInput
                  v-model.number="newInsumo.costPerUnit"
                  type="number"
                  step="any"
                  placeholder="Custo"
                  :width="90"
                  data-testid="order-ficha-create-cost"
                />
              </div>
              <div
                v-if="newInsumoError"
                data-testid="order-ficha-create-error"
                :style="{
                  color: UI.rose2,
                  fontSize: '12px',
                }"
              >
                {{ newInsumoError }}
              </div>
              <div style="display: flex; gap: 8px; justify-content: flex-end">
                <UIBtn
                  size="sm"
                  variant="secondary"
                  data-testid="order-ficha-create-cancel"
                  @click="closeCreateInsumo"
                >
                  Cancelar
                </UIBtn>
                <UIBtn
                  size="sm"
                  variant="primary"
                  icon="check"
                  :disabled="creatingInsumo"
                  data-testid="order-ficha-create-submit"
                  @click="submitNewInsumo"
                >
                  Criar e selecionar
                </UIBtn>
              </div>
            </div>
          </div>

          <div v-if="!fichaLines.length" :style="{ fontSize: '12.5px', color: UI.textMute }">
            Nenhum insumo por pedido configurado — os pedidos seguem custando o mesmo de hoje.
          </div>

          <UIBtn
            icon="plus"
            variant="secondary"
            data-testid="order-ficha-add-line-button"
            @click="addFichaLine"
          >
            Adicionar insumo
          </UIBtn>
        </div>

        <div
          :style="{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '12px',
            background: UI.bg,
            border: `1px solid ${UI.border}`,
            borderRadius: '10px',
          }"
        >
          <span :style="{ fontSize: '12.5px', color: UI.textSub, fontWeight: 600 }">
            Custo adicionado a cada pedido
          </span>
          <span
            data-testid="order-ficha-total-cost"
            :style="{ fontSize: '17px', fontWeight: 700 }"
          >
            {{ brl(fichaTotalCost) }}
          </span>
        </div>
      </div>

      <template #footer>
        <UIBtn variant="secondary" @click="closeFichaModal">Cancelar</UIBtn>
        <UIBtn
          variant="primary"
          icon="check"
          :disabled="savingFicha"
          data-testid="order-ficha-save-button"
          @click="saveFicha"
        >
          Salvar
        </UIBtn>
      </template>
    </UIModal>

    <!-- Create/Edit modal -->
    <UIModal
      v-if="showModal"
      :title="editingOrderId ? 'Editar Pedido' : 'Novo Pedido'"
      :subtitle="editingOrderId ? 'Atualize os detalhes do pedido' : 'Preencha os detalhes para registrar'"
      :width="680"
      title-test-id="order-form-title"
      @close="closeModal"
    >
      <form id="order-form" @submit.prevent="handleSubmit">
        <div style="display: flex; flex-direction: column; gap: 16px">
          <div
            v-if="submitError"
            data-testid="order-modal-error"
            :style="{
              background: UI.roseBg,
              color: UI.rose2,
              border: `1px solid ${UI.rose}22`,
              borderRadius: '9px',
              padding: '10px 12px',
              fontSize: '12.5px',
            }"
          >
            {{ submitError }}
          </div>
          <div
            :class="editingOrderId ? 'grid-cols-3' : 'grid-cols-2'"
            :style="{ gap: '12px' }"
          >
            <UIField label="Cliente">
              <UICombobox
                v-model="form.customerId"
                v-model:query="form.customerName"
                :options="customerOptions"
                placeholder="Digite o nome do cliente…"
                data-testid="order-customer-input"
                @update:query="customerError = null"
              >
                <template #create="{ query }">Criar cliente “{{ query }}”</template>
              </UICombobox>
              <div
                v-if="customerError"
                data-testid="order-customer-error"
                :style="{ color: UI.rose, fontSize: '11.5px', marginTop: '4px' }"
              >
                {{ customerError }}
              </div>
            </UIField>
            <UIField label="Canal">
              <UISelect v-model="form.origin" data-testid="order-origin-select">
                <option value="MENUBANK">MenuBank</option>
                <option value="ANOTA_AI">Anota.AI</option>
                <option value="IFOOD">iFood</option>
              </UISelect>
            </UIField>
            <UIField v-if="editingOrderId" label="Status">
              <UISelect v-model="form.status" data-testid="order-status-select">
                <option value="PENDING">Pendente</option>
                <option value="PAID">Pago</option>
                <option value="CANCELLED">Cancelado</option>
              </UISelect>
            </UIField>
            <UIField label="Taxa">
              <UISelect v-model="form.feeId" data-testid="order-fee-select">
                <option value="">Nenhuma</option>
                <option v-for="f in feeStore.items" :key="f.id" :value="f.id">
                  {{ f.name }} ({{ f.feeRate }}%)
                </option>
              </UISelect>
            </UIField>
          </div>

          <div>
            <div
              :style="{
                fontSize: '13px',
                color: UI.text,
                fontWeight: 600,
                marginBottom: '10px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }"
            >
              <span>Itens do pedido</span>
              <UIBtn size="sm" icon="plus" variant="secondary" @click="addItem">
                Adicionar item
              </UIBtn>
            </div>
            <div style="display: flex; flex-direction: column; gap: 10px">
              <div
                v-for="(item, i) in form.items"
                :key="i"
                :style="{
                  background: UI.bgSoft,
                  border: `1px solid ${UI.border}`,
                  borderRadius: '11px',
                  padding: '14px',
                  position: 'relative',
                }"
              >
                <div style="display: flex; gap: 10px; align-items: flex-end">
                  <UIField label="Produto" width="100%">
                    <UISelect
                      v-model="item.productId"
                      placeholder="Selecionar…"
                      :data-testid="`order-item-${i}-product-select`"
                    >
                      <option v-for="p in productStore.items" :key="p.id" :value="p.id">
                        {{ p.name }} — {{ brl(Number(p.price)) }}
                      </option>
                    </UISelect>
                  </UIField>
                  <UIField label="Qtd" :width="80">
                    <UIInput
                      v-model.number="item.quantity"
                      type="number"
                      :data-testid="`order-item-${i}-quantity-input`"
                    />
                  </UIField>
                  <div
                    v-if="form.items.length > 1"
                    :style="{
                      width: '38px',
                      height: '38px',
                      borderRadius: '9px',
                      background: UI.roseBg,
                      color: UI.rose,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      cursor: 'pointer',
                      flexShrink: 0,
                    }"
                    @click="removeItem(i)"
                  >
                    <UIIcon name="trash" :size="14" />
                  </div>
                </div>

                <!-- Insumos da ficha técnica: entram no custo por padrão; desmarcar exclui -->
                <div
                  v-if="insumosOf(item).length"
                  :style="{
                    marginTop: '14px',
                    paddingTop: '12px',
                    borderTop: `1px dashed ${UI.border}`,
                  }"
                >
                  <div
                    :style="{
                      fontSize: '11.5px',
                      color: UI.textSub,
                      fontWeight: 600,
                      marginBottom: '8px',
                    }"
                  >
                    Insumos da ficha técnica
                  </div>
                  <div style="display: flex; flex-wrap: wrap; gap: 6px 14px">
                    <label
                      v-for="inc in insumosOf(item)"
                      :key="inc.id"
                      :data-testid="`order-item-${i}-insumo-${inc.id}`"
                      :style="{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                        fontSize: '12px',
                        cursor: 'pointer',
                        color: isInsumoIncluded(item, inc.id) ? UI.text : UI.textMute,
                        textDecoration: isInsumoIncluded(item, inc.id) ? 'none' : 'line-through',
                      }"
                    >
                      <input
                        type="checkbox"
                        :checked="isInsumoIncluded(item, inc.id)"
                        :data-testid="`order-item-${i}-insumo-${inc.id}-checkbox`"
                        style="accent-color: #2563eb; cursor: pointer"
                        @change="toggleInsumo(item, inc.id, ($event.target as HTMLInputElement).checked)"
                      />
                      <span>{{ inc.name }} — {{ brl(Number(inc.totalCost)) }}</span>
                    </label>
                  </div>
                </div>

                <div
                  :style="{
                    marginTop: '14px',
                    paddingTop: '12px',
                    borderTop: `1px dashed ${UI.border}`,
                  }"
                >
                  <div
                    :style="{
                      fontSize: '11.5px',
                      color: UI.textSub,
                      fontWeight: 600,
                      marginBottom: '8px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                    }"
                  >
                    <span>Ingredientes extras</span>
                    <span
                      :style="{ color: UI.blue, fontSize: '11px', cursor: 'pointer' }"
                      :data-testid="`order-item-${i}-add-extra-button`"
                      @click="addExtra(i)"
                    >
                      + Adicionar extra
                    </span>
                  </div>
                  <div
                    v-if="!ensureExtras(item).length"
                    :style="{ fontSize: '11.5px', color: UI.textMute, fontStyle: 'italic' }"
                  >
                    Nenhum extra
                  </div>
                  <div v-else style="display: flex; flex-direction: column; gap: 6px">
                    <div
                      v-for="(extra, j) in ensureExtras(item)"
                      :key="j"
                      style="display: flex; gap: 8px; align-items: center"
                    >
                      <UISelect
                        :model-value="extra.ingredientId"
                        width="100%"
                        :data-testid="`order-item-${i}-extra-${j}-ingredient-select`"
                        @update:model-value="(v) => onExtraChange(extra, v as string)"
                      >
                        <option v-for="ing in ingredientStore.items" :key="ing.id" :value="ing.id">
                          {{ ing.name }} ({{ ing.unit }})
                        </option>
                      </UISelect>
                      <UIInput
                        v-model.number="extra.quantity"
                        type="number"
                        :width="80"
                        :data-testid="`order-item-${i}-extra-${j}-quantity-input`"
                      />
                      <div
                        :style="{
                          width: '30px',
                          height: '30px',
                          borderRadius: '7px',
                          background: UI.bg,
                          color: UI.rose,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          cursor: 'pointer',
                          flexShrink: 0,
                        }"
                        @click="removeExtra(i, j)"
                      >
                        <UIIcon name="x" :size="12" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div
            :style="{
              padding: '14px',
              background: UI.bg,
              borderRadius: '10px',
              fontSize: '12.5px',
              color: UI.textSub,
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
            }"
          >
            <UIIcon name="info" :size="16" />
            O lucro estimado será calculado automaticamente a partir das fichas técnicas.
          </div>
        </div>
      </form>

      <template #footer>
        <UIBtn variant="secondary" @click="closeModal">Cancelar</UIBtn>
        <UIBtn
          variant="primary"
          icon="check"
          :disabled="orderStore.loading"
          @click="handleSubmit"
        >
          {{ editingOrderId ? 'Salvar alterações' : 'Criar pedido' }}
        </UIBtn>
      </template>
    </UIModal>

    <!-- Detail modal -->
    <UIModal
      v-if="showDetailModal"
      title="Detalhes do Pedido"
      :subtitle="
        selectedOrder
          ? `${selectedOrder.customerName} · ${originLabel(selectedOrder.origin)} · ${formatDateTime(selectedOrder.dateTime)}`
          : ''
      "
      :width="720"
      data-testid="order-detail-modal"
      @close="closeDetail"
    >
      <div
        v-if="loadingDetail && !selectedOrder"
        style="padding: 40px; text-align: center; color: #94a3b8"
      >
        Carregando…
      </div>
      <div
        v-else-if="selectedOrder"
        style="display: flex; flex-direction: column; gap: 18px"
      >
        <!-- Discreet hint. The row is always present and only its opacity changes,
             so showing it never pushes the content below it. -->
        <div
          data-testid="order-detail-pending"
          :style="{
            height: '13px',
            fontSize: '11px',
            lineHeight: '13px',
            color: UI.textMute,
            opacity: detailPending ? 1 : 0,
            transition: 'opacity 0.15s ease',
          }"
        >
          Atualizando…
        </div>
        <div class="grid-cols-4" style="gap: 10px">
          <div
            :style="{
              padding: '12px',
              background: UI.bg,
              border: `1px solid ${UI.border}`,
              borderRadius: '10px',
            }"
          >
            <div
              :style="{
                fontSize: '10.5px',
                color: UI.textMute,
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
                marginBottom: '6px',
              }"
            >
              Status
            </div>
            <UIPill :color="STATUS_PILL[selectedOrder.status]?.color ?? 'gray'" dot>
              {{ STATUS_PILL[selectedOrder.status]?.label ?? selectedOrder.status }}
            </UIPill>
          </div>
          <div
            :style="{
              padding: '12px',
              background: UI.bg,
              border: `1px solid ${UI.border}`,
              borderRadius: '10px',
            }"
          >
            <div
              :style="{
                fontSize: '10.5px',
                color: UI.textMute,
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
                marginBottom: '6px',
              }"
            >
              Valor total
            </div>
            <span :style="{ fontSize: '17px', fontWeight: 700 }">
              {{ brl(Number(selectedOrder.totalValue)) }}
            </span>
          </div>
          <div
            :style="{
              padding: '12px',
              background: UI.bg,
              border: `1px solid ${UI.border}`,
              borderRadius: '10px',
            }"
          >
            <div
              :style="{
                fontSize: '10.5px',
                color: UI.textMute,
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
                marginBottom: '6px',
              }"
            >
              Custo
            </div>
            <span
              data-testid="order-detail-total-cost"
              :style="{ fontSize: '17px', fontWeight: 700, color: UI.textSub }"
            >
              {{ brl(Number(selectedOrder.totalCost ?? orderTotalCost(selectedOrder))) }}
            </span>
          </div>
          <div
            :style="{
              padding: '12px',
              background: UI.bg,
              border: `1px solid ${UI.border}`,
              borderRadius: '10px',
            }"
          >
            <div
              :style="{
                fontSize: '10.5px',
                color: UI.textMute,
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
                marginBottom: '6px',
              }"
            >
              Lucro · margem
            </div>
            <span
              data-testid="order-detail-estimated-profit"
              :style="{ fontSize: '17px', fontWeight: 700, color: UI.emerald2 }"
            >
              {{ brl(Number(selectedOrder.estimatedProfit)) }}
            </span>
            <span
              data-testid="order-detail-margin"
              :style="{ fontSize: '11px', color: UI.textSub, marginLeft: '6px' }"
            >
              {{ marginLabel(selectedOrder) }}
            </span>
          </div>
        </div>

        <div v-if="selectedOrder.deliveryFee && Number(selectedOrder.deliveryFee) > 0">
          <strong>Taxa de entrega:</strong>
          {{ brl(Number(selectedOrder.deliveryFee)) }}
          <span v-if="selectedOrder.feeName" :style="{ color: UI.textSub }">
            · {{ selectedOrder.feeName }} ({{ selectedOrder.feeRate }}%)
          </span>
        </div>

        <!-- Ficha do pedido: torna explicável a parcela do custo que não vem dos itens -->
        <div
          v-if="selectedOrder.orderFicha && selectedOrder.orderFicha.length"
          data-testid="order-detail-ficha"
        >
          <div :style="{ fontSize: '13px', fontWeight: 700, color: UI.text, marginBottom: '10px' }">
            Ficha do pedido ·
            <span :style="{ color: UI.textSub, fontWeight: 600 }">
              {{ brl(Number(selectedOrder.orderFichaCost ?? 0)) }}
            </span>
          </div>
          <div
            :style="{
              background: UI.bgSoft,
              border: `1px solid ${UI.border}`,
              borderRadius: '11px',
              padding: '10px 14px',
              display: 'flex',
              flexDirection: 'column',
              gap: '6px',
            }"
          >
            <div :style="{ fontSize: '11px', color: UI.textMute }">
              Cobrado uma vez neste pedido, independentemente da quantidade de itens.
            </div>
            <div
              v-for="line in selectedOrder.orderFicha"
              :key="line.id"
              :style="{ display: 'flex', justifyContent: 'space-between', fontSize: '12.5px' }"
            >
              <span>
                {{ line.ingredientName }}
                <span :style="{ color: UI.textMute }">
                  · {{ line.quantity }} {{ line.ingredientUnit }}
                </span>
              </span>
              <span :style="{ color: UI.textSub }">{{ brl(Number(line.totalCost)) }}</span>
            </div>
          </div>
        </div>

        <div>
          <div :style="{ fontSize: '13px', fontWeight: 700, color: UI.text, marginBottom: '10px' }">
            Itens ({{ selectedOrder.items.length }})
          </div>
          <div style="display: flex; flex-direction: column; gap: 10px">
            <div
              v-for="item in selectedOrder.items"
              :key="item.id"
              :style="{
                background: UI.bgSoft,
                border: `1px solid ${UI.border}`,
                borderRadius: '11px',
                overflow: 'hidden',
              }"
            >
              <div
                :style="{
                  padding: '12px 16px',
                  borderBottom: `1px solid ${UI.border}`,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                }"
              >
                <div
                  :style="{
                    width: '4px',
                    height: '40px',
                    borderRadius: '2px',
                    background: UI.violet,
                    flexShrink: 0,
                  }"
                />
                <div style="flex: 1">
                  <div :style="{ fontSize: '13.5px', fontWeight: 600 }">{{ item.productName }}</div>
                  <div :style="{ fontSize: '11px', color: UI.textMute }">
                    Qtd. {{ item.quantity }} · Unit. {{ brl(Number(item.unitPrice)) }} · Custo
                    {{ brl(Number(item.unitCost)) }}
                  </div>
                </div>
                <div style="text-align: right">
                  <div
                    :data-testid="`item-${item.id}-value`"
                    :style="{ fontSize: '14px', fontWeight: 700 }"
                  >
                    {{ brl(itemChargedValue(item)) }}
                  </div>
                  <div :style="{ fontSize: '11px', color: UI.emerald2, fontWeight: 600 }">
                    + {{ brl(Number(item.unitPrice) * Number(item.quantity) - Number(item.totalCost)) }}
                  </div>
                </div>
              </div>

              <div
                v-if="
                  (item.insumos && item.insumos.length) ||
                  (item.extraIngredients && item.extraIngredients.length) ||
                  (item.unmatchedSubItems && item.unmatchedSubItems.length)
                "
                style="padding: 12px 16px"
              >
                <div
                  :style="{
                    fontSize: '11px',
                    color: UI.textSub,
                    fontWeight: 600,
                    textTransform: 'uppercase',
                    letterSpacing: '0.5px',
                    marginBottom: '8px',
                  }"
                >
                  Ficha técnica + extras
                </div>
                <div style="display: flex; flex-direction: column; gap: 4px">
                  <div
                    :style="{
                      display: 'grid',
                      gridTemplateColumns: '1fr 70px 90px 90px',
                      gap: '10px',
                      padding: '0 0 4px',
                      fontSize: '10px',
                      fontWeight: 600,
                      textTransform: 'uppercase',
                      letterSpacing: '0.4px',
                      color: UI.textMute,
                      borderBottom: `1px solid ${UI.border}`,
                    }"
                  >
                    <span>Item</span>
                    <span>Qtd</span>
                    <span style="text-align: right">Pago</span>
                    <span style="text-align: right">Custo</span>
                  </div>
                  <div
                    v-for="ins in item.insumos ?? []"
                    :key="'i' + ins.id"
                    :style="{
                      display: 'grid',
                      gridTemplateColumns: '1fr 70px 90px 90px',
                      gap: '10px',
                      padding: '6px 0',
                      fontSize: '12px',
                      alignItems: 'center',
                    }"
                  >
                    <span style="display: flex; align-items: center; gap: 8px">
                      <span
                        :style="{
                          width: '6px',
                          height: '6px',
                          borderRadius: '3px',
                          background: UI.emerald,
                        }"
                      />
                      {{ ins.name }}
                    </span>
                    <span :style="{ color: UI.textSub }">{{ ins.quantity }}</span>
                    <!-- Insumos da ficha técnica não são cobrados à parte: já estão no preço do produto. -->
                    <span :style="{ textAlign: 'right', color: UI.textMute }">Incluso</span>
                    <span
                      :style="{
                        textAlign: 'right',
                        fontVariantNumeric: 'tabular-nums',
                        color: UI.textSub,
                      }"
                    >
                      {{ brl(Number(ins.totalCost)) }}
                    </span>
                  </div>
                  <div
                    v-for="ex in item.extraIngredients ?? []"
                    :key="'e' + ex.id"
                    :style="{
                      display: 'grid',
                      gridTemplateColumns: '1fr 70px 90px 90px',
                      gap: '10px',
                      padding: '6px 0',
                      fontSize: '12px',
                      alignItems: 'center',
                    }"
                  >
                    <span
                      :data-testid="'extra-' + ex.id + '-name'"
                      style="display: flex; align-items: center; gap: 8px"
                    >
                      <span
                        :style="{
                          width: '6px',
                          height: '6px',
                          borderRadius: '3px',
                          background: UI.textMute,
                        }"
                      />
                      {{ extraLabel(ex) }}
                    </span>
                    <span :style="{ color: UI.textSub }">
                      {{ ex.quantity }} {{ ex.ingredientUnit }}
                    </span>
                    <span
                      :data-testid="'extra-' + ex.id + '-paid'"
                      :style="{
                        textAlign: 'right',
                        fontVariantNumeric: 'tabular-nums',
                        color: Number(ex.salePriceTotal) > 0 ? UI.emerald : UI.textMute,
                        fontWeight: Number(ex.salePriceTotal) > 0 ? 600 : 400,
                      }"
                    >
                      {{ paidLabel(ex) }}
                    </span>
                    <span
                      :data-testid="'extra-' + ex.id + '-cost'"
                      :style="{
                        textAlign: 'right',
                        fontVariantNumeric: 'tabular-nums',
                        color: UI.textSub,
                      }"
                    >
                      {{ brl(Number(ex.totalCost)) }}
                    </span>
                  </div>
                  <!-- SubItems não-casados: aparecem com um botão para cadastrar o
                       ingrediente faltante. Somem quando o ingrediente é criado. -->
                  <div
                    v-for="u in item.unmatchedSubItems ?? []"
                    :key="'u' + u.id"
                    :data-testid="'unmatched-' + u.id + '-row'"
                    :style="{
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '8px',
                      padding: '8px 0',
                      borderTop: `1px dashed ${UI.border}`,
                    }"
                  >
                    <div style="display: flex; align-items: center; gap: 8px">
                      <span
                        :style="{
                          width: '6px',
                          height: '6px',
                          borderRadius: '3px',
                          background: UI.amber,
                          flexShrink: 0,
                        }"
                      />
                      <span :style="{ fontSize: '12px', color: UI.text }">
                        {{ u.rawName }}
                        <span :style="{ color: UI.textMute }">· {{ u.quantity }}</span>
                      </span>
                      <span :style="{ fontSize: '11px', color: UI.amber, fontStyle: 'italic' }">
                        Ingrediente não cadastrado
                      </span>
                      <span style="flex: 1" />
                      <UIBtn
                        v-if="creatingIngredientFor !== u.id"
                        variant="secondary"
                        size="sm"
                        :data-testid="'unmatched-' + u.id + '-create-button'"
                        @click="startCreateIngredient(u)"
                      >
                        Cadastrar ingrediente
                      </UIBtn>
                    </div>
                    <div
                      v-if="creatingIngredientFor === u.id"
                      :data-testid="'unmatched-' + u.id + '-form'"
                      :style="{
                        display: 'flex',
                        flexWrap: 'wrap',
                        alignItems: 'flex-end',
                        gap: '8px',
                        padding: '8px',
                        background: UI.bg,
                        borderRadius: '8px',
                      }"
                    >
                      <UIField label="Nome" :width="160">
                        <UIInput
                          v-model="ingredientForm.name"
                          :data-testid="'unmatched-' + u.id + '-name-input'"
                        />
                      </UIField>
                      <UIField label="Unidade" :width="80">
                        <UIInput
                          v-model="ingredientForm.unit"
                          placeholder="g, ml, un"
                          :data-testid="'unmatched-' + u.id + '-unit-input'"
                        />
                      </UIField>
                      <UIField label="Custo / unidade" :width="100">
                        <UIInput
                          v-model.number="ingredientForm.costPerUnit"
                          type="number"
                          :data-testid="'unmatched-' + u.id + '-cost-input'"
                        />
                      </UIField>
                      <UIField label="Qtd. padrão" :width="100">
                        <UIInput
                          v-model.number="ingredientForm.defaultQuantity"
                          type="number"
                          :data-testid="'unmatched-' + u.id + '-quantity-input'"
                        />
                      </UIField>
                      <UIBtn
                        variant="primary"
                        size="sm"
                        :disabled="savingIngredient"
                        :data-testid="'unmatched-' + u.id + '-save-button'"
                        @click="submitCreateIngredient"
                      >
                        Salvar
                      </UIBtn>
                      <UIBtn
                        variant="secondary"
                        size="sm"
                        :data-testid="'unmatched-' + u.id + '-cancel-button'"
                        @click="cancelCreateIngredient"
                      >
                        Cancelar
                      </UIBtn>
                      <div
                        v-if="creatingIngredientError"
                        :data-testid="'unmatched-' + u.id + '-error'"
                        :style="{ width: '100%', fontSize: '11px', color: UI.rose }"
                      >
                        {{ creatingIngredientError }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <UIBtn variant="secondary" @click="closeDetail">Fechar</UIBtn>
        <UIBtn
          v-if="selectedOrder"
          variant="primary"
          icon="edit"
          @click="
            () => {
              const o = selectedOrder!
              closeDetail()
              openEdit(o)
            }
          "
        >
          Editar pedido
        </UIBtn>
      </template>
    </UIModal>

    <!-- Delete confirm modal -->
    <UIModal
      v-if="confirmDeleteId"
      title="Excluir pedido"
      subtitle="Esta ação não pode ser desfeita"
      :width="420"
      @close="confirmDeleteId = null"
    >
      <p :style="{ color: UI.textSub, fontSize: '13.5px', lineHeight: 1.6 }">
        Tem certeza que deseja excluir este pedido?
      </p>
      <template #footer>
        <UIBtn variant="secondary" @click="confirmDeleteId = null">Cancelar</UIBtn>
        <UIBtn variant="danger" icon="trash" @click="handleDelete">Excluir</UIBtn>
      </template>
    </UIModal>

    <!-- Comanda do pedido do iFood -->
    <IfoodOrderTicketModal
      v-if="ticketOrder"
      :order="ticketOrder"
      @updated="onTicketUpdated"
      @close="closeTicket"
    />
  </div>
</template>

<style scoped>
.ui-row {
  transition: background 0.12s ease;
}
.ui-row:hover {
  background: rgba(15, 23, 42, 0.025);
}
</style>
