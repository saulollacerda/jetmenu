<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useIngredientStore } from '@/stores/ingredientStore'
import {
  UI,
  UITopbar,
  UIBtn,
  UIPill,
  UISearch,
  UIField,
  UIInput,
  UISelect,
  UIModal,
  UIIcon,
  UIRowAction,
  UIEmpty,
  brl,
} from '@/design'
import type {
  IngredientRequest,
  IngredientResponse,
  IngredientProductUsageResponse,
} from '@/types/Ingredient'
import type { ProductResponse } from '@/types/Product'
import { ingredientService } from '@/services/ingredientService'
import { productService } from '@/services/productService'
import { includeService } from '@/services/includeService'
import { useToast } from '@/composables/useToast'
import {
  useIngredientFilters,
  type IngredientFilterState,
  type IngredientSortKey,
} from '@/composables/useIngredientFilters'

const store = useIngredientStore()
const route = useRoute()
const router = useRouter()
const { showToast } = useToast()

const showModal = ref(false)
const editing = ref<IngredientResponse | null>(null)
const form = ref<IngredientRequest>({ name: '', unit: '', costPerUnit: 0, defaultQuantity: 0 })
const confirmDeleteId = ref<string | null>(null)

const autoCalcCost = ref(false)
const purchasePrice = ref<number | null>(null)
const purchaseQuantity = ref<number | null>(null)

const unitFilter = ref<string>('')
const statusFilter = ref<'' | 'ACTIVE' | 'INACTIVE'>('')
const minCost = ref<number | null>(null)
const maxCost = ref<number | null>(null)
const createdFrom = ref<string>('')
const createdTo = ref<string>('')
const sortBy = ref<IngredientSortKey>('')

const computedCostPerUnit = computed(() => {
  const price = purchasePrice.value ?? 0
  const qty = purchaseQuantity.value ?? 0
  if (qty <= 0 || price <= 0) return null
  return price / qty
})

interface SpecificQuantity {
  includeId?: string
  productId: string
  productName: string
  quantity: number
}

const specificGrammages = ref<SpecificQuantity[]>([])
const toDelete = ref<{ includeId: string; productId: string }[]>([])
const allProducts = ref<ProductResponse[]>([])
const newSpecificProductId = ref('')
const newSpecificGrammage = ref<number | null>(null)
const addSpecificError = ref<string | null>(null)
const submitError = ref<string | null>(null)

const availableProducts = computed(() =>
  allProducts.value.filter((p) => !specificGrammages.value.some((sg) => sg.productId === p.id)),
)

const filterState: IngredientFilterState = {
  nameQuery: computed(() => store.search),
  unit: unitFilter,
  status: statusFilter,
  minCost,
  maxCost,
  createdFrom,
  createdTo,
  sortBy,
}
const {
  sorted: filteredItems,
  activeFilterCount,
  reset: resetFilters,
} = useIngredientFilters(
  computed(() => store.items),
  filterState,
)

const uniqueUnits = computed(() => Array.from(new Set(store.items.map((i) => i.unit))).sort())

const activeCount = computed(() => store.items.filter((i) => i.status === 'ACTIVE').length)

// -- Manual drag-and-drop ordering -----------------------------------------
// Reordering is only meaningful in the default (position) order: any active
// search / filter / sort changes what "position N in the page" means, so the
// handles are disabled while those are set.
const dragEnabled = computed(
  () => !store.search && activeFilterCount.value === 0 && sortBy.value === '',
)

const draggedIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)
const dragOverPager = ref<'' | 'prev' | 'next'>('')
const isDragging = computed(() => draggedIndex.value !== null)

const canDropPrev = computed(() => store.page > 0)
const canDropNext = computed(() => store.page < store.totalPages - 1)

function resetDrag() {
  draggedIndex.value = null
  dragOverIndex.value = null
  dragOverPager.value = ''
}

function onDragStart(index: number, e: DragEvent) {
  if (!dragEnabled.value) return
  draggedIndex.value = index
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(index))
  }
}

