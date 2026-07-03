import type { UserRole } from '@/types'
import { matchPermission, type AppPermission } from './permissions'

interface QuickQuestionDef {
  text: string
  permissions?: AppPermission[]
}

function teamLabel(teamName?: string) {
  const name = teamName?.trim()
  return name || '本班组'
}

function buildAdminQuestions(): QuickQuestionDef[] {
  return [
    { text: '今日车间生产概况如何？' },
    { text: '当前未处理异常有哪些？', permissions: ['异常上报'] },
    { text: '哪些物料触发缺料预警？', permissions: ['物料'] }
  ]
}

function buildSupervisorQuestions(teamName?: string): QuickQuestionDef[] {
  const team = teamLabel(teamName)
  return [
    { text: `${team}今日有哪些在制工单？`, permissions: ['工单管理'] },
    { text: '有哪些工单待派工？', permissions: ['工单管理'] },
    { text: '当前缺料预警涉及哪些物料？', permissions: ['物料'] }
  ]
}

function buildWorkerQuestions(teamName?: string): QuickQuestionDef[] {
  const team = teamLabel(teamName)
  return [
    { text: `${team}有哪些待认领的工序任务？`, permissions: ['工序进度'] },
    { text: '工位缺料应该如何上报异常？', permissions: ['异常上报'] },
    { text: '设备停机处理 SOP 是什么？', permissions: ['异常上报'] }
  ]
}

function filterByPermissions(
  questions: QuickQuestionDef[],
  permissions: string[],
  fullAccess: boolean
) {
  const matched = questions.filter(
    (item) => !item.permissions || matchPermission(permissions, item.permissions, fullAccess)
  )
    .map((item) => item.text)

  if (matched.length) return matched.slice(0, 4)

  return questions.map((item) => item.text).slice(0, 4)
}

export function getChatQuickQuestions(
  role: UserRole | '',
  permissions: string[],
  fullAccess: boolean,
  teamName?: string
): string[] {
  if (fullAccess || role === 'admin') {
    return filterByPermissions(buildAdminQuestions(), permissions, fullAccess)
  }
  if (role === 'supervisor') {
    return filterByPermissions(buildSupervisorQuestions(teamName), permissions, fullAccess)
  }
  return filterByPermissions(buildWorkerQuestions(teamName), permissions, fullAccess)
}
