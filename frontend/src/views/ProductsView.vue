<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useProductStore } from '@/stores/productStore'
import { useCategoryStore } from '@/stores/categoryStore'
import { useAnotaAIStore } from '@/stores/anotaAIStore'
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
import type { ProductRequest, ProductResponse, IncludeRequest } from '@/types/Product'
import { catalogOriginLabel, catalogOriginPillColor } from '@/types/Category'
import { useToast } from '@/composables/useToast'

const productStore = useProductStore()
const categoryStore = useCategoryStore()
const anotaAIStore = useAnotaAIStore()
const { showToast } = useToast()

const syncClearRecipes = ref(false)

const showModal = ref(false)
const showRecipeModal = ref(false)
const editing = ref<ProductResponse | null>(null)
const selectedProduct = ref<ProductResponse | null>(null)
const form = ref<ProductRequest>({ name: '', price: 0, categoryId: '' })
/**
 * A margem ideal fica fora de `form` porque o input precisa distinguir "vazio"
 * (não acompanhar) de `0`, o que um number binding não faz.
 */
const targetMarginInput = ref('')
const targetMarginError = ref<string | null>(null)
const recipeForm = ref<IncludeRequest>({ name: '', cost: 0, quantity: 1, kind: 'PACKAGING' })
const confirmDeleteId = ref<string | null>(null)
const confirmClearRecipe = ref(false)
const editingIncludeId = ref<string | null>(null)
const editForm = ref<{ name: string; cost: number; quantity: number }>({ name: '', cost: 0, quantity: 1 })

const categoryFilter = ref<string>('')
const statusFilter = ref<'' | 'ACTIVE' | 'INACTIVE'>('')

// Deterministic color stripe per category — derived from category name hash.
const PALETTE = ['#fbbf24', '#a78bfa', '#60a5fa', '#34d399', '#f87171', '#e879f9', '#22d3ee', '#94a3b8']
function colorFor(name?: string | null): string {
  if (!name) return PALETTE[0]!
  let h = 0
  for (const c of name) h = (h * 31 + c.charCodeAt(0)) | 0
  return PALETTE[Math.abs(h) % PALETTE.length]!
}

const filteredItems = computed(() => {
  return productStore.items.filter((p) => {
    if (categoryFilter.value && p.categoryId !== categoryFilter.value) return false
    if (statusFilter.value && p.status !== statusFilter.value) return false
    return true
  })
})

const activeCount = computed(() => productStore.items.filter((p) => p.status === 'ACTIVE').length)

let searchDebounce: ReturnType<typeof setTimeout> | null = null
function onSearchInput(v: string) {
  productStore.search = v
  if (searchDebounce) clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => productStore.fetchPage({ search: v, page: 0 }), 300)
}
function onPageChange(p: number) {
  if (p < 0 || p >= productStore.totalPages) return
  productStore.fetchPage({ page: p })
}

async function handleSyncCatalog() {
  anotaAIStore.clearResult()
  try {
    await anotaAIStore.syncCatalog({ clearRecipes: syncClearRecipes.value })
    await productStore.fetchPage({ page: 0 })
  } catch {
    /* error in store */
  }
}

