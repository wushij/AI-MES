import request from './request'

export interface DeviceRepairOrder {
  id: number | string
  repairNo: string
  deviceId: number | string
  eventId?: number | string
  faultReason: string
  faultCode?: string
  description?: string
  status: string
  statusLabel?: string
  reporterId?: number | string
  reporterName?: string
  repairerId?: number | string
  repairerName?: string
  reportTime?: string
  startTime?: string
  endTime?: string
  repairMinutes?: number
  repairAction?: string
  repairResult?: string
  remark?: string
}

export function getDeviceRepairs(params?: { deviceId?: number | string; status?: string }) {
  return request.get<DeviceRepairOrder[]>('/device-repairs', { params }).then((res) => res.data)
}

export function getDeviceRepair(id: number | string) {
  return request.get<DeviceRepairOrder>(`/device-repairs/${id}`).then((res) => res.data)
}

export function createDeviceRepair(payload: {
  deviceId: number | string
  faultReason: string
  faultCode?: string
  description?: string
  eventId?: number | string
  remark?: string
}) {
  return request.post<DeviceRepairOrder>('/device-repairs', payload).then((res) => res.data)
}

export function startDeviceRepair(id: number | string) {
  return request.put<DeviceRepairOrder>(`/device-repairs/${id}/start`).then((res) => res.data)
}

export function completeDeviceRepair(id: number | string, payload: {
  repairAction: string
  repairResult: string
  remark?: string
}) {
  return request.put<DeviceRepairOrder>(`/device-repairs/${id}/complete`, payload).then((res) => res.data)
}

export function deleteDeviceRepair(id: number | string) {
  return request.delete(`/device-repairs/${id}`).then((res) => res.data)
}