function onRowDragOver(index: number) {
  if (draggedIndex.value === null) return
  dragOverIndex.value = index
  dragOverPager.value = ''
}

async function onRowDrop(index: number) {
  const from = draggedIndex.value
  const id = from === null ? undefined : filteredItems.value[from]?.id
  resetDrag()
  if (from === null || from === index || !id) return
  // Global position within the merchant = page offset + target index in the page.
  const globalPosition = store.page * store.size + index
  try {
    await store.moveWithinPage(id, index, globalPosition)
  } catch {
    /* rollback + error toast handled by the store */
  }
}

function onPagerDragOver(target: 'prev' | 'next') {
  if (draggedIndex.value === null) return
  if (target === 'prev' && !canDropPrev.value) return
  if (target === 'next' && !canDropNext.value) return
  dragOverPager.value = target
  dragOverIndex.value = null
}

function onPagerDragLeave(target: 'prev' | 'next') {
  if (dragOverPager.value === target) dragOverPager.value = ''
}

async function onDropPrev() {
  const from = draggedIndex.value
  const id = from === null ? undefined : filteredItems.value[from]?.id
  const canDrop = canDropPrev.value
  const targetPage = store.page - 1
  // End of the previous page = first index of the current page minus one.
  const globalPosition = store.page * store.size - 1
  resetDrag()
  if (from === null || !id || !canDrop) return
  try {
    await store.moveToPage(id, globalPosition, targetPage)
  } catch {
    /* error toast handled by the store */
  }
}

async function onDropNext() {
  const from = draggedIndex.value
  const id = from === null ? undefined : filteredItems.value[from]?.id
  const canDrop = canDropNext.value
  const targetPage = store.page + 1
  // Start of the next page = first index of that page.
  const globalPosition = (store.page + 1) * store.size
  resetDrag()
  if (from === null || !id || !canDrop) return
  try {
    await store.moveToPage(id, globalPosition, targetPage)
  } catch {
    /* error toast handled by the store */
  }
}

async function loadProducts() {
  const page = await productService.findAll({ search: '', page: 0, size: 500 })
  allProducts.value = page.content
}

function addSpecificGrammage() {
  addSpecificError.value = null
  if (!newSpecificProductId.value) {
    addSpecificError.value = 'Selecione um produto.'
    return
  }
  if (!newSpecificGrammage.value || newSpecificGrammage.value <= 0) {
    addSpecificError.value = 'Informe uma quantidade maior que zero.'
    return
  }
  const product = allProducts.value.find((p) => p.id === newSpecificProductId.value)
  if (!product) return
  specificGrammages.value.push({
    productId: product.id,
    productName: product.name,
    quantity: newSpecificGrammage.value,
  })
  newSpecificProductId.value = ''
  newSpecificGrammage.value = null
}
function removeSpecificGrammage(index: number, sg: SpecificQuantity) {
  if (sg.includeId) toDelete.value.push({ includeId: sg.includeId, productId: sg.productId })
  specificGrammages.value.splice(index, 1)
}

function resetAuto() {
  autoCalcCost.value = false
  purchasePrice.value = null
  purchaseQuantity.value = null
}

