import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { sendCozeMessage } from '@/api/coze'
import type { CozeChatMessage } from '@/types'

const defaultWelcomeMessage: CozeChatMessage = {
  id: 'welcome',
  role: 'assistant',
  content: '您好，我是 AI-MES 智能助手，可以协助查询工单进度、班组任务、异常处置和 SOP。',
  createdAt: new Date().toISOString()
}

export const useChatStore = defineStore('chat', () => {
  const visible = ref(false)
  const sending = ref(false)
  const conversationId = ref('')
  const messages = ref<CozeChatMessage[]>([{ ...defaultWelcomeMessage }])

  const hasMessages = computed(() => messages.value.length > 0)

  function open() {
    visible.value = true
  }

  function close() {
    visible.value = false
  }

  function toggle() {
    visible.value = !visible.value
  }

  function reset() {
    visible.value = false
    sending.value = false
    conversationId.value = ''
    messages.value = [{ ...defaultWelcomeMessage, createdAt: new Date().toISOString() }]
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
    messages,
    hasMessages,
    open,
    close,
    toggle,
    sendMessage,
    reset
  }
})
