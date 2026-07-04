import request from './request'

export interface ProcessParameter {
  id?: number | string
  paramName: string
  paramValue?: string
  minValue?: string
  maxValue?: string
  unit?: string
}

export interface ProcessDeviceBinding {
  id?: number | string
  bindType: 'device' | 'category'
  deviceId?: number | string
  categoryId?: number | string
  deviceName?: string
  deviceCode?: string
  deviceStatus?: string
  categoryName?: string
}

export interface ProcessMaterialBinding {
  id?: number | string
  materialId: number | string
  materialCode?: string
  materialName?: string
  stockQty?: number
  qty: number
  unit?: string
  materialType?: string
  remark?: string
}

export interface ProcessSop {
  id: number | string
  fileName: string
  fileType: string
  fileSize?: number
  remark?: string
  previewUrl?: string
  createTime?: string
}

export interface ProcessOperation {
  id?: number | string
  seqNo: number
  operationCode?: string
  operationName: string
  standardHours?: number
  prepHours?: number
  changeoverHours?: number
  needReport?: number
  needCheck?: number
  needScan?: number
  remark?: string
  parameters?: ProcessParameter[]
  devices?: ProcessDeviceBinding[]
  materials?: ProcessMaterialBinding[]
  sops?: ProcessSop[]
  deviceIds?: number[]
  categoryIds?: number[]
}

export interface ProcessRouteHistory {
  id: number | string
  version: string
  actionType: string
  actionDesc: string
  operatorName?: string
  createTime?: string
}

export interface ProcessRoute {
  id: number | string
  routeCode?: string
  routeName: string
  productName?: string
  version?: string
  status?: string
  rejectedReason?: string
  isDefault?: boolean
  enabled?: boolean
  remark?: string
  totalStandardHours?: number
  operations: ProcessOperation[]
  history?: ProcessRouteHistory[]
}

export interface ProcessExecutionContext {
  workOrderId: number | string
  routingId?: number | string
  routeVersion?: string
  productName?: string
  operations: ProcessOperation[]
}

export type SaveMode = 'draft' | 'submit' | 'publish'

export function getDefaultProcessRoute() {
  return request.get<ProcessRoute>('/process-routes/default').then((res) => res.data)
}

export function getProcessOperationNames() {
  return request.get<string[]>('/process-routes/operations').then((res) => res.data)
}

export function getProcessExecutionContext(workOrderId: number | string) {
  return request.get<ProcessExecutionContext>(`/process-routes/execution/${workOrderId}`).then((res) => res.data)
}

export function getProcessRoutes() {
  return request.get<ProcessRoute[]>('/process-routes').then((res) => res.data)
}

export function getProcessRoute(id: number | string) {
  return request.get<ProcessRoute>(`/process-routes/${id}`).then((res) => res.data)
}

export function createProcessRoute(payload: Partial<ProcessRoute> & { saveMode?: SaveMode }) {
  return request.post<ProcessRoute>('/process-routes', payload).then((res) => res.data)
}

export function updateProcessRoute(id: number | string, payload: Partial<ProcessRoute> & { saveMode?: SaveMode }) {
  return request.put<ProcessRoute>(`/process-routes/${id}`, payload).then((res) => res.data)
}

export function submitProcessRoute(id: number | string) {
  return request.put<ProcessRoute>(`/process-routes/${id}/submit`).then((res) => res.data)
}

export function approveProcessRoute(id: number | string) {
  return request.put<ProcessRoute>(`/process-routes/${id}/approve`).then((res) => res.data)
}

export function rejectProcessRoute(id: number | string, reason: string) {
  return request.put<ProcessRoute>(`/process-routes/${id}/reject`, { reason }).then((res) => res.data)
}

export function updateDefaultProcessRoute(payload: Partial<ProcessRoute>) {
  return request.put<ProcessRoute>('/process-routes/default', payload).then((res) => res.data)
}

export function copyProcessRoute(id: number | string) {
  return request.post<ProcessRoute>(`/process-routes/${id}/copy`).then((res) => res.data)
}

export function setDefaultProcessRoute(id: number | string) {
  return request.put<ProcessRoute>(`/process-routes/${id}/default`).then((res) => res.data)
}

export function toggleProcessRoute(id: number | string) {
  return request.put<ProcessRoute>(`/process-routes/${id}/toggle`).then((res) => res.data)
}

export function deleteProcessRoute(id: number | string) {
  return request.delete(`/process-routes/${id}`).then((res) => res.data)
}

export function uploadOperationSop(operationId: number | string, file: File, remark?: string) {
  const form = new FormData()
  form.append('file', file)
  if (remark) form.append('remark', remark)
  return request.post<ProcessSop>(`/process-routes/operations/${operationId}/sop`, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }).then((res) => res.data)
}

export function deleteOperationSop(sopId: number | string) {
  return request.delete(`/process-routes/sop/${sopId}`).then((res) => res.data)
}

export function buildSopPreviewUrl(sopId: number | string) {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  return `${base}/process-routes/sop/${sopId}/file`
}

export function routeStatusLabel(status?: string) {
  const map: Record<string, string> = {
    draft: '草稿',
    pending_approval: '待审批',
    published: '已发布',
    rejected: '已驳回',
    disabled: '已停用'
  }
  return status ? (map[status] ?? status) : '--'
}

export function routeStatusType(status?: string) {
  if (status === 'published') return 'success'
  if (status === 'pending_approval') return 'warning'
  if (status === 'rejected') return 'danger'
  if (status === 'draft') return 'info'
  return 'info'
}

export function materialTypeLabel(type?: string) {
  const map: Record<string, string> = {
    raw: '原材料',
    semi: '半成品',
    aux: '辅料',
    tooling: '工装夹具'
  }
  return type ? (map[type] ?? type) : '原材料'
}

export function buildOperationPayload(operations: ProcessOperation[]) {
  return operations.map((item, index) => ({
    id: item.id,
    seqNo: item.seqNo ?? index + 1,
    operationCode: item.operationCode,
    operationName: item.operationName.trim(),
    standardHours: item.standardHours,
    prepHours: item.prepHours,
    changeoverHours: item.changeoverHours,
    needReport: item.needReport ?? 1,
    needCheck: item.needCheck ?? 0,
    needScan: item.needScan ?? 0,
    remark: item.remark,
    parameters: item.parameters?.filter((p) => p.paramName?.trim()) ?? [],
    deviceIds: item.deviceIds ?? item.devices?.filter((d) => d.bindType === 'device').map((d) => Number(d.deviceId)) ?? [],
    categoryIds: item.categoryIds ?? item.devices?.filter((d) => d.bindType === 'category').map((d) => Number(d.categoryId)) ?? [],
    materials: (item.materials ?? []).map((m) => ({
      materialId: m.materialId,
      qty: m.qty,
      unit: m.unit,
      materialType: m.materialType || 'raw',
      remark: m.remark
    }))
  }))
}
