import request from './request'
import type { ApiListParams, ApiPage, Material } from '@/types'

export function getMaterials(params?: ApiListParams) {
  return request.get<ApiPage<Material>>('/materials', { params }).then((res) => res.data)
}

export function createMaterial(payload: {
  materialCode?: string
  materialName: string
  stockQty?: number
  safetyStock: number
  unit?: string
  remark?: string
}) {
  return request.post<Material>('/materials', payload).then((res) => res.data)
}

export function updateMaterial(id: number | string, payload: Partial<Material>) {
  return request.put<Material>(`/materials/${id}`, payload).then((res) => res.data)
}

export function deleteMaterial(id: number | string) {
  return request.delete(`/materials/${id}`).then((res) => res.data)
}

export function getMaterialAlerts() {
  return request.get<Material[]>('/materials/alerts').then((res) => res.data)
}

export interface MaterialTransaction {
  id: number | string
  materialId: number | string
  txnType: string
  qty: number
  beforeQty: number
  afterQty: number
  refType?: string
  refId?: number | string
  operatorId?: number | string
  remark?: string
  createdTime: string
}

export function getMaterialTransactions(id: number | string) {
  return request.get<MaterialTransaction[]>(`/materials/${id}/transactions`).then((res) => res.data)
}

export function getMaterialOptions() {
  return request.get<Array<{ id: number | string; materialCode: string; materialName: string; unit: string; stockQty: number }>>('/materials/options').then((res) => res.data)
}
