import request from './request'
import { exceptionTypeLabel } from '@/utils/labels'

export async function getDashboardData() {
  const [stats, progress, alerts] = await Promise.all([
    request.get<Record<string, unknown>>('/dashboard/stats').then((r) => r.data),
    request.get<Record<string, unknown>[]>('/dashboard/progress').then((r) => r.data),
    request
      .get<{ exceptions?: Record<string, unknown>[]; materials?: Record<string, unknown>[]; devices?: Record<string, unknown>[] }>(
        '/dashboard/alerts'
      )
      .then((r) => r.data)
  ])

  return {
    overview: {
      planCount: Number(stats.planCount ?? 0),
      planTrend: String(stats.planTrend ?? '0%'),
      inProgressCount: Number(stats.inProgressWorkOrderCount ?? 0),
      inProgressTrend: String(stats.inProgressTrend ?? '0%'),
      openExceptionCount: Number(stats.openExceptionCount ?? 0),
      newExceptionCount: Number(stats.newExceptionCount ?? 0),
      materialAlertCount: Number(stats.materialAlertCount ?? 0),
      newMaterialAlertCount: Number(stats.newMaterialAlertCount ?? 0),
      deviceTotalCount: Number(stats.deviceTotalCount ?? 0),
      deviceRunningCount: Number(stats.deviceRunningCount ?? 0),
      deviceFaultCount: Number(stats.deviceFaultCount ?? 0),
      deviceTodayAlertCount: Number(stats.deviceTodayAlertCount ?? 0),
      todayOutput: Number(stats.todayOutput ?? 0)
    },
    teamProgress: progress.map((item) => ({
      teamName: String(item.teamName ?? '--'),
      completedQty: Number(item.doneCount ?? 0),
      totalQty: Number(item.totalCount ?? 0),
      progress: Number(item.avgProgress ?? 0)
    })),
    outputTrend: normalizeOutputTrend(stats.outputTrend, stats.todayOutput),
    exceptionList: (alerts.exceptions ?? []).map((item) => ({
      id: item.id,
      code: item.eventNo,
      typeLabel: exceptionTypeLabel(String(item.eventType ?? '')),
      workOrderCode: item.workOrderId,
      deviceLabel: item.deviceCode && item.deviceName ? `${item.deviceCode} · ${item.deviceName}` : '',
      reportedAt: item.occurTime,
      status: item.status
    })),
    deviceList: (alerts.devices ?? []) as Array<Record<string, unknown>>,
    materialAlerts: (alerts.materials ?? []).map((item) => ({
      id: item.id,
      name: item.materialName,
      stockQty: item.stockQty,
      safetyStock: item.safetyStock,
      gap: item.gap
    }))
  }
}

export function getWorkshopSummary() {
  return request
    .get<{
      completionRate: number
      completedOrders: number
      totalOrders: number
      lineName: string
      shiftName?: string
      todayOutput?: number
      onTimeRate?: number
      activeLines?: number
    }>('/dashboard/workshop-summary')
    .then((res) => res.data)
}

function normalizeOutputTrend(raw: unknown, todayOutput: unknown) {
  const trend = Array.isArray(raw)
    ? raw.map((item) => {
        const row = item as { date?: string; outputQty?: number }
        return {
          date: String(row.date ?? ''),
          outputQty: Number(row.outputQty ?? 0)
        }
      })
    : buildEmptyTrend()
  if (trend.length && todayOutput != null && todayOutput !== '') {
    trend[trend.length - 1].outputQty = Number(todayOutput)
  }
  return trend
}

function buildEmptyTrend() {
  const days: { date: string; outputQty: number }[] = []
  const now = new Date()
  for (let i = 6; i >= 0; i -= 1) {
    const d = new Date(now)
    d.setDate(now.getDate() - i)
    days.push({
      date: `${d.getMonth() + 1}/${d.getDate()}`,
      outputQty: 0
    })
  }
  return days
}
