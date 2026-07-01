import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  deleteChatSession,
  getChatMessages,
  getChatSessions,
  sendChatMessage
} from '@/api/ai'
import { normalizeList } from '@/utils/normalizeList'
import { USER_STORAGE_KEY } from '@/api/request'

export interface ChatSession {
  id: string | number
  title: string
  fullTitle?: string
  updatedAt: string
}

export interface ChatMessage {
  id: string | number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  loading?: boolean
}

function getSessionKey(sessionId: string | number | null | undefined) {
  return String(sessionId ?? '')
}

function nowTime() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

function getWelcomeMessage(sessionId: string | number): ChatMessage[] {
  return [{
    id: `welcome-${sessionId}`,
    role: 'assistant',
    content: '您好，我是 AI-MES 智能助手，可协助查询工单进度、异常处理及 SOP 指导。',
    createdAt: nowTime()
  }]
}

function isWelcomeOnly(messages: ChatMessage[]) {
  return messages.length === 1 && String(messages[0]?.id ?? '').startsWith('welcome-')
}

function sessionTitleFromMessage(message: string) {
  const text = message.trim()
  return text.length > 20 ? text.slice(0, 20) : text
}

function readCurrentUserId(): string | number | null {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY)
    if (!raw) return null
    const profile = JSON.parse(raw) as { id?: string | number }
    return profile?.id ?? null
  } catch {
    return null
  }
}

