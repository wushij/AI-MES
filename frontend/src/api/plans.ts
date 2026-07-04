import request from './request'
import type { ApiListParams, ApiPage, Plan } from '@/types'

export function getPlans(params?: ApiListParams) {
  return request.get<ApiPage<Plan>>('/plans', { params }).then((res) => res.data)
}

export interface PlanWorkOrderSummary {
  id: number | string
  orderNo: string
  productName: string
  processName?: string
  progress: number
  status: string
  teamName?: string
  deadline?: string
  scheduledStartTime?: string
  estimatedHours?: number | string
  schedulingRank?: number
}

export interface PlanDetail extends Plan {
  releaseTime?: string
  createdByName?: string
  createdTime?: string
  updatedTime?: string
  workOrderCount?: number
  workOrders?: PlanWorkOrderSummary[]
  completionProgress?: number
  executionStatus?: string
  product?: Record<string, unknown>
  hasBom?: boolean
}

export function getPlan(id: number | string) {
  return request.get<PlanDetail>(`/plans/${id}`).then((res) => res.data)
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

export interface ReleasePreviewWorkOrder {
  batchNo: number
  quantity: number
  productName: string
  deadline?: string
}

export interface ReleasePreview {
  plan: PlanDetail
  splitQty: number
  workOrderCount: number
  workOrders: ReleasePreviewWorkOrder[]
  hasBom: boolean
  bomWarning?: string
}

export function previewPlanRelease(id: number | string) {
  return request.get<ReleasePreview>(`/plans/${id}/release-preview`).then((res) => res.data)
}

export function deletePlan(id: number | string) {
  return request.delete(`/plans/${id}`).then((res) => res.data)
}
