import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { DashboardResponse, IngredientRanking } from '@/types/Dashboard'
import { dashboardService } from '@/services/dashboardService'
import { createStaleCache } from '@/utils/staleCache'
import { REFRESH_INTERVAL_MS } from '@/utils/refresh'

function lastDayOfMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

export const useDashboardStore = defineStore('dashboard', () => {
  const data = ref<DashboardResponse | null>(null)
  const loading = ref(false)
  // Background refresh in flight while data is already on screen (stale-while-revalidate).
  const refreshing = ref(false)
  const error = ref<string | null>(null)
  const exporting = ref(false)

  const ingredientRanking = ref<IngredientRanking[]>([])
  const ingredientRankingLoading = ref(false)
  const ingredientRankingError = ref<string | null>(null)

  const filterMode = ref<'month' | 'custom'>('month')

  const now = new Date()
  const selectedYear = ref<number>(now.getFullYear())
  const selectedMonthNumber = ref<number>(now.getMonth() + 1)

  const selectedMonth = computed<string>(
    () => `${selectedYear.value}-${pad2(selectedMonthNumber.value)}`,
  )

  // used only in custom mode
  const startDate = ref<string>('')
  const endDate = ref<string>('')

  const resolvedStartDate = computed<string>(() => {
    if (filterMode.value === 'month') return `${selectedMonth.value}-01`
    return startDate.value
  })

  const resolvedEndDate = computed<string>(() => {
    if (filterMode.value === 'month') {
      const last = lastDayOfMonth(selectedYear.value, selectedMonthNumber.value)
      return `${selectedMonth.value}-${pad2(last)}`
    }
    return endDate.value
  })

  const loaded = ref(false)
  const loadedKey = ref<string | null>(null)
  const cache = createStaleCache(REFRESH_INTERVAL_MS)
  let fetchDashboardInFlight: Promise<void> | null = null

  function getCurrentKey(): string {
    return `${resolvedStartDate.value}|${resolvedEndDate.value}`
  }

  async function fetchDashboard(force = false, silent = false) {
    const key = getCurrentKey()

    // Serve the cache while it is fresh (within the 10-minute window) for the same
    // period. User-initiated fetches pass `force` and always bypass the TTL.
    if (!force && loaded.value && loadedKey.value === key && !cache.isStale()) return
    if (!force && fetchDashboardInFlight) return fetchDashboardInFlight

    // Stale-while-revalidate: only surface the full-view loading state on the very
    // first load for this period (nothing on screen yet). Background/poll refreshes
    // keep the current numbers visible and flag `refreshing` instead, so the KPIs
    // never flash to placeholders.
    if (data.value !== null && loadedKey.value === key) refreshing.value = true
    else loading.value = true
    if (!silent) error.value = null

    fetchDashboardInFlight = (async () => {
      try {
        data.value = await dashboardService.getDashboard(
          resolvedStartDate.value || undefined,
          resolvedEndDate.value || undefined,
        )
        loaded.value = true
        loadedKey.value = key
        cache.markFresh()
        error.value = null
      } catch (e: unknown) {
        loaded.value = false
        loadedKey.value = null
        cache.invalidate()
        if (!silent) error.value = 'Erro ao carregar dashboard'
        throw e
      } finally {
        loading.value = false
        refreshing.value = false
        fetchDashboardInFlight = null
      }
    })()

    return fetchDashboardInFlight
  }

  async function fetchIngredientRanking() {
    ingredientRankingLoading.value = true
    ingredientRankingError.value = null
    try {
      ingredientRanking.value = await dashboardService.getIngredientRanking(
        resolvedStartDate.value || undefined,
        resolvedEndDate.value || undefined,
      )
    } catch (e: unknown) {
      ingredientRankingError.value = 'Erro ao carregar ranking de ingredientes'
      throw e
    } finally {
      ingredientRankingLoading.value = false
    }
  }

  async function exportDashboard() {
    exporting.value = true
    try {
      const blob = await dashboardService.exportDashboard(
        resolvedStartDate.value || undefined,
        resolvedEndDate.value || undefined,
      )
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      const label =
        filterMode.value === 'month'
          ? selectedMonth.value
          : `${resolvedStartDate.value}-${resolvedEndDate.value}`
      a.download = `jetmenu-export-${label}.xlsx`
      a.click()
      URL.revokeObjectURL(url)
    } finally {
      exporting.value = false
    }
  }

  async function exportDayClosing() {
    exporting.value = true
    try {
      const today = new Date()
      const y = today.getFullYear()
      const m = pad2(today.getMonth() + 1)
      const d = pad2(today.getDate())
      const iso = `${y}-${m}-${d}`

      const blob = await dashboardService.exportDashboard(iso, iso)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `jetmenu-fechamento-${iso}.xlsx`
      a.click()
      URL.revokeObjectURL(url)
    } finally {
      exporting.value = false
    }
  }

  return {
    data,
    loading,
    refreshing,
    error,
    exporting,
    filterMode,
    selectedYear,
    selectedMonthNumber,
    selectedMonth,
    startDate,
    endDate,
    resolvedStartDate,
    resolvedEndDate,
    ingredientRanking,
    ingredientRankingLoading,
    ingredientRankingError,
    fetchDashboard,
    fetchIngredientRanking,
    exportDashboard,
    exportDayClosing,
  }
})
