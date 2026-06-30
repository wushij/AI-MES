<template>
  <div class="view-page ai-chat-page">
    <PageHeader title="AI 客服" subtitle="咨询工单状态、SOP、异常处理及生产相关信息。" />

    <div class="chat-layout">
      <el-card shadow="hover" class="history-panel">
        <template #header>
          <div class="panel-header">
            <span>对话历史</span>
            <el-button class="new-chat-btn" type="primary" @click="startConversation()">新对话</el-button>
          </div>
        </template>

        <div v-if="historyLoading" class="history-loading">
          <el-skeleton v-for="item in 5" :key="item" animated>
            <template #template>
              <el-skeleton-item variant="rect" style="height: 58px; border-radius: 16px; width: 100%" />
            </template>
          </el-skeleton>
        </div>

        <el-empty v-else-if="!sessions.length" description="暂无历史对话" />

        <div v-else class="history-list">
          <div
            v-for="session in sessions"
            :key="session.id"
            class="history-item"
            :class="{ 'history-item--active': session.id === activeSessionId }"
          >
            <button class="history-item__main" @click="selectSession(session)">
              <span class="history-item__title">
                {{ session.title }}
                <span v-if="chatStore.pendingSessions[String(session.id)]" class="history-item__pending">回复中</span>
              </span>
              <span class="history-item__time">{{ formatSessionTime(session.updatedAt) }}</span>
            </button>
            <el-button
              link
              type="danger"
              class="history-item__delete"
              :loading="deletingSessionId === session.id"
              @click="removeSession(session)"
            >
              删除
            </el-button>
          </div>
        </div>
      </el-card>

      <el-card shadow="hover" class="chat-panel">
        <template #header>
          <div class="panel-header">
            <div>
              <div class="chat-title">{{ activeSession?.title || '新对话' }}</div>
              <div class="chat-subtitle">AI-MES 助手可协助查询工单、异常、物料及排产相关信息。</div>
            </div>
          </div>
        </template>

        <div ref="messageViewportRef" class="message-viewport">
          <template v-if="messages.length && !isFreshSession">
            <div v-for="message in messages" :key="message.id" class="message-row" :class="`message-row--${message.role}`">
              <div class="message-meta">{{ message.role === 'assistant' ? 'AI 助手' : '我' }} · {{ message.createdAt }}</div>
              <div class="message-bubble" :class="`message-bubble--${message.role}`">
                <div v-if="message.loading" class="typing-indicator"><span></span><span></span><span></span></div>
                <div v-else-if="message.role === 'assistant'" class="markdown-body" v-html="renderMarkdown(message.content)"></div>
                <div v-else>{{ message.content }}</div>
              </div>
            </div>
          </template>

          <div v-else class="fresh-state">
            <div class="fresh-state__badge">AI 智能客服</div>
            <h3>今天想了解什么生产问题？</h3>
            <p>可直接咨询工单进度、班组任务、异常处理、物料预警与 SOP 指导。</p>
            <div class="fresh-state__questions">
              <button v-for="question in quickQuestions" :key="question" type="button" class="fresh-state__chip" @click="useQuickQuestion(question)">
                {{ question }}
              </button>
            </div>
          </div>
        </div>

        <div v-if="!isFreshSession" class="quick-questions">
          <span class="quick-questions__label">快捷提问：</span>
          <el-button v-for="question in quickQuestions" :key="question" size="small" round @click="useQuickQuestion(question)">
            {{ question }}
          </el-button>
        </div>

        <div class="composer">
          <el-input
            v-model="draftMessage"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            resize="none"
            placeholder="例如：WO-2026-001 的进度是多少？"
            @keydown.enter.prevent="handleEnter"
          />
          <el-button type="primary" :loading="sending" class="send-btn" @click="sendMessage">发送</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>



