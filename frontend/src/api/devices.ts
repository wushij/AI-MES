import request from './request'

export interface DeviceItem {
  id: number | string
  deviceCode: string
  deviceName: string
  categoryId?: number | string
  categoryName?: string
  deviceType?: string
  brand?: string
  model?: string
  serialNumber?: string
  workshop?: string
  lineName?: string
  station?: string
  managerId?: number | string
  managerName?: string
  purchaseDate?: string
  installDate?: string
  enableDate?: string
  warrantyDate?: string
  status?: string
  statusLabel?: string
  teamId?: number | string
  teamName?: string
  remark?: string
  todayAlertCount?: number
  maintenanceOverdueCount?: number
  todayRunMinutes?: number
  todayRunLabel?: string
  todayStopMinutes?: number
  todayStopLabel?: string
  utilizationRate?: number
  createdTime?: string
  updatedTime?: string
}

export interface DeviceCategoryItem {
  id: number | string
  parentId?: number | string
  categoryName: string
  sortNo?: number
  children?: DeviceCategoryItem[]
}

export interface DeviceHistoryItem {
  id: number | string
  actionType: string
  actionDesc: string
  operatorName?: string
  beforeValue?: string
  afterValue?: string
  beforeLabel?: string
  afterLabel?: string
  relatedEventId?: number | string
  createTime?: string
}

export interface DeviceTodayAlert {
  id: number | string
  alertType: string
  alertTypeLabel?: string
  refNo?: string
  title?: string
  status?: string
  statusLabel?: string
  occurTime?: string
  source?: string
  sourceId?: number | string
  targetUrl?: string
}

export interface DeviceRuntimeStats {
  todayRunMinutes?: number
  todayRunLabel?: string
  todayStopMinutes?: number
  todayStopLabel?: string
  todayPauseMinutes?: number
  todayRepairStopMinutes?: number
  todayStatusStopMinutes?: number
  utilizationRate?: number
  todayAlertCount?: number
  todayAlerts?: DeviceTodayAlert[]
  openExceptionCount?: number
  maintenanceOverdueCount?: number
  statusLabel?: string
  dataSource?: string
}

export interface DeviceFullDetail extends DeviceItem {
  runtime?: DeviceRuntimeStats
  exceptions?: Array<{
    id: number | string
    eventNo: string
    eventType: string
    description: string
    status: string
    occurTime?: string
    handleResult?: string
  }>
  history?: DeviceHistoryItem[]
}

export interface DeviceProcessRecordItem {
  id: number | string
  workOrderId?: number | string
  orderNo?: string
  productName?: string
  processName?: string
  seqNo?: number
  status?: string
  statusLabel?: string
  startTime?: string
  endTime?: string
  remark?: string
}

export interface DeviceSummary {
  totalCount: number
  runningCount: number
  idleCount: number
  faultCount: number
  maintenanceCount: number
  scrappedCount: number
  todayAlertCount: number
  devices: DeviceItem[]
}

export function getDevices(params?: { keyword?: string; status?: string; categoryId?: number | string }) {
  return request.get<DeviceItem[]>('/devices', { params }).then((res) => res.data)
}

export function getDeviceOptions() {
  return request.get<Array<{ id: number | string; deviceCode: string; deviceName: string; lineName?: string; status?: string; statusLabel?: string; selectable?: boolean }>>('/devices/options').then((res) => res.data)
}

export function getDeviceProcessRecords(id: number | string) {
  return request.get<DeviceProcessRecordItem[]>(`/devices/${id}/process-records`).then((res) => res.data)
}

export function getDeviceTodayAlerts(id: number | string) {
  return request.get<DeviceTodayAlert[]>(`/devices/${id}/today-alerts`).then((res) => res.data)
}

export function getDeviceDetail(id: number | string) {
  return request.get<DeviceItem>(`/devices/${id}`).then((res) => res.data)
}

export function getDeviceFullDetail(id: number | string) {
  return request.get<DeviceFullDetail>(`/devices/${id}/full`).then((res) => res.data)
}

export function getDeviceFormOptions() {
  return request.get<{
    categories: DeviceCategoryItem[]
    teams: Array<{ id: number | string; teamName: string }>
    managers: Array<{ id: number | string; realName: string; role: string }>
    statuses: Array<{ value: string; label: string }>
  }>('/devices/form-options').then((res) => res.data)
}

export function getDeviceCategories() {
  return request.get<DeviceCategoryItem[]>('/devices/categories').then((res) => res.data)
}

export function getDeviceSummary() {
  return request.get<DeviceSummary>('/devices/summary').then((res) => res.data)
}

export function createDeviceCategory(payload: Partial<DeviceCategoryItem>) {
  return request.post<DeviceCategoryItem>('/devices/categories', payload).then((res) => res.data)
}

export function updateDeviceCategory(id: number | string, payload: Partial<DeviceCategoryItem>) {
  return request.put<DeviceCategoryItem>(`/devices/categories/${id}`, payload).then((res) => res.data)
}

export function deleteDeviceCategory(id: number | string) {
  return request.delete(`/devices/categories/${id}`).then((res) => res.data)
}

export function createDevice(payload: Partial<DeviceItem>) {
  return request.post<DeviceItem>('/devices', payload).then((res) => res.data)
}

export function updateDevice(id: number | string, payload: Partial<DeviceItem>) {
  return request.put<DeviceItem>(`/devices/${id}`, payload).then((res) => res.data)
}

export function updateDeviceStatus(id: number | string, payload: { status: string; remark?: string }) {
  return request.put<DeviceItem>(`/devices/${id}/status`, payload).then((res) => res.data)
}

export function deleteDevice(id: number | string) {
  return request.delete(`/devices/${id}`).then((res) => res.data)
}