async function openCreate(prefilledName = '') {
  editing.value = null
  form.value = { name: prefilledName, unit: '', costPerUnit: 0, defaultQuantity: 0 }
  specificGrammages.value = []
  toDelete.value = []
  newSpecificProductId.value = ''
  newSpecificGrammage.value = null
  addSpecificError.value = null
  submitError.value = null
  resetAuto()
  await loadProducts()
  showModal.value = true
}
async function openEdit(ing: IngredientResponse) {
  editing.value = ing
  form.value = {
    name: ing.name,
    unit: ing.unit,
    costPerUnit: ing.costPerUnit,
    defaultQuantity: ing.defaultQuantity ?? 0,
  }
  specificGrammages.value = []
  toDelete.value = []
  newSpecificProductId.value = ''
  newSpecificGrammage.value = null
  addSpecificError.value = null
  submitError.value = null
  resetAuto()
  await loadProducts()
  try {
    const usages: IngredientProductUsageResponse[] = await ingredientService.fetchUsages(ing.id)
    specificGrammages.value = usages.map((u) => ({
      includeId: u.includeId,
      productId: u.productId,
      productName: u.productName,
      quantity: u.quantity,
    }))
  } catch {
    /* non-critical */
  }
  showModal.value = true
}
async function openDuplicate(ing: IngredientResponse) {
  await openCreate(`${ing.name} (cópia)`)
  form.value.unit = ing.unit
  form.value.costPerUnit = ing.costPerUnit
  form.value.defaultQuantity = ing.defaultQuantity ?? 0
  try {
    const usages: IngredientProductUsageResponse[] = await ingredientService.fetchUsages(ing.id)
    // No includeId: submitting must create new includes for the copy, never
    // touch the source ingredient's existing ones
    specificGrammages.value = usages.map((u) => ({
      productId: u.productId,
      productName: u.productName,
      quantity: u.quantity,
    }))
  } catch {
    /* non-critical */
  }
}
function closeModal() {
  showModal.value = false
  editing.value = null
}

function extractErrorMessage(err: unknown): string {
  const e = err as {
    response?: { data?: { detail?: string; message?: string } }
    message?: string
  }
  return (
    e?.response?.data?.detail || e?.response?.data?.message || e?.message || 'Erro ao salvar'
  )
}

async function handleSubmit() {
  submitError.value = null
  if (autoCalcCost.value) {
    if (computedCostPerUnit.value == null) return
    form.value.costPerUnit = Number(computedCostPerUnit.value.toFixed(4))
  }
  try {
    if (editing.value) {
      await store.update(editing.value.id, form.value)
    } else {
      await store.create(form.value)
      showToast('Ingrediente criado com sucesso!')
    }
  } catch (err: unknown) {
    submitError.value = extractErrorMessage(err)
    return
  }

  const includeName = form.value.name
  const includeCost = Number(form.value.costPerUnit ?? 0)

  try {
    for (const d of toDelete.value) {
      await includeService.remove(d.productId, d.includeId)
    }
    for (const sg of specificGrammages.value) {
      if (sg.includeId) {
        await includeService.update(sg.productId, sg.includeId, {
          name: includeName,
          cost: includeCost,
          quantity: sg.quantity,
        })
      } else {
        await includeService.add(sg.productId, {
          name: includeName,
          cost: includeCost,
          quantity: sg.quantity,
        })
      }
    }
  } catch (err: unknown) {
    submitError.value =
      'Ingrediente salvo, mas falha em sincronizar com fichas técnicas: ' + extractErrorMessage(err)
    return
  }
  closeModal()
}

async function handleDelete() {
  if (!confirmDeleteId.value) return
  try {
    await store.remove(confirmDeleteId.value)
  } catch {
    /* error in store */
  }
  confirmDeleteId.value = null
}

let searchDebounce: ReturnType<typeof setTimeout> | null = null
function onSearchInput(v: string) {
  store.search = v
  if (searchDebounce) clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => store.fetchPage({ search: v, page: 0 }), 300)
}
function onPageChange(p: number) {
  if (p < 0 || p >= store.totalPages) return
  store.fetchPage({ page: p })
}

function maybeOpenFromQuery() {
  const createName = route.query.createName
  if (typeof createName === 'string' && createName.trim().length > 0) {
    openCreate(createName)
    router.replace({ query: {} })
  }
}

onMounted(() => {
  store.fetchPage({ page: 0, search: '' })
  maybeOpenFromQuery()
})
watch(() => route.query.createName, () => maybeOpenFromQuery())

const cols = '1.4fr 90px 110px 110px 100px 90px 110px 44px'
const tableMinWidth = '864px'
</script>

