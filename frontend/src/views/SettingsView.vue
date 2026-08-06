<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useAnotaAIStore } from '@/stores/anotaAIStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { UI, UITopbar, UICard, UIBtn, UIField, UIInput, UIPill, UIIcon } from '@/design'
import type { DayOfWeek, OpeningHour } from '@/types/User'
import { ifoodAuthService, type IfoodStatusResponse } from '@/services/ifoodAuthService'
import { billingService } from '@/services/billingService'
import type { PlanResponse, SubscriptionResponse } from '@/types/Billing'
import { clearPendingIfoodAuth, hasPendingIfoodAuth } from '@/composables/useIfoodConnectFlow'
import IfoodConnectModal from '@/components/IfoodConnectModal.vue'
import IfoodCatalogImportModal from '@/components/IfoodCatalogImportModal.vue'
import IfoodCatalogPublishModal from '@/components/IfoodCatalogPublishModal.vue'
import IfoodOrderSyncModal from '@/components/IfoodOrderSyncModal.vue'
import IfoodMerchantModal from '@/components/IfoodMerchantModal.vue'

const authStore = useAuthStore()
const anotaAIStore = useAnotaAIStore()
const notificationStore = useNotificationStore()
const route = useRoute()

const apiKey = ref('')
const showKey = ref(false)
const successMessage = ref<string | null>(null)
const loadError = ref<string | null>(null)
const section = ref<'loja' | 'ints' | 'horario' | 'alerta' | 'time' | 'billing' | 'danger'>('loja')

const inputType = computed(() => (showKey.value ? 'text' : 'password'))

const user = computed(() => authStore.currentUser)
const initials = computed(() => {
  const name = authStore.restaurantName || 'MB'
  const parts = name.trim().split(/\s+/)
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || 'MB'
})

// ── Opening hours ────────────────────────────────────────────────────────────

const DAY_ORDER: DayOfWeek[] = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
]
const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'Segunda', TUESDAY: 'Terça', WEDNESDAY: 'Quarta', THURSDAY: 'Quinta',
  FRIDAY: 'Sexta', SATURDAY: 'Sábado', SUNDAY: 'Domingo',
}

function defaultHours(): OpeningHour[] {
  return DAY_ORDER.map((d) => ({
    dayOfWeek: d,
    openTime: '11:00',
    closeTime: '22:00',
    closed: true,
  }))
}

const localHours = ref<OpeningHour[]>(defaultHours())
const hoursSaving = ref(false)
const hoursSuccess = ref<string | null>(null)

function initHours(saved: OpeningHour[] | null | undefined) {
  if (!saved || saved.length === 0) {
    localHours.value = defaultHours()
    return
  }
  // Fill missing days with defaults so the grid always shows all 7 days
  const byDay = Object.fromEntries(saved.map((h) => [h.dayOfWeek, h]))
  localHours.value = DAY_ORDER.map((d) => byDay[d] ?? {
    dayOfWeek: d, openTime: '11:00', closeTime: '22:00', closed: false,
  })
}

async function saveHours() {
  hoursSaving.value = true
  hoursSuccess.value = null
  try {
    await authStore.updateOpeningHours(localHours.value)
    hoursSuccess.value = 'Horários salvos com sucesso.'
    setTimeout(() => (hoursSuccess.value = null), 4000)
  } catch {
    /* error in store */
  } finally {
    hoursSaving.value = false
  }
}

// ── Profile load ─────────────────────────────────────────────────────────────

async function loadProfile() {
  loadError.value = null
  try {
    const u = await authStore.fetchCurrentUser()
    apiKey.value = u?.anotaAiApiKey ?? ''
    initHours(u?.openingHours)
  } catch {
    loadError.value = 'Não foi possível carregar suas configurações.'
  }
}

// ── iFood ─────────────────────────────────────────────────────────────────────

const ifoodStatus = ref<IfoodStatusResponse | null>(null)
const ifoodModal = ref(false)
const ifoodResume = ref(false)
const ifoodCatalogModal = ref(false)
const ifoodPublishModal = ref(false)
const ifoodSyncModal = ref(false)
const ifoodMerchantModal = ref(false)

const ifoodConnected = computed(() => ifoodStatus.value?.connected ?? false)
// Linking is blocked server-side while the integration awaits iFood homologation.
const ifoodConnectionEnabled = computed(() => ifoodStatus.value?.connectionEnabled ?? true)
const ifoodConnectBlocked = computed(() => !ifoodConnected.value && !ifoodConnectionEnabled.value)
const ifoodCatalogImportedLabel = computed(() => {
  const importedAt = ifoodStatus.value?.catalogImportedAt
  if (!importedAt) return null
  return new Date(importedAt).toLocaleDateString('pt-BR')
})

function startIfoodAuth() {
  ifoodResume.value = false
  ifoodModal.value = true
}

async function revokeIfood() {
  try {
    await ifoodAuthService.revoke()
    clearPendingIfoodAuth()
    await loadIfoodStatus()
  } catch {
    /* ignore */
  }
}

function handleIfoodStage1Action() {
  if (ifoodConnected.value) {
    revokeIfood()
  } else if (!ifoodConnectBlocked.value) {
    startIfoodAuth()
  }
}

async function handleIfoodConnected() {
  await loadIfoodStatus()
}

async function handleIfoodCatalogImported() {
  // o modal continua aberto exibindo o resumo; aqui só atualizamos o checklist
  await loadIfoodStatus()
}

function handleIfoodSyncUpdated(status: IfoodStatusResponse) {
  ifoodStatus.value = status
  ifoodSyncModal.value = false
}

async function handleSaveKey() {
  successMessage.value = null
  try {
    const trimmed = apiKey.value.trim()
    await authStore.updateAnotaAIKey(trimmed.length > 0 ? trimmed : null)
    successMessage.value = 'Chave do Anota.AI salva com sucesso.'
    showTokenForm.value = false
    setTimeout(() => (successMessage.value = null), 4000)
  } catch {
    /* error in store */
  }
}

