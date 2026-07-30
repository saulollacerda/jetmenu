import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { SubscriptionResponse } from '@/types/Billing'
import { billingService } from '@/services/billingService'

export const useSubscriptionStore = defineStore('subscription', () => {
  const subscription = ref<SubscriptionResponse | null>(null)
  const loading = ref(false)

  const isBlocked = computed(() => {
    const sub = subscription.value
    if (!sub) return false
    // PENDING: subscription with no plan and no trial — the merchant must pick a
    // plan before using the system. Sign-up now grants TRIAL instead, so PENDING
    // only reaches here through the legacy backfill.
    if (sub.status === 'PENDING' || sub.status === 'PAST_DUE' || sub.status === 'CANCELED')
      return true
    const now = Date.now()
    if (sub.status === 'TRIAL') {
      return !!sub.trialEndsAt && new Date(sub.trialEndsAt).getTime() < now
    }
    if (sub.status === 'ACTIVE') {
      return !!sub.currentPeriodEnd && new Date(sub.currentPeriodEnd).getTime() < now
    }
    return false
  })

  async function fetch() {
    loading.value = true
    try {
      subscription.value = await billingService.getMySubscription()
    } catch {
      // Fail-open: a fetch error (network, missing subscription) must not
      // lock the merchant out of the system.
      subscription.value = null
    } finally {
      loading.value = false
    }
  }

  return { subscription, loading, isBlocked, fetch }
})