function openCreate() {
  editing.value = null
  form.value = { name: '', price: 0, categoryId: '' }
  targetMarginInput.value = ''
  targetMarginError.value = null
  showModal.value = true
}
function openEdit(p: ProductResponse) {
  editing.value = p
  form.value = { name: p.name, price: Number(p.price), categoryId: p.categoryId }
  targetMarginInput.value = p.targetMarginPct == null ? '' : String(p.targetMarginPct)
  targetMarginError.value = null
  showModal.value = true
}
function closeModal() {
  showModal.value = false
  editing.value = null
}
/** `null` quando o campo está vazio; `NaN` quando o texto não é um número. */
function parseTargetMargin(): number | null {
  const raw = targetMarginInput.value.trim()
  if (raw === '') return null
  return Number(raw)
}
async function handleSubmit() {
  const targetMarginPct = parseTargetMargin()
  if (
    targetMarginPct !== null &&
    (Number.isNaN(targetMarginPct) || targetMarginPct < 0 || targetMarginPct > 100)
  ) {
    targetMarginError.value = 'Margem ideal deve estar entre 0 e 100.'
    return
  }
  targetMarginError.value = null
  // O campo vai sempre no payload: o update do backend o atribui incondicionalmente,
  // então omiti-lo apagaria a margem ideal já gravada no produto.
  const payload: ProductRequest = { ...form.value, targetMarginPct }
  try {
    if (editing.value) {
      await productStore.update(editing.value.id, payload)
    } else {
      await productStore.create(payload)
      showToast('Produto criado com sucesso!')
    }
    closeModal()
  } catch {
    /* error in store */
  }
}

async function openRecipe(p: ProductResponse) {
  selectedProduct.value = p
  recipeForm.value = { name: '', cost: 0, quantity: 1, kind: 'PACKAGING' }
  showRecipeModal.value = true
  await productStore.fetchIncludes(p.id)
}
function closeRecipe() {
  showRecipeModal.value = false
  selectedProduct.value = null
}
async function handleAddRecipeItem() {
  if (!selectedProduct.value) return
  try {
    await productStore.addInclude(selectedProduct.value.id, recipeForm.value)
    recipeForm.value = { name: '', cost: 0, quantity: 1, kind: 'PACKAGING' }
  } catch {
    /* error in store */
  }
}
function handleStartEdit(it: { id: string; name: string; cost: number; quantity: number }) {
  editingIncludeId.value = it.id
  editForm.value = { name: it.name, cost: Number(it.cost), quantity: Number(it.quantity) }
}
function handleCancelEdit() {
  editingIncludeId.value = null
}
async function handleUpdateRecipeItem(includeId: string, kind: string) {
  if (!selectedProduct.value) return
  try {
    await productStore.updateInclude(selectedProduct.value.id, includeId, {
      name: editForm.value.name,
      cost: editForm.value.cost,
      quantity: editForm.value.quantity,
      kind: kind as 'INGREDIENT' | 'PACKAGING',
    })
    editingIncludeId.value = null
  } catch {
    /* error in store */
  }
}
async function handleRemoveRecipeItem(includeId: string) {
  if (!selectedProduct.value) return
  try {
    await productStore.removeInclude(selectedProduct.value.id, includeId)
  } catch {
    /* error in store */
  }
}
async function handleClearRecipe() {
  if (!selectedProduct.value) return
  try {
    await productStore.clearRecipe(selectedProduct.value.id)
  } catch {
    /* error in store */
  } finally {
    confirmClearRecipe.value = false
  }
}
async function handleDelete() {
  if (!confirmDeleteId.value) return
  try {
    await productStore.remove(confirmDeleteId.value)
  } catch {
    /* error in store */
  }
  confirmDeleteId.value = null
}

const ingredientIncludes = computed(() =>
  productStore.includes.filter((i) => !i.kind || i.kind === 'INGREDIENT'),
)
const packagingIncludes = computed(() =>
  productStore.includes.filter((i) => i.kind === 'PACKAGING'),
)
const hasMultipleKinds = computed(() => packagingIncludes.value.length > 0)

const ingredientSubtotal = computed(() =>
  ingredientIncludes.value.reduce((s, i) => s + Number(i.totalCost), 0),
)
const packagingSubtotal = computed(() =>
  packagingIncludes.value.reduce((s, i) => s + Number(i.totalCost), 0),
)

const recipeTotalCost = computed(() =>
  productStore.includes.reduce((s, i) => s + Number(i.totalCost), 0),
)

// The Ficha column ("Abrir") only exists on phones/tablets, where the row
// actions sit far right after horizontal scroll; desktop opens the recipe
// via the row action icon instead.
const smallScreenQuery =
  typeof window.matchMedia === 'function' ? window.matchMedia('(max-width: 1024px)') : null