// ── Integration cards (collapsible checklists) ───────────────────────────────

const expandedIntegrations = ref<{ ifood: boolean; anotaai: boolean }>({
  ifood: false,
  anotaai: false,
})

function toggleIntegration(key: 'ifood' | 'anotaai') {
  expandedIntegrations.value[key] = !expandedIntegrations.value[key]
}

// ── Anota.AI checklist ────────────────────────────────────────────────────────

const showTokenForm = ref(false)

const anotaAiConnected = computed(() => !!user.value?.anotaAiApiKey)
const anotaAiHoursConfigured = computed(() => (user.value?.openingHours?.length ?? 0) > 0)

async function handleSyncAnotaAIOrders() {
  anotaAIStore.clearResult()
  try {
    await anotaAIStore.syncOrders()
  } catch {
    /* error in store */
  } finally {
    notificationStore.refreshCount()
  }
}

// ── Plano e pagamento ────────────────────────────────────────────────────────

const billingPlans = ref<PlanResponse[]>([])
const billingSubscription = ref<SubscriptionResponse | null>(null)
const billingLoading = ref(false)
const billingLoaded = ref(false)
const billingError = ref<string | null>(null)
const subscribingPlanId = ref<string | null>(null)

const BILLING_STATUS_LABELS: Record<SubscriptionResponse['status'], string> = {
  PENDING: 'Aguardando pagamento',
  TRIAL: 'Período de teste',
  ACTIVE: 'Ativa',
  PAST_DUE: 'Pagamento pendente',
  CANCELED: 'Cancelada',
}

const billingStatusLabel = computed(() => {
  const sub = billingSubscription.value
  return sub ? (BILLING_STATUS_LABELS[sub.status] ?? sub.status) : ''
})

const billingStatusDetail = computed(() => {
  const sub = billingSubscription.value
  if (!sub) return ''
  if (sub.status === 'PENDING') {
    return 'Escolha um plano e conclua o pagamento para começar a usar o JetMenu.'
  }
  if (sub.status === 'TRIAL' && sub.trialEndsAt) {
    return `Seu teste grátis termina em ${formatDate(sub.trialEndsAt)}. Assine para não perder o acesso.`
  }
  if (sub.status === 'ACTIVE') {
    return `Plano ${sub.planName ?? ''} — válida até ${formatDate(sub.currentPeriodEnd)}.`
  }
  if (sub.status === 'PAST_DUE') {
    return 'Há um pagamento pendente. Renove a assinatura para manter o acesso.'
  }
  return 'Sua assinatura está cancelada. Assine um plano para continuar usando o JetMenu.'
})

function formatBRL(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatDate(iso: string | null) {
  return iso ? new Date(iso).toLocaleDateString('pt-BR') : ''
}

function planFeatureDescription(plan: PlanResponse) {
  const description = plan.features?.description
  return typeof description === 'string' ? description : 'Acesso a todas as funcionalidades'
}

async function loadBilling() {
  billingLoading.value = true
  billingError.value = null
  try {
    // Merchants created before the trial flow may have no subscription row
    // (404) — the plans must still be listed so they can subscribe.
    const [subscription, plans] = await Promise.allSettled([
      billingService.getMySubscription(),
      billingService.listPlans(),
    ])
    if (plans.status === 'rejected') throw plans.reason
    billingPlans.value = plans.value
    billingSubscription.value = subscription.status === 'fulfilled' ? subscription.value : null
    billingLoaded.value = true
  } catch {
    billingError.value = 'Não foi possível carregar seu plano. Tente novamente.'
  } finally {
    billingLoading.value = false
  }
}

// In-app renew/upgrade path: the only way a merchant whose subscription lapsed can
// pay, since pricing itself lives on the landing page now.
async function subscribeToPlan(plan: PlanResponse) {
  subscribingPlanId.value = plan.id
  billingError.value = null
  try {
    const { url } = await billingService.createCheckout(plan.id)
    // Full-page handover: the provider brings the merchant back on its own URLs.
    window.location.href = url
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number } })?.response?.status
    billingError.value =
      status === 503
        ? 'O pagamento online está temporariamente indisponível. Tente novamente em alguns minutos ou fale com o suporte.'
        : 'Não foi possível iniciar o pagamento. Tente novamente.'
  } finally {
    subscribingPlanId.value = null
  }
}

watch(section, (current) => {
  if (current === 'billing' && !billingLoaded.value && !billingLoading.value) {
    loadBilling()
  }
})

const SUBNAV: Array<{
  id: typeof section.value
  ic: string
  l: string
  danger?: boolean
}> = [
  { id: 'loja', ic: 'box', l: 'Perfil da loja' },
  { id: 'ints', ic: 'sync', l: 'Integrações' },
  { id: 'horario', ic: 'clock', l: 'Horários' },
  { id: 'alerta', ic: 'bell', l: 'Alertas' },
  { id: 'time', ic: 'user', l: 'Time' },
  { id: 'billing', ic: 'card', l: 'Plano e pagamento' },
  { id: 'danger', ic: 'alert', l: 'Zona perigosa', danger: true },
]

async function loadIfoodStatus() {
  try {
    ifoodStatus.value = await ifoodAuthService.status()
  } catch {
    /* best-effort — mantém o estado atual em caso de erro */
  }
}

onMounted(async () => {
  await Promise.all([loadProfile(), loadIfoodStatus()])
  const q = route.query.section
  if (q && typeof q === 'string' && SUBNAV.some((s) => s.id === q)) {
    section.value = q as typeof section.value
  }

  // Resume an interrupted iFood linking flow (reload/navigation mid-flow).
  if (!ifoodConnected.value && !ifoodConnectBlocked.value && hasPendingIfoodAuth()) {
    section.value = 'ints'
    expandedIntegrations.value.ifood = true
    ifoodResume.value = true
    ifoodModal.value = true
  } else if ((ifoodConnected.value || ifoodConnectBlocked.value) && hasPendingIfoodAuth()) {
    clearPendingIfoodAuth()
  }
})
</script>

