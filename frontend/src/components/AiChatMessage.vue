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
    <div class="chat-message__bubble" v-html="html" />
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
  min-width: 260px;
  table-layout: fixed;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
}

.chat-message__bubble :deep(th),
.chat-message__bubble :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 6px 8px;
  text-align: left;
  vertical-align: top;
  word-break: keep-all;
  overflow-wrap: break-word;
}

.chat-message__bubble :deep(th:first-child),
.chat-message__bubble :deep(td:first-child) {
  width: 32%;
  white-space: nowrap;
}

.chat-message__bubble :deep(th:last-child),
.chat-message__bubble :deep(td:last-child) {
  width: 68%;
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
</style>
