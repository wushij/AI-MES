<template>
  <div class="view-page ai-chat-page">
    <PageHeader title="AI 客服" subtitle="咨询工单状态、SOP、异常处理及生产相关信息。" />

    <div class="chat-layout" :class="{ 'chat-layout--collapsed': historyCollapsed }">
      <aside class="history-aside" :class="{ 'history-aside--collapsed': historyCollapsed }">
        <div class="history-aside__surface">
          <header class="history-aside__header">
            <div v-show="!historyCollapsed" class="history-aside__heading">
              <el-icon class="history-aside__heading-icon"><ChatLineRound /></el-icon>
              <span>对话历史</span>
            </div>
            <div class="history-aside__actions">
              <button
                v-if="!historyCollapsed"
                type="button"
                class="history-aside__fold-btn"
                @click="toggleHistoryCollapsed(true)"
              >
                <el-icon><Fold /></el-icon>
              </button>
              <button type="button" class="history-aside__new-btn" @click="startConversation()">
                <el-icon><Plus /></el-icon>
                <span v-show="!historyCollapsed">新对话</span>
              </button>
            </div>
          </header>

          <div v-show="!historyCollapsed" class="history-aside__body">
            <div v-if="historyLoading" class="history-loading">
              <el-skeleton v-for="item in 5" :key="item" animated>
                <template #template>
                  <el-skeleton-item variant="rect" style="height: 58px; border-radius: 16px; width: 100%" />
                </template>
              </el-skeleton>
            </div>

            <el-empty v-else-if="!sessions.length" description="暂无历史对话" :image-size="72" />

            <div v-else class="history-list">
              <div
                v-for="session in visibleSessions"
                :key="session.id"
                class="history-item"
                :class="{ 'history-item--active': session.id === activeSessionId }"
              >
                <button class="history-item__main" @click="selectSession(session)">
                  <span class="history-item__title-container">
                    <el-tooltip
                      :content="session.fullTitle || session.title"
                      placement="right"
                      :show-after="300"
                      :disabled="!shouldShowSessionTitleTooltip(session)"
                    >
                      <span
                        class="history-item__title"
                        :ref="(el) => bindSessionTitleEl(session.id, el as HTMLElement | null)"
                      >
                        {{ session.title }}
                      </span>
                    </el-tooltip>
                    <span v-if="chatStore.pendingSessions[String(session.id)]" class="history-item__pending">
                      <el-icon class="is-loading" style="font-size: 11px;"><Loading /></el-icon>
                    </span>
                  </span>
                  <span class="history-item__time">{{ formatSessionTime(session.updatedAt) }}</span>
                </button>
                <button
                  type="button"
                  class="history-item__delete"
                  :disabled="deletingSessionId === session.id"
                  @click.stop="removeSession(session)"
                >
                  <el-icon v-if="deletingSessionId === session.id" class="history-item__delete-loading"><Loading /></el-icon>
                  <el-icon v-else class="history-item__delete-icon"><Delete /></el-icon>
                </button>
              </div>
              <button
                v-if="hiddenSessionCount > 0"
                type="button"
                class="history-load-more"
                @click="historyExpanded = !historyExpanded"
              >
                {{ historyExpanded ? '收起' : `更多（${hiddenSessionCount}）` }}
              </button>
            </div>
          </div>

          <div v-show="historyCollapsed" class="history-aside__rail">
            <button type="button" class="history-rail-btn" @click="toggleHistoryCollapsed(false)">
              <el-icon><Memo /></el-icon>
            </button>
          </div>
        </div>
      </aside>

      <section class="chat-main">
      <el-card shadow="hover" class="chat-panel">
        <template #header>
          <div class="panel-header">
            <div class="chat-panel__title-wrap">
              <div>
                <div class="chat-title">{{ activeSession?.title || '新对话' }}</div>
                <div class="chat-subtitle">AI-MES 助手可协助查询工单、异常、物料及排产相关信息。</div>
              </div>
            </div>
            <el-button v-if="historyCollapsed" class="chat-panel__new-btn" type="primary" round @click="startConversation()">
              <el-icon><Plus /></el-icon>
              新对话
            </el-button>
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
            placeholder="输入您的问题..."
            @keydown.enter.prevent="handleEnter"
          />
          <el-button
            v-if="sending"
            type="danger"
            plain
            class="stop-btn"
            @click="stopGenerating"
          >
            停止
          </el-button>
          <el-button
            v-else
            type="primary"
            :disabled="!draftMessage.trim()"
            class="send-btn"
            @click="sendMessage"
          >
            发送
          </el-button>
        </div>
      </el-card>
      </section>
    </div>
  </div>
</template>



