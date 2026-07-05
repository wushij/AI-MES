export const DEVICE_STATUSES = [
  { value: 'idle', label: '空闲', tagType: 'info' as const },
  { value: 'running', label: '运行中', tagType: 'success' as const },
  { value: 'paused', label: '暂停', tagType: 'warning' as const },
  { value: 'stopped', label: '停机', tagType: 'warning' as const },
  { value: 'maintenance', label: '保养中', tagType: 'warning' as const },
  { value: 'repairing', label: '维修中', tagType: 'warning' as const },
  { value: 'fault', label: '故障', tagType: 'danger' as const },
  { value: 'scrapped', label: '报废', tagType: 'info' as const }
]

export function deviceStatusLabel(status?: string | null) {
  return DEVICE_STATUSES.find((item) => item.value === status)?.label ?? '空闲'
}

export function deviceStatusTagType(status?: string | null) {
  return DEVICE_STATUSES.find((item) => item.value === status)?.tagType ?? 'info'
}

export function deviceActionLabel(actionType?: string) {
  switch (actionType) {
    case 'create': return '新建'
    case 'update': return '资料变更'
    case 'status': return '状态变更'
    case 'exception': return '异常上报'
    case 'handle': return '异常处理'
    case 'inspection': return '设备点检'
    case 'maintenance': return '设备保养'
    case 'repair': return '设备维修'
    default: return actionType ?? '操作'
  }
}