export const useAiChatStore = defineStore('aiChat', () => {
  const initialized = ref(false)
  const ownerUserId = ref<string | number | null>(null)
  const sessions = ref<ChatSession[]>([])
  const sessionMessages = ref<Record<string, ChatMessage[]>>({})
  const activeSessionId = ref<string | number | null>(null)
  const pendingSessions = ref<Record<string, boolean>>({})
  const historyLoading = ref(false)
  const deletingSessionId = ref<string | number | null>(null)
  let ensureSessionPromise: Promise<ChatSession> | null = null

  const activeSession = computed(() =>
    sessions.value.find((item) => item.id === activeSessionId.value) ?? null
  )

  const messages = computed(() => {
    const key = getSessionKey(activeSessionId.value)
    return key ? sessionMessages.value[key] ?? [] : []
  })

  const sending = computed(() => {
    const key = getSessionKey(activeSessionId.value)
    return Boolean(key && pendingSessions.value[key])
  })

  const isFreshSession = computed(() => isWelcomeOnly(messages.value))

  function setSessionMessages(key: string, list: ChatMessage[]) {
    sessionMessages.value = { ...sessionMessages.value, [key]: list }
  }

  function isSessionEmpty(sessionId: string | number): boolean {
    const key = getSessionKey(sessionId)
    const msgs = sessionMessages.value[key]
    if (!msgs?.length) {
      return String(sessionId).startsWith('sess_')
    }
    return isWelcomeOnly(msgs)
  }

  function findReusableEmptySession(): ChatSession | null {
    return sessions.value.find(
      (session) => session.title === '新对话' && isSessionEmpty(session.id)
    ) ?? null
  }

  function activateSession(session: ChatSession) {
    activeSessionId.value = session.id
    session.updatedAt = new Date().toISOString()
    sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)]
    const key = getSessionKey(session.id)
    if (!sessionMessages.value[key]?.length) {
      setSessionMessages(key, getWelcomeMessage(session.id))
    }
  }

  function enrichSessionFullTitle(session: ChatSession) {
    if (session.fullTitle || session.title === '新对话') return
    const msgs = sessionMessages.value[getSessionKey(session.id)]
    const firstUser = msgs?.find((item) => item.role === 'user')
    if (!firstUser?.content) return
    session.fullTitle = firstUser.content
    session.title = sessionTitleFromMessage(firstUser.content)
  }

  function resetState() {
    initialized.value = false
    ownerUserId.value = null
    sessions.value = []
    sessionMessages.value = {}
    activeSessionId.value = null
    pendingSessions.value = {}
    historyLoading.value = false
    deletingSessionId.value = null
    ensureSessionPromise = null
  }

  function syncOwnerUser() {
    const userId = readCurrentUserId()
    if (userId == null) return false
    if (ownerUserId.value != null && String(ownerUserId.value) !== String(userId)) {
      resetState()
    }
    ownerUserId.value = userId
    return true
  }

  function reset() {
    resetState()
  }

  function markPending(key: string, pending: boolean) {
    if (pending) {
      pendingSessions.value = { ...pendingSessions.value, [key]: true }
    } else {
      const next = { ...pendingSessions.value }
      delete next[key]
      pendingSessions.value = next
    }
  }

  function createLocalSession(title = '新对话'): ChatSession {
    const session: ChatSession = {
      id: `sess_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      title,
      updatedAt: new Date().toISOString()
    }
    activeSessionId.value = session.id
    sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)]
    setSessionMessages(getSessionKey(session.id), getWelcomeMessage(session.id))
    return session
  }

  async function loadSessions() {
    if (!syncOwnerUser()) return
    historyLoading.value = true
    try {
      type SessionRow = { id: string; title: string; fullTitle?: string; updatedAt: string }
      const remoteSessions = normalizeList<SessionRow>(await getChatSessions()).map((item) => ({
        id: item.id,
        title: item.title,
        fullTitle: item.fullTitle,
        updatedAt: item.updatedAt
      }))

      const localOnly = sessions.value.filter(
        (item) =>
          String(item.id).startsWith('sess_') &&
          !remoteSessions.some((remote) => String(remote.id) === String(item.id))
      )
      sessions.value = [...localOnly, ...remoteSessions].sort(
        (a, b) => (Date.parse(b.updatedAt) || 0) - (Date.parse(a.updatedAt) || 0)
      )
      sessions.value.forEach(enrichSessionFullTitle)
    } finally {
      historyLoading.value = false
    }
  }

  async function ensureInitialized() {
    if (!syncOwnerUser()) return
    if (initialized.value) return
    await loadSessions()
    if (!sessions.value.length) {
      createLocalSession('新对话')
    } else if (activeSessionId.value) {
      await selectSession(activeSessionId.value)
    } else {
      await selectSession(sessions.value[0].id)
    }
    initialized.value = true
  }

  async function startConversation(title = '新对话') {
    const key = getSessionKey(activeSessionId.value)
    const currentMessages = key ? sessionMessages.value[key] ?? [] : []
    if (activeSessionId.value && isWelcomeOnly(currentMessages)) {
      const session = sessions.value.find((item) => item.id === activeSessionId.value)
      if (session) {
        if (title !== '新对话') {
          session.title = title
        }
        activateSession(session)
        return session
      }
    }

    if (title === '新对话') {
      const reusable = findReusableEmptySession()
      if (reusable) {
        activateSession(reusable)
        return reusable
      }
    }

    return createLocalSession(title)
  }

  async function ensureActiveSession(firstMessage?: string) {
    if (activeSessionId.value) {
      return sessions.value.find((item) => item.id === activeSessionId.value) ?? null
    }
    if (!ensureSessionPromise) {
      ensureSessionPromise = Promise.resolve(startConversation(firstMessage?.slice(0, 20) || '新对话')).finally(() => {
        ensureSessionPromise = null
      })
    }
    return ensureSessionPromise
  }

  async function selectSession(sessionOrId: ChatSession | string | number) {
    const session = typeof sessionOrId === 'object'
      ? sessionOrId
      : sessions.value.find((item) => String(item.id) === String(sessionOrId))
    if (!session) return

    activeSessionId.value = session.id
    const key = getSessionKey(session.id)

    if (sessionMessages.value[key]?.length && !isWelcomeOnly(sessionMessages.value[key])) {
      enrichSessionFullTitle(session)
      return
    }

    if (pendingSessions.value[key]) {
      return
    }

    try {
      type MessageRow = { id: string | number; role: string; content: string; createdAt: string }
      const list = normalizeList<MessageRow>(await getChatMessages({ sessionId: session.id }))
      setSessionMessages(
        key,
        list.length
          ? list.map((item) => ({
              id: item.id,
              role: item.role === 'assistant' ? 'assistant' as const : 'user' as const,
              content: item.content,
              createdAt: item.createdAt
            }))
          : getWelcomeMessage(session.id)
      )
    } catch {
      setSessionMessages(key, getWelcomeMessage(session.id))
    }
    enrichSessionFullTitle(session)
  }

  async function removeSession(session: ChatSession) {
    deletingSessionId.value = session.id
    try {
      await deleteChatSession({ sessionId: session.id })
      const key = getSessionKey(session.id)
      const nextMessages = { ...sessionMessages.value }
      delete nextMessages[key]
      sessionMessages.value = nextMessages
      markPending(key, false)
      sessions.value = sessions.value.filter((item) => item.id !== session.id)

      if (String(activeSessionId.value) === String(session.id)) {
        if (sessions.value[0]) {
          await selectSession(sessions.value[0])
        } else {
          activeSessionId.value = null
          createLocalSession('新对话')
        }
      }
    } finally {
      deletingSessionId.value = null
    }
  }

  async function sendMessage(text: string) {
    const content = text.trim()
    if (!content) return

    await ensureActiveSession(content)
    const sessionId = activeSessionId.value
    if (!sessionId) return

    const key = getSessionKey(sessionId)
    const currentMessages = sessionMessages.value[key] ?? getWelcomeMessage(sessionId)
    const targetMessages = isWelcomeOnly(currentMessages) ? [] : [...currentMessages]

    const userMessage: ChatMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content,
      createdAt: nowTime()
    }
    const aiPlaceholder: ChatMessage = {
      id: `assistant-${Date.now()}`,
      role: 'assistant',
      content: '',
      createdAt: nowTime(),
      loading: true
    }

    targetMessages.push(userMessage, aiPlaceholder)
    setSessionMessages(key, targetMessages)
    markPending(key, true)

    try {
      const response = await sendChatMessage({ sessionId, message: content })
      aiPlaceholder.loading = false
      aiPlaceholder.content = String(response.reply ?? '助手未返回有效回复。')

      const resolvedSessionId = response.sessionId ?? sessionId
      activeSessionId.value = resolvedSessionId

      const session = sessions.value.find((item) => String(item.id) === String(sessionId))
        ?? sessions.value.find((item) => String(item.id) === String(resolvedSessionId))
      if (session) {
        if (session.title === '新对话' || isWelcomeOnly(currentMessages)) {
          session.title = sessionTitleFromMessage(content)
          session.fullTitle = content
        }
        session.updatedAt = new Date().toISOString()
        session.id = resolvedSessionId
      } else {
        sessions.value.unshift({
          id: resolvedSessionId,
          title: sessionTitleFromMessage(content),
          fullTitle: content,
          updatedAt: new Date().toISOString()
        })
      }

      if (String(resolvedSessionId) !== String(sessionId)) {
        const nextMessages = { ...sessionMessages.value }
        nextMessages[getSessionKey(resolvedSessionId)] = [...targetMessages]
        delete nextMessages[key]
        sessionMessages.value = nextMessages
      } else {
        setSessionMessages(key, [...targetMessages])
      }

      await loadSessions()
      if (String(activeSessionId.value) !== String(resolvedSessionId)) {
        activeSessionId.value = resolvedSessionId
      }
    } catch (error) {
      aiPlaceholder.loading = false
      const message = error instanceof Error ? error.message : ''
      aiPlaceholder.content = message.includes('timeout') || message.includes('超时')
        ? 'AI 响应超时（Coze 处理较慢，约需 30～90 秒），请稍后重试。'
        : message || 'AI 服务暂时不可用，请稍后重试。'
      setSessionMessages(key, [...targetMessages])
    } finally {
      markPending(getSessionKey(activeSessionId.value), false)
    }
  }

  return {
    initialized,
    sessions,
    sessionMessages,
    activeSessionId,
    pendingSessions,
    historyLoading,
    deletingSessionId,
    activeSession,
    messages,
    sending,
    isFreshSession,
    ensureInitialized,
    loadSessions,
    startConversation,
    selectSession,
    removeSession,
    sendMessage,
    reset
  }
})
