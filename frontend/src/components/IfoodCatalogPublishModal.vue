<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { UI, UIBtn, UIIcon, UIModal, UIPill, brl } from '@/design'
import {
  ifoodCatalogService,
  publishErrorMessage,
  type IfoodCatalogBatchAccepted,
  type IfoodCatalogBatchStatus,
  type IfoodCatalogItemStatus,
  type IfoodCatalogPublishResult,
} from '@/services/ifoodCatalogService'
import { useProductStore } from '@/stores/productStore'

const emit = defineEmits<{
  (e: 'published', result: IfoodCatalogPublishResult): void
  (e: 'close'): void
}>()

const productStore = useProductStore()

// Batch operations on the iFood side are asynchronous: accept now, result later.
const POLL_INTERVAL_MS = 2000
const POLL_MAX_ATTEMPTS = 10
const PENDING_BATCH_STATUSES = ['CREATED', 'PENDING', 'PROCESSING', 'IN_PROGRESS', 'RUNNING']

const publishing = ref(false)
const result = ref<IfoodCatalogPublishResult | null>(null)
const error = ref<string | null>(null)
const selectedIds = ref<string[]>([])

const batchLabel = ref<string | null>(null)
const batchAccepted = ref<IfoodCatalogBatchAccepted | null>(null)
const batchStatus = ref<IfoodCatalogBatchStatus | null>(null)
const batchError = ref<string | null>(null)
const batchRunning = ref(false)

let pollTimer: ReturnType<typeof setTimeout> | null = null

const products = computed(() => productStore.items.filter((p) => p.status === 'ACTIVE'))

const publishedItems = computed(() => result.value?.items.filter((i) => i.outcome === 'PUBLISHED') ?? [])
const issueItems = computed(() => result.value?.items.filter((i) => i.outcome !== 'PUBLISHED') ?? [])
// O backend agrega falhas em `skippedProducts`; aqui os dois são separados a partir dos itens.
const failedCount = computed(() => result.value?.items.filter((i) => i.outcome === 'FAILED').length ?? 0)
const skippedCount = computed(() => result.value?.items.filter((i) => i.outcome === 'SKIPPED').length ?? 0)
const publishedIds = computed(() => publishedItems.value.map((i) => i.productId))

const batchDone = computed(() => {
  if (batchAccepted.value && !batchAccepted.value.batchId) return true
  return !!batchStatus.value && !PENDING_BATCH_STATUSES.includes(batchStatus.value.status)
})

onMounted(async () => {
  try {
    await productStore.fetchAll()
  } catch {
    /* the store already surfaces its own error state */
  }
  selectedIds.value = products.value.map((p) => p.id)
})

function clearPoll() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

onUnmounted(clearPoll)

function selectAll() {
  selectedIds.value = products.value.map((p) => p.id)
}

function selectNone() {
  selectedIds.value = []
}

async function doPublish() {
  publishing.value = true
  error.value = null
  try {
    const published = await ifoodCatalogService.publishCatalog(selectedIds.value)
    result.value = published
    emit('published', published)
  } catch (e: unknown) {
    error.value = publishErrorMessage(e)
  } finally {
    publishing.value = false
  }
}

async function pollBatch(batchId: string, attempt = 1) {
  try {
    const status = await ifoodCatalogService.getBatch(batchId)
    batchStatus.value = status
    if (!PENDING_BATCH_STATUSES.includes(status.status)) {
      batchRunning.value = false
      return
    }
  } catch (e: unknown) {
    batchError.value = publishErrorMessage(e)
    batchRunning.value = false
    return
  }
  if (attempt >= POLL_MAX_ATTEMPTS) {
    batchError.value =
      'O iFood ainda está processando esta operação. Aguarde alguns instantes e confira o cardápio.'
    batchRunning.value = false
    return
  }
  pollTimer = setTimeout(() => {
    void pollBatch(batchId, attempt + 1)
  }, POLL_INTERVAL_MS)
}

async function runBatch(label: string, call: () => Promise<IfoodCatalogBatchAccepted>) {
  clearPoll()
  batchLabel.value = label
  batchAccepted.value = null
  batchStatus.value = null
  batchError.value = null
  batchRunning.value = true
  try {
    const accepted = await call()
    batchAccepted.value = accepted
    if (!accepted.batchId) {
      // nada foi enviado ao iFood — só há itens ignorados para mostrar
      batchRunning.value = false
      return
    }
    await pollBatch(accepted.batchId)
  } catch (e: unknown) {
    batchError.value = publishErrorMessage(e)
    batchRunning.value = false
  }
}

