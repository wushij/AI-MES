export {
  sendCozeMessage,
  getCozeConfig,
  saveCozeConfig,
  getSchedulingSuggestions
} from './coze'

import request from './request'
import { sendCozeMessage } from './coze'
import { normalizeList } from '@/utils/normalizeList'

export async function getChatSessions() {
  type HistoryRow = { sessionId: string; userMessage?: string; createTime?: string }
  const history = normalizeList<HistoryRow>(
    await request
      .get<Array<HistoryRow>>('/coze/chat/history')
      .then((r) => r.data)
  )

  const groups = new Map<string, { id: string; title: string; fullTitle?: string; updatedAt: string; firstAt: number }>()
  for (const item of history) {
    const id = String(item.sessionId ?? '').trim()
    if (!id) continue
    const createdAt = String(item.createTime ?? '')
    const createdTs = Date.parse(createdAt) || 0
    const userMessage = item.userMessage?.trim() ?? ''
    const existing = groups.get(id)
    if (!existing) {
      groups.set(id, {
        id,
        title: userMessage ? sessionTitleFromMessage(userMessage) : '对话',
        fullTitle: userMessage || undefined,
        updatedAt: createdAt,
        firstAt: createdTs || Date.now()
      })
      continue
    }
    if (createdTs && createdTs > (Date.parse(existing.updatedAt) || 0)) {
      existing.updatedAt = createdAt
    }
    if (createdTs && (createdTs < existing.firstAt || !existing.firstAt)) {
      existing.firstAt = createdTs
      if (userMessage) {
        existing.title = sessionTitleFromMessage(userMessage)
        existing.fullTitle = userMessage
      }
    }
  }

  return Array.from(groups.values())
    .sort((a, b) => (Date.parse(b.updatedAt) || 0) - (Date.parse(a.updatedAt) || 0))
    .map(({ id, title, fullTitle, updatedAt }) => ({ id, title, fullTitle, updatedAt }))
}

function sessionTitleFromMessage(message: string) {
  const text = message.trim()
  return text.length > 20 ? text.slice(0, 20) : text
}

export async function createChatSession(payload: { title?: string }) {
  const id = `sess_${Date.now()}`
  return { id, sessionId: id, title: payload.title ?? '新对话', updatedAt: new Date().toISOString() }
}

export async function getChatMessages(payload: { sessionId: string | number }) {
  const history = await request
    .get<Array<{ userMessage: string; aiResponse: string; createTime: string }>>('/coze/chat/history', {
      params: { sessionId: payload.sessionId }
    })
    .then((r) => r.data)

  const messages: Array<{ id: string; role: string; content: string; createdAt: string }> = []
  history.slice().reverse().forEach((item, index) => {
    messages.push({
      id: `u-${index}`,
      role: 'user',
      content: item.userMessage,
      createdAt: item.createTime
    })
    messages.push({
      id: `a-${index}`,
      role: 'assistant',
      content: item.aiResponse,
      createdAt: item.createTime
    })
  })
  return messages
}

export async function deleteChatSession(payload: { sessionId: string | number }) {
  return request
    .delete<number>('/coze/chat/history', {
      params: { sessionId: payload.sessionId }
    })
    .then((r) => r.data)
}

export async function sendChatMessage(payload: { sessionId?: string | number | null; message: string }) {
  return sendCozeMessage({
    message: payload.message,
    sessionId: payload.sessionId != null ? String(payload.sessionId) : undefined
  })
}

export const askAssistant = sendChatMessage
