export function priorityLabel(value?: string | number | null) {
  switch (String(value ?? 2)) {
    case '1':
    case 'high':
      return '高'
    case '3':
    case 'low':
      return '低'
    case '2':
    case 'medium':
    default:
      return '中'
  }
}

/** Backend stores priority as 1=高, 2=中, 3=低 */
export function normalizePriority(value?: string | number | null): number {
  if (typeof value === 'number' && value >= 1 && value <= 3) return value
  switch (String(value ?? '')) {
    case '1':
    case 'high':
      return 1
    case '3':
    case 'low':
      return 3
    default:
      return 2
  }
}

export function exceptionTypeLabel(type?: string) {
  switch (type) {
    case 'device':
      return '设备停机'
    case 'material':
    case 'shortage':
      return '缺料'
    case 'quality':
      return '质量异常'
    default:
      return '其他'
  }
}

export function roleLabel(value?: string) {
  switch (value) {
    case 'admin':
      return '管理员'
    case 'supervisor':
      return '车间主管'
    case 'worker':
      return '普通员工'
    default:
      return value ?? '--'
  }
}