<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatLineRound, Delete, Fold, Loading, Memo, Plus } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, nextTick, onActivated, onMounted, onUnmounted, ref, watch } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { renderChatMarkdown } from '@/utils/chatMarkdown'
import { getChatQuickQuestions } from '@/utils/chatQuickQuestions'
import type { ChatSession } from '@/stores/aiChat'
import { useAiChatStore } from '@/stores/aiChat'
import { useUserStore } from '@/stores/user'
import { confirmDelete } from '@/utils/confirmDelete'

const chatStore = useAiChatStore()
const userStore = useUserStore()
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

const HISTORY_PREVIEW_COUNT = 8
const HISTORY_COLLAPSED_KEY = 'ai-mes-chat-history-collapsed'

const draftMessage = ref('')
const messageViewportRef = ref<HTMLDivElement>()
const historyExpanded = ref(false)
const historyCollapsed = ref(readHistoryCollapsedPreference())
const sessionTitleOverflow = ref<Record<string, boolean>>({})
const sessionTitleObservers = new Map<string, ResizeObserver>()
const sessionTitleElements = new Map<string, HTMLElement>()

function measureSessionTitleOverflow(key: string, el: HTMLElement) {
  const overflow = el.scrollWidth > el.clientWidth
  if (sessionTitleOverflow.value[key] !== overflow) {
    sessionTitleOverflow.value = { ...sessionTitleOverflow.value, [key]: overflow }
  }
}

function remeasureAllSessionTitles() {
  sessionTitleElements.forEach((el, key) => measureSessionTitleOverflow(key, el))
}

function bindSessionTitleEl(sessionId: string | number, el: HTMLElement | null) {
  const key = String(sessionId)
  const existing = sessionTitleObservers.get(key)
  if (existing) {
    existing.disconnect()
    sessionTitleObservers.delete(key)
  }
  if (!el) {
    sessionTitleElements.delete(key)
    if (key in sessionTitleOverflow.value) {
      const next = { ...sessionTitleOverflow.value }
      delete next[key]
      sessionTitleOverflow.value = next
    }
    return
  }

  sessionTitleElements.set(key, el)
  const measure = () => measureSessionTitleOverflow(key, el)

  measure()
  requestAnimationFrame(measure)
  const observer = new ResizeObserver(measure)
  observer.observe(el)
  sessionTitleObservers.set(key, observer)
}

function isSessionTitleOverflow(sessionId: string | number) {
  return Boolean(sessionTitleOverflow.value[String(sessionId)])
}

function shouldShowSessionTitleTooltip(session: ChatSession) {
  if (isSessionTitleOverflow(session.id)) return true
  const full = session.fullTitle?.trim()
  if (!full) return false
  const shown = session.title?.trim().replace(/…$/, '') ?? ''
  return full.length > shown.length
}

