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

export function getMaterialAlerts() {
  return request.get<Material[]>('/materials/alerts').then((res) => res.data)
}
