import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { OrderRequest, OrderResponse } from '@/types/Order'
import { DEFAULT_PAGE_SIZE } from '@/types/Page'
import { orderService, type OrderFilterParams } from '@/services/orderService'
import { useDashboardStore } from '@/stores/dashboardStore'
import { createStaleCache } from '@/utils/staleCache'
import { createRequestSequence } from '@/utils/requestSequence'
import { REFRESH_INTERVAL_MS } from '@/utils/refresh'

/** Extrai o `detail` do ProblemDetail retornado pelo backend, quando presente. */
function extractDetail(e: unknown): string | null {
  if (typeof e === 'object' && e !== null && 'response' in e) {
    const data = (e as { response?: { data?: { detail?: unknown } } }).response?.data
    if (typeof data?.detail === 'string' && data.detail.trim()) return data.detail
  }
  return null
}

export const useOrderStore = defineStore('order', () => {
  const items = ref<OrderResponse[]>([])
  const loading = ref(false)
  // Background refresh in flight while rows are already on screen (stale-while-revalidate).
  const refreshing = ref(false)
  const error = ref<string | null>(null)
  const loaded = ref(false)
  const loadedKey = ref<string | null>(null)
  const cache = createStaleCache(REFRESH_INTERVAL_MS)
  const requests = createRequestSequence()

  const search = ref('')
  const page = ref(0)
  const size = ref(DEFAULT_PAGE_SIZE)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const sort = ref<string>('dateTime,desc')

  function refreshDashboard() {
    const dashboardStore = useDashboardStore()
    void dashboardStore.fetchDashboard(true).catch(() => {
      // dashboard refresh is best-effort; errors are surfaced by the dashboard store itself
    })
  }

  async function fetchPage(params: OrderFilterParams = {}, silent = false) {
    const effectiveSearch = params.search ?? search.value
    const effectivePage = params.page ?? page.value
    const effectiveSize = params.size ?? size.value
    const effectiveSort = params.sort ?? sort.value
    const key = `${effectiveSearch}|${effectivePage}|${effectiveSize}|${effectiveSort}`

    // `search`/`sort` alimentam controles da tela (o campo de busca é um input
    // controlado), então são atualizados antes de qualquer await ou short-circuit —
    // atribuí-los só depois da resposta devolvia ao input um termo já superado,
    // apagando o que o usuário digitou enquanto a requisição estava em voo.
    search.value = effectiveSearch
    sort.value = effectiveSort

    // Serve the cache while it is fresh for the exact same query. Changing page,
    // search or sort produces a new key and always refetches; mutations invalidate
    // the cache so they refetch too.
    if (loaded.value && loadedKey.value === key && !cache.isStale()) return

    const token = requests.next()

    // Stale-while-revalidate: full-view loading only before the first successful
    // load (empty screen). Afterwards keep the current rows visible and flag
    // `refreshing`, so a background poll never blanks the list.
    if (loaded.value) refreshing.value = true
    else loading.value = true
    if (!silent) error.value = null
    try {
      const result = await orderService.findAll({
        search: effectiveSearch,
        page: effectivePage,
        size: effectiveSize,
        sort: effectiveSort,
      })
      if (requests.isStale(token)) return
      items.value = result.content
      page.value = result.number
      size.value = result.size
      totalElements.value = result.totalElements
      totalPages.value = result.totalPages
      loaded.value = true
      loadedKey.value = key
      cache.markFresh()
      error.value = null
    } catch (e: unknown) {
      // Falha de uma busca já superada não interessa mais ao usuário.
      if (requests.isStale(token)) return
      loaded.value = false
      loadedKey.value = null
      cache.invalidate()
      if (!silent) error.value = 'Erro ao carregar pedidos'
      throw e
    } finally {
      if (!requests.isStale(token)) {
        loading.value = false
        refreshing.value = false
      }
    }
  }

  // Legacy entry point: fills `items` without touching pagination state.
  async function fetchAll(force = false) {
    if (!force && loaded.value) return
    loading.value = true
    error.value = null
    try {
      const result = await orderService.findAll({ search: '', page: 0, size: 1000 })
      items.value = result.content
      loaded.value = true
    } catch (e: unknown) {
      error.value = 'Erro ao carregar pedidos'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function create(request: OrderRequest) {
    loading.value = true
    error.value = null
    try {
      const created = await orderService.create(request)
      cache.invalidate()
      await fetchPage({})
      refreshDashboard()
      return created
    } catch (e: unknown) {
      error.value = extractDetail(e) ?? 'Erro ao criar pedido'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function update(id: string, request: OrderRequest) {
    loading.value = true
    error.value = null
    try {
      const updated = await orderService.update(id, request)
      const index = items.value.findIndex((item) => item.id === id)
      if (index !== -1) items.value[index] = updated
      refreshDashboard()
      return updated
    } catch (e: unknown) {
      error.value = extractDetail(e) ?? 'Erro ao atualizar pedido'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function remove(id: string) {
    loading.value = true
    error.value = null
    try {
      await orderService.remove(id)
      cache.invalidate()
      await fetchPage({})
      refreshDashboard()
    } catch (e: unknown) {
      error.value = 'Erro ao excluir pedido'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function findById(id: string): Promise<OrderResponse> {
    try {
      return await orderService.findById(id)
    } catch (e: unknown) {
      error.value = 'Erro ao carregar detalhes do pedido'
      throw e
    }
  }

  return {
    items,
    loading,
    refreshing,
    error,
    search,
    page,
    size,
    totalElements,
    totalPages,
    sort,
    fetchPage,
    fetchAll,
    create,
    update,
    remove,
    findById,
  }
})
