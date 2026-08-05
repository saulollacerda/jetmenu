<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { UI, UIBtn, UIIcon } from '@/design'
import {
  ifoodDiagnosticsService,
  diagnosticsErrorMessage,
  type IfoodRawResponse,
} from '@/services/ifoodDiagnosticsService'

/**
 * Diagnóstico read-only do catálogo do iFood, usado ao vivo nas reuniões de homologação:
 * cada botão dispara uma chamada isolada e mostra a resposta crua (URL, status, corpo),
 * que é o que o analista pede item a item do checklist. Não escreve nada — nem no iFood,
 * nem no JetMenu.
 *
 * Vive como janela flutuante (arrastável pelo cabeçalho, expansível para tela cheia) em
 * vez de modal para poder ficar aberta ao lado do resto do app durante a demonstração.
 */
const emit = defineEmits<{ (e: 'close'): void }>()

const expanded = ref(false)
const loading = ref<'catalogs' | 'items' | null>(null)
const result = ref<IfoodRawResponse | null>(null)
const error = ref<string | null>(null)

const position = ref({ x: 0, y: 0 })
let dragOrigin: { pointerX: number; pointerY: number; x: number; y: number } | null = null

async function run(action: 'catalogs' | 'items') {
  loading.value = action
  error.value = null
  try {
    result.value =
      action === 'catalogs'
        ? await ifoodDiagnosticsService.listCatalogs()
        : await ifoodDiagnosticsService.listItems()
  } catch (e: unknown) {
    result.value = null
    error.value = diagnosticsErrorMessage(e)
  } finally {
    loading.value = null
  }
}

/** JSON indentado quando dá para parsear; senão o texto como veio (erro em HTML, etc.). */
const formattedBody = computed(() => {
  const body = result.value?.body ?? ''
  if (!body.trim()) return '(sem conteúdo)'
  try {
    return JSON.stringify(JSON.parse(body), null, 2)
  } catch {
    return body
  }
})

const statusColor = computed(() => {
  const status = result.value?.status ?? 0
  return status >= 200 && status < 300 ? UI.emerald2 : UI.rose
})

function startDrag(ev: MouseEvent) {
  if (expanded.value) return
  dragOrigin = {
    pointerX: ev.clientX,
    pointerY: ev.clientY,
    x: position.value.x,
    y: position.value.y,
  }
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', stopDrag)
}

function onDrag(ev: MouseEvent) {
  if (!dragOrigin) return
  position.value = {
    x: dragOrigin.x + (ev.clientX - dragOrigin.pointerX),
    y: dragOrigin.y + (ev.clientY - dragOrigin.pointerY),
  }
}

function stopDrag() {
  dragOrigin = null
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', stopDrag)
}

onBeforeUnmount(stopDrag)

/** Expandida ocupa quase toda a viewport e ignora o arrasto acumulado. */
const windowStyle = computed(() =>
  expanded.value
    ? { inset: '16px', width: 'auto', height: 'auto', transform: 'none' }
    : {
        right: '20px',
        bottom: '20px',
        width: 'min(560px, calc(100vw - 40px))',
        height: 'min(560px, calc(100vh - 40px))',
        transform: `translate(${position.value.x}px, ${position.value.y}px)`,
      },
)
</script>

<template>
  <div
    class="diag-window"
    data-testid="ifood-diagnostics-window"
    :data-expanded="String(expanded)"
    :style="windowStyle"
  >
    <header class="diag-header" :class="{ 'is-static': expanded }" @mousedown="startDrag">
      <UIIcon name="file" :size="15" />
      <div style="flex: 1; min-width: 0">
        <div :style="{ fontSize: '13px', fontWeight: 600, color: UI.text }">
          Diagnóstico iFood — Catálogo
        </div>
        <div :style="{ fontSize: '11px', color: UI.textSub }">
          Chamadas isoladas, somente leitura
        </div>
      </div>
      <button
        class="diag-icon-btn"
        data-testid="diag-expand"
        :title="expanded ? 'Restaurar' : 'Expandir'"
        @click="expanded = !expanded"
      >
        <UIIcon :name="expanded ? 'collapse' : 'expand'" :size="14" />
      </button>
      <button class="diag-icon-btn" data-testid="diag-close" title="Fechar" @click="emit('close')">
        <UIIcon name="x" :size="14" />
      </button>
    </header>

    <div class="diag-body">
      <div :style="{ display: 'flex', gap: '8px', flexWrap: 'wrap' }">
        <UIBtn
          size="sm"
          variant="secondary"
          data-testid="diag-list-catalogs"
          :disabled="loading !== null"
          @click="run('catalogs')"
        >
          {{ loading === 'catalogs' ? 'Consultando…' : 'Listar catálogos' }}
        </UIBtn>
        <UIBtn
          size="sm"
          variant="secondary"
          data-testid="diag-list-items"
          :disabled="loading !== null"
          @click="run('items')"
        >
          {{ loading === 'items' ? 'Consultando…' : 'Listar itens' }}
        </UIBtn>
      </div>

      <div
        v-if="error"
        data-testid="diag-error"
        :style="{
          marginTop: '12px',
          padding: '10px 12px',
          borderRadius: '8px',
          background: UI.roseBg,
          color: UI.rose2,
          fontSize: '12px',
        }"
      >
        {{ error }}
      </div>

      <div v-if="result" class="diag-result">
        <div :style="{ display: 'flex', alignItems: 'baseline', gap: '8px', marginBottom: '4px' }">
          <span :style="{ fontSize: '11px', color: UI.textSub, fontWeight: 600 }">Status</span>
          <span
            data-testid="diag-result-status"
            :style="{ fontSize: '12px', fontWeight: 700, color: statusColor }"
          >
            {{ result.status }}
          </span>
        </div>
        <div :style="{ fontSize: '11px', color: UI.textSub, fontWeight: 600 }">Endpoint</div>
        <div
          data-testid="diag-result-endpoint"
          :style="{
            fontSize: '11.5px',
            color: UI.text,
            wordBreak: 'break-all',
            marginBottom: '8px',
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          }"
        >
          {{ result.endpoint }}
        </div>
        <div :style="{ fontSize: '11px', color: UI.textSub, fontWeight: 600 }">Resposta</div>
        <pre data-testid="diag-result-body" class="diag-pre">{{ formattedBody }}</pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.diag-window {
  position: fixed;
  z-index: 400;
  display: flex;
  flex-direction: column;
  background: v-bind('UI.panel');
  border: 1px solid v-bind('UI.border');
  border-radius: 12px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.22);
  overflow: hidden;
}

.diag-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-bottom: 1px solid v-bind('UI.borderSub');
  background: v-bind('UI.bgSoft');
  cursor: grab;
  user-select: none;
  flex-shrink: 0;
}
.diag-header.is-static {
  cursor: default;
}
.diag-header:active {
  cursor: grabbing;
}
.diag-header.is-static:active {
  cursor: default;
}

.diag-icon-btn {
  background: transparent;
  border: none;
  color: v-bind('UI.textSub');
  cursor: pointer;
  padding: 5px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.diag-icon-btn:hover {
  background: rgba(15, 23, 42, 0.06);
  color: v-bind('UI.text');
}

.diag-body {
  padding: 12px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.diag-result {
  margin-top: 12px;
}

.diag-pre {
  margin: 4px 0 0;
  padding: 10px;
  background: v-bind('UI.bg');
  border: 1px solid v-bind('UI.borderSub');
  border-radius: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11.5px;
  line-height: 1.5;
  color: v-bind('UI.text');
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