const isSmallScreen = ref(smallScreenQuery?.matches ?? false)
function onScreenChange(e: MediaQueryListEvent) {
  isSmallScreen.value = e.matches
}
onMounted(() => smallScreenQuery?.addEventListener('change', onScreenChange))
onUnmounted(() => smallScreenQuery?.removeEventListener('change', onScreenChange))

// Tighter fixed columns (Preço/Custo/Margem/Ficha) so the name column keeps
// room on smaller screens instead of truncating.
const cols = computed(() =>
  isSmallScreen.value
    ? '2.6fr 0.8fr 84px 84px 84px 64px 88px 112px'
    : '2.6fr 0.8fr 84px 84px 84px 88px 112px',
)
const tableMinWidth = computed(() => (isSmallScreen.value ? '780px' : '708px'))

onMounted(() => {
  productStore.fetchPage({ page: 0, search: '' })
  categoryStore.fetchAll()
})
</script>

<template>
  <div style="display: flex; flex-direction: column; flex: 1">
    <UITopbar
      title="Produtos"
      :subtitle="`${productStore.totalElements} produtos · ${activeCount} ativos nesta página`"
    >
      <template #actions>
        <label
          :style="{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            fontSize: '12px',
            color: UI.textSub,
            cursor: 'pointer',
          }"
          title="Apaga todos os itens das fichas técnicas antes de re-importar"
        >
          <input v-model="syncClearRecipes" type="checkbox" data-testid="sync-clear-recipes-checkbox" />
          Limpar fichas técnicas
        </label>
        <UIBtn
          icon="sync"
          variant="secondary"
          :disabled="anotaAIStore.syncingCatalog"
          @click="handleSyncCatalog"
        >
          {{ anotaAIStore.syncingCatalog ? 'Sincronizando…' : 'Sincronizar Cardápio' }}
        </UIBtn>
        <UIBtn icon="plus" variant="dark" data-testid="new-product-button" @click="openCreate">
          Novo Produto
        </UIBtn>
      </template>
    </UITopbar>

    <div class="view-content">
      <div
        v-if="anotaAIStore.error"
        :style="{
          padding: '10px 14px',
          background: UI.roseBg,
          color: UI.rose2,
          borderRadius: '10px',
          fontSize: '13px',
          marginBottom: '12px',
        }"
      >
        {{ anotaAIStore.error }}
      </div>
      <div
        v-if="anotaAIStore.lastResult && !anotaAIStore.error"
        :style="{
          padding: '10px 14px',
          background: UI.emeraldBg,
          color: UI.emerald2,
          borderRadius: '10px',
          fontSize: '13px',
          marginBottom: '12px',
        }"
      >
        Categorias: {{ anotaAIStore.lastResult.categoriesCreated }} criada(s),
        {{ anotaAIStore.lastResult.categoriesUpdated }} atualizada(s) · Produtos:
        {{ anotaAIStore.lastResult.productsCreated }} criado(s),
        {{ anotaAIStore.lastResult.productsUpdated }} atualizado(s).
      </div>
      <div
        v-if="productStore.error"
        :style="{
          padding: '10px 14px',
          background: UI.roseBg,
          color: UI.rose2,
          borderRadius: '10px',
          fontSize: '13px',
          marginBottom: '12px',
        }"
      >
        {{ productStore.error }}
      </div>

      <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 18px; flex-wrap: wrap">
        <UISearch
          :model-value="productStore.search"
          placeholder="Buscar produto por nome…"
          :width="340"
          @update:model-value="onSearchInput"
        />
        <UISelect v-model="categoryFilter" :width="180">
          <option value="">Todas categorias</option>
          <option v-for="c in categoryStore.items" :key="c.id" :value="c.id">{{ c.name }}</option>
        </UISelect>
        <UISelect v-model="statusFilter" :width="140">
          <option value="">Todos status</option>
          <option value="ACTIVE">Ativos</option>
          <option value="INACTIVE">Inativos</option>
        </UISelect>
        <div style="flex: 1" />
      </div>

      <UIEmpty
        v-if="!productStore.loading && !filteredItems.length && !productStore.search"
        icon="burger"
        accent="emerald"
        title="Cadastre seu primeiro produto"
        body="Defina preço, categoria e ficha técnica. Com a ficha cadastrada, o JetMenu calcula automaticamente seu custo e margem em cada pedido."
        primary="Adicionar produto"
        secondary="Sincronizar Cardápio"
        @primary="openCreate"
        @secondary="handleSyncCatalog"
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
            gap: '8px',
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
          <span>Categoria</span>
          <span style="text-align: right">Preço</span>
          <span style="text-align: right">Custo</span>
          <span style="text-align: right">Margem</span>
          <span v-if="isSmallScreen" data-testid="ficha-column-header">Ficha</span>
          <span>Status</span>
          <span style="text-align: right">Ações</span>
        </div>

        <div>
          <div
            v-if="productStore.loading"
            :style="{ padding: '32px', textAlign: 'center', color: UI.textMute }"
          >
            Carregando…
          </div>
          <div
            v-else-if="!filteredItems.length"
            :style="{ padding: '60px 32px', textAlign: 'center', color: UI.textMute, fontSize: '13px' }"
          >
            Nenhum produto encontrado.
          </div>
          <div
            v-for="(p, i) in filteredItems"
            v-else
            :key="p.id"
            class="ui-row"
            :style="{
              display: 'grid',
              gridTemplateColumns: cols,
              gap: '8px',
              padding: '12px 18px',
              borderBottom: i === filteredItems.length - 1 ? 'none' : `1px solid ${UI.borderSub}`,
              fontSize: '13px',
              color: UI.text,
              alignItems: 'center',
            }"
          >
            <span style="display: flex; align-items: center; gap: 11px; min-width: 0">
              <span
                :style="{
                  width: '4px',
                  height: '26px',
                  borderRadius: '2px',
                  background: colorFor(p.categoryName),
                  flexShrink: 0,
                }"
              />
              <span
                :style="{
                  fontWeight: 600,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }"
              >
                {{ p.name }}
              </span>
              <UIPill
                :color="catalogOriginPillColor(p.origin)"
                size="sm"
                data-testid="product-origin-pill"
              >
                {{ catalogOriginLabel(p.origin) }}
              </UIPill>
            </span>
            <span :style="{ color: UI.textSub }">{{ p.categoryName }}</span>
            <span
              :style="{
                textAlign: 'right',
                fontWeight: 600,
                fontVariantNumeric: 'tabular-nums',
              }"
            >
              {{ brl(Number(p.price)) }}
            </span>
            <span :style="{ textAlign: 'right', color: UI.textMute, fontVariantNumeric: 'tabular-nums' }">
              —
            </span>
            <span :style="{ textAlign: 'right', color: UI.textMute, fontVariantNumeric: 'tabular-nums' }">
              —
            </span>
            <span v-if="isSmallScreen">
              <button
                type="button"
                :data-testid="`product-${p.id}-open-recipe-pill`"
                title="Abrir ficha técnica"
                style="border: none; background: none; padding: 0; cursor: pointer"
                @click="openRecipe(p)"
              >
                <UIPill color="blue" size="sm">Abrir</UIPill>
              </button>
            </span>
            <span>
              <UIPill :color="p.status === 'ACTIVE' ? 'emerald' : 'gray'" dot>
                {{ p.status === 'ACTIVE' ? 'Ativo' : 'Inativo' }}
              </UIPill>
            </span>
            <span style="display: flex; gap: 5px; justify-content: flex-end">
              <UIRowAction
                icon="file"
                color="blue"
                label="Ficha técnica"
                :data-testid="`product-${p.id}-recipe-button`"
                @click="openRecipe(p)"
              />
              <UIRowAction
                icon="edit"
                color="gray"
                label="Editar"
                :data-testid="`product-${p.id}-edit-button`"
                @click="openEdit(p)"
              />
              <UIRowAction
                icon="trash"
                color="rose"
                label="Excluir"
                :data-testid="`product-${p.id}-delete-button`"
                @click="confirmDeleteId = p.id"
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
            padding: '10px 18px',
            borderTop: `1px solid ${UI.border}`,
            background: UI.bgSoft,
            fontSize: '12px',
            color: UI.textSub,
            flexShrink: 0,
          }"
        >
          <span>
            Página {{ productStore.page + 1 }} de {{ Math.max(productStore.totalPages, 1) }}
            · {{ productStore.totalElements }} produtos
          </span>
          <div style="display: flex; gap: 6px; align-items: center">
            <UIBtn
              size="sm"
              icon="chevLeft"
              variant="secondary"
              :disabled="productStore.page === 0 || productStore.loading"
              @click="onPageChange(productStore.page - 1)"
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
              {{ productStore.page + 1 }}
            </span>
            <UIBtn
              size="sm"
              icon="chevRight"
              variant="secondary"
              :disabled="productStore.page >= productStore.totalPages - 1 || productStore.loading"
              @click="onPageChange(productStore.page + 1)"
            >
              Próximo
            </UIBtn>
          </div>
        </div>
      </div>
    </div>

    <!-- Create/Edit modal -->
    <UIModal
      v-if="showModal"
      :title="editing ? 'Editar Produto' : 'Novo Produto'"
      :subtitle="editing ? 'Atualize as informações do produto' : 'Preencha os dados do novo produto'"
      :width="520"
      @close="closeModal"
    >
      <form id="product-form" data-testid="product-form" @submit.prevent="handleSubmit">
        <div style="display: flex; flex-direction: column; gap: 14px">
          <UIField label="Nome">
            <UIInput
              v-model="form.name"
              placeholder="Nome do produto"
              data-testid="product-name-input"
            />
          </UIField>
          <UIField label="Categoria">
            <UISelect
              v-model="form.categoryId"
              placeholder="Selecione…"
              data-testid="product-category-select"
            >
              <option v-for="c in categoryStore.items" :key="c.id" :value="c.id">{{ c.name }}</option>
            </UISelect>
          </UIField>
          <UIField label="Preço (R$)" hint="Valor de venda ao cliente">
            <UIInput
              v-model.number="form.price"
              type="number"
              placeholder="0,00"
              data-testid="product-price-input"
            />
          </UIField>
          <UIField label="Margem ideal (%)" hint="Opcional. Deixe em branco para não acompanhar.">
            <UIInput
              v-model="targetMarginInput"
              type="number"
              placeholder="Ex.: 60"
              data-testid="product-target-margin-input"
            />
            <div
              v-if="targetMarginError"
              data-testid="product-target-margin-error"
              :style="{ color: UI.rose, fontSize: '11.5px', marginTop: '4px' }"
            >
              {{ targetMarginError }}
            </div>
          </UIField>
          <div
            :style="{
              padding: '12px',
              background: UI.blueBg,
              borderRadius: '9px',
              fontSize: '12px',
              color: UI.blue2,
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
            }"
          >
            <UIIcon name="info" :size="16" />
            {{
              editing
                ? 'Edite a ficha técnica separadamente para alterar o custo.'
                : 'Após criar, configure a ficha técnica para calcular o custo automaticamente.'
            }}
          </div>
        </div>
      </form>

      <template #footer>
        <UIBtn
          v-if="editing"
          variant="softDanger"
          icon="trash"
          @click="
            () => {
              if (editing) confirmDeleteId = editing.id
              closeModal()
            }
          "
        >
          Excluir
        </UIBtn>
        <div style="flex: 1" />
        <UIBtn variant="secondary" @click="closeModal">Cancelar</UIBtn>
        <UIBtn
          variant="primary"
          icon="check"
          :disabled="productStore.loading"
          @click="handleSubmit"
        >
          {{ editing ? 'Salvar' : 'Criar produto' }}
        </UIBtn>
      </template>
    </UIModal>

    <!-- Recipe modal -->
    <UIModal
      v-if="showRecipeModal && selectedProduct"
      :title="`Ficha Técnica — ${selectedProduct.name}`"
      :subtitle="`${productStore.includes.length} ${productStore.includes.length === 1 ? 'item' : 'itens'}`"
      :width="720"
      @close="closeRecipe"
    >
      <div style="display: flex; flex-direction: column; gap: 16px">
        <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px">
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
              Preço de venda
            </div>
            <div
              :style="{
                fontSize: '18px',
                fontWeight: 700,
                color: UI.text,
                fontVariantNumeric: 'tabular-nums',
              }"
            >
              {{ brl(Number(selectedProduct.price)) }}
            </div>
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
              Custo total
            </div>
            <div
              :style="{
                fontSize: '18px',
                fontWeight: 700,
                color: UI.textSub,
                fontVariantNumeric: 'tabular-nums',
              }"
            >
              {{ brl(recipeTotalCost) }}
            </div>
          </div>
        </div>

        <form
          data-testid="recipe-add-form"
          :style="{
            padding: '12px 14px',
            background: UI.bgSoft,
            border: `1px dashed ${UI.border}`,
            borderRadius: '10px',
            display: 'grid',
            gridTemplateColumns: '1fr 120px 100px auto',
            gap: '10px',
            alignItems: 'flex-end',
          }"
          @submit.prevent="handleAddRecipeItem"
        >
          <UIField label="Embalagem / Insumo">
            <UIInput
              v-model="recipeForm.name"
              placeholder="Ex.: Copo, Colher, Embalagem…"
              data-testid="recipe-name-input"
            />
          </UIField>
          <UIField label="Custo (R$)">
            <UIInput
              v-model.number="recipeForm.cost"
              type="number"
              placeholder="0,00"
              data-testid="recipe-cost-input"
            />
          </UIField>
          <UIField label="Qtd.">
            <UIInput
              v-model.number="recipeForm.quantity"
              type="number"
              placeholder="1"
              data-testid="recipe-quantity-input"
            />
          </UIField>
          <UIBtn variant="primary" icon="plus" type="submit">Adicionar</UIBtn>
        </form>

        <div
          :style="{
            background: UI.panel,
            border: `1px solid ${UI.border}`,
            borderRadius: '11px',
            overflow: 'hidden',
          }"
        >
          <!-- header -->
          <div
            :style="{
              display: 'grid',
              gridTemplateColumns: '1fr 90px 110px 80px',
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
            <span>Item</span>
            <span style="text-align: right">Qtd</span>
            <span style="text-align: right">Custo total</span>
            <span />
          </div>

          <!-- empty -->
          <div
            v-if="!productStore.includes.length"
            :style="{ padding: '24px', textAlign: 'center', color: UI.textMute, fontSize: '13px' }"
          >
            Nenhum item na ficha técnica.
          </div>

          <template v-else>
            <!-- seção Embalagens & Insumos (insumos listados antes dos ingredientes) -->
            <template v-if="hasMultipleKinds">
              <div
                :style="{
                  padding: '7px 14px',
                  background: UI.bgSoft,
                  borderBottom: `1px solid ${UI.border}`,
                  fontSize: '10px',
                  color: UI.textMute,
                  fontWeight: 700,
                  textTransform: 'uppercase',
                  letterSpacing: '0.6px',
                  display: 'flex',
                  justifyContent: 'space-between',
                }"
              >
                <span>Embalagens & Insumos</span>
                <span :style="{ fontVariantNumeric: 'tabular-nums' }">{{ brl(packagingSubtotal) }}</span>
              </div>
              <template v-for="(it, i) in packagingIncludes" :key="it.id">
                <!-- edit mode -->
                <div
                  v-if="editingIncludeId === it.id"
                  :style="{
                    display: 'grid',
                    gridTemplateColumns: '1fr 90px 110px 80px',
                    gap: '8px',
                    padding: '8px 14px',
                    alignItems: 'center',
                    borderBottom: i === packagingIncludes.length - 1 ? 'none' : `1px solid ${UI.borderSub}`,
                    background: UI.blueBg,
                  }"
                >
                  <UIInput
                    v-model="editForm.name"
                    :data-testid="`include-${it.id}-name-input`"
                    placeholder="Nome"
                  />
                  <UIInput
                    v-model.number="editForm.quantity"
                    type="number"
                    :data-testid="`include-${it.id}-quantity-input`"
                    placeholder="Qtd"
                  />
                  <UIInput
                    v-model.number="editForm.cost"
                    type="number"
                    :data-testid="`include-${it.id}-cost-input`"
                    placeholder="Custo"
                  />
                  <span style="display: flex; gap: 4px; justify-content: flex-end">
                    <UIRowAction
                      icon="check"
                      color="blue"
                      :data-testid="`include-${it.id}-confirm-button`"
                      @click="handleUpdateRecipeItem(it.id, it.kind)"
                    />
                    <UIRowAction
                      icon="x"
                      color="gray"
                      :data-testid="`include-${it.id}-cancel-button`"
                      @click="handleCancelEdit"
                    />
                  </span>
                </div>
                <!-- view mode -->
                <div
                  v-else
                  :style="{
                    display: 'grid',
                    gridTemplateColumns: '1fr 90px 110px 80px',
                    gap: '10px',
                    padding: '11px 14px',
                    alignItems: 'center',
                    fontSize: '13px',
                    borderBottom: i === packagingIncludes.length - 1 ? 'none' : `1px solid ${UI.borderSub}`,
                  }"
                >
                  <span :style="{ fontWeight: 600 }">{{ it.name }}</span>
                  <span :style="{ textAlign: 'right', color: UI.textSub, fontVariantNumeric: 'tabular-nums' }">
                    {{ it.quantity }}
                  </span>
                  <span :style="{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', fontWeight: 600 }">
                    {{ brl(Number(it.totalCost)) }}
                  </span>
                  <span style="display: flex; gap: 4px; justify-content: flex-end">
                    <UIRowAction
                      icon="edit"
                      color="blue"
                      :data-testid="`include-${it.id}-edit-button`"
                      @click="handleStartEdit(it)"
                    />
                    <UIRowAction icon="trash" color="rose" @click="handleRemoveRecipeItem(it.id)" />
                  </span>
                </div>
              </template>
            </template>

            <!-- seção Ingredientes -->
            <template v-if="hasMultipleKinds">
              <div
                :style="{
                  padding: '7px 14px',
                  background: UI.bgSoft,
                  borderTop: `1px solid ${UI.border}`,
                  borderBottom: `1px solid ${UI.border}`,
                  fontSize: '10px',
                  color: UI.textMute,
                  fontWeight: 700,
                  textTransform: 'uppercase',
                  letterSpacing: '0.6px',
                  display: 'flex',
                  justifyContent: 'space-between',
                }"
              >
                <span>Ingredientes</span>
                <span :style="{ fontVariantNumeric: 'tabular-nums' }">{{ brl(ingredientSubtotal) }}</span>
              </div>
            </template>
            <template v-for="(it, i) in ingredientIncludes" :key="it.id">
              <!-- edit mode -->
              <div
                v-if="editingIncludeId === it.id"
                :style="{
                  display: 'grid',
                  gridTemplateColumns: '1fr 90px 110px 80px',
                  gap: '8px',
                  padding: '8px 14px',
                  alignItems: 'center',
                  borderBottom:
                    i === ingredientIncludes.length - 1 ? 'none' : `1px solid ${UI.borderSub}`,
                  background: UI.blueBg,
                }"
              >
                <UIInput
                  v-model="editForm.name"
                  :data-testid="`include-${it.id}-name-input`"
                  placeholder="Nome"
                />
                <UIInput
                  v-model.number="editForm.quantity"
                  type="number"
                  :data-testid="`include-${it.id}-quantity-input`"
                  placeholder="Qtd"
                />
                <UIInput
                  v-model.number="editForm.cost"
                  type="number"
                  :data-testid="`include-${it.id}-cost-input`"
                  placeholder="Custo"
                />
                <span style="display: flex; gap: 4px; justify-content: flex-end">
                  <UIRowAction
                    icon="check"
                    color="blue"
                    :data-testid="`include-${it.id}-confirm-button`"
                    @click="handleUpdateRecipeItem(it.id, it.kind)"
                  />
                  <UIRowAction
                    icon="x"
                    color="gray"
                    :data-testid="`include-${it.id}-cancel-button`"
                    @click="handleCancelEdit"
                  />
                </span>
              </div>
              <!-- view mode -->
              <div
                v-else
                :style="{
                  display: 'grid',
                  gridTemplateColumns: '1fr 90px 110px 80px',
                  gap: '10px',
                  padding: '11px 14px',
                  alignItems: 'center',
                  fontSize: '13px',
                  borderBottom:
                    i === ingredientIncludes.length - 1 ? 'none' : `1px solid ${UI.borderSub}`,
                }"
              >
                <span :style="{ fontWeight: 600 }">{{ it.name }}</span>
                <span :style="{ textAlign: 'right', color: UI.textSub, fontVariantNumeric: 'tabular-nums' }">
                  {{ it.quantity }}
                </span>
                <span :style="{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', fontWeight: 600 }">
                  {{ brl(Number(it.totalCost)) }}
                </span>
                <span style="display: flex; gap: 4px; justify-content: flex-end">
                  <UIRowAction
                    icon="edit"
                    color="blue"
                    :data-testid="`include-${it.id}-edit-button`"
                    @click="handleStartEdit(it)"
                  />
                  <UIRowAction icon="trash" color="rose" @click="handleRemoveRecipeItem(it.id)" />
                </span>
              </div>
            </template>
          </template>
        </div>
      </div>

      <template #footer>
        <UIBtn
          v-if="productStore.includes.length"
          variant="softDanger"
          icon="trash"
          @click="confirmClearRecipe = true"
        >
          Limpar ficha
        </UIBtn>
        <div style="flex: 1" />
        <UIBtn variant="secondary" @click="closeRecipe">Fechar</UIBtn>
      </template>
    </UIModal>

    <UIModal
      v-if="confirmClearRecipe"
      title="Limpar ficha técnica"
      :subtitle="`Remove todos os itens da ficha de ${selectedProduct?.name ?? ''}`"
      :width="420"
      @close="confirmClearRecipe = false"
    >
      <p :style="{ color: UI.textSub, fontSize: '13.5px', lineHeight: 1.6 }">
        Essa ação não pode ser desfeita.
      </p>
      <template #footer>
        <UIBtn variant="secondary" @click="confirmClearRecipe = false">Cancelar</UIBtn>
        <UIBtn variant="danger" icon="trash" @click="handleClearRecipe">Limpar tudo</UIBtn>
      </template>
    </UIModal>

    <UIModal
      v-if="confirmDeleteId"
      title="Excluir produto"
      subtitle="Esta ação não pode ser desfeita"
      :width="420"
      @close="confirmDeleteId = null"
    >
      <p :style="{ color: UI.textSub, fontSize: '13.5px', lineHeight: 1.6 }">
        Tem certeza que deseja excluir este produto?
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
</style>
