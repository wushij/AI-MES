import request from './request'
import type { ApiListParams, ApiPage, Plan } from '@/types'

export function getPlans(params?: ApiListParams) {
  return request.get<ApiPage<Plan>>('/plans', { params }).then((res) => res.data)
}

export function createPlan(payload: Partial<Plan>) {
  return request.post<Plan>('/plans', payload).then((res) => res.data)
}

export function updatePlan(id: number | string, payload: Partial<Plan>) {
  return request.put<Plan>(`/plans/${id}`, payload).then((res) => res.data)
}

export function releasePlan(id: number | string) {
  return request.post(`/plans/${id}/release`).then((res) => res.data)
}

export function deletePlan(id: number | string) {
  return request.delete(`/plans/${id}`).then((res) => res.data)
}