function syncPrices() {
  const ids = publishedIds.value
  return runBatch('Sincronização de preços', () => ifoodCatalogService.syncPrices(ids))
}

function changeStatus(status: IfoodCatalogItemStatus) {
  const items = publishedIds.value.map((productId) => ({ productId, status }))
  const label = status === 'UNAVAILABLE' ? 'Pausa dos itens' : 'Reativação dos itens'
  return runBatch(label, () => ifoodCatalogService.syncStatus(items))
}
</script>

<template>
  <UIModal
    title="Publicar cardápio no iFood"
    subtitle="Envia seus produtos do JetMenu para o Cardápio Digital (WHITELABEL) da sua loja no iFood."
    :width="600"
    title-test-id="ifood-publish-modal-title"
    @close="emit('close')"
  >
    <!-- Result -->
    <div v-if="result" data-testid="ifood-publish-result">
      <div :style="{ display: 'flex', gap: '10px', marginBottom: '14px' }">
        <div
          v-for="stat in [
            { id: 'published', label: 'Publicados', value: result.publishedProducts, color: UI.emerald2, bg: UI.emeraldBg },
            { id: 'skipped', label: 'Ignorados', value: skippedCount, color: UI.textSub, bg: UI.bg },
            { id: 'failed', label: 'Falhas', value: failedCount, color: UI.rose, bg: UI.roseBg },
          ]"
          :key="stat.label"
          :data-testid="`ifood-publish-count-${stat.id}`"
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

      <div
        v-if="publishedItems.length"
        data-testid="ifood-publish-published-list"
        :style="{
          maxHeight: '200px',
          overflowY: 'auto',
          border: `1px solid ${UI.border}`,
          borderRadius: '9px',
          marginBottom: '12px',
        }"
      >
        <div
          v-for="(item, i) in publishedItems"
          :key="item.productId"
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
          <UIPill color="emerald" size="sm">Publicado</UIPill>
        </div>
      </div>

      <div v-if="issueItems.length" data-testid="ifood-publish-issues" :style="{ marginBottom: '12px' }">
        <div :style="{ fontSize: '12px', fontWeight: 600, color: UI.textSub, marginBottom: '6px' }">
          Itens não publicados
        </div>
        <div
          v-for="item in issueItems"
          :key="item.productId"
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
          <UIPill :color="item.outcome === 'FAILED' ? 'rose' : 'gray'" size="sm">
            {{ item.outcome === 'FAILED' ? 'Falhou' : 'Ignorado' }}
          </UIPill>
          <span :style="{ fontSize: '12.5px', color: UI.text }">{{ item.name }}</span>
          <span :style="{ fontSize: '12px', color: UI.textSub }">{{ item.reason }}</span>
        </div>
      </div>

      <!-- Operações em massa sobre os itens já publicados -->
      <div v-if="publishedItems.length">
        <div :style="{ fontSize: '12px', fontWeight: 600, color: UI.textSub, marginBottom: '6px' }">
          Operações no cardápio publicado
        </div>
        <div :style="{ display: 'flex', gap: '8px', flexWrap: 'wrap' }">
          <UIBtn
            variant="secondary"
            size="sm"
            data-testid="ifood-publish-sync-prices"
            :disabled="batchRunning"
            @click="syncPrices"
          >
            <UIIcon name="link" :size="12" />
            Sincronizar preços
          </UIBtn>
          <UIBtn
            variant="secondary"
            size="sm"
            data-testid="ifood-publish-pause"
            :disabled="batchRunning"
            @click="changeStatus('UNAVAILABLE')"
          >
            Pausar itens
          </UIBtn>
          <UIBtn
            variant="secondary"
            size="sm"
            data-testid="ifood-publish-resume"
            :disabled="batchRunning"
            @click="changeStatus('AVAILABLE')"
          >
            Reativar itens
          </UIBtn>
        </div>

        <div
          v-if="batchAccepted || batchStatus"
          data-testid="ifood-publish-batch"
          :style="{
            marginTop: '10px',
            padding: '10px 12px',
            borderRadius: '9px',
            background: UI.bg,
          }"
        >
          <div :style="{ display: 'flex', alignItems: 'center', gap: '8px' }">
            <span :style="{ fontSize: '12.5px', fontWeight: 600, color: UI.text }">
              {{ batchLabel }}
            </span>
            <UIPill :color="batchDone ? 'emerald' : 'amber'" size="sm" dot>
              {{ batchDone ? 'Concluído' : 'Processando…' }}
            </UIPill>
          </div>
          <div
            v-if="batchStatus"
            :style="{ fontSize: '12px', color: UI.textSub, marginTop: '5px' }"
          >
            {{ batchStatus.successCount }} com sucesso · {{ batchStatus.failureCount }} com falha
          </div>
          <div
            v-if="batchAccepted && batchAccepted.skipped.length"
            :style="{ fontSize: '12px', color: UI.textSub, marginTop: '5px' }"
          >
            <div v-for="skip in batchAccepted.skipped" :key="skip.productId">
              Ignorado: {{ skip.reason }}
            </div>
          </div>
        </div>

        <div
          v-if="batchError"
          data-testid="ifood-publish-batch-error"
          :style="{ fontSize: '12px', color: UI.rose, marginTop: '10px' }"
        >
          {{ batchError }}
        </div>
      </div>
    </div>

    <!-- Idle / publicando -->
    <div v-else>
      <div :style="{ fontSize: '13px', color: UI.textSub, lineHeight: 1.6, marginBottom: '12px' }">
        Os produtos são enviados apenas para o <strong>Cardápio Digital (WHITELABEL)</strong> da sua
        loja — o cardápio de entrega (Delivery) <strong>não é alterado</strong>. Selecione o que
        deseja publicar; produtos já publicados são atualizados sem duplicar.
      </div>

      <div
        :style="{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          marginBottom: '8px',
        }"
      >
        <span :style="{ flex: 1, fontSize: '12px', color: UI.textSub }">
          {{ selectedIds.length }} de {{ products.length }} selecionados
        </span>
        <UIBtn variant="ghost" size="sm" data-testid="ifood-publish-select-all" @click="selectAll">
          Selecionar todos
        </UIBtn>
        <UIBtn variant="ghost" size="sm" data-testid="ifood-publish-select-none" @click="selectNone">
          Limpar seleção
        </UIBtn>
      </div>

      <div
        data-testid="ifood-publish-products"
        :style="{
          maxHeight: '260px',
          overflowY: 'auto',
          border: `1px solid ${UI.border}`,
          borderRadius: '9px',
        }"
      >
        <label
          v-for="(product, i) in products"
          :key="product.id"
          :style="{
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            padding: '8px 12px',
            borderTop: i > 0 ? `1px solid ${UI.border}` : 'none',
            cursor: 'pointer',
          }"
        >
          <input
            v-model="selectedIds"
            type="checkbox"
            :value="product.id"
            :data-testid="`ifood-publish-check-${product.id}`"
          />
          <span :style="{ flex: 1, fontSize: '13px', color: UI.text }">{{ product.name }}</span>
          <span :style="{ fontSize: '12px', color: UI.textSub }">{{ product.categoryName }}</span>
          <span :style="{ fontSize: '12.5px', fontWeight: 600, color: UI.text }">
            {{ brl(product.price) }}
          </span>
        </label>
      </div>

      <div
        v-if="error"
        data-testid="ifood-publish-error"
        :style="{ fontSize: '12px', color: '#ef4444', marginTop: '12px' }"
      >
        {{ error }}
      </div>
    </div>

    <template #footer>
      <template v-if="result">
        <UIBtn variant="primary" data-testid="ifood-publish-close" @click="emit('close')">
          Fechar
        </UIBtn>
      </template>
      <template v-else>
        <UIBtn variant="ghost" data-testid="ifood-publish-cancel" @click="emit('close')">
          Cancelar
        </UIBtn>
        <UIBtn
          variant="primary"
          data-testid="ifood-publish-start"
          :disabled="publishing || selectedIds.length === 0"
          @click="doPublish"
        >
          {{ publishing ? 'Publicando…' : 'Publicar no Cardápio Digital' }}
        </UIBtn>
      </template>
    </template>
  </UIModal>
</template>
