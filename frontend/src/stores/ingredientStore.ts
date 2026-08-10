import { ref } from 'vue'
import { defineStore } from 'pinia'
import { createStaleCache } from '@/utils/staleCache'
import { createRequestSequence } from '@/utils/requestSequence'

const TTL_MS = 5 * 60 * 1000
import type { IngredientRequest, IngredientResponse } from '@/types/Ingredient'
import type { PageParams } from '@/types/Page'
import { DEFAULT_PAGE_SIZE } from '@/types/Page'
import { ingredientService } from '@/services/ingredientService'
import { useNotificationStore } from '@/stores/notificationStore'

export const useIngredientStore = defineStore('ingredient', () => {
  const items = ref<IngredientResponse[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const loaded = ref(false)
  const cache = createStaleCache(TTL_MS)
  const requests = createRequestSequence()

  const search = ref('')
  const page = ref(0)
  const size = ref(DEFAULT_PAGE_SIZE)
  const totalElements = ref(0)
  const totalPages = ref(0)

  async function fetchPage(params: PageParams = {}) {
    const effectiveSearch = params.search ?? search.value
    // `search` alimenta o campo de busca (input controlado), então precisa refletir o
    // que o usuário digitou de imediato. Atribuir depois do await devolvia o termo da
    // resposta atrasada ao input, apagando as letras digitadas nesse meio-tempo.
    search.value = effectiveSearch
    const token = requests.next()
    loading.value = true
    error.value = null
    try {
      const result = await ingredientService.findAll({
        search: effectiveSearch,
        page: params.page ?? page.value,
        size: params.size ?? size.value,
      })
      if (requests.isStale(token)) return
      items.value = result.content
      page.value = result.number
      size.value = result.size
      totalElements.value = result.totalElements
      totalPages.value = result.totalPages
      loaded.value = true
    } catch (e: unknown) {
      // Falha de uma busca já superada não interessa mais ao usuário.
      if (requests.isStale(token)) return
      loaded.value = false
      error.value = 'Erro ao carregar ingredientes'
      throw e
    } finally {
      if (!requests.isStale(token)) loading.value = false
    }
  }

  // Legacy entry point: fills `items` for forms/dropdowns without touching
  // pagination state (so a later list view starts with default size).
  async function fetchAll(force = false) {
    if (!force && loaded.value && !cache.isStale()) return
    loading.value = true
    error.value = null
    try {
      const result = await ingredientService.findAll({ search: '', page: 0, size: 1000 })
      items.value = result.content
      loaded.value = true
      cache.markFresh()
    } catch (e: unknown) {
      error.value = 'Erro ao carregar ingredientes'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function create(request: IngredientRequest) {
    loading.value = true
    error.value = null
    try {
      const created = await ingredientService.create(request)
      cache.invalidate()
      await fetchPage({})
      // Refresh notifications so any resolved MISSING_INGREDIENT
      // notifications disappear from the bell badge and panel immediately.
      const notifStore = useNotificationStore()
      notifStore.refreshCount()
      if (notifStore.items.length > 0) notifStore.fetchAll()
      return created
    } catch (e: unknown) {
      error.value = 'Erro ao criar ingrediente'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function update(id: string, request: IngredientRequest) {
    loading.value = true
    error.value = null
    try {
      const updated = await ingredientService.update(id, request)
      const index = items.value.findIndex((item) => item.id === id)
      if (index !== -1) items.value[index] = updated
      return updated
    } catch (e: unknown) {
      error.value = 'Erro ao atualizar ingrediente'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function remove(id: string) {
    loading.value = true
    error.value = null
    try {
      await ingredientService.remove(id)
      cache.invalidate()
      await fetchPage({})
    } catch (e: unknown) {
      error.value = 'Erro ao excluir ingrediente'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * Optimistically reorders the ingredient within the current page, then persists the
   * new global position. On failure the previous order is restored and the error toast
   * pattern is set. `globalPosition` = page × size + target index within page.
   */
  async function moveWithinPage(id: string, toIndex: number, globalPosition: number) {
    const fromIndex = items.value.findIndex((i) => i.id === id)
    if (fromIndex === -1 || fromIndex === toIndex) return
    const snapshot = [...items.value]
    const next = [...items.value]
    const [moved] = next.splice(fromIndex, 1)
    if (!moved) return
    next.splice(toIndex, 0, moved)
    items.value = next
    error.value = null
    try {
      await ingredientService.updatePosition(id, globalPosition)
    } catch (e: unknown) {
      items.value = snapshot
      error.value = 'Erro ao reordenar ingrediente'
      throw e
    }
  }

  /**
   * Persists a cross-page move (drop on the pagination targets) to the given global
   * position, then navigates to the target page so the user sees where the row landed.
   */
  async function moveToPage(id: string, globalPosition: number, targetPage: number) {
    error.value = null
    try {
      await ingredientService.updatePosition(id, globalPosition)
      cache.invalidate()
      await fetchPage({ page: targetPage })
    } catch (e: unknown) {
      error.value = 'Erro ao reordenar ingrediente'
      throw e
    }
  }

  return {
    items,
    loading,
    error,
    search,
    page,
    size,
    totalElements,
    totalPages,
    fetchPage,
    fetchAll,
    create,
    update,
    remove,
    moveWithinPage,
    moveToPage,
  }
})
