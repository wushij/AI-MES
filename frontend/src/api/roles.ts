import request from './request'

export interface RoleItem {
  id: string
  roleName: string
  permissions: string[]
  fullAccess?: boolean
}

export function getRoles() {
  return request.get<RoleItem[]>('/admin/roles').then((res) => res.data)
}

export function updateRolePermissions(roleKey: string, permissions: string[]) {
  return request.put<string>(`/admin/roles/${roleKey}/permissions`, permissions).then((res) => res.data)
}
