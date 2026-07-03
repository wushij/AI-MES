<script setup lang="ts">
import { computed } from 'vue'
import { renderChatMarkdown } from '@/utils/chatMarkdown'
import type { CozeChatMessage } from '@/types'

const props = defineProps<{
  message: CozeChatMessage
}>()

const html = computed(() => renderChatMarkdown(props.message.content))
</script>

<template>
  <div class="chat-message" :class="`chat-message--${message.role}`">
    <div class="chat-message__bubble">
      <div v-if="message.pending" class="typing-indicator"><span></span><span></span><span></span></div>
      <div v-else v-html="html" />
    </div>
  </div>
</template>

<style scoped>
.chat-message {
  display: flex;
}

.chat-message--user {
  justify-content: flex-end;
}

.chat-message--assistant {
  width: 100%;
}

.chat-message__bubble {
  max-width: 100%;
  padding: 12px 14px;
  border-radius: 16px;
  line-height: 1.6;
  font-size: 14px;
  background: #fff;
  color: var(--cd-text-primary);
  box-shadow: 0 8px 16px rgba(15, 23, 42, 0.06);
  overflow-x: hidden;
}

.chat-message--assistant .chat-message__bubble {
  width: 100%;
}

.chat-message--user .chat-message__bubble {
  background: var(--cd-primary-gradient);
  color: #fff;
  width: auto;
}

.chat-message__bubble :deep(p) {
  margin: 0;
}

.chat-message__bubble :deep(p + p) {
  margin-top: 8px;
}

.chat-message__bubble :deep(hr) {
  border: none;
  border-top: 1px solid #e2e8f0;
  margin: 12px 0;
}

.chat-message__bubble :deep(table) {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 12px;
}

.chat-message__bubble :deep(th),
.chat-message__bubble :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 6px;
  text-align: left;
  vertical-align: top;
  white-space: normal;
  word-break: break-all;
  overflow-wrap: anywhere;
}

.chat-message__bubble :deep(th:first-child),
.chat-message__bubble :deep(td:first-child) {
  width: auto;
}

.chat-message__bubble :deep(th:last-child),
.chat-message__bubble :deep(td:last-child) {
  width: auto;
}

.chat-message__bubble :deep(th) {
  background: #f8fafc;
  font-weight: 600;
}

.chat-message__bubble :deep(ul),
.chat-message__bubble :deep(ol) {
  margin: 6px 0;
  padding-left: 18px;
}

.chat-message__bubble :deep(strong) {
  font-weight: 700;
}

.chat-message__bubble :deep(h1),
.chat-message__bubble :deep(h2),
.chat-message__bubble :deep(h3) {
  margin: 10px 0 6px;
  font-size: 14px;
  font-weight: 700;
}

.chat-message__bubble :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 10px;
  border-left: 3px solid #4f46e5;
  background: rgba(99, 102, 241, 0.06);
  color: #475569;
}

.typing-indicator {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  min-height: 20px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #64748b;
  animation: bounce 1.2s infinite ease-in-out;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.5; }
  40% { transform: translateY(-4px); opacity: 1; }
}
</style>