function readHistoryCollapsedPreference() {
  try {
    return localStorage.getItem(HISTORY_COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}

function toggleHistoryCollapsed(next?: boolean) {
  historyCollapsed.value = typeof next === 'boolean' ? next : !historyCollapsed.value
}

const quickQuestions = computed(() =>
  getChatQuickQuestions(
    userStore.role,
    userStore.permissions,
    userStore.fullAccess,
    userStore.profile?.teamName
  )
)

const visibleSessions = computed(() =>
  historyExpanded.value ? sessions.value : sessions.value.slice(0, HISTORY_PREVIEW_COUNT)
)

const hiddenSessionCount = computed(() =>
  Math.max(0, sessions.value.length - HISTORY_PREVIEW_COUNT)
)

onMounted(async () => {
  try {
    await chatStore.ensureInitialized()
    await chatStore.openDefaultConversation()
  } catch (error) {
    console.error(error)
    ElMessage.error('加载对话失败')
  }
  await nextTick()
  scrollToBottom()
})

onUnmounted(() => {
  sessionTitleObservers.forEach((observer) => observer.disconnect())
  sessionTitleObservers.clear()
  sessionTitleElements.clear()
})

onActivated(async () => {
  try {
    await chatStore.ensureInitialized()
    if (chatStore.initialized) {
      await chatStore.loadSessions()
      await chatStore.openDefaultConversation()
    }
  } catch {
    /* ignore refresh errors */
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
  const ok = await confirmDelete({
    title: '删除对话',
    message: `确认删除对话「${session.title}」吗？`
  })
  if (!ok) return
  try {
    await chatStore.removeSession(session)
    ElMessage.success('对话已删除')
  } catch (error) {
    console.error(error)
    ElMessage.error('删除对话失败')
  }
}

function stopGenerating() {
  chatStore.stopMessage()
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
  return renderChatMarkdown(text)
}

watch(
  () => messages.value.map((m) => m.content).join('|'),
  async () => {
    await nextTick()
    scrollToBottom()
  }
)

watch(historyCollapsed, (collapsed) => {
  try {
    localStorage.setItem(HISTORY_COLLAPSED_KEY, collapsed ? '1' : '0')
  } catch {
    /* ignore storage errors */
  }
})

watch(
  () => [sessions.value.length, historyExpanded.value, historyCollapsed.value] as const,
  async () => {
    await nextTick()
    requestAnimationFrame(remeasureAllSessionTitles)
  }
)
</script>



<style scoped>

.ai-chat-page {
  display: flex;
  flex-direction: column;
  gap: 6px;
  height: calc(100vh - 100px);
  min-height: 0;
  overflow: hidden;
}

.ai-chat-page :deep(.page-banner) {
  margin-bottom: 0;
  flex-shrink: 0;
}

.chat-layout {
  position: relative;
  display: flex;
  align-items: stretch;
  gap: 0;
  flex: 1;
  min-height: 0;
}

.chat-main {
  flex: 1;
  min-width: 0;
  height: 100%;
}

.history-aside {
  position: relative;
  flex: 0 0 256px;
  width: 256px;
  min-width: 0;
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1), flex-basis 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.history-aside--collapsed {
  flex-basis: 60px;
  width: 60px;
}

.history-aside__surface {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 24px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 255, 0.96) 100%);
  box-shadow: 0 16px 40px rgba(79, 70, 229, 0.06);
  overflow: hidden;
}

.history-aside__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 12px 10px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
}

.history-aside--collapsed .history-aside__header {
  flex-direction: column;
  justify-content: center;
  padding: 14px 10px;
}

.history-aside__actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.history-aside--collapsed .history-aside__actions {
  flex-direction: column;
}

.history-aside__fold-btn {
  width: 36px;
  height: 36px;
  border: 1px solid rgba(99, 102, 241, 0.14);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  color: #64748b;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.history-aside__fold-btn:hover {
  color: #4f46e5;
  border-color: rgba(99, 102, 241, 0.28);
  background: rgba(99, 102, 241, 0.08);
}

.history-aside__heading {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #0f172a;
  font-weight: 700;
  font-size: 14px;
  min-width: 0;
}

.history-aside__heading-icon {
  color: #4f46e5;
  font-size: 18px;
}

.history-aside__new-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: none;
  border-radius: 999px;
  padding: 7px 12px;
  background: linear-gradient(135deg, #4f46e5, #1677ff);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  flex-shrink: 0;
}

.history-aside--collapsed .history-aside__new-btn {
  width: 36px;
  height: 36px;
  padding: 0;
  border-radius: 12px;
}

.history-aside__new-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.24);
}

.history-aside__body {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow-y: auto;
  padding: 10px 10px 14px;
  scrollbar-width: none;
}

.history-aside__body::-webkit-scrollbar {
  display: none;
}

.history-aside__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 12px 10px 16px;
}

.history-rail-btn {
  width: 40px;
  height: 40px;
  border: 1px solid rgba(99, 102, 241, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  color: #4f46e5;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.history-rail-btn:hover {
  border-color: rgba(99, 102, 241, 0.32);
  background: rgba(99, 102, 241, 0.08);
}

.chat-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 24px;
  margin-left: 10px;
  overflow: hidden;
  transition: margin-left 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.chat-panel :deep(.el-card__header) {
  flex-shrink: 0;
  padding: 12px 18px;
  background: #fff;
  border-bottom: 1px solid #eef2f7;
}

.chat-panel :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  background: #fff;
}

.chat-layout--collapsed .chat-panel {
  margin-left: 8px;
}

.chat-panel__title-wrap {
  min-width: 0;
}

.chat-panel__new-btn {
  flex-shrink: 0;
}

.panel-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }

.history-loading, .history-list { display: flex; flex-direction: column; gap: 10px; }

.history-load-more {
  align-self: center;
  border: none;
  background: transparent;
  color: #4f46e5;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 999px;
  transition: background 0.2s ease;
}

.history-load-more:hover {
  background: rgba(99, 102, 241, 0.08);
}

.history-aside__body :deep(.el-empty) {
  padding: 24px 0;
}

.history-item {
  position: relative;
  display: block;
  width: 100%;
  min-width: 0;
  padding: 8px 34px 8px 10px;
  border: 1px solid #eef2ff;
  border-radius: 14px;
  background: #fff;
  overflow: hidden;
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
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
  text-align: left;
}