<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'
import { nextTick, onActivated, onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import type { ChatSession } from '@/stores/aiChat'
import { useAiChatStore } from '@/stores/aiChat'

const chatStore = useAiChatStore()
const {
  sessions,
  messages,
  activeSession,
  activeSessionId,
  historyLoading,
  deletingSessionId,
  sending,
  isFreshSession
} = storeToRefs(chatStore)

const draftMessage = ref('')
const messageViewportRef = ref<HTMLDivElement>()
const quickQuestions = ['今日生产概况', '甲班有哪些任务？', 'WO-2026-001 的进度是多少？', '设备停机应该如何处理？']

onMounted(async () => {
  try {
    await chatStore.ensureInitialized()
  } catch (error) {
    console.error(error)
    ElMessage.error('加载对话失败')
  }
  await nextTick()
  scrollToBottom()
})

onActivated(async () => {
  if (chatStore.initialized) {
    try {
      await chatStore.loadSessions()
    } catch {
      /* ignore refresh errors */
    }
  }
  await nextTick()
  scrollToBottom()
})

async function startConversation(title = '新对话') {
  try {
    await chatStore.startConversation(title)
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error(error)
    ElMessage.error('创建对话失败')
  }
}

async function selectSession(session: ChatSession) {
  try {
    await chatStore.selectSession(session)
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error(error)
    ElMessage.error('加载消息失败')
  }
}

function useQuickQuestion(question: string) {
  draftMessage.value = question
  sendMessage()
}

async function removeSession(session: ChatSession) {
  await ElMessageBox.confirm(`确认删除对话“${session.title}”吗？`, '删除对话', { type: 'warning' })
  try {
    await chatStore.removeSession(session)
    ElMessage.success('对话已删除')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      console.error(error)
      ElMessage.error('删除对话失败')
    }
  }
}

async function sendMessage() {
  const text = draftMessage.value.trim()
  if (!text || sending.value) return
  draftMessage.value = ''
  try {
    await chatStore.sendMessage(text)
  } catch (error) {
    console.error(error)
    ElMessage.error('发送失败')
  }
  await nextTick()
  scrollToBottom()
}

function handleEnter(event: KeyboardEvent) {
  if (event.shiftKey) return
  sendMessage()
}

function scrollToBottom() {
  if (messageViewportRef.value) {
    messageViewportRef.value.scrollTop = messageViewportRef.value.scrollHeight
  }
}

