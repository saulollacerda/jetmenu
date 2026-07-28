<script setup lang="ts">
import { ref } from 'vue'
import { UI, UIBtn, UIIcon, UIModal, UIPill } from '@/design'
import {
  ifoodCatalogService,
  importErrorMessage,
  type IfoodCatalogImportResult,
} from '@/services/ifoodCatalogService'
import { useProductStore } from '@/stores/productStore'
import { useCategoryStore } from '@/stores/categoryStore'

const emit = defineEmits<{
  (e: 'imported', result: IfoodCatalogImportResult): void
  (e: 'close'): void
}>()

const productStore = useProductStore()
const categoryStore = useCategoryStore()

const importing = ref(false)
const result = ref<IfoodCatalogImportResult | null>(null)
const error = ref<string | null>(null)
const showProducts = ref(false)

const skippedItems = () => result.value?.items.filter((item) => item.outcome === 'SKIPPED') ?? []
const importedItems = () =>
  result.value?.items.filter((item) => item.outcome === 'IMPORTED' || item.outcome === 'LINKED') ?? []

async function doImport() {
  importing.value = true
  error.value = null
  try {
    const imported = await ifoodCatalogService.importCatalog()
    result.value = imported
    // stale caches would hide the just-imported products/categories
    await Promise.all([productStore.fetchAll(true), categoryStore.fetchAll(true)])
    emit('imported', imported)
  } catch (e: unknown) {
    error.value = importErrorMessage(e)
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <UIModal
    title="Importar cardápio do iFood"
    subtitle="Traz seus produtos e categorias do iFood para o JetMenu — assim os pedidos chegam sem erros de produto não cadastrado."
    :width="560"
    title-test-id="ifood-import-modal-title"
    @close="emit('close')"
  >
    <!-- Result -->
    <div v-if="result" data-testid="ifood-import-result">
      <div :style="{ display: 'flex', gap: '10px', marginBottom: '14px' }">
        <div
          v-for="stat in [
            { label: 'Importados', value: result.importedProducts, color: UI.emerald2, bg: UI.emeraldBg },
            { label: 'Vinculados', value: result.linkedProducts, color: UI.blue, bg: UI.blueBg },
            { label: 'Ignorados', value: result.skippedProducts, color: UI.textSub, bg: UI.bg },
          ]"
          :key="stat.label"
          :style="{
            flex: 1,
            background: stat.bg,
            borderRadius: '10px',
            padding: '12px',
            textAlign: 'center',
          }"
        >
          <div :style="{ fontSize: '22px', fontWeight: 700, color: stat.color }">{{ stat.value }}</div>
          <div :style="{ fontSize: '12px', color: UI.textSub }">{{ stat.label }}</div>
        </div>
      </div>

      <div :style="{ fontSize: '13px', color: UI.textSub, marginBottom: '10px' }">
        Categorias: {{ result.importedCategories }} importadas, {{ result.linkedCategories }} vinculadas.
      </div>

      <div v-if="importedItems().length" :style="{ marginBottom: '10px' }">
        <UIBtn
          variant="ghost"
          size="sm"
          data-testid="ifood-import-toggle-products"
          @click="showProducts = !showProducts"
        >
          <UIIcon :name="showProducts ? 'x' : 'link'" :size="12" />
          {{ showProducts ? 'Ocultar produtos' : 'Visualizar produtos importados' }}
        </UIBtn>

        <div
          v-if="showProducts"
          data-testid="ifood-import-products-list"
          :style="{
            marginTop: '8px',
            maxHeight: '220px',
            overflowY: 'auto',
            border: `1px solid ${UI.border}`,
            borderRadius: '9px',
          }"
        >
          <div
            v-for="(item, i) in importedItems()"
            :key="item.name + (item.externalCode ?? '')"
            :style="{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '8px 12px',
              borderTop: i > 0 ? `1px solid ${UI.border}` : 'none',
            }"
          >
            <span :style="{ flex: 1, fontSize: '13px', color: UI.text }">{{ item.name }}</span>
            <span
              v-if="item.externalCode"
              :style="{
                fontSize: '11px',
                color: UI.textMute,
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
              }"
            >
              {{ item.externalCode }}
            </span>
            <UIPill :color="item.outcome === 'IMPORTED' ? 'emerald' : 'blue'" size="sm">
              {{ item.outcome === 'IMPORTED' ? 'Importado' : 'Vinculado' }}
            </UIPill>
          </div>
        </div>
      </div>

      <div v-if="skippedItems().length" :style="{ marginTop: '8px' }">
        <div :style="{ fontSize: '12px', fontWeight: 600, color: UI.textSub, marginBottom: '6px' }">
          Itens ignorados
        </div>
        <div
          v-for="item in skippedItems()"
          :key="item.name + (item.externalCode ?? '')"
          :style="{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            padding: '8px 10px',
            borderRadius: '8px',
            background: UI.bg,
            marginBottom: '6px',
          }"
        >
          <UIPill color="gray" size="sm">{{ item.name }}</UIPill>
          <span :style="{ fontSize: '12px', color: UI.textSub }">{{ item.reason }}</span>
        </div>
      </div>

      <div
        :style="{
          display: 'flex',
          gap: '8px',
          alignItems: 'flex-start',
          marginTop: '14px',
          fontSize: '12px',
          color: UI.textSub,
        }"
      >
        <UIIcon name="alert" :size="14" :style="{ flexShrink: 0, marginTop: '1px' }" />
        <span>
          Produtos importados entram sem custo — preencha a ficha técnica de cada um para
          acompanhar custo e lucro dos pedidos.
        </span>
      </div>
    </div>

    <!-- Idle / importing -->
    <div v-else>
      <div :style="{ fontSize: '13px', color: UI.textSub, lineHeight: 1.6 }">
        A importação lê o cardápio de entrega da sua loja no iFood e cadastra os produtos e
        categorias aqui. Produtos que você já cadastrou são apenas vinculados pelo código PDV —
        a importação <strong>não altera</strong> preços nem dados de produtos existentes.
      </div>
      <div
        v-if="error"
        data-testid="ifood-import-error"
        :style="{ fontSize: '12px', color: '#ef4444', marginTop: '12px' }"
      >
        {{ error }}
      </div>
    </div>

    <template #footer>
      <template v-if="result">
        <UIBtn variant="primary" data-testid="ifood-import-close" @click="emit('close')">
          Fechar
        </UIBtn>
      </template>
      <template v-else>
        <UIBtn variant="ghost" data-testid="ifood-import-cancel" @click="emit('close')">
          Cancelar
        </UIBtn>
        <UIBtn
          variant="primary"
          data-testid="ifood-import-start"
          :disabled="importing"
          @click="doImport"
        >
          {{ importing ? 'Importando…' : 'Importar cardápio' }}
        </UIBtn>
      </template>
    </template>
  </UIModal>
</template>
