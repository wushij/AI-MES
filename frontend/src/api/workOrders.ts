import request from './request'
import type { ApiListParams, ApiPage, ProcessTask, WorkOrder } from '@/types'

export function getWorkOrders(params?: ApiListParams) {
  return request.get<ApiPage<WorkOrder>>('/work-orders', { params }).then((res) => res.data)
}

export function createWorkOrder(payload: {
  productName: string
  processName?: string
  priority?: number
  deadline?: string
  teamId?: number | string
  remark?: string
  planId?: number | string
}) {
  return request.post<WorkOrder>('/work-orders', payload).then((res) => res.data)
}

export function getWorkOrderDetail(id: number | string) {
  return request.get<WorkOrder>(`/work-orders/${id}`).then((res) => res.data)
}

export function claimWorkOrder(id: number | string) {
  return request.post(`/work-orders/${id}/claim`).then((res) => res.data)
}

export function assignWorkOrder(
  id: number | string,
  payload: { teamId: number | string; priority: number; remark?: string }
) {
  return request.post(`/work-orders/${id}/assign`, payload).then((res) => res.data)
}

export function updateWorkOrder(
  id: number | string,
  payload: {
    planId?: number | string
    productName?: string
    teamId?: number | string
    processName?: string
    priority?: number
    deadline?: string
    remark?: string
  }
) {
  return request.put<WorkOrder>(`/work-orders/${id}`, payload).then((res) => res.data)
}

export function updateWorkOrderProgress(
  id: number | string,
  payload: { progress: number; processName?: string; deviceId?: number | string; remark?: string; completeCurrentProcess?: boolean }
) {
  return request.put(`/work-orders/${id}/progress`, payload).then((res) => res.data)
}

export function completeWorkOrder(id: number | string) {
  return request.post(`/work-orders/${id}/complete`).then((res) => res.data)
}

export function getMyProcessTasks() {
  return request.get<ProcessTask[]>('/work-orders/process').then((res) => res.data)
}

export function deleteWorkOrder(id: number | string) {
  return request.delete(`/work-orders/${id}`).then((res) => res.data)
}
