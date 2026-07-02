import request from './request'

export interface SysNotification {
  id: number
  userId: number
  title: string
  content: string
  type: 'info' | 'warning' | 'danger'
  targetUrl: string
  isRead: number
  createTime: string
}

export function getNotifications(params?: { isRead?: number }) {
  return request.get<SysNotification[]>('/notifications', { params }).then((res) => res.data)
}

export function getUnreadNotifications() {
  return request.get<SysNotification[]>('/notifications/unread').then((res) => res.data)
}

export function markAllNotificationsAsRead() {
  return request.post<string>('/notifications/read-all').then((res) => res.data)
}

export function markNotificationAsRead(id: number | string) {
  return request.put<string>(`/notifications/${id}/read`).then((res) => res.data)
}

export function clearReadNotifications() {
  return request.delete<string>('/notifications/read').then((res) => res.data)
}