<template>
  <div style="display: flex; flex-direction: column; flex: 1">
    <UITopbar
      title="Configurações"
      subtitle="Perfil da loja, integrações e preferências"
    />

    <div class="settings-layout">
      <!-- Sub-nav -->
      <div class="settings-subnav">
        <div
          v-for="it in SUBNAV"
          :key="it.id"
          class="settings-subnav-item"
          :style="{
            padding: '10px 14px',
            borderRadius: '9px',
            background: section === it.id ? UI.panel : 'transparent',
            border: section === it.id ? `1px solid ${UI.border}` : '1px solid transparent',
            color: it.danger ? UI.rose : section === it.id ? UI.text : UI.textSub,
            fontSize: '13px',
            fontWeight: section === it.id ? 600 : 500,
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            cursor: 'pointer',
          }"
          @click="section = it.id"
        >
          <UIIcon :name="it.ic" :size="15" />
          {{ it.l }}
        </div>
      </div>

      <!-- Right content -->
      <div
        style="
          flex: 1;
          display: flex;
          flex-direction: column;
          gap: 16px;
          overflow: auto;
          min-height: 0;
        "
      >
        <div
          v-if="loadError"
          :style="{
            padding: '10px 14px',
            background: UI.roseBg,
            color: UI.rose2,
            borderRadius: '10px',
            fontSize: '13px',
          }"
        >
          {{ loadError }}
        </div>
        <div
          v-if="authStore.error"
          :style="{
            padding: '10px 14px',
            background: UI.roseBg,
            color: UI.rose2,
            borderRadius: '10px',
            fontSize: '13px',
          }"
        >
          {{ authStore.error }}
        </div>
        <div
          v-if="successMessage"
          :style="{
            padding: '10px 14px',
            background: UI.emeraldBg,
            color: UI.emerald2,
            borderRadius: '10px',
            fontSize: '13px',
          }"
        >
          {{ successMessage }}
        </div>

        <!-- Perfil da loja -->
        <UICard v-if="section === 'loja'" :padding="22">
          <div
            style="
              display: flex;
              align-items: flex-start;
              justify-content: space-between;
              margin-bottom: 4px;
            "
          >
            <div>
              <div
                :style="{
                  fontSize: '16px',
                  fontWeight: 700,
                  color: UI.text,
                  letterSpacing: '-0.3px',
                }"
              >
                Perfil da loja
              </div>
              <div :style="{ fontSize: '12px', color: UI.textSub, marginTop: '4px' }">
                Informações principais do seu cadastro.
              </div>
            </div>
          </div>

          <div style="display: flex; gap: 22px; margin-top: 22px; align-items: flex-start">
            <div style="display: flex; flex-direction: column; align-items: center; gap: 10px">
              <div
                :style="{
                  width: '100px',
                  height: '100px',
                  borderRadius: '50px',
                  background: 'linear-gradient(135deg,#10b981,#059669)',
                  color: '#fff',
                  fontSize: '36px',
                  fontWeight: 800,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }"
              >
                {{ initials }}
              </div>
              <UIBtn variant="secondary" size="sm" icon="upload">Trocar logo</UIBtn>
            </div>

            <div class="grid-cols-2" style="flex: 1; gap: 14px">
              <UIField label="Nome da loja">
                <UIInput :model-value="user?.merchantName ?? ''" disabled />
              </UIField>
              <UIField label="CNPJ">
                <UIInput :model-value="user?.cnpj ?? ''" disabled />
              </UIField>
              <UIField label="Email">
                <UIInput :model-value="user?.email ?? ''" icon="mail" disabled />
              </UIField>
              <UIField label="Telefone">
                <UIInput :model-value="user?.phone ?? ''" disabled />
              </UIField>
            </div>
          </div>

          <div
            :style="{
              marginTop: '22px',
              padding: '12px 14px',
              background: UI.amberBg,
              color: UI.amber2,
              borderRadius: '9px',
              fontSize: '12.5px',
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
            }"
          >
            <UIIcon name="info" :size="16" />
            A edição dos dados da loja ainda não está disponível no backend
            (apenas a chave Anota.AI é editável hoje).
          </div>
        </UICard>

        <!-- Integrações -->
        <UICard v-if="section === 'ints'" :padding="22">
          <div
            style="
              display: flex;
              align-items: center;
              justify-content: space-between;
              margin-bottom: 14px;
            "
          >
            <div>
              <div
                :style="{
                  fontSize: '16px',
                  fontWeight: 700,
                  color: UI.text,
                  letterSpacing: '-0.3px',
                }"
              >
                Integrações
              </div>
              <div :style="{ fontSize: '12px', color: UI.textSub, marginTop: '4px' }">
                Conecte canais de venda externos.
              </div>
            </div>
          </div>

          <div style="display: flex; flex-direction: column; gap: 8px">
            <!-- iFood -->
            <div
              :style="{
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
                padding: '12px 14px',
                background: UI.bgSoft,
                border: `1px solid ${UI.border}`,
                borderRadius: '11px',
              }"
            >
              <div
                data-testid="ifood-card-toggle"
                style="display: flex; align-items: center; gap: 14px; cursor: pointer"
                @click="toggleIntegration('ifood')"
              >
                <div
                  :style="{
                    width: '38px',
                    height: '38px',
                    borderRadius: '9px',
                    background: ifoodConnected ? UI.emeraldBg : UI.bg,
                    color: ifoodConnected ? UI.emerald2 : UI.textMute,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }"
                >
                  <UIIcon name="sync" :size="16" />
                </div>
                <div style="flex: 1">
                  <div :style="{ fontSize: '13.5px', fontWeight: 600 }">iFood</div>
                  <div :style="{ fontSize: '11.5px', color: UI.textSub, marginTop: '2px' }">
                    Importação de cardápio e pedidos via API oficial do iFood, em 3 etapas.
                  </div>
                </div>
                <UIPill :color="ifoodConnected ? 'emerald' : 'gray'" size="sm" dot>
                  {{ ifoodConnected ? 'Conectado' : 'Pendente' }}
                </UIPill>
                <UIIcon
                  :name="expandedIntegrations.ifood ? 'chevDown' : 'chevRight'"
                  :size="15"
                  :style="{ color: UI.textSub, flexShrink: 0 }"
                />
              </div>

              <template v-if="expandedIntegrations.ifood">
              <!-- Etapa 1 — Conectar -->
              <div
                data-testid="ifood-stage-connect"
                :style="{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 12px',
                  background: UI.bg,
                  borderRadius: '9px',
                }"
              >
                <span
                  :style="{
                    width: '22px', height: '22px', borderRadius: '50%',
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '11px', fontWeight: 700, flexShrink: 0,
                    background: ifoodConnected ? UI.emeraldBg : UI.bgSoft,
                    color: ifoodConnected ? UI.emerald2 : UI.textMute,
                    border: `1px solid ${ifoodConnected ? 'transparent' : UI.border}`,
                  }"
                >
                  <UIIcon v-if="ifoodConnected" name="check" :size="12" />
                  <template v-else>1</template>
                </span>
                <div style="flex: 1">
                  <div :style="{ fontSize: '12.5px', fontWeight: 600 }">Conectar conta</div>
                  <div :style="{ fontSize: '11px', color: ifoodConnectBlocked ? UI.amber2 : UI.textSub }">
                    {{ ifoodConnectBlocked
                      ? 'Integração em homologação pelo iFood — disponível em breve.'
                      : 'Autorize o JetMenu no portal do iFood.' }}
                  </div>
                </div>
                <UIPill
                  :color="ifoodConnected ? 'emerald' : ifoodConnectBlocked ? 'amber' : 'gray'"
                  size="sm"
                  dot
                >
                  {{ ifoodConnected ? 'Conectado' : ifoodConnectBlocked ? 'Em homologação' : 'Pendente' }}
                </UIPill>
                <UIBtn
                  :variant="ifoodConnected ? 'ghost' : 'primary'"
                  size="sm"
                  data-testid="ifood-stage-connect-action"
                  :disabled="ifoodConnectBlocked"
                  @click="handleIfoodStage1Action"
                >
                  {{ ifoodConnected ? 'Desconectar' : 'Conectar' }}
                </UIBtn>
              </div>

              <!-- Etapa 2 — Importar cardápio -->
              <div
                data-testid="ifood-stage-catalog"
                :style="{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 12px',
                  background: UI.bg,
                  borderRadius: '9px',
                  opacity: ifoodConnected ? 1 : 0.6,
                }"
              >
                <span
                  :style="{
                    width: '22px', height: '22px', borderRadius: '50%',
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '11px', fontWeight: 700, flexShrink: 0,
                    background: ifoodCatalogImportedLabel ? UI.emeraldBg : UI.bgSoft,
                    color: ifoodCatalogImportedLabel ? UI.emerald2 : UI.textMute,
                    border: `1px solid ${ifoodCatalogImportedLabel ? 'transparent' : UI.border}`,
                  }"
                >
                  <UIIcon v-if="ifoodCatalogImportedLabel" name="check" :size="12" />
                  <template v-else>2</template>
                </span>
                <div style="flex: 1">
                  <div :style="{ fontSize: '12.5px', fontWeight: 600 }">Importar cardápio</div>
                  <div :style="{ fontSize: '11px', color: UI.textSub }">
                    {{
                      ifoodCatalogImportedLabel
                        ? `Última importação: ${ifoodCatalogImportedLabel}`
                        : 'Evita erros de produto não cadastrado nos pedidos.'
                    }}
                  </div>
                </div>
                <UIPill :color="ifoodCatalogImportedLabel ? 'emerald' : 'gray'" size="sm" dot>
                  {{ ifoodCatalogImportedLabel ? 'Importado' : 'Pendente' }}
                </UIPill>
                <UIBtn
                  variant="secondary"
                  size="sm"
                  data-testid="ifood-stage-catalog-action"
                  :disabled="!ifoodConnected"
                  @click="ifoodCatalogModal = true"
                >
                  {{ ifoodCatalogImportedLabel ? 'Reimportar' : 'Importar' }}
                </UIBtn>
              </div>

              <!-- Publicar cardápio no Cardápio Digital (WHITELABEL) -->
              <div
                data-testid="ifood-stage-publish"
                :style="{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 12px',
                  background: UI.bg,
                  borderRadius: '9px',
                  opacity: ifoodConnected ? 1 : 0.6,
                }"
              >
                <span
                  :style="{
                    width: '22px', height: '22px', borderRadius: '50%',
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    flexShrink: 0,
                    background: UI.blueBg,
                    color: UI.blue2,
                  }"
                >
                  <UIIcon name="link" :size="12" />
                </span>
                <div style="flex: 1">
                  <div :style="{ fontSize: '12.5px', fontWeight: 600 }">Publicar cardápio</div>
                  <div :style="{ fontSize: '11px', color: UI.textSub }">
                    Envia seus produtos para o Cardápio Digital do iFood e sincroniza preços e
                    disponibilidade.
                  </div>
                </div>
                <UIBtn
                  variant="secondary"
                  size="sm"
                  data-testid="ifood-stage-publish-action"
                  :disabled="!ifoodConnected"
                  @click="ifoodPublishModal = true"
                >
                  Publicar
                </UIBtn>
              </div>

              <!-- Etapa 3 — Sincronia de pedidos -->
              <div
                data-testid="ifood-stage-sync"
                :style="{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 12px',
                  background: UI.bg,
                  borderRadius: '9px',
                  opacity: ifoodConnected ? 1 : 0.6,
                }"
              >
                <span
                  :style="{
                    width: '22px', height: '22px', borderRadius: '50%',
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '11px', fontWeight: 700, flexShrink: 0,
                    background: ifoodStatus?.orderSyncEnabled ? UI.emeraldBg : UI.bgSoft,
                    color: ifoodStatus?.orderSyncEnabled ? UI.emerald2 : UI.textMute,
                    border: `1px solid ${ifoodStatus?.orderSyncEnabled ? 'transparent' : UI.border}`,
                  }"
                >
                  <UIIcon v-if="ifoodStatus?.orderSyncEnabled" name="check" :size="12" />
                  <template v-else>3</template>
                </span>
                <div style="flex: 1">
                  <div
                    :style="{
                      fontSize: '12.5px',
                      fontWeight: 600,
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px',
                    }"
                  >
                    Sincronia de pedidos
                    <UIIcon
                      v-if="ifoodStatus?.orderSyncEnabled && !ifoodCatalogImportedLabel"
                      data-testid="ifood-stage-sync-warning"
                      name="alert"
                      :size="13"
                      :style="{ color: UI.rose }"
                      title="Sincronia ativa sem cardápio importado — itens desconhecidos serão ignorados."
                    />
                  </div>
                  <div :style="{ fontSize: '11px', color: UI.textSub }">
                    Importa seus pedidos do iFood automaticamente.
                  </div>
                </div>
                <UIPill :color="ifoodStatus?.orderSyncEnabled ? 'emerald' : 'gray'" size="sm" dot>
                  {{ ifoodStatus?.orderSyncEnabled ? 'Ativa' : 'Inativa' }}
                </UIPill>
                <UIBtn
                  variant="secondary"
                  size="sm"
                  data-testid="ifood-stage-sync-action"
                  :disabled="!ifoodConnected"
                  @click="ifoodSyncModal = true"
                >
                  {{ ifoodStatus?.orderSyncEnabled ? 'Gerenciar' : 'Ativar' }}
                </UIBtn>
              </div>

              <!-- Minha loja no iFood — status, pausas e horários (Merchant) -->
              <div
                v-if="ifoodConnected"
                data-testid="ifood-stage-merchant"
                :style="{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 12px',
                  background: UI.bg,
                  borderRadius: '9px',
                }"
              >
                <span
                  :style="{
                    width: '22px', height: '22px', borderRadius: '50%',
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    flexShrink: 0,
                    background: UI.blueBg,
                    color: UI.blue2,
                  }"
                >
                  <UIIcon name="home" :size="12" />
                </span>
                <div style="flex: 1">
                  <div :style="{ fontSize: '12.5px', fontWeight: 600 }">Minha loja no iFood</div>
                  <div :style="{ fontSize: '11px', color: UI.textSub }">
                    Veja o status da loja, gerencie pausas e ajuste os horários de funcionamento.
                  </div>
                </div>
                <UIBtn
                  variant="secondary"
                  size="sm"
                  data-testid="ifood-stage-merchant-action"
                  @click="ifoodMerchantModal = true"
                >
                  Gerenciar
                </UIBtn>
              </div>
              </template>
            </div>

            <div
              :style="{
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
                padding: '12px 14px',
                background: UI.bgSoft,
                border: `1px solid ${UI.border}`,
                borderRadius: '11px',
              }"
            >
              <div
                data-testid="anotaai-card-toggle"
                style="display: flex; align-items: center; gap: 14px; cursor: pointer"
                @click="toggleIntegration('anotaai')"
              >
                <div
                  :style="{
                    width: '38px',
                    height: '38px',
                    borderRadius: '9px',
                    background: anotaAiConnected ? UI.emeraldBg : UI.bg,
                    color: anotaAiConnected ? UI.emerald2 : UI.textMute,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }"
                >
                  <UIIcon name="sync" :size="16" />
                </div>
                <div style="flex: 1">
                  <div :style="{ fontSize: '13.5px', fontWeight: 600 }">Anota.AI</div>
                  <div :style="{ fontSize: '11.5px', color: UI.textSub, marginTop: '2px' }">
                    Importação automática de pedidos dentro dos horários da loja, em 3 etapas.
                  </div>
                </div>
                <UIPill :color="anotaAiConnected ? 'emerald' : 'gray'" size="sm" dot>
                  {{ anotaAiConnected ? 'Conectado' : 'Pendente' }}
                </UIPill>
                <UIIcon
                  :name="expandedIntegrations.anotaai ? 'chevDown' : 'chevRight'"
                  :size="15"
                  :style="{ color: UI.textSub, flexShrink: 0 }"
                />
              </div>

              <template v-if="expandedIntegrations.anotaai">
              <!-- Etapa 1 — Conectar -->
              <div
                data-testid="anotaai-stage-connect"
                :style="{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 12px',
                  background: UI.bg,
                  borderRadius: '9px',
                }"
              >
                <span
                  :style="{
                    width: '22px', height: '22px', borderRadius: '50%',
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '11px', fontWeight: 700, flexShrink: 0,
                    background: anotaAiConnected ? UI.emeraldBg : UI.bgSoft,
                    color: anotaAiConnected ? UI.emerald2 : UI.textMute,
                    border: `1px solid ${anotaAiConnected ? 'transparent' : UI.border}`,
                  }"
                >
                  <UIIcon v-if="anotaAiConnected" name="check" :size="12" />
                  <template v-else>1</template>
                </span>
                <div style="flex: 1">
                  <div :style="{ fontSize: '12.5px', fontWeight: 600 }">Conectar conta</div>
                  <div :style="{ fontSize: '11px', color: UI.textSub }">
                    Salve o token de integração fornecido pelo Anota.AI.
                  </div>
                </div>
                <UIPill :color="anotaAiConnected ? 'emerald' : 'gray'" size="sm" dot>
                  {{ anotaAiConnected ? 'Conectado' : 'Pendente' }}
                </UIPill>
                <UIBtn
                  :variant="anotaAiConnected ? 'ghost' : 'primary'"
                  size="sm"
                  data-testid="anotaai-stage-connect-action"
                  @click="showTokenForm = !showTokenForm"
                >
                  {{ anotaAiConnected ? 'Alterar' : 'Configurar' }}
                </UIBtn>
              </div>

              <form
                v-if="showTokenForm || !anotaAiConnected"
                style="width: 100%; display: flex; flex-direction: column; gap: 10px"
                @submit.prevent="handleSaveKey"
              >
                <UIField
                  label="Token de integração"
                  hint="Cole aqui o token de integração fornecido pelo Anota.AI."
                >
                  <UIInput v-model="apiKey" :type="inputType" placeholder="Cole o token do Anota.AI">
                    <template #rightAddon>
                      <span
                        :style="{
                          fontSize: '11.5px',
                          color: UI.blue,
                          fontWeight: 600,
                          cursor: 'pointer',
                        }"
                        @click="showKey = !showKey"
                      >
                        {{ showKey ? 'ocultar' : 'mostrar' }}
                      </span>
                    </template>
                  </UIInput>
                </UIField>
                <div style="display: flex; justify-content: flex-end">
                  <UIBtn
                    variant="primary"
                    icon="check"
                    type="submit"
                    :disabled="authStore.loading"
                  >
                    {{ authStore.loading ? 'Salvando…' : 'Salvar token' }}
                  </UIBtn>
                </div>
              </form>

              <!-- Etapa 2 — Horários de funcionamento -->
              <div
                data-testid="anotaai-stage-hours"
                :style="{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 12px',
                  background: UI.bg,
                  borderRadius: '9px',
                  opacity: anotaAiConnected ? 1 : 0.6,
                }"
              >
                <span
                  :style="{
                    width: '22px', height: '22px', borderRadius: '50%',
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '11px', fontWeight: 700, flexShrink: 0,
                    background: anotaAiHoursConfigured ? UI.emeraldBg : UI.bgSoft,
                    color: anotaAiHoursConfigured ? UI.emerald2 : UI.textMute,
                    border: `1px solid ${anotaAiHoursConfigured ? 'transparent' : UI.border}`,
                  }"
                >
                  <UIIcon v-if="anotaAiHoursConfigured" name="check" :size="12" />
                  <template v-else>2</template>
                </span>
                <div style="flex: 1">
                  <div :style="{ fontSize: '12.5px', fontWeight: 600 }">Horários de funcionamento</div>
                  <div :style="{ fontSize: '11px', color: UI.textSub }">
                    Os pedidos são importados automaticamente dentro desses horários.
                  </div>
                </div>
                <UIPill :color="anotaAiHoursConfigured ? 'emerald' : 'gray'" size="sm" dot>
                  {{ anotaAiHoursConfigured ? 'Configurado' : 'Pendente' }}
                </UIPill>
                <UIBtn
                  variant="secondary"
                  size="sm"
                  data-testid="anotaai-stage-hours-action"
                  @click="section = 'horario'"
                >
                  {{ anotaAiHoursConfigured ? 'Ajustar' : 'Definir horários' }}
                </UIBtn>
              </div>

              <!-- Etapa 3 — Importação de pedidos -->
              <div
                data-testid="anotaai-stage-import"
                :style="{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 12px',
                  background: UI.bg,
                  borderRadius: '9px',
                  opacity: anotaAiConnected ? 1 : 0.6,
                }"
              >
                <span
                  :style="{
                    width: '22px', height: '22px', borderRadius: '50%',
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '11px', fontWeight: 700, flexShrink: 0,
                    background: anotaAiConnected && anotaAiHoursConfigured ? UI.emeraldBg : UI.bgSoft,
                    color: anotaAiConnected && anotaAiHoursConfigured ? UI.emerald2 : UI.textMute,
                    border: `1px solid ${anotaAiConnected && anotaAiHoursConfigured ? 'transparent' : UI.border}`,
                  }"
                >
                  <UIIcon v-if="anotaAiConnected && anotaAiHoursConfigured" name="check" :size="12" />
                  <template v-else>3</template>
                </span>
                <div style="flex: 1">
                  <div :style="{ fontSize: '12.5px', fontWeight: 600 }">Importação de pedidos</div>
                  <div :style="{ fontSize: '11px', color: UI.textSub }">
                    {{
                      anotaAiConnected && anotaAiHoursConfigured
                        ? 'Sincronização automática ativa · importe manualmente se precisar.'
                        : 'Conclua as etapas anteriores para ativar a sincronização automática.'
                    }}
                  </div>
                </div>
                <UIPill :color="anotaAiConnected && anotaAiHoursConfigured ? 'emerald' : 'gray'" size="sm" dot>
                  {{ anotaAiConnected && anotaAiHoursConfigured ? 'Automática' : 'Manual' }}
                </UIPill>
                <UIBtn
                  variant="secondary"
                  size="sm"
                  data-testid="anotaai-stage-import-action"
                  :disabled="!anotaAiConnected || anotaAIStore.syncingOrders"
                  @click="handleSyncAnotaAIOrders"
                >
                  {{ anotaAIStore.syncingOrders ? 'Importando…' : 'Importar agora' }}
                </UIBtn>
              </div>

              <!-- Resultado da importação manual -->
              <div
                v-if="anotaAIStore.error"
                :style="{
                  padding: '10px 14px',
                  background: UI.roseBg,
                  color: UI.rose2,
                  borderRadius: '9px',
                  fontSize: '12.5px',
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
                  borderRadius: '9px',
                  fontSize: '12.5px',
                }"
              >
                {{ anotaAIStore.lastResult.ordersImported }} pedido(s) importado(s).
                {{ anotaAIStore.lastResult.ordersSkipped }} já existente(s).
              </div>
              <div
                v-if="anotaAIStore.lastResult && (anotaAIStore.lastResult.missingIngredientNames?.length ?? 0) > 0"
                :style="{
                  padding: '10px 14px',
                  background: UI.amberBg,
                  color: UI.amber2,
                  borderRadius: '9px',
                  fontSize: '12.5px',
                }"
              >
                ⚠️ {{ anotaAIStore.lastResult.missingIngredientNames!.length }} ingrediente(s) não encontrado(s):
                <strong>{{ anotaAIStore.lastResult.missingIngredientNames!.join(', ') }}</strong>.
                Abra o sino para cadastrá-los.
              </div>
              </template>
            </div>

            <!-- Em breve -->
            <div
              v-for="g in [
                { name: 'WhatsApp Business', desc: 'Em breve · notificações de status para o cliente.' },
                { name: 'Mercado Pago', desc: 'Em breve · conciliação de recebíveis.' },
              ]"
              :key="g.name"
              :style="{
                display: 'flex',
                alignItems: 'center',
                gap: '14px',
                padding: '12px 14px',
                background: UI.bgSoft,
                border: `1px solid ${UI.border}`,
                borderRadius: '11px',
              }"
            >
              <div
                :style="{
                  width: '38px',
                  height: '38px',
                  borderRadius: '9px',
                  background: UI.bg,
                  color: UI.textMute,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }"
              >
                <UIIcon name="sync" :size="16" />
              </div>
              <div style="flex: 1">
                <div :style="{ fontSize: '13.5px', fontWeight: 600 }">{{ g.name }}</div>
                <div :style="{ fontSize: '11.5px', color: UI.textSub, marginTop: '2px' }">
                  {{ g.desc }}
                </div>
              </div>
              <UIPill color="gray" dot>Em breve</UIPill>
            </div>

            <!-- iFood connect flow -->
            <IfoodConnectModal
              v-if="ifoodModal"
              :resume="ifoodResume"
              @connected="handleIfoodConnected"
              @close="ifoodModal = false"
            />
            <IfoodCatalogImportModal
              v-if="ifoodCatalogModal"
              @imported="handleIfoodCatalogImported"
              @close="ifoodCatalogModal = false"
            />
            <IfoodCatalogPublishModal
              v-if="ifoodPublishModal"
              @close="ifoodPublishModal = false"
            />
            <IfoodOrderSyncModal
              v-if="ifoodSyncModal"
              :enabled="ifoodStatus?.orderSyncEnabled ?? false"
              :catalog-imported="!!ifoodStatus?.catalogImportedAt"
              @updated="handleIfoodSyncUpdated"
              @close="ifoodSyncModal = false"
            />
            <IfoodMerchantModal
              v-if="ifoodMerchantModal"
              @close="ifoodMerchantModal = false"
            />
          </div>
        </UICard>

        <!-- Horários de funcionamento -->
        <UICard v-if="section === 'horario'" :padding="22">
          <div
            style="
              display: flex;
              align-items: flex-start;
              justify-content: space-between;
              margin-bottom: 20px;
            "
          >
            <div>
              <div
                :style="{ fontSize: '16px', fontWeight: 700, color: UI.text, letterSpacing: '-0.3px' }"
              >
                Horários de funcionamento
              </div>
              <div :style="{ fontSize: '12px', color: UI.textSub, marginTop: '4px' }">
                Durante esses horários os pedidos do Anota.AI são importados automaticamente.
              </div>
            </div>
          </div>

          <div style="display: flex; flex-direction: column; gap: 8px">
            <div
              v-for="hour in localHours"
              :key="hour.dayOfWeek"
              :style="{
                display: 'grid',
                gridTemplateColumns: '96px 1fr 1fr auto',
                alignItems: 'center',
                gap: '12px',
                padding: '10px 14px',
                background: UI.bgSoft,
                border: `1px solid ${UI.border}`,
                borderRadius: '10px',
                opacity: hour.closed ? '0.55' : '1',
              }"
            >
              <div :style="{ fontSize: '13px', fontWeight: 600, color: UI.text }">
                {{ DAY_LABELS[hour.dayOfWeek] }}
              </div>

              <UIField label="Abertura" style="margin: 0">
                <UIInput
                  type="time"
                  :model-value="hour.openTime ?? ''"
                  :disabled="hour.closed"
                  @update:model-value="hour.openTime = $event || null"
                />
              </UIField>

              <UIField label="Fechamento" style="margin: 0">
                <UIInput
                  type="time"
                  :model-value="hour.closeTime ?? ''"
                  :disabled="hour.closed"
                  @update:model-value="hour.closeTime = $event || null"
                />
              </UIField>

              <label
                :style="{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  fontSize: '12.5px',
                  color: UI.textSub,
                  cursor: 'pointer',
                  userSelect: 'none',
                  whiteSpace: 'nowrap',
                }"
              >
                <input
                  type="checkbox"
                  :checked="hour.closed"
                  style="accent-color: var(--color-rose, #ef4444); width: 14px; height: 14px"
                  @change="hour.closed = ($event.target as HTMLInputElement).checked"
                />
                Fechado
              </label>
            </div>
          </div>

          <div
            v-if="hoursSuccess"
            :style="{
              marginTop: '14px',
              padding: '10px 14px',
              background: UI.emeraldBg,
              color: UI.emerald2,
              borderRadius: '9px',
              fontSize: '13px',
            }"
          >
            {{ hoursSuccess }}
          </div>
          <div v-if="authStore.error" :style="{
            marginTop: '14px',
            padding: '10px 14px',
            background: UI.roseBg,
            color: UI.rose2,
            borderRadius: '9px',
            fontSize: '13px',
          }">
            {{ authStore.error }}
          </div>

          <div style="margin-top: 18px; display: flex; justify-content: flex-end">
            <UIBtn :loading="hoursSaving" @click="saveHours">Salvar horários</UIBtn>
          </div>
        </UICard>

        <!-- Plano e pagamento -->
        <UICard v-if="section === 'billing'" :padding="22">
          <div
            :style="{
              fontSize: '16px',
              fontWeight: 700,
              color: UI.text,
              letterSpacing: '-0.3px',
              marginBottom: '6px',
            }"
          >
            Plano e pagamento
          </div>
          <div :style="{ fontSize: '12.5px', color: UI.textSub, marginBottom: '18px' }">
            Sua assinatura do JetMenu.
          </div>

          <div
            v-if="billingError"
            data-testid="billing-error"
            :style="{
              padding: '10px 14px',
              background: UI.roseBg,
              color: UI.rose2,
              borderRadius: '10px',
              fontSize: '13px',
              marginBottom: '14px',
            }"
          >
            {{ billingError }}
          </div>

          <div v-if="billingLoading" :style="{ fontSize: '13px', color: UI.textSub }">
            Carregando informações do plano…
          </div>

          <template v-else-if="billingLoaded">
            <div
              v-if="billingSubscription"
              data-testid="billing-status"
              :style="{
                padding: '14px',
                background: UI.panel,
                border: `1px solid ${UI.border}`,
                borderRadius: '10px',
                marginBottom: '16px',
              }"
            >
              <div :style="{ fontSize: '13px', fontWeight: 600, color: UI.text }">
                Assinatura: {{ billingStatusLabel }}
              </div>
              <div :style="{ fontSize: '12.5px', color: UI.textSub, marginTop: '2px' }">
                {{ billingStatusDetail }}
              </div>
            </div>

            <div style="display: flex; flex-direction: column; gap: 12px">
              <div
                v-for="plan in billingPlans"
                :key="plan.id"
                data-testid="billing-plan-card"
                :style="{
                  padding: '16px',
                  border: `1px solid ${UI.border}`,
                  borderRadius: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '14px',
                }"
              >
                <div>
                  <div :style="{ fontSize: '14px', fontWeight: 700, color: UI.text }">
                    {{ plan.name }}
                  </div>
                  <div :style="{ fontSize: '12.5px', color: UI.textSub, marginTop: '2px' }">
                    {{ planFeatureDescription(plan) }}
                  </div>
                </div>
                <div style="display: flex; align-items: center; gap: 14px">
                  <div :style="{ fontSize: '15px', fontWeight: 700, color: UI.text }">
                    {{ formatBRL(plan.priceMonthly) }}
                    <span :style="{ fontSize: '12px', color: UI.textSub, fontWeight: 500 }">/mês</span>
                  </div>
                  <UIBtn
                    variant="primary"
                    data-testid="billing-subscribe-action"
                    :disabled="subscribingPlanId === plan.id"
                    @click="subscribeToPlan(plan)"
                  >
                    {{ subscribingPlanId === plan.id ? 'Gerando pagamento…' : 'Assinar' }}
                  </UIBtn>
                </div>
              </div>
            </div>
          </template>
        </UICard>

        <!-- Placeholder sections -->
        <UICard
          v-if="['alerta', 'time'].includes(section)"
          :padding="22"
        >
          <div
            :style="{
              fontSize: '16px',
              fontWeight: 700,
              color: UI.text,
              letterSpacing: '-0.3px',
              marginBottom: '6px',
            }"
          >
            {{ SUBNAV.find((s) => s.id === section)?.l }}
          </div>
          <div :style="{ fontSize: '12.5px', color: UI.textSub, marginBottom: '18px' }">
            Esta seção ainda não está disponível.
          </div>
          <div
            :style="{
              padding: '14px',
              background: UI.amberBg,
              color: UI.amber2,
              borderRadius: '10px',
              fontSize: '13px',
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
            }"
          >
            <UIIcon name="info" :size="16" />
            O backend ainda não expõe os campos necessários para esta tela. Consulte
            <code style="font-family: ui-monospace, monospace">docs/BACKEND_GAPS.md</code>.
          </div>
        </UICard>

        <UICard v-if="section === 'danger'" :padding="22">
          <div
            :style="{
              fontSize: '16px',
              fontWeight: 700,
              color: UI.rose,
              letterSpacing: '-0.3px',
              marginBottom: '6px',
            }"
          >
            Zona perigosa
          </div>
          <div :style="{ fontSize: '12.5px', color: UI.textSub, marginBottom: '18px' }">
            Ações destrutivas. Só faça aqui o que você tem certeza.
          </div>
          <div
            :style="{
              padding: '14px',
              background: UI.roseBg,
              color: UI.rose2,
              borderRadius: '10px',
              fontSize: '13px',
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
            }"
          >
            <UIIcon name="alert" :size="16" />
            Funcionalidades como excluir conta / cancelar plano ainda não existem no backend.
          </div>
        </UICard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-layout {
  flex: 1;
  padding: 28px;
  display: flex;
  gap: 20px;
  overflow: hidden;
  min-height: 0;
}

.settings-subnav {
  width: 220px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
}

.settings-subnav-item {
  transition: opacity 0.12s ease, background 0.12s ease;
  white-space: nowrap;
}
.settings-subnav-item:hover {
  opacity: 0.85;
}

@media (max-width: 768px) {
  .settings-layout {
    flex-direction: column;
    padding: 14px;
    overflow: auto;
  }
  .settings-subnav {
    width: 100%;
    flex-direction: row;
    overflow-x: auto;
    gap: 6px;
    padding-bottom: 4px;
  }
}
</style>
