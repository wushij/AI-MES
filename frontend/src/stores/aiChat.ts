import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  deleteChatSession,
  getChatMessages,
  getChatSessions,
  sendChatMessage
} from '@/api/ai'
import { getCozeWelcomeMessage } from '@/api/coze'
import { isAbortError } from '@/api/request'
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

const DEFAULT_WELCOME_MESSAGE =
  '您好，我是 AI-MES 智能助手，可协助查询工单进度、异常处理及 SOP 指导。'

function getWelcomeMessage(sessionId: string | number, content = DEFAULT_WELCOME_MESSAGE): ChatMessage[] {
  return [{
    id: `welcome-${sessionId}`,
    role: 'assistant',
    content,
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
  const welcomeMessage = ref(DEFAULT_WELCOME_MESSAGE)
  const sessions = ref<ChatSession[]>([])
  const sessionMessages = ref<Record<string, ChatMessage[]>>({})
  const activeSessionId = ref<string | number | null>(null)
  const pendingSessions = ref<Record<string, boolean>>({})
  const historyLoading = ref(false)
  const deletingSessionId = ref<string | number | null>(null)
  let ensureSessionPromise: Promise<ChatSession> | null = null
  const abortControllers = ref<Record<string, AbortController>>({})

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

  function refreshWelcomeOnlySessions() {
    const nextMessages = { ...sessionMessages.value }
    let changed = false
    for (const [key, msgs] of Object.entries(nextMessages)) {
      if (!isWelcomeOnly(msgs)) continue
      const sessionId = key
      nextMessages[key] = getWelcomeMessage(sessionId, welcomeMessage.value)
      changed = true
    }
    if (changed) {
      sessionMessages.value = nextMessages
    }
  }

  async function loadWelcomeMessage() {
    try {
      const data = await getCozeWelcomeMessage()
      if (data.welcomeMessage?.trim()) {
        welcomeMessage.value = data.welcomeMessage.trim()
        refreshWelcomeOnlySessions()
      }
    } catch {
      /* keep current welcome message */
    }
  }

  function activateSession(session: ChatSession) {
    activeSessionId.value = session.id
    session.updatedAt = new Date().toISOString()
    sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)]
    const key = getSessionKey(session.id)
    if (!sessionMessages.value[key]?.length) {
      setSessionMessages(key, getWelcomeMessage(session.id, welcomeMessage.value))
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
    Object.values(abortControllers.value).forEach((controller) => controller.abort())
    abortControllers.value = {}
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
    setSessionMessages(getSessionKey(session.id), getWelcomeMessage(session.id, welcomeMessage.value))
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

  function findPendingSession(): ChatSession | null {
    const pending = sessions.value.filter((session) => pendingSessions.value[getSessionKey(session.id)])
    if (!pending.length) return null
    return pending.sort((a, b) => (Date.parse(b.updatedAt) || 0) - (Date.parse(a.updatedAt) || 0))[0]
  }

  async function openDefaultConversation() {
    const pendingSession = findPendingSession()
    if (pendingSession) {
      await selectSession(pendingSession)
      return pendingSession
    }
    const orphanedKey = Object.keys(pendingSessions.value).find((key) => pendingSessions.value[key])
    if (orphanedKey) {
      activeSessionId.value = orphanedKey
      return null
    }
    return startConversation('新对话')
  }

  async function ensureInitialized() {
    if (!syncOwnerUser()) return
    if (initialized.value) return
    await loadWelcomeMessage()
    await loadSessions()
    await openDefaultConversation()
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
          : getWelcomeMessage(session.id, welcomeMessage.value)
      )
    } catch {
      setSessionMessages(key, getWelcomeMessage(session.id, welcomeMessage.value))
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

  function stopMessage() {
    const key = getSessionKey(activeSessionId.value)
    if (!key) return
    abortControllers.value[key]?.abort()
  }

  async function sendMessage(text: string) {
    const content = text.trim()
    if (!content) return

    await ensureActiveSession(content)
    const sessionId = activeSessionId.value
    if (!sessionId) return

    const key = getSessionKey(sessionId)
    const currentMessages = sessionMessages.value[key] ?? getWelcomeMessage(sessionId, welcomeMessage.value)
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

    const controller = new AbortController()
    abortControllers.value = { ...abortControllers.value, [key]: controller }

    try {
      const response = await sendChatMessage(
        { sessionId, message: content },
        { signal: controller.signal }
      )
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
      if (isAbortError(error)) {
        aiPlaceholder.content = '已停止生成'
      } else {
        const message = error instanceof Error ? error.message : ''
        aiPlaceholder.content = message.includes('timeout') || message.includes('超时')
          ? 'AI 响应超时（Coze 处理较慢，约需 30～90 秒），请稍后重试。'
          : message || 'AI 服务暂时不可用，请稍后重试。'
      }
      setSessionMessages(key, [...targetMessages])
    } finally {
      const pendingKey = getSessionKey(activeSessionId.value)
      const nextControllers = { ...abortControllers.value }
      delete nextControllers[key]
      if (pendingKey && pendingKey !== key) {
        delete nextControllers[pendingKey]
      }
      abortControllers.value = nextControllers
      markPending(key, false)
      if (pendingKey && pendingKey !== key) {
        markPending(pendingKey, false)
      }
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
    loadWelcomeMessage,
    loadSessions,
    startConversation,
    openDefaultConversation,
    selectSession,
    removeSession,
    sendMessage,
    stopMessage,
    reset
  }
})
