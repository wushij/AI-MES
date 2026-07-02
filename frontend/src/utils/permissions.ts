export const ALL_PERMISSIONS = [
  '生产计划', '工单管理', '班组', '物料', '排产',
  '工序进度', '工单反馈', '异常上报', 'AI 客服',
  '用户管理', '角色管理', 'Coze 配置', '系统配置'
] as const

export type AppPermission = (typeof ALL_PERMISSIONS)[number]

export function matchPermission(
  userPermissions: string[],
  required?: string | string[],
  fullAccess = false
) {
  if (fullAccess) return true
  if (!required) return true
  const requiredList = Array.isArray(required) ? required : [required]
  return requiredList.some((item) => userPermissions.includes(item))
}
