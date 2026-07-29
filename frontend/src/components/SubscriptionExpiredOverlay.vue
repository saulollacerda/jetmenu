<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSubscriptionStore } from '@/stores/subscriptionStore'
import { UI, UIBtn, UIIcon } from '@/design'

const subscriptionStore = useSubscriptionStore()
const route = useRoute()
const router = useRouter()

// The billing section stays reachable so the merchant can renew.
const onBillingSection = computed(
  () => route.path === '/settings' && route.query.section === 'billing',
)
const visible = computed(() => subscriptionStore.isBlocked && !onBillingSection.value)

// PENDING means the subscription was created at registration and never paid,
// so the copy invites the merchant to pick a plan instead of renewing.
const isPending = computed(() => subscriptionStore.subscription?.status === 'PENDING')
const title = computed(() =>
  isPending.value ? 'Escolha um plano para começar' : 'Sua assinatura expirou',
)
const body = computed(() =>
  isPending.value
    ? 'O JetMenu exige um plano ativo para ser utilizado. Escolha um plano e conclua o pagamento para liberar o acesso.'
    : 'Para continuar usando o JetMenu, renove sua assinatura. Seus dados continuam salvos e voltam a ficar disponíveis assim que o pagamento for confirmado.',
)
const buttonLabel = computed(() => (isPending.value ? 'Escolher plano' : 'Renovar assinatura'))

onMounted(() => {
  subscriptionStore.fetch()
})

// While blocked, re-check on every navigation so the block lifts right
// after the payment activates the subscription (no manual reload needed).
watch(
  () => route.fullPath,
  () => {
    if (subscriptionStore.isBlocked) {
      subscriptionStore.fetch()
    }
  },
)

function goToBilling() {
  router.push({ path: '/settings', query: { section: 'billing' } })
}
</script>

<template>
  <div
    v-if="visible"
    data-testid="subscription-expired-overlay"
    :style="{
      position: 'fixed',
      inset: 0,
      zIndex: 1000,
      background: 'rgba(15, 23, 42, 0.55)',
      backdropFilter: 'blur(3px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '24px',
    }"
  >
    <div
      :style="{
        background: UI.panel,
        border: `1px solid ${UI.border}`,
        borderRadius: '14px',
        padding: '28px',
        maxWidth: '420px',
        width: '100%',
        textAlign: 'center',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '10px',
      }"
    >
      <div
        :style="{
          width: '44px',
          height: '44px',
          borderRadius: '50%',
          background: UI.roseBg,
          color: UI.rose,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }"
      >
        <UIIcon name="lock" :size="20" />
      </div>
      <div :style="{ fontSize: '17px', fontWeight: 700, color: UI.text, letterSpacing: '-0.3px' }">
        {{ title }}
      </div>
      <div :style="{ fontSize: '13px', color: UI.textSub, lineHeight: 1.5 }">
        {{ body }}
      </div>
      <UIBtn
        variant="primary"
        size="lg"
        data-testid="subscription-expired-renew"
        style="margin-top: 8px"
        @click="goToBilling"
      >
        {{ buttonLabel }}
      </UIBtn>
    </div>
  </div>
</template>
