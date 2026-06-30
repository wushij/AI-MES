import request from './request'
import type { ApiListParams, ApiPage, ProductionException } from '@/types'

export function getExceptions(params?: ApiListParams) {
  return request.get<ApiPage<ProductionException>>('/exceptions', { params }).then((res) => res.data)
}

export function createException(payload: Partial<ProductionException>) {
  return request.post<ProductionException>('/exceptions', payload).then((res) => res.data)
}

export function handleException(
  id: number | string,
  payload: { handleAction: string; handleResult: string }
) {
  return request.put(`/exceptions/${id}/handle`, payload).then((res) => res.data)
}
