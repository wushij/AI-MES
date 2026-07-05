import request from './request'

export interface DeviceInspectionPlan {
  id: number | string
  planCode: string
  planName: string
  deviceId?: number | string
  categoryId?: number | string
  categoryName?: string
  cycleType?: string
  cycleTypeLabel?: string
  checkItems: string[]
  enabled?: boolean
  remark?: string
  createdTime?: string
  updatedTime?: string
}

export interface DeviceInspectionItemResult {
  itemName: string
  isNormal: boolean
  remark?: string
}

export interface DeviceInspectionRecord {
  id: number | string
  recordNo: string
  deviceId: number | string
  planId?: number | string
  planName?: string
  inspectorId?: number | string
  inspectorName?: string
  inspectTime?: string
  checkItems: DeviceInspectionItemResult[]
  isNormal: boolean
  isNormalLabel?: string
  remark?: string
  createdTime?: string
}

export function getDeviceInspectionPlans(params?: { deviceId?: number | string; categoryId?: number | string; keyword?: string }) {
  return request.get<DeviceInspectionPlan[]>('/device-inspections/plans', { params }).then((res) => res.data)
}

export function getDeviceInspectionPlansForDevice(deviceId: number | string) {
  return request.get<DeviceInspectionPlan[]>(`/device-inspections/plans/for-device/${deviceId}`).then((res) => res.data)
}

export function getDeviceInspectionPlan(id: number | string) {
  return request.get<DeviceInspectionPlan>(`/device-inspections/plans/${id}`).then((res) => res.data)
}

export function createDeviceInspectionPlan(payload: Partial<DeviceInspectionPlan> & { checkItems: string[] }) {
  return request.post<DeviceInspectionPlan>('/device-inspections/plans', payload).then((res) => res.data)
}

export function updateDeviceInspectionPlan(id: number | string, payload: Partial<DeviceInspectionPlan> & { checkItems: string[] }) {
  return request.put<DeviceInspectionPlan>(`/device-inspections/plans/${id}`, payload).then((res) => res.data)
}

export function deleteDeviceInspectionPlan(id: number | string) {
  return request.delete(`/device-inspections/plans/${id}`).then((res) => res.data)
}

export function getDeviceInspectionRecords(deviceId: number | string) {
  return request.get<DeviceInspectionRecord[]>('/device-inspections/records', { params: { deviceId } }).then((res) => res.data)
}

export function submitDeviceInspection(payload: {
  deviceId: number | string
  planId?: number | string
  items: DeviceInspectionItemResult[]
  remark?: string
}) {
  return request.post<DeviceInspectionRecord>('/device-inspections/records', payload).then((res) => res.data)
}

export function deleteDeviceInspectionRecord(id: number | string) {
  return request.delete(`/device-inspections/records/${id}`).then((res) => res.data)
}
