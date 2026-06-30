<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import type { CozeChatMessage } from '@/types'

const props = defineProps<{
  message: CozeChatMessage
}>()

const html = computed(() => marked.parse(props.message.content) as string)
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

.chat-message--user .chat-message__bubble {
  background: var(--cd-primary-gradient);
  color: #fff;
}

.chat-message__bubble :deep(p) {
  margin: 0;
}

.chat-message__bubble :deep(p + p) {
  margin-top: 8px;
}
</style>
