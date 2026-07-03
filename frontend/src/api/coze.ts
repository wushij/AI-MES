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

export function getSchedulingSuggestions(payload: { planDate?: string; workOrderIds?: Array<number | string> }) {
  return request
    .post<CozeSchedulingResponse>('/coze/scheduling', payload, {
      timeout: 180000,
      skipErrorHandler: true
    })
    .then((res) => res.data)
}

export function applySchedulingSuggestions(payload: {
  planDate?: string
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
