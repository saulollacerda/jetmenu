import { ref } from 'vue'
import { defineStore } from 'pinia'
import { createStaleCache } from '@/utils/staleCache'
import { createRequestSequence } from '@/utils/requestSequence'

const TTL_MS = 5 * 60 * 1000
import type {
  ProductRequest,
  ProductResponse,
  IncludeRequest,
  IncludeResponse,
} from '@/types/Product'
import type { PageParams } from '@/types/Page'
import { DEFAULT_PAGE_SIZE } from '@/types/Page'
import { productService } from '@/services/productService'
import { includeService } from '@/services/includeService'

export const useProductStore = defineStore('product', () => {
  const items = ref<ProductResponse[]>([])
  const includes = ref<IncludeResponse[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const loaded = ref(false)
  const cache = createStaleCache(TTL_MS)
  const requests = createRequestSequence()

  // pagination state
  const search = ref('')
  const page = ref(0)
  const size = ref(DEFAULT_PAGE_SIZE)
  const totalElements = ref(0)
  const totalPages = ref(0)

  async function fetchPage(params: PageParams = {}) {
    const effectiveSearch = params.search ?? search.value
    // Ver ingredientStore.fetchPage: `search` alimenta o input controlado e precisa
    // ser atualizado antes do await, nunca com o termo de uma resposta atrasada.
    search.value = effectiveSearch
    const token = requests.next()
    loading.value = true
    error.value = null
    try {
      const result = await productService.findAll({
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
      if (requests.isStale(token)) return
      loaded.value = false
      error.value = 'Erro ao carregar produtos'
      throw e
    } finally {
      if (!requests.isStale(token)) loading.value = false
    }
  }

  async function fetchAll(force = false) {
    if (!force && loaded.value && !cache.isStale()) return
    loading.value = true
    error.value = null
    try {
      const result = await productService.findAll({ search: '', page: 0, size: 1000 })
      items.value = result.content
      loaded.value = true
      cache.markFresh()
    } catch (e: unknown) {
      error.value = 'Erro ao carregar produtos'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function create(request: ProductRequest) {
    loading.value = true
    error.value = null
    try {
      const created = await productService.create(request)
      cache.invalidate()
      await fetchPage({})
      return created
    } catch (e: unknown) {
      error.value = 'Erro ao criar produto'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function update(id: string, request: ProductRequest) {
    loading.value = true
    error.value = null
    try {
      const updated = await productService.update(id, request)
      const index = items.value.findIndex((item) => item.id === id)
      if (index !== -1) items.value[index] = updated
      return updated
    } catch (e: unknown) {
      error.value = 'Erro ao atualizar produto'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function remove(id: string) {
    loading.value = true
    error.value = null
    try {
      await productService.remove(id)
      cache.invalidate()
      await fetchPage({})
    } catch (e: unknown) {
      error.value = 'Erro ao excluir produto'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function fetchIncludes(productId: string) {
    loading.value = true
    error.value = null
    try {
      includes.value = await includeService.findByProductId(productId)
    } catch (e: unknown) {
      error.value = 'Erro ao carregar ficha técnica'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function addInclude(productId: string, request: IncludeRequest) {
    loading.value = true
    error.value = null
    try {
      const created = await includeService.add(productId, request)
      includes.value.push(created)
      return created
    } catch (e: unknown) {
      error.value = 'Erro ao adicionar item à ficha técnica'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function batchAddIncludes(productId: string, requests: IncludeRequest[]) {
    loading.value = true
    error.value = null
    try {
      const created = await includeService.addBatch(productId, requests)
      includes.value.push(...created)
      return created
    } catch (e: unknown) {
      error.value = 'Erro ao salvar itens em lote'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function clearRecipe(productId: string) {
    loading.value = true
    error.value = null
    try {
      const deleted = await includeService.clear(productId)
      includes.value = []
      return deleted
    } catch (e: unknown) {
      error.value = 'Erro ao limpar ficha técnica'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updateInclude(productId: string, includeId: string, request: IncludeRequest) {
    loading.value = true
    error.value = null
    try {
      const updated = await includeService.update(productId, includeId, request)
      const idx = includes.value.findIndex((i) => i.id === includeId)
      if (idx !== -1) includes.value[idx] = updated
      return updated
    } catch (e: unknown) {
      error.value = 'Erro ao atualizar item da ficha técnica'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function removeInclude(productId: string, includeId: string) {
    loading.value = true
    error.value = null
    try {
      await includeService.remove(productId, includeId)
      includes.value = includes.value.filter((item) => item.id !== includeId)
    } catch (e: unknown) {
      error.value = 'Erro ao remover item da ficha técnica'
      throw e
    } finally {
      loading.value = false
    }
  }

  return {
    items,
    includes,
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
    fetchIncludes,
    addInclude,
    batchAddIncludes,
    updateInclude,
    removeInclude,
    clearRecipe,
  }
})