.history-item__main :deep(.el-tooltip__trigger) {
  display: block;
  flex: 1 1 0%;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.history-item__title-container {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
  width: 100%;
  overflow: hidden;
}

.history-item__title {
  display: block;
  color: #0f172a;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 100%;
  min-width: 0;
}

.history-item__pending {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  color: #4f46e5;
  background: rgba(99, 102, 241, 0.08);
}

.history-item__time, .chat-subtitle, .message-meta, .quick-questions__label { color: #64748b; font-size: 12px; }

.history-item__delete {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  color: #b8c2d4;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.05);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transform: scale(0.9);
  transition: opacity 0.18s ease, transform 0.18s ease, color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.history-item__delete-icon {
  font-size: 13px;
}

.history-item__delete:disabled {
  cursor: wait;
  opacity: 1;
}

.history-item__delete-loading {
  font-size: 12px;
  color: #94a3b8;
  animation: history-delete-spin 0.8s linear infinite;
}

.history-item:hover .history-item__delete,
.history-item--active .history-item__delete {
  opacity: 1;
  transform: scale(1);
}

.history-item__delete:hover:not(:disabled) {
  color: #64748b;
  background: #fff;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

@keyframes history-delete-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.chat-title { font-weight: 700; color: #0f172a; line-height: 1.35; }

.chat-subtitle { line-height: 1.45; margin-top: 2px; }

.message-viewport {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 16px 12px;
  background: #fafbfd;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.55) transparent;
}

.message-viewport::-webkit-scrollbar {
  width: 6px;
}

.message-viewport::-webkit-scrollbar-track {
  background: transparent;
}

.message-viewport::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.45);
  border-radius: 999px;
}

.message-viewport::-webkit-scrollbar-thumb:hover {
  background: rgba(100, 116, 139, 0.65);
}

.message-row { display: flex; flex-direction: column; margin-bottom: 16px; }

.message-row--user { align-items: flex-end; }

.message-bubble {
  max-width: min(82%, 900px);
  padding: 14px 16px;
  border-radius: 18px;
  line-height: 1.75;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}

.message-bubble--assistant {
  background: linear-gradient(180deg, #f8fbff 0%, #eef4ff 100%);
  color: #1e293b;
  border-top-left-radius: 6px;
  align-self: flex-start;
  width: min(82%, 900px);
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

.quick-questions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 8px; padding: 0 16px; background: #fff; }

.composer {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin: 10px 16px 16px;
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
  padding: 6px 8px !important;
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

.stop-btn {
  height: 32px;
  padding: 0 16px !important;
  border-radius: 16px !important;
  font-weight: 500;
  flex-shrink: 0;
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

.markdown-body {
  width: 100%;
  overflow-x: auto;
}

.markdown-body :deep(pre) { overflow-x: auto; padding: 12px; background: rgba(15, 23, 42, 0.06); border-radius: 12px; }

.markdown-body :deep(code) { font-family: Consolas, 'Courier New', monospace; font-size: 13px; }

.markdown-body :deep(p) { margin: 0 0 10px; }

.markdown-body :deep(p:last-child) { margin-bottom: 0; }

.markdown-body :deep(hr) { border: none; border-top: 1px solid #e2e8f0; margin: 10px 0; }

.message-row:first-child {
  margin-top: 0;
}

.message-meta {
  margin-bottom: 6px;
}

.markdown-body :deep(table) {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  margin: 8px 0 0;
  font-size: 12px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 6px;
  text-align: left;
  vertical-align: top;
  white-space: normal;
  word-break: break-all;
  overflow-wrap: anywhere;
}

.markdown-body :deep(th:first-child),
.markdown-body :deep(td:first-child) {
  width: auto;
}

.markdown-body :deep(th:last-child),
.markdown-body :deep(td:last-child) {
  width: auto;
}

.markdown-body :deep(th) { background: #f8fafc; font-weight: 600; }

.markdown-body :deep(ul),
.markdown-body :deep(ol) { margin: 8px 0; padding-left: 20px; }

.markdown-body :deep(li) { margin: 4px 0; }

.markdown-body :deep(strong) { font-weight: 700; color: #0f172a; }

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 12px 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.markdown-body :deep(h1:first-child),
.markdown-body :deep(h2:first-child),
.markdown-body :deep(h3:first-child),
.markdown-body :deep(p:first-child) { margin-top: 0; }

.markdown-body :deep(blockquote) {
  margin: 10px 0;
  padding: 8px 12px;
  border-left: 3px solid #4f46e5;
  background: rgba(99, 102, 241, 0.06);
  color: #475569;
}

@keyframes bounce { 0%, 80%, 100% { transform: translateY(0); opacity: 0.5; } 40% { transform: translateY(-4px); opacity: 1; } }

@media (max-width: 1100px) {
  .ai-chat-page {
    height: auto;
    overflow: visible;
  }

  .chat-layout {
    flex-direction: column;
    flex: none;
    min-height: auto;
  }

  .history-aside,
  .history-aside--collapsed {
    flex-basis: auto;
    width: 100%;
  }

  .history-aside--collapsed .history-aside__surface {
    min-height: 72px;
  }

  .chat-panel {
    margin-left: 0;
    margin-top: 12px;
  }

  .chat-panel :deep(.el-card__body) {
    height: 560px;
    min-height: 560px;
  }
}

</style>

