import request from './request'
import { normalizeList } from '@/utils/normalizeList'
import type { UserProfile } from '@/types'

export function getUsers() {
  return request.get<UserProfile[]>('/admin/users').then((res) => normalizeList<UserProfile>(res.data))
}

export function getUserDetail(id: number | string) {
  return request.get<UserProfile>(`/admin/users/${id}`).then((res) => res.data)
}

export function createUser(payload: Record<string, unknown>) {
  return request.post('/admin/users', payload).then((res) => res.data)
}

export function updateUser(id: number | string, payload: Record<string, unknown>) {
  return request.put(`/admin/users/${id}`, payload).then((res) => res.data)
}

export function resetUserPassword(id: number | string, password: string) {
  return request.post(`/admin/users/${id}/reset-password`, { password }).then((res) => res.data)
}

export function toggleUserStatus(id: number | string) {
  return request.post(`/admin/users/${id}/toggle-status`).then((res) => res.data)
}
