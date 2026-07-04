import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCozeWelcomeMessage, sendCozeMessageStream } from '@/api/coze'
import { isAbortError } from '@/api/request'
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
  let abortController: AbortController | null = null

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
    abortController?.abort()
    abortController = null
    visible.value = false
    sending.value = false
    conversationId.value = ''
    messages.value = [buildWelcomeMessage(welcomeMessage.value)]
  }

  function stopMessage() {
    abortController?.abort()
  }

  function pushMessage(message: CozeChatMessage) {
    messages.value.push(message)
  }

  function findMessage(id: string) {
    return messages.value.find((item) => item.id === id)
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
      content: '',
      createdAt: new Date().toISOString(),
      pending: true
    })

    sending.value = true
    abortController = new AbortController()
    const signal = abortController.signal
    let resolvedSessionId = conversationId.value
    try {
      await sendCozeMessageStream(
        {
          message: text,
          sessionId: resolvedSessionId || undefined
        },
        (eventObj) => {
          const { event, data } = eventObj
          if (event === 'metadata') {
            try {
              const meta = JSON.parse(data)
              if (meta.sessionId) {
                resolvedSessionId = meta.sessionId
              }
            } catch (ignored) {}
          } else if (event === 'conversation.message.delta') {
            try {
              const payload = JSON.parse(data)
              if (payload.type === 'answer' && payload.content) {
                const assistantMessage = findMessage(pendingId)
                if (!assistantMessage) return
                assistantMessage.pending = false
                assistantMessage.content += payload.content
              }
            } catch (ignored) {}
          } else if (event === 'error') {
            try {
              const payload = JSON.parse(data)
              throw new Error(payload.message || '对话出错')
            } catch (e) {
              throw e
            }
          }
        },
        { signal }
      )

      conversationId.value = resolvedSessionId
      const assistantMessage = findMessage(pendingId)
      if (assistantMessage) {
        assistantMessage.pending = false
        if (!assistantMessage.content) {
          assistantMessage.content = '助手未返回有效回复。'
        }
      }
    } catch (error) {
      const assistantMessage = findMessage(pendingId)
      if (assistantMessage) {
        assistantMessage.pending = false
        const errorMsg = error instanceof Error ? error.message : ''
        if (isAbortError(error)) {
          if (!assistantMessage.content) {
            assistantMessage.content = '已停止生成'
          } else {
            assistantMessage.content += '\n\n*(已停止生成)*'
          }
        } else {
          const errorText = errorMsg.includes('timeout') || errorMsg.includes('超时')
            ? 'AI 响应超时（Coze 处理较慢，约需 30～90 秒），请稍后重试。'
            : 'AI 服务暂时不可用，请稍后重试或切换到独立 AI 页面。'
          if (assistantMessage.content) {
            assistantMessage.content += `\n\n*(${errorText})*`
          } else {
            assistantMessage.content = errorText
          }
        }
      }
    } finally {
      abortController = null
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
    stopMessage,
    reset
  }
})
