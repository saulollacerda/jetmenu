import api from './api'
import type { OrderRequest, OrderResponse, OrderValuesRequest } from '@/types/Order'
import type { Page, PageParams } from '@/types/Page'
import { DEFAULT_PAGE_SIZE } from '@/types/Page'

export interface OrderFilterParams extends PageParams {
  sort?: string
}

export const orderService = {
  async findAll(params: OrderFilterParams = {}): Promise<Page<OrderResponse>> {
    const query: Record<string, string | number> = {
      search: params.search ?? '',
      page: params.page ?? 0,
      size: params.size ?? DEFAULT_PAGE_SIZE,
    }
    if (params.sort) query.sort = params.sort
    const { data } = await api.get<Page<OrderResponse>>('/orders', { params: query })
    return data
  },

  async findById(id: string): Promise<OrderResponse> {
    const { data } = await api.get<OrderResponse>(`/orders/${id}`)
    return data
  },

  async create(request: OrderRequest): Promise<OrderResponse> {
    const { data } = await api.post<OrderResponse>('/orders', request)
    return data
  },

  async update(id: string, request: OrderRequest): Promise<OrderResponse> {
    const { data } = await api.put<OrderResponse>(`/orders/${id}`, request)
    return data
  },

  async remove(id: string): Promise<void> {
    await api.delete(`/orders/${id}`)
  },

  async updateValues(id: string, request: OrderValuesRequest): Promise<OrderResponse> {
    const { data } = await api.patch<OrderResponse>(`/orders/${id}/values`, request)
    return data
  },

  async restoreValues(id: string): Promise<OrderResponse> {
    const { data } = await api.delete<OrderResponse>(`/orders/${id}/values`)
    return data
  },
}
