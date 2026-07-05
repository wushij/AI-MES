import request from './request'

export interface DeviceMaintenancePlan {
  id: number | string
  planCode: string
  planName: string
  deviceId?: number | string
  categoryId?: number | string
  categoryName?: string
  cycleType?: string
  cycleTypeLabel?: string
  maintenanceItems: string[]
  nextDueDate?: string
  lastMaintenanceDate?: string
  enabled?: boolean
  overdue?: boolean
  overdueDays?: number
  remark?: string
}

export interface DeviceMaintenanceItemResult {
  itemName: string
  done: boolean
  remark?: string
}

export interface DeviceMaintenanceRecord {
  id: number | string
  recordNo: string
  deviceId: number | string
  planId?: number | string
  planName?: string
  maintainerId?: number | string
  maintainerName?: string
  maintenanceTime?: string
  maintenanceItems: DeviceMaintenanceItemResult[]
  isCompleted: boolean
  isCompletedLabel?: string
  remark?: string
}

export function getDeviceMaintenancePlans(params?: { deviceId?: number | string; categoryId?: number | string; keyword?: string }) {
  return request.get<DeviceMaintenancePlan[]>('/device-maintenances/plans', { params }).then((res) => res.data)
}

export function getDeviceMaintenancePlansForDevice(deviceId: number | string) {
  return request.get<DeviceMaintenancePlan[]>(`/device-maintenances/plans/for-device/${deviceId}`).then((res) => res.data)
}

export function getDueDeviceMaintenancePlans(deviceId?: number | string) {
  return request.get<DeviceMaintenancePlan[]>('/device-maintenances/plans/due', { params: { deviceId } }).then((res) => res.data)
}

export function getDeviceMaintenanceRecords(deviceId: number | string) {
  return request.get<DeviceMaintenanceRecord[]>('/device-maintenances/records', { params: { deviceId } }).then((res) => res.data)
}

export function submitDeviceMaintenance(payload: {
  deviceId: number | string
  planId?: number | string
  items: DeviceMaintenanceItemResult[]
  remark?: string
}) {
  return request.post<DeviceMaintenanceRecord>('/device-maintenances/records', payload).then((res) => res.data)
}

export function deleteDeviceMaintenanceRecord(id: number | string) {
  return request.delete(`/device-maintenances/records/${id}`).then((res) => res.data)
}

export function createDeviceMaintenancePlan(payload: Partial<DeviceMaintenancePlan> & { maintenanceItems: string[] }) {
  return request.post<DeviceMaintenancePlan>('/device-maintenances/plans', payload).then((res) => res.data)
}

export function updateDeviceMaintenancePlan(id: number | string, payload: Partial<DeviceMaintenancePlan> & { maintenanceItems: string[] }) {
  return request.put<DeviceMaintenancePlan>(`/device-maintenances/plans/${id}`, payload).then((res) => res.data)
}

export function deleteDeviceMaintenancePlan(id: number | string) {
  return request.delete(`/device-maintenances/plans/${id}`).then((res) => res.data)
}
