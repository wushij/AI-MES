import request from './request'
import type {
  CozeChatRequest,
  CozeChatResponse,
  CozeConfig,
  CozeConfigSavePayload,
  CozeHealthCheckItem,
  CozeHealthResult,
  CozeSchedulingResponse
} from '@/types'

export function sendCozeMessage(
  payload: CozeChatRequest,
  options?: { signal?: AbortSignal }
) {
  const body = {
    message: payload.message,
    sessionId: payload.sessionId || payload.conversationId
  }
  return request
    .post<CozeChatResponse>('/coze/chat', body, {
      timeout: 120000,
      skipErrorHandler: true,
      signal: options?.signal
    })
    .then((res) => res.data)
}

export interface StreamEvent {
  event: string
  data: string
}

export async function sendCozeMessageStream(
  payload: CozeChatRequest,
  onEvent: (event: StreamEvent) => void,
  options?: { signal?: AbortSignal }
) {
  const body = {
    message: payload.message,
    sessionId: payload.sessionId || payload.conversationId
  }

  const token = localStorage.getItem('ai_mes_access_token')
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  
  const response = await fetch(`${baseUrl}/coze/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    },
    body: JSON.stringify(body),
    signal: options?.signal
  })

  if (!response.ok) {
    const errorText = await response.text().catch(() => '')
    throw new Error(errorText || `HTTP error! status: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('Response body is not readable')
  }

  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let currentEvent = ''
  let currentData = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      
      // Keep the last partial line in buffer
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed) {
          // Empty line indicates event boundary
          if (currentEvent || currentData) {
            onEvent({ event: currentEvent, data: currentData })
            currentEvent = ''
            currentData = ''
          }
          continue
        }

        if (trimmed.startsWith('event:')) {
          currentEvent = trimmed.slice(6).trim()
        } else if (trimmed.startsWith('data:')) {
          currentData = trimmed.slice(5).trim()
        }
      }
    }
    
    // Process any remaining event in buffer
    if (buffer.trim()) {
      const line = buffer.trim()
      if (line.startsWith('event:')) {
        onEvent({ event: line.slice(6).trim(), data: '' })
      } else if (line.startsWith('data:')) {
        onEvent({ event: '', data: line.slice(5).trim() })
      }
    }
  } finally {
    reader.releaseLock()
  }
}

export function getCozeConfig() {
  return request.get<CozeConfig>('/coze/config').then((res) => res.data)
}

export function getCozeWelcomeMessage() {
  return request.get<{ welcomeMessage: string }>('/coze/welcome').then((res) => res.data)
}

export function saveCozeConfig(payload: CozeConfigSavePayload) {
  return request.put<CozeConfig>('/coze/config', payload).then((res) => res.data)
}

export function testCozeConnection() {
  return request
    .get<CozeHealthResult>('/coze/health', {
      timeout: 180000,
      skipErrorHandler: true
    })
    .then((res) => res.data)
}

export function testCozeChatHealth(): Promise<CozeHealthCheckItem> {
  return request
    .get('/coze/health/chat', {
      timeout: 60000,
      skipErrorHandler: true
    })
    .then((res) => res.data as CozeHealthCheckItem)
}

export function testCozeWorkflowHealth(): Promise<CozeHealthCheckItem> {
  return request
    .get('/coze/health/workflow', {
      timeout: 180000,
      skipErrorHandler: true
    })
    .then((res) => res.data as CozeHealthCheckItem)
}

export function getSchedulingContext(workOrderIds: Array<number | string>) {
  if (!workOrderIds.length) {
    return Promise.resolve({
      workOrders: [],
      materialAlerts: [],
      exceptions: [],
      teams: [],
      kpi: { selectedCount: 0, overdueCount: 0, exceptionCount: 0, materialWarningCount: 0 }
    })
  }
  return request
    .get<{
      workOrders: unknown[]
      materialAlerts: unknown[]
      exceptions: unknown[]
      teams: unknown[]
      kpi: Record<string, number>
    }>('/coze/scheduling/context', {
      params: { workOrderIds: workOrderIds.join(',') }
    })
    .then((res) => res.data)
}

export function getSchedulingSuggestions(payload: {
  planDate?: string
  workOrderIds?: Array<number | string>
  materialConstraint?: boolean
  deviceConstraint?: boolean
  teamConstraint?: boolean
}) {
  return request
    .post<CozeSchedulingResponse>('/coze/scheduling', payload, {
      timeout: 180000,
      skipErrorHandler: true
    })
    .then((res) => res.data)
}

export function applySchedulingSuggestions(payload: {
  planDate?: string
  summary?: string
  priorities?: Array<{
    rank?: number
    workOrderCode?: string
    priorityLabel?: string
    reason?: string
  }>
  bottlenecks?: Array<{
    processName?: string
    loadRate?: number
    suggestion?: string
  }>
  dispatches: Array<{
    workOrderCode?: string
    teamName?: string
    startTime?: string
    hours?: string
  }>
}) {
  return request
    .post<{
      appliedCount: number
      skippedCount: number
      applied: unknown[]
      skipped: Array<{ workOrderCode?: string; reason?: string }>
    }>('/coze/scheduling/apply', payload)
    .then((res) => res.data)
}
