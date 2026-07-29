<template>
  <div class="lp-root">
    <!-- NAV -->
    <header class="lp-nav">
      <div class="lp-nav-inner">
        <a :href="LANDING_URL" class="lp-wordmark-link" data-testid="landing-wordmark-link">
          <div class="lp-wordmark">jet<span>menu</span></div>
        </a>
        <div class="lp-spacer"></div>
        <a :href="LANDING_URL" class="lp-nav-signin" data-testid="landing-back-link">
          Voltar ao início
        </a>
      </div>
    </header>

    <!-- HERO -->
    <section class="lp-plans-hero">
      <div class="lp-container-md">
        <div class="lp-eyebrow" style="text-align:center;">Planos e preços</div>
        <h1 class="lp-plans-h1">
          Pare de vender no escuro.<br />
          Comece a <span class="lp-accent">lucrar com clareza</span>.
        </h1>
        <p class="lp-plans-sub">
          Um preço justo e transparente para saber exatamente quanto sobra em cada pedido.
          Sem letra miúda, sem surpresa na fatura.
        </p>
      </div>
    </section>

    <!-- PLANS -->
    <section class="lp-plans">
      <div class="lp-container-md">
        <div v-if="loading" class="lp-plans-state">Carregando planos…</div>

        <div v-else-if="loadError" class="lp-plans-state">
          <p class="lp-plans-error">{{ loadError }}</p>
          <button type="button" class="lp-btn lp-btn-secondary" @click="loadPlans">
            Tentar novamente
          </button>
        </div>

        <div v-else class="lp-plans-grid">
          <div v-for="plan in plans" :key="plan.id" class="lp-plan-card">
            <div class="lp-plan-ribbon">Tudo incluído</div>
            <div class="lp-plan-name">{{ plan.name }}</div>
            <div class="lp-plan-desc">{{ planDescription(plan) }}</div>

            <div class="lp-plan-price">
              <span class="lp-plan-amount">{{ formatBRL(plan.priceMonthly) }}</span>
              <span class="lp-plan-per">/mês</span>
            </div>

            <ul class="lp-plan-features">
              <li v-for="feature in planFeatures(plan)" :key="feature">
                <IconCheck :size="16" color="#10b981" />
                {{ feature }}
              </li>
            </ul>

            <button type="button" class="lp-plan-cta" data-testid="plan-cta" @click="subscribe(plan)">
              {{ auth.isAuthenticated ? 'Falar com o suporte' : 'Criar minha conta' }}
            </button>

            <p
              v-if="checkoutUnavailable"
              class="lp-plan-notice"
              data-testid="plan-unavailable-notice"
            >
              O pagamento online está temporariamente indisponível enquanto integramos um novo
              provedor. Entre em contato com o suporte para ativar seu plano.
            </p>

            <p class="lp-plan-reassure">Sem fidelidade. Cancele quando quiser.</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { billingService } from '@/services/billingService'
import { useAuthStore } from '@/stores/authStore'
import type { PlanResponse } from '@/types/Billing'

// The landing page is a separate project served at the root domain; this app
// only owns app.jetmenu.com.br, so linking back to it is a plain external link.
const LANDING_URL = 'https://jetmenu.com.br'

// ── Inline SVG icon (matches the landing page aesthetic) ────────────────────
const IconCheck = {
  props: { size: { default: 16 }, color: { default: 'currentColor' } },
  template: `<svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" :stroke="color" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12l5 5L20 6"/></svg>`,
}

const router = useRouter()
const auth = useAuthStore()

const plans = ref<PlanResponse[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)
const checkoutUnavailable = ref(false)

// Benefit-oriented copy used when the plan payload does not carry its own list.
const DEFAULT_FEATURES = [
  'Custo por ingrediente calculado automaticamente',
  'Ficha técnica e margem real de cada produto',
  'Pedidos ilimitados, importados do Anota.AI e iFood',
  'Dashboard de lucro atualizado em tempo real',
  'Alertas de ingrediente ausente antes de fechar o dia',
  'Suporte por email quando você precisar',
]

