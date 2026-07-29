<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { billingService } from '@/services/billingService'
import { useAuthStore } from '@/stores/authStore'
import { UI, UIIcon } from '@/design'

/**
 * Entry point for the landing page pricing CTA (jetmenu.com.br links here as
 * /checkout?plan=<slug>). The landing page is static and has no session, so it
 * cannot create a checkout itself: the subscription must be attached to an
 * authenticated merchant. This view is the bridge — it makes sure there is an
 * account, then hands the browser over to the payment provider.
 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const errorMessage = ref<string | null>(null)

/** "Básico" → "basico": matches the plan slug the landing page links with. */
function slugify(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function planSlug(): string {
  const raw = route.query.plan
  return typeof raw === 'string' ? raw : ''
}

async function startCheckout() {
  loading.value = true
  errorMessage.value = null
  try {
    const slug = planSlug()
    const plans = await billingService.listPlans()
    // Without a slug (or with a single plan) there is nothing to choose from.
    const plan = slug ? plans.find((p) => slugify(p.name) === slug) : plans[0]
    if (!plan) {
      errorMessage.value =
        'Plano não encontrado. Volte a jetmenu.com.br e escolha um plano novamente.'
      return
    }
    const { url } = await billingService.createCheckout(plan.id)
    // Full-page handover: the provider brings the merchant back on its own URLs.
    window.location.href = url
  } catch (e: unknown) {
    const status = (e as { response?: { status?: number } })?.response?.status
    errorMessage.value =
      status === 503
        ? 'O pagamento online está temporariamente indisponível. Tente novamente em alguns minutos ou fale com o suporte.'
        : 'Não foi possível iniciar o pagamento. Tente novamente.'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!auth.isAuthenticated) {
    // The plan rides along through register/login and checkout resumes there.
    const slug = planSlug()
    router.replace({ path: '/register', query: slug ? { plan: slug } : {} })
    return
  }
  return startCheckout()
})
</script>

<template>
  <div
    :style="{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '24px',
      background: UI.navBg,
      color: '#fff',
      fontFamily: UI.font,
    }"
  >
    <div :style="{ width: '100%', maxWidth: '440px', textAlign: 'center' }">
      <div :style="{ fontSize: '26px', fontWeight: 700, letterSpacing: '-0.5px' }">
        jet<span :style="{ color: UI.emerald }">menu</span>
      </div>

      <div
        v-if="loading"
        data-testid="checkout-loading"
        :style="{ marginTop: '28px', fontSize: '15px', color: '#cbd5e1', lineHeight: 1.6 }"
      >
        Preparando seu pagamento…
        <div :style="{ fontSize: '13px', color: '#94a3b8', marginTop: '8px' }">
          Você será redirecionado para o ambiente seguro de pagamento.
        </div>
      </div>

      <div
        v-else-if="errorMessage"
        data-testid="checkout-error"
        :style="{
          marginTop: '28px',
          padding: '18px',
          background: 'rgba(225,29,72,0.12)',
          border: '1px solid rgba(225,29,72,0.45)',
          borderRadius: '12px',
          fontSize: '14px',
          lineHeight: 1.6,
          color: '#fecdd3',
        }"
      >
        {{ errorMessage }}
      </div>

      <div
        v-if="!loading && errorMessage"
        :style="{
          marginTop: '18px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: '12px',
        }"
      >
        <button
          type="button"
          data-testid="checkout-retry"
          :style="{
            padding: '12px 20px',
            background: UI.emerald,
            color: '#fff',
            border: 'none',
            borderRadius: '10px',
            fontSize: '14px',
            fontWeight: 600,
            cursor: 'pointer',
            fontFamily: 'inherit',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }"
          @click="startCheckout"
        >
          Tentar novamente
          <UIIcon name="arrow" :size="15" />
        </button>
        <RouterLink
          to="/settings?section=billing"
          :style="{ fontSize: '13px', color: '#94a3b8', textDecoration: 'underline' }"
        >
          Ir para Plano e pagamento
        </RouterLink>
      </div>
    </div>
  </div>
</template>
