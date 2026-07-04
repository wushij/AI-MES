export interface ScenarioPreset {
  id: string
  label: string
  desc: string
  materialConstraint: boolean
  deviceConstraint: boolean
  teamConstraint: boolean
}

export const SCENARIO_PRESETS: ScenarioPreset[] = [
  {
    id: 'deadline',
    label: '交期优先',
    desc: '优先保障交期与班组排班',
    materialConstraint: true,
    deviceConstraint: false,
    teamConstraint: true
  },
  {
    id: 'material',
    label: '物料齐套',
    desc: '重点考虑缺料与库存预警',
    materialConstraint: true,
    deviceConstraint: true,
    teamConstraint: false
  },
  {
    id: 'balance',
    label: '负荷均衡',
    desc: '均衡设备与班组负荷',
    materialConstraint: false,
    deviceConstraint: true,
    teamConstraint: true
  }
]

export interface GanttItem {
  workOrderCode: string
  teamName: string
  startLabel: string
  endLabel: string
  hours: number
  leftPercent: number
  widthPercent: number
}

export interface ApplyDiffRow {
  workOrderCode: string
  currentTeam: string
  suggestedTeam: string
  teamChanged: boolean
  currentPriority: string
  suggestedPriority: string
  priorityChanged: boolean
  priorityReason: string
  suggestedStart: string
  suggestedHours: string
  hasChanges: boolean
}

export interface TeamLoadRow {
  id?: number | string
  teamName: string
  memberCount?: number
  pendingCount?: number
  producingCount?: number
  activeTaskCount?: number
  selectedCount?: number
  proposedHours?: number
  loadRate?: number
}

const TIME_FORMAT = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/

export function parseDispatchHours(raw: unknown): number {
  const text = String(raw ?? '').trim()
  if (!text || text === '待定' || text === '--') return 2
  const digits = text.replace(/[^\d.]/g, '')
  const num = Number(digits)
  return Number.isFinite(num) && num > 0 ? num : 2
}

export function parseStartTime(raw: string, fallbackDate: string): Date | null {
  const text = raw.trim()
  if (!text || text === '--') return null
  if (TIME_FORMAT.test(text)) {
    const d = new Date(text.replace(' ', 'T') + ':00')
    return Number.isNaN(d.getTime()) ? null : d
  }
  if (/^\d{2}:\d{2}$/.test(text)) {
    const d = new Date(`${fallbackDate}T${text}:00`)
    return Number.isNaN(d.getTime()) ? null : d
  }
  return null
}

export function formatTimeLabel(date: Date): string {
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

export function buildGanttItems(
  dispatches: Array<{ workOrderCode: string; teamName: string; startTime: string; hours: string }>,
  planDate: string
): GanttItem[] {
  if (!dispatches.length) return []

  const parsed = dispatches
    .map((item) => {
      const start = parseStartTime(item.startTime, planDate)
      const hours = parseDispatchHours(item.hours)
      if (!start) return null
      const end = new Date(start.getTime() + hours * 60 * 60 * 1000)
      return { item, start, end, hours }
    })
    .filter(Boolean) as Array<{
    item: (typeof dispatches)[number]
    start: Date
    end: Date
    hours: number
  }>

  if (!parsed.length) return []

  const minStart = Math.min(...parsed.map((p) => p.start.getTime()))
  const maxEnd = Math.max(...parsed.map((p) => p.end.getTime()))
  const spanMs = Math.max(maxEnd - minStart, 60 * 60 * 1000)

  return parsed.map(({ item, start, end, hours }) => {
    const leftPercent = ((start.getTime() - minStart) / spanMs) * 100
    const widthPercent = Math.max(((end.getTime() - start.getTime()) / spanMs) * 100, 8)
    return {
      workOrderCode: item.workOrderCode,
      teamName: item.teamName,
      startLabel: formatTimeLabel(start),
      endLabel: formatTimeLabel(end),
      hours,
      leftPercent,
      widthPercent
    }
  })
}

export function enrichTeamLoads(
  teams: TeamLoadRow[],
  dispatches: Array<{ teamName: string; hours: string }>
): TeamLoadRow[] {
  const proposedByTeam = new Map<string, number>()
  for (const row of dispatches) {
    const team = row.teamName?.trim()
    if (!team) continue
    proposedByTeam.set(team, (proposedByTeam.get(team) ?? 0) + parseDispatchHours(row.hours))
  }

  return teams.map((team) => {
    const proposedHours = proposedByTeam.get(team.teamName) ?? 0
    const baseLoad = team.loadRate ?? 0
    const boosted = Math.min(100, baseLoad + proposedHours * 8)
    return {
      ...team,
      proposedHours,
      loadRate: boosted
    }
  })
}

export function buildApplyDiffRows(
  workOrders: Array<{
    orderNo: string
    teamName?: string | null
    priorityLabel?: string
  }>,
  priorities: Array<{ workOrderCode: string; priorityLabel: string; reason?: string; rank?: number }>,
  dispatches: Array<{ workOrderCode: string; teamName: string; startTime: string; hours: string }>
): ApplyDiffRow[] {
  const orderMap = new Map(workOrders.map((o) => [o.orderNo, o]))
  const priorityMap = new Map(priorities.map((p) => [p.workOrderCode, p]))

  return dispatches.map((dispatch) => {
    const current = orderMap.get(dispatch.workOrderCode)
    const currentTeam = current?.teamName?.trim() || '未分配'
    const suggestedTeam = dispatch.teamName?.trim() || '--'
    const currentPriority = current?.priorityLabel ?? '--'
    const priorityItem = priorityMap.get(dispatch.workOrderCode)
    const suggestedPriority = priorityItem?.priorityLabel ?? '--'
    const teamChanged = currentTeam !== suggestedTeam
    const priorityChanged = currentPriority !== suggestedPriority
    return {
      workOrderCode: dispatch.workOrderCode,
      currentTeam,
      suggestedTeam,
      teamChanged,
      currentPriority,
      suggestedPriority,
      priorityChanged,
      priorityReason: priorityItem?.reason ?? '--',
      suggestedStart: dispatch.startTime,
      suggestedHours: dispatch.hours,
      hasChanges: teamChanged || priorityChanged
    }
  })
}

export function statusLabel(status?: string) {
  const map: Record<string, string> = {
    pending: '待派工',
    assigned: '已派工',
    producing: '生产中',
    exception: '异常',
    done: '已完成'
  }
  return map[String(status ?? '')] ?? status ?? '--'
}

export function exceptionTypeLabel(type?: string) {
  const map: Record<string, string> = {
    device: '设备停机',
    material: '缺料',
    shortage: '缺料',
    quality: '质量异常'
  }
  return map[String(type ?? '')] ?? type ?? '异常'
}