<template>
  <div style="display: flex; flex-direction: column; flex: 1">
    <UITopbar
      title="Ingredientes"
      :subtitle="`${store.totalElements} ingredientes · ${activeCount} ativos nesta página`"
    >
      <template #actions>
        <UIBtn
          icon="plus"
          variant="dark"
          data-testid="new-ingredient-button"
          @click="openCreate()"
        >
          Novo Ingrediente
        </UIBtn>
      </template>
    </UITopbar>

    <div class="view-content">
      <div
        v-if="store.error"
        :style="{
          padding: '10px 14px',
          background: UI.roseBg,
          color: UI.rose2,
          borderRadius: '10px',
          fontSize: '13px',
          marginBottom: '12px',
        }"
      >
        {{ store.error }}
      </div>

      <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 18px; flex-wrap: wrap">
        <UISearch
          :model-value="store.search"
          placeholder="Buscar ingrediente por nome…"
          :width="340"
          @update:model-value="onSearchInput"
        />
        <UISelect v-model="unitFilter" :width="170">
          <option value="">Todas unidades</option>
          <option v-for="u in uniqueUnits" :key="u" :value="u">{{ u }}</option>
        </UISelect>
        <UISelect v-model="statusFilter" :width="140">
          <option value="">Todos status</option>
          <option value="ACTIVE">Ativos</option>
          <option value="INACTIVE">Inativos</option>
        </UISelect>
        <UIInput
          v-model.number="minCost"
          type="number"
          step="0.0001"
          min="0"
          placeholder="Custo mín."
          :width="130"
          data-testid="ingredient-cost-min"
        />
        <UIInput
          v-model.number="maxCost"
          type="number"
          step="0.0001"
          min="0"
          placeholder="Custo máx."
          :width="130"
          data-testid="ingredient-cost-max"
        />
        <label
          :style="{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: UI.textSub }"
        >
          Criado de
          <UIInput
            v-model="createdFrom"
            type="date"
            :width="150"
            data-testid="ingredient-created-from"
          />
        </label>
        <label
          :style="{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: UI.textSub }"
        >
          até
          <UIInput
            v-model="createdTo"
            type="date"
            :width="150"
            data-testid="ingredient-created-to"
          />
        </label>
        <UISelect v-model="sortBy" :width="180" data-testid="ingredient-sort">
          <option value="">Ordenar por…</option>
          <option value="name-asc">Nome (A–Z)</option>
          <option value="name-desc">Nome (Z–A)</option>
          <option value="cost-asc">Menor custo</option>
          <option value="cost-desc">Maior custo</option>
          <option value="created-desc">Mais recentes</option>
          <option value="created-asc">Mais antigos</option>
        </UISelect>
        <div style="flex: 1" />
        <UIBtn
          v-if="activeFilterCount > 0"
          size="sm"
          icon="x"
          variant="secondary"
          data-testid="ingredient-clear-filters"
          @click="resetFilters"
        >
          Limpar filtros
        </UIBtn>
      </div>

      <UIEmpty
        v-if="!store.loading && !store.items.length && !store.search"
        icon="leaf"
        accent="emerald"
        title="Cadastre seus ingredientes"
        body="Defina valor da compra e quantidade — o JetMenu calcula o custo por unidade automaticamente e usa nas fichas técnicas dos produtos."
        primary="Adicionar ingrediente"
        @primary="openCreate()"
      />

      <div
        v-else
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
          <span>Nome</span>
          <span>Unidade</span>
          <span style="text-align: right">Custo / un</span>
          <span style="text-align: right">Preço venda</span>
          <span style="text-align: right">Qtd. padrão</span>
          <span>Status</span>
          <span style="text-align: right">Ações</span>
          <span />
        </div>

        <div>
          <div
            v-if="store.loading"
            :style="{ padding: '32px', textAlign: 'center', color: UI.textMute }"
          >
            Carregando…
          </div>
          <div
            v-else-if="!filteredItems.length"
            :style="{ padding: '60px 32px', textAlign: 'center', color: UI.textMute, fontSize: '13px' }"
          >
            Nenhum ingrediente encontrado.
          </div>
          <div
            v-for="(it, i) in filteredItems"
            v-else
            :key="it.id"
            class="ui-row"
            :class="{ 'drag-over': isDragging && dragOverIndex === i }"
            :style="{
              display: 'grid',
              gridTemplateColumns: cols,
              gap: '12px',
              padding: '12px 18px',
              borderBottom: i === filteredItems.length - 1 ? 'none' : `1px solid ${UI.borderSub}`,
              fontSize: '13px',
              color: UI.text,
              alignItems: 'center',
              opacity: isDragging && draggedIndex === i ? 0.5 : 1,
            }"
            @dragover.prevent="onRowDragOver(i)"
            @drop.prevent="onRowDrop(i)"
          >
            <span style="display: flex; align-items: center; gap: 11px; min-width: 0">
              <span
                :style="{
                  width: '30px',
                  height: '30px',
                  borderRadius: '8px',
                  background: UI.emeraldBg,
                  color: UI.emerald2,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }"
              >
                <UIIcon name="leaf" :size="15" />
              </span>
              <span
                :style="{
                  fontWeight: 600,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }"
              >
                {{ it.name }}
              </span>
            </span>
            <span>
              <span
                :style="{
                  padding: '3px 9px',
                  background: UI.bg,
                  borderRadius: '5px',
                  fontSize: '11.5px',
                  color: UI.textSub,
                  fontWeight: 600,
                  letterSpacing: '0.3px',
                }"
              >
                {{ it.unit }}
              </span>
            </span>
            <span
              :style="{
                textAlign: 'right',
                fontVariantNumeric: 'tabular-nums',
                fontWeight: 600,
                color: Number(it.costPerUnit) === 0 ? UI.amber2 : UI.text,
              }"
            >
              <UIPill v-if="Number(it.costPerUnit) === 0" color="amber" size="sm">⚠ R$ 0,00</UIPill>
              <template v-else>
                R$ {{ Number(it.costPerUnit).toFixed(4).replace('.', ',') }}
              </template>
            </span>
            <span
              :style="{
                textAlign: 'right',
                color: it.salePrice == null ? UI.textMute : UI.text,
                fontVariantNumeric: 'tabular-nums',
                fontWeight: it.salePrice == null ? 400 : 600,
              }"
            >
              {{ it.salePrice == null ? '—' : brl(Number(it.salePrice)) }}
            </span>
            <span
              :style="{
                textAlign: 'right',
                color: UI.textSub,
                fontVariantNumeric: 'tabular-nums',
              }"
            >
              {{ it.defaultQuantity ?? '—' }}
            </span>
            <span>
              <UIPill :color="it.status === 'ACTIVE' ? 'emerald' : 'gray'" dot>
                {{ it.status === 'ACTIVE' ? 'Ativo' : 'Inativo' }}
              </UIPill>
            </span>
            <span style="display: flex; gap: 5px; justify-content: flex-end">
              <UIRowAction icon="edit" color="blue" label="Editar" @click="openEdit(it)" />
              <UIRowAction
                icon="copy"
                color="emerald"
                label="Duplicar"
                data-testid="duplicate-ingredient-button"
                @click="openDuplicate(it)"
              />
              <UIRowAction icon="trash" color="rose" label="Excluir" @click="confirmDeleteId = it.id" />
            </span>
            <span style="display: flex; align-items: center; justify-content: center">
              <span
                v-if="dragEnabled"
                class="drag-handle"
                draggable="true"
                data-testid="ingredient-drag-handle"
                title="Arraste para reordenar"
                :style="{ display: 'flex', cursor: 'grab', color: UI.textMute }"
                @dragstart="onDragStart(i, $event)"
                @dragend="resetDrag"
              >
                <UIIcon name="menu" :size="16" />
              </span>
              <span
                v-else
                data-testid="ingredient-drag-handle-disabled"
                title="Disponível apenas na ordenação padrão"
                :style="{ display: 'flex', color: UI.textMute, opacity: 0.35, cursor: 'not-allowed' }"
              >
                <UIIcon name="menu" :size="16" />
              </span>
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
            padding: '10px 18px',
            borderTop: `1px solid ${UI.border}`,
            background: UI.bgSoft,
            fontSize: '12px',
            color: UI.textSub,
            flexShrink: 0,
          }"
        >
          <span>
            Página {{ store.page + 1 }} de {{ Math.max(store.totalPages, 1) }}
            · {{ store.totalElements }} ingredientes
          </span>
          <div style="display: flex; gap: 6px; align-items: center">
            <div
              data-testid="ingredient-pager-prev"
              :style="{
                position: 'relative',
                borderRadius: '9px',
                padding: '2px',
                outline:
                  isDragging && canDropPrev
                    ? dragOverPager === 'prev'
                      ? `2px solid ${UI.emerald}`
                      : `2px dashed ${UI.border}`
                    : 'none',
                background: dragOverPager === 'prev' ? UI.emeraldBg : 'transparent',
                transition: 'background 0.12s ease',
              }"
              @dragover.prevent="onPagerDragOver('prev')"
              @dragleave="onPagerDragLeave('prev')"
              @drop.prevent="onDropPrev"
            >
              <span :style="{ display: 'block', pointerEvents: isDragging ? 'none' : 'auto' }">
                <UIBtn
                  size="sm"
                  icon="chevLeft"
                  variant="secondary"
                  :disabled="store.page === 0 || store.loading"
                  @click="onPageChange(store.page - 1)"
                >
                  Anterior
                </UIBtn>
              </span>
              <span
                v-if="isDragging && canDropPrev"
                :style="{
                  position: 'absolute',
                  bottom: 'calc(100% + 6px)',
                  left: '50%',
                  transform: 'translateX(-50%)',
                  whiteSpace: 'nowrap',
                  padding: '3px 8px',
                  borderRadius: '6px',
                  background: UI.emerald,
                  color: '#fff',
                  fontSize: '11px',
                  fontWeight: 600,
                }"
              >
                Solte para mover
              </span>
            </div>
            <div
              data-testid="ingredient-pager-next"
              :style="{
                position: 'relative',
                borderRadius: '9px',
                padding: '2px',
                outline:
                  isDragging && canDropNext
                    ? dragOverPager === 'next'
                      ? `2px solid ${UI.emerald}`
                      : `2px dashed ${UI.border}`
                    : 'none',
                background: dragOverPager === 'next' ? UI.emeraldBg : 'transparent',
                transition: 'background 0.12s ease',
              }"
              @dragover.prevent="onPagerDragOver('next')"
              @dragleave="onPagerDragLeave('next')"
              @drop.prevent="onDropNext"
            >
              <span :style="{ display: 'block', pointerEvents: isDragging ? 'none' : 'auto' }">
                <UIBtn
                  size="sm"
                  icon="chevRight"
                  variant="secondary"
                  :disabled="store.page >= store.totalPages - 1 || store.loading"
                  @click="onPageChange(store.page + 1)"
                >
                  Próximo
                </UIBtn>
              </span>
              <span
                v-if="isDragging && canDropNext"
                :style="{
                  position: 'absolute',
                  bottom: 'calc(100% + 6px)',
                  left: '50%',
                  transform: 'translateX(-50%)',
                  whiteSpace: 'nowrap',
                  padding: '3px 8px',
                  borderRadius: '6px',
                  background: UI.emerald,
                  color: '#fff',
                  fontSize: '11px',
                  fontWeight: 600,
                }"
              >
                Solte para mover
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <UIModal
      v-if="showModal"
      :title="editing ? 'Editar Ingrediente' : 'Novo Ingrediente'"
      subtitle="Ative o cálculo automático para custo por unidade"
      :width="580"
      @close="closeModal"
    >
      <div
        v-if="submitError"
        :style="{
          marginBottom: '14px',
          padding: '10px 12px',
          background: UI.roseBg,
          color: UI.rose2,
          borderRadius: '8px',
          fontSize: '13px',
        }"
      >
        {{ submitError }}
      </div>

      <form id="ing-form" @submit.prevent="handleSubmit">
        <div style="display: flex; flex-direction: column; gap: 14px">
          <div style="display: grid; grid-template-columns: 2fr 1fr; gap: 12px">
            <UIField label="Nome">
              <UIInput
                v-model="form.name"
                placeholder="Nome do ingrediente"
                data-testid="ingredient-name-input"
              />
            </UIField>
            <UIField label="Unidade" hint="Ex.: kg, g, L, un">
              <UIInput v-model="form.unit" placeholder="Ex: kg, L, un" />
            </UIField>
          </div>

          <label
            :style="{
              padding: '14px',
              background: UI.blueBg,
              border: `1px solid ${UI.blueBg}`,
              borderRadius: '10px',
              display: 'flex',
              alignItems: 'center',
              gap: '11px',
              cursor: 'pointer',
            }"
          >
            <span
              :style="{
                width: '18px',
                height: '18px',
                borderRadius: '4px',
                background: autoCalcCost ? UI.blue : UI.panel,
                color: '#fff',
                border: `1px solid ${autoCalcCost ? UI.blue : UI.border}`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }"
            >
              <UIIcon v-if="autoCalcCost" name="check" :size="12" />
            </span>
            <input
              v-model="autoCalcCost"
              type="checkbox"
              data-testid="ingredient-cost-auto-checkbox"
              style="display: none"
            />
            <div style="flex: 1; font-size: 13px">
              <div :style="{ fontWeight: 600, color: UI.text }">
                Calcular custo por unidade automaticamente
              </div>
              <div :style="{ fontSize: '11.5px', color: UI.textSub, marginTop: '2px' }">
                Recomendado para fechamento por pacote / saco
              </div>
            </div>
          </label>

          <div v-if="!autoCalcCost">
            <UIField label="Custo por Unidade (R$)">
              <UIInput
                v-model.number="form.costPerUnit"
                type="number"
                step="0.0001"
                min="0"
                placeholder="0,0000"
                data-testid="ingredient-cost-per-unit-input"
              />
            </UIField>
          </div>

          <div v-else>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
              <UIField label="Valor da compra (R$)">
                <UIInput
                  v-model.number="purchasePrice"
                  type="number"
                  placeholder="195,00"
                  data-testid="ingredient-purchase-price-input"
                />
              </UIField>
              <UIField label="Qtd. comprada (na unidade)">
                <UIInput
                  v-model.number="purchaseQuantity"
                  type="number"
                  placeholder="9000"
                  data-testid="ingredient-purchase-quantity-input"
                />
              </UIField>
            </div>
            <div
              :style="{
                marginTop: '10px',
                padding: '12px',
                background: UI.emeraldBg,
                border: `1px solid ${UI.emeraldBg}`,
                borderRadius: '9px',
                display: 'flex',
                alignItems: 'center',
                gap: '11px',
              }"
              data-testid="ingredient-cost-per-unit-computed"
            >
              <UIIcon name="trend" :size="16" />
              <div style="flex: 1; font-size: 12.5px">
                <span :style="{ color: UI.textSub }">Custo calculado:</span>
                <strong
                  :style="{
                    marginLeft: '6px',
                    color: UI.emerald2,
                    fontSize: '15px',
                    fontVariantNumeric: 'tabular-nums',
                  }"
                >
                  {{
                    computedCostPerUnit != null
                      ? `R$ ${computedCostPerUnit.toFixed(4).replace('.', ',')} / ${form.unit || 'un'}`
                      : '—'
                  }}
                </strong>
              </div>
            </div>
          </div>

          <UIField
            label="Quantidade padrão"
            hint="Usada quando esse ingrediente entra como extra padrão num produto"
          >
            <UIInput
              v-model.number="form.defaultQuantity"
              type="number"
              placeholder="0"
              data-testid="ingredient-default-quantity-input"
            />
          </UIField>

          <div :style="{ height: '1px', background: UI.border, margin: '4px 0' }" />

          <div>
            <div :style="{ fontSize: '13px', color: UI.text, fontWeight: 700, marginBottom: '4px' }">
              Quantidade específica por produto
            </div>
            <div
              :style="{
                fontSize: '11.5px',
                color: UI.textSub,
                marginBottom: '10px',
                lineHeight: 1.5,
              }"
            >
              Adiciona este ingrediente à ficha técnica do produto selecionado. Nome e custo
              são gravados no item.
            </div>
            <div
              style="
                display: grid;
                grid-template-columns: 1fr 110px auto;
                gap: 8px;
                align-items: flex-end;
              "
            >
              <UISelect v-model="newSpecificProductId" placeholder="Selecionar produto…">
                <option v-for="p in availableProducts" :key="p.id" :value="p.id">
                  {{ p.name }}
                </option>
              </UISelect>
              <UIInput v-model.number="newSpecificGrammage" type="number" placeholder="Qtd." />
              <UIBtn icon="plus" variant="primary" @click="addSpecificGrammage">Adicionar</UIBtn>
            </div>
            <div
              v-if="addSpecificError"
              :style="{ color: UI.rose, fontSize: '12px', marginTop: '6px' }"
            >
              {{ addSpecificError }}
            </div>

            <div
              v-if="specificGrammages.length"
              :style="{
                marginTop: '12px',
                background: UI.panel,
                border: `1px solid ${UI.border}`,
                borderRadius: '11px',
                overflow: 'hidden',
              }"
            >
              <div
                :style="{
                  display: 'grid',
                  gridTemplateColumns: '1fr 110px 50px',
                  gap: '10px',
                  padding: '10px 14px',
                  background: UI.bgSoft,
                  borderBottom: `1px solid ${UI.border}`,
                  fontSize: '10.5px',
                  color: UI.textSub,
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  letterSpacing: '0.5px',
                }"
              >
                <span>Produto</span>
                <span style="text-align: right">Qtd</span>
                <span />
              </div>
              <div
                v-for="(sg, idx) in specificGrammages"
                :key="sg.productId"
                :style="{
                  display: 'grid',
                  gridTemplateColumns: '1fr 110px 50px',
                  gap: '10px',
                  padding: '11px 14px',
                  alignItems: 'center',
                  fontSize: '13px',
                  borderBottom: idx === specificGrammages.length - 1 ? 'none' : `1px solid ${UI.borderSub}`,
                }"
              >
                <span :style="{ fontWeight: 600 }">{{ sg.productName }}</span>
                <UIInput v-model.number="sg.quantity" type="number" :width="100" />
                <span style="display: flex; justify-content: flex-end">
                  <UIRowAction icon="trash" color="rose" @click="removeSpecificGrammage(idx, sg)" />
                </span>
              </div>
            </div>
          </div>
        </div>
      </form>

      <template #footer>
        <UIBtn variant="secondary" @click="closeModal">Cancelar</UIBtn>
        <UIBtn variant="primary" icon="check" :disabled="store.loading" @click="handleSubmit">
          {{ editing ? 'Salvar' : 'Criar ingrediente' }}
        </UIBtn>
      </template>
    </UIModal>

    <UIModal
      v-if="confirmDeleteId"
      title="Excluir ingrediente"
      subtitle="Esta ação não pode ser desfeita"
      :width="420"
      @close="confirmDeleteId = null"
    >
      <p :style="{ color: UI.textSub, fontSize: '13.5px', lineHeight: 1.6 }">
        Tem certeza que deseja excluir este ingrediente?
      </p>
      <template #footer>
        <UIBtn variant="secondary" @click="confirmDeleteId = null">Cancelar</UIBtn>
        <UIBtn variant="danger" icon="trash" @click="handleDelete">Excluir</UIBtn>
      </template>
    </UIModal>
  </div>
</template>

<style scoped>
.ui-row {
  transition: background 0.12s ease;
}
.ui-row:hover {
  background: rgba(15, 23, 42, 0.025);
}
.ui-row.drag-over {
  background: rgba(16, 185, 129, 0.1);
  box-shadow: inset 0 2px 0 rgba(16, 185, 129, 0.55);
}
.drag-handle:active {
  cursor: grabbing;
}
</style>
