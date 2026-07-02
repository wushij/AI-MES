import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCozeWelcomeMessage, sendCozeMessage } from '@/api/coze'
import type { CozeChatMessage } from '@/types'

const DEFAULT_WELCOME_MESSAGE =
  '您好，我是 AI-MES 智能助手，可协助查询工单进度、异常处理及 SOP 指导。'

function buildWelcomeMessage(content: string): CozeChatMessage {
  return {
    id: 'welcome',
    role: 'assistant',
    content,
    createdAt: new Date().toISOString()
  }
}

export const useChatStore = defineStore('chat', () => {
  const visible = ref(false)
  const sending = ref(false)
  const conversationId = ref('')
  const welcomeMessage = ref(DEFAULT_WELCOME_MESSAGE)
  const messages = ref<CozeChatMessage[]>([buildWelcomeMessage(DEFAULT_WELCOME_MESSAGE)])

  const hasMessages = computed(() => messages.value.length > 0)

  function isWelcomeOnly() {
    return messages.value.length === 1 && messages.value[0]?.id === 'welcome'
  }

  function applyWelcomeMessage(content: string) {
    welcomeMessage.value = content
    if (isWelcomeOnly()) {
      messages.value = [buildWelcomeMessage(content)]
    }
  }

  async function loadWelcomeMessage() {
    try {
      const data = await getCozeWelcomeMessage()
      if (data.welcomeMessage?.trim()) {
        applyWelcomeMessage(data.welcomeMessage.trim())
      }
    } catch {
      /* keep current welcome message */
    }
  }

  function open() {
    visible.value = true
    void loadWelcomeMessage()
  }

  function close() {
    visible.value = false
  }

  function toggle() {
    if (visible.value) {
      close()
      return
    }
    open()
  }

  function reset() {
    visible.value = false
    sending.value = false
    conversationId.value = ''
    messages.value = [buildWelcomeMessage(welcomeMessage.value)]
  }

  function pushMessage(message: CozeChatMessage) {
    messages.value.push(message)
  }

  async function sendMessage(content: string) {
    const text = content.trim()
    if (!text || sending.value) return

    const userMessage: CozeChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: text,
      createdAt: new Date().toISOString()
    }
    const pendingId = crypto.randomUUID()

    pushMessage(userMessage)
    pushMessage({
      id: pendingId,
      role: 'assistant',
      content: 'AI 正在分析您的请求...',
      createdAt: new Date().toISOString(),
      pending: true
    })

    sending.value = true
    try {
      const response = await sendCozeMessage({
        message: text,
        sessionId: conversationId.value || undefined
      })
      conversationId.value = response.sessionId ?? response.conversationId ?? conversationId.value
      messages.value = messages.value.filter((item) => item.id !== pendingId)
      pushMessage({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: response.reply,
        createdAt: new Date().toISOString()
      })
    } catch (error) {
      messages.value = messages.value.filter((item) => item.id !== pendingId)
      const message = error instanceof Error ? error.message : ''
      pushMessage({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: message.includes('timeout') || message.includes('超时')
          ? 'AI 响应超时（Coze 处理较慢，约需 30～90 秒），请稍后重试。'
          : 'AI 服务暂时不可用，请稍后重试或切换到独立 AI 页面。',
        createdAt: new Date().toISOString()
      })
    } finally {
      sending.value = false
    }
  }

  return {
    visible,
    sending,
    conversationId,
    welcomeMessage,
    messages,
    hasMessages,
    loadWelcomeMessage,
    open,
    close,
    toggle,
    sendMessage,
    reset
  }
})
