import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import { Calendar, InfoFilled, Warning } from '@element-plus/icons-vue'
import {
  clearReadNotifications,
  getNotifications,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  type SysNotification
} from '@/api/notifications'

export interface NotificationItem {
  id: number
  type: SysNotification['type']
  icon: typeof Calendar
  text: string
  time: string
  target: string
  isRead: boolean
}

export interface DeviceAlertPushPayload {
  deviceId: number | string
  deviceCode?: string
  deviceName?: string
  message?: string
  todayAlertCount?: number
  alert?: {
    alertType?: string
    alertTypeLabel?: string
    title?: string
    targetUrl?: string
  }
}

const MAX_RECONNECT_ATTEMPTS = 8
const BASE_RECONNECT_DELAY_MS = 1000

function isNotificationRead(value: unknown) {
  return value === 1 || value === true || value === '1'
}

function formatRelativeTime(timeStr: string) {
  if (!timeStr) return '刚刚'
  try {
    const date = new Date(timeStr.replace(' ', 'T'))
    const diff = Date.now() - date.getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
    return `${date.getMonth() + 1}月${date.getDate()}日`
  } catch {
    return '刚刚'
  }
}

function mapNotification(item: SysNotification): NotificationItem {
  return {
    id: item.id,
    type: item.type,
    icon: item.type === 'danger' ? InfoFilled : item.type === 'warning' ? Warning : Calendar,
    text: item.content,
    time: formatRelativeTime(item.createTime),
    target: item.targetUrl,
    isRead: isNotificationRead(item.isRead)
  }
}

function buildWebSocketUrl(token: string) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/notifications?token=${encodeURIComponent(token)}`
}

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref<NotificationItem[]>([])
  const connected = ref(false)
  const deviceAlertVersion = ref(0)
  const lastDeviceAlert = ref<DeviceAlertPushPayload | null>(null)

  let socket: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttempts = 0
  let manualClose = false

  const unreadItems = computed(() => items.value.filter((item) => !item.isRead))
  const readItems = computed(() => items.value.filter((item) => item.isRead))
  const unreadCount = computed(() => unreadItems.value.length)

  function clearReconnectTimer() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function disconnect() {
    manualClose = true
    clearReconnectTimer()
    connected.value = false
    if (socket) {
      socket.close()
      socket = null
    }
  }

  function scheduleReconnect(token: string) {
    if (manualClose || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      return
    }

    const delay = Math.min(BASE_RECONNECT_DELAY_MS * 2 ** reconnectAttempts, 30000)
    reconnectAttempts += 1
    reconnectTimer = setTimeout(() => {
      connect(token)
    }, delay)
  }

  function prependNotification(notification: SysNotification) {
    const mapped = mapNotification(notification)
    items.value = [mapped, ...items.value.filter((item) => item.id !== mapped.id)]
  }

  function handleSocketMessage(event: MessageEvent<string>) {
    try {
      const payload = JSON.parse(event.data) as {
        type?: string
        data?: SysNotification | DeviceAlertPushPayload
      }

      if (payload.type === 'device_alert' && payload.data) {
        const alertData = payload.data as DeviceAlertPushPayload
        lastDeviceAlert.value = alertData
        deviceAlertVersion.value += 1

        const label = alertData.alert?.alertTypeLabel || '设备报警'
        ElNotification({
          title: label,
          message: alertData.message || `${alertData.deviceCode ?? ''} ${alertData.deviceName ?? ''}`.trim() || '收到新的设备报警',
          type: 'error',
          position: 'top-right',
          duration: 5000
        })
        return
      }

      if (payload.type !== 'notification' || !payload.data) {
        return
      }

      prependNotification(payload.data as SysNotification)

      const type = (payload.data as SysNotification).type
      ElNotification({
        title: (payload.data as SysNotification).title || '新通知',
        message: (payload.data as SysNotification).content,
        type: type === 'danger' ? 'error' : type === 'warning' ? 'warning' : 'info',
        position: 'top-right',
        duration: 4500
      })
    } catch (error) {
      console.error('[NotificationWS] 解析消息失败:', error)
    }
  }

  function connect(token: string) {
    if (!token) {
      return
    }

    clearReconnectTimer()
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      return
    }

    manualClose = false
    socket = new WebSocket(buildWebSocketUrl(token))

    socket.onopen = () => {
      connected.value = true
      reconnectAttempts = 0
    }

    socket.onmessage = handleSocketMessage

    socket.onclose = () => {
      connected.value = false
      socket = null
      if (!manualClose) {
        scheduleReconnect(token)
      }
    }

    socket.onerror = () => {
      connected.value = false
    }
  }

  async function fetchAll() {
    try {
      const list = await getNotifications()
      items.value = list.map(mapNotification)
    } catch (error) {
      console.error('加载系统通知失败:', error)
    }
  }

  async function markAllAsRead() {
    await markAllNotificationsAsRead()
    items.value = items.value.map((item) => ({ ...item, isRead: true }))
  }

  async function markAsRead(id: number) {
    await markNotificationAsRead(id)
    items.value = items.value.map((item) => (item.id === id ? { ...item, isRead: true } : item))
  }

  async function clearRead() {
    await clearReadNotifications()
    items.value = items.value.filter((item) => !item.isRead)
  }

  function reset() {
    disconnect()
    items.value = []
    deviceAlertVersion.value = 0
    lastDeviceAlert.value = null
    reconnectAttempts = 0
  }

  return {
    items,
    connected,
    deviceAlertVersion,
    lastDeviceAlert,
    unreadItems,
    readItems,
    unreadCount,
    connect,
    disconnect,
    fetchAll,
    markAllAsRead,
    markAsRead,
    clearRead,
    reset
  }
})