function formatSessionTime(value: string) {
  if (!value || value === '--') return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

function renderMarkdown(text: string) {
  const escaped = escapeHtml(text)
  const codeBlocks = escaped.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
  const bolded = codeBlocks.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  const inlineCode = bolded.replace(/`([^`]+)`/g, '<code>$1</code>')
  const withLists = inlineCode.replace(/(?:^|\n)- (.*?)(?=\n|$)/g, '<li>$1</li>')
  const wrappedLists = withLists.replace(/(<li>.*<\/li>)/gs, '<ul>$1</ul>')
  return wrappedLists.replace(/\n/g, '<br />')
}

function escapeHtml(text: string) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
</script>



<style scoped>

.ai-chat-page { display: flex; flex-direction: column; gap: 16px; }

.chat-layout { display: grid; grid-template-columns: 280px 1fr; gap: 16px; min-height: 720px; }

.history-panel, .chat-panel { border-radius: 24px; }

.panel-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }

.history-loading, .history-list { display: flex; flex-direction: column; gap: 10px; }

.new-chat-btn {
  min-width: 84px;
}

.history-panel :deep(.el-card__body) {
  padding-top: 12px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #eef2ff;
  border-radius: 16px;
  background: #fff;
  transition: all 0.2s ease;
}

.history-item:hover,
.history-item--active {
  border-color: rgba(99, 102, 241, 0.35);
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(59, 130, 246, 0.04));
  box-shadow: 0 10px 24px rgba(79, 70, 229, 0.08);
}

.history-item__main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  min-width: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
  text-align: left;
}

.history-item__title {
  color: #0f172a;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
  gap: 6px;
}

.history-item__pending {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: #4f46e5;
  background: rgba(99, 102, 241, 0.1);
  padding: 1px 6px;
  border-radius: 999px;
}

.history-item__time, .chat-subtitle, .message-meta, .quick-questions__label { color: #64748b; font-size: 12px; }

.history-item__delete {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.history-item:hover .history-item__delete,
.history-item--active .history-item__delete {
  opacity: 1;
}

.chat-title { font-weight: 700; color: #0f172a; }

.message-viewport {
  height: 520px;
  overflow-y: auto;
  padding: 12px 8px;
}

.message-row { display: flex; flex-direction: column; margin-bottom: 16px; }

.message-row--user { align-items: flex-end; }

.message-bubble {
  max-width: min(76%, 760px);
  padding: 14px 16px;
  border-radius: 18px;
  line-height: 1.75;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}

.message-bubble--assistant {
  background: linear-gradient(180deg, #f8fbff 0%, #eef4ff 100%);
  color: #1e293b;
  border-top-left-radius: 6px;
}

.message-bubble--user {
  background: linear-gradient(135deg, #4f46e5, #1677ff);
  color: #fff;
  border-top-right-radius: 6px;
}

.fresh-state {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 32px 20px;
}

.fresh-state__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(99, 102, 241, 0.08);
  color: #4f46e5;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 18px;
}

.fresh-state h3 {
  margin: 0;
  font-size: 30px;
  line-height: 1.25;
  color: #0f172a;
}

.fresh-state p {
  margin: 12px 0 0;
  max-width: 560px;
  color: #64748b;
  line-height: 1.7;
}

.fresh-state__questions {
  margin-top: 26px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}

.fresh-state__chip {
  border: 1px solid rgba(99, 102, 241, 0.14);
  background: #fff;
  color: #334155;
  border-radius: 999px;
  padding: 12px 18px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.fresh-state__chip:hover {
  border-color: rgba(99, 102, 241, 0.28);
  color: #4f46e5;
  background: rgba(99, 102, 241, 0.04);
  transform: translateY(-1px);
}

.quick-questions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 12px; }

.composer {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-top: 14px;
  padding: 8px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 24px;
  transition: all 0.2s ease;
}

.composer:focus-within {
  background: #fff;
  border-color: #4f46e5;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);
}

.composer :deep(.el-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 6px 0 !important;
  font-size: 14px;
  color: #1e293b;
  min-height: 24px !important;
  line-height: 1.5;
  resize: none !important;
}

.composer :deep(.el-textarea__inner)::placeholder {
  color: #94a3b8;
}

.send-btn {
  height: 32px;
  padding: 0 16px !important;
  border-radius: 16px !important;
  font-weight: 500;
  background: linear-gradient(135deg, #4f46e5, #1677ff) !important;
  border: none !important;
  color: #fff !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  margin-bottom: 2px;
}

.send-btn:hover {
  opacity: 0.95;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2);
}

.send-btn:active {
  transform: scale(0.97);
}

.typing-indicator { display: inline-flex; gap: 6px; align-items: center; }

.typing-indicator span { width: 8px; height: 8px; border-radius: 50%; background: #64748b; animation: bounce 1.2s infinite ease-in-out; }

.typing-indicator span:nth-child(2) { animation-delay: 0.15s; }

.typing-indicator span:nth-child(3) { animation-delay: 0.3s; }

.markdown-body :deep(pre) { overflow-x: auto; padding: 12px; background: rgba(15, 23, 42, 0.06); border-radius: 12px; }

.markdown-body :deep(code) { font-family: Consolas, 'Courier New', monospace; }

@keyframes bounce { 0%, 80%, 100% { transform: translateY(0); opacity: 0.5; } 40% { transform: translateY(-4px); opacity: 1; } }

@media (max-width: 1100px) { .chat-layout { grid-template-columns: 1fr; } }

</style>