function formatBRL(value: number): string {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function planDescription(plan: PlanResponse): string {
  const description = plan.features?.description
  return typeof description === 'string'
    ? description
    : 'Acesso completo a todas as funcionalidades do JetMenu.'
}

function planFeatures(plan: PlanResponse): string[] {
  const list = plan.features?.items
  if (Array.isArray(list) && list.every((item): item is string => typeof item === 'string')) {
    return list
  }
  return DEFAULT_FEATURES
}

async function loadPlans() {
  loading.value = true
  loadError.value = null
  try {
    plans.value = await billingService.listPlans()
  } catch {
    loadError.value = 'Não foi possível carregar os planos. Tente novamente.'
  } finally {
    loading.value = false
  }
}

// Sign-up still works; online checkout does not. The previous payment provider was
// removed and the next one is not integrated yet, so an authenticated visitor gets an
// explicit notice instead of a checkout that cannot be created. To restore it, call
// billingService.createCheckout(plan.id) here and redirect to the returned URL.
function subscribe(_plan: PlanResponse) {
  if (!auth.isAuthenticated) {
    router.push('/register')
    return
  }
  checkoutUnavailable.value = true
}

onMounted(loadPlans)
</script>

<style scoped>
/* Reuses the landing page (lp-) visual language, kept in sync by hand since
   that page now lives in the jetmenu-lp project. */
.lp-root {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  background: #fff;
  color: #0f172a;
  -webkit-font-smoothing: antialiased;
  font-size: 14px;
  line-height: 1.5;
  min-height: 100vh;
}

/* ── Wordmark ───────────────────────────────────────────────── */
.lp-wordmark {
  font-size: 22px;
  font-weight: 700;
  color: #0c1626;
  letter-spacing: -0.55px;
  line-height: 1;
}
.lp-wordmark span {
  color: #10b981;
}
.lp-wordmark-link {
  text-decoration: none;
}

/* ── Nav ────────────────────────────────────────────────────── */
.lp-nav {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid #eef0f3;
}
.lp-nav-inner {
  display: flex;
  align-items: center;
  gap: 36px;
  padding: 14px 32px;
  max-width: 1200px;
  margin: 0 auto;
}
.lp-spacer {
  flex: 1;
}
.lp-nav-signin {
  font-size: 13.5px;
  font-weight: 500;
  color: #0f172a;
  text-decoration: none;
  transition: opacity 0.12s;
}
.lp-nav-signin:hover {
  opacity: 0.7;
}

/* ── Containers ─────────────────────────────────────────────── */
.lp-container-md {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 32px;
}

/* ── Buttons ────────────────────────────────────────────────── */
.lp-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: inherit;
  font-weight: 600;
  text-decoration: none;
  border-radius: 10px;
  cursor: pointer;
  border: none;
  transition: transform 0.18s, box-shadow 0.18s, filter 0.12s;
  white-space: nowrap;
}
.lp-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.14);
  filter: brightness(1.03);
}
.lp-btn-secondary {
  background: #fff;
  color: #0f172a;
  padding: 12px 20px;
  font-size: 14px;
  border: 1px solid #e8eaee;
}

/* ── Hero ───────────────────────────────────────────────────── */
.lp-plans-hero {
  position: relative;
  padding: 72px 32px 40px;
  overflow: hidden;
  background: radial-gradient(ellipse at 70% -10%, #ecfdf5 0%, transparent 50%),
    radial-gradient(ellipse at -10% 120%, #eff6ff 0%, transparent 50%), #fff;
}
.lp-eyebrow {
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 1.4px;
  text-transform: uppercase;
  color: #059669;
  margin-bottom: 14px;
}
.lp-plans-h1 {
  font-size: 52px;
  font-weight: 700;
  letter-spacing: -1.8px;
  line-height: 1.08;
  color: #0f172a;
  text-align: center;
  max-width: 820px;
  margin: 0 auto;
}
.lp-accent {
  color: #059669;
}
.lp-plans-sub {
  font-size: 18px;
  color: #475569;
  text-align: center;
  max-width: 600px;
  margin: 22px auto 0;
  line-height: 1.55;
}

/* ── Plans ──────────────────────────────────────────────────── */
.lp-plans {
  padding: 40px 0 100px;
  background: #fff;
}
.lp-plans-state {
  text-align: center;
  color: #475569;
  font-size: 15px;
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
.lp-plans-error {
  color: #e11d48;
  font-weight: 600;
}
.lp-plans-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(0, 380px));
  gap: 20px;
  align-items: stretch;
  justify-content: center;
}
.lp-plan-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  padding: 34px 32px 28px;
  background: #0c1626;
  color: #fff;
  border: 1px solid #0c1626;
  box-shadow: 0 24px 48px rgba(12, 22, 38, 0.22);
}
.lp-plan-ribbon {
  position: absolute;
  top: -12px;
  left: 50%;
  transform: translateX(-50%);
  background: #10b981;
  color: #fff;
  padding: 5px 14px;
  border-radius: 100px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.4px;
  white-space: nowrap;
}
.lp-plan-name {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 6px;
}
.lp-plan-desc {
  font-size: 13.5px;
  color: #94a3b8;
  margin-bottom: 24px;
  line-height: 1.5;
}
.lp-plan-price {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.lp-plan-amount {
  font-size: 44px;
  font-weight: 800;
  letter-spacing: -1.5px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.lp-plan-per {
  font-size: 13px;
  color: #94a3b8;
}
.lp-plan-features {
  list-style: none;
  padding: 0;
  margin: 0 0 28px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}
.lp-plan-features li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 13.5px;
  line-height: 1.45;
  color: #e2e8f0;
}
.lp-plan-cta {
  padding: 14px 18px;
  border-radius: 10px;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  text-align: center;
  cursor: pointer;
  border: none;
  background: #10b981;
  color: #fff;
  transition: transform 0.18s, box-shadow 0.18s, filter 0.12s;
}
.lp-plan-cta:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 14px 32px rgba(16, 185, 129, 0.3);
}
.lp-plan-cta:disabled {
  opacity: 0.65;
  cursor: default;
}
.lp-plan-notice {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(251, 191, 36, 0.12);
  border: 1px solid rgba(251, 191, 36, 0.35);
  color: #fde68a;
  font-size: 12.5px;
  line-height: 1.45;
  text-align: center;
}
.lp-plan-reassure {
  margin-top: 14px;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}
</style>
