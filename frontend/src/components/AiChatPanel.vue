<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import AiChatMessage from './AiChatMessage.vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const draft = ref('')

const disabled = computed(() => chatStore.sending || !draft.value.trim())

async function submit() {
  if (disabled.value) return
  const content = draft.value
  draft.value = ''
  await chatStore.sendMessage(content)
}
</script>

<template>
  <transition name="slide-panel">
    <aside v-if="chatStore.visible" class="chat-panel">
      <header class="chat-panel__header">
        <div class="chat-panel__title">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 智能助手</span>
        </div>
        <el-button circle text @click="chatStore.close()">
          <el-icon><Close /></el-icon>
        </el-button>
      </header>

      <div class="chat-panel__messages">
        <AiChatMessage v-for="message in chatStore.messages" :key="message.id" :message="message" />
      </div>

      <div class="chat-panel__composer">
        <div class="chat-panel__composer-box">
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 3 }"
            resize="none"
            placeholder="请输入问题"
            @keydown.enter.prevent="submit"
          />
          <el-button
            type="primary"
            :loading="chatStore.sending"
            :disabled="disabled"
            class="send-btn"
            @click="submit"
          >
            发送
          </el-button>
        </div>
      </div>
    </aside>
  </transition>
</template>

<style scoped>
.chat-panel {
  position: fixed;
  right: 32px;
  bottom: 104px;
  width: 380px;
  height: 560px;
  z-index: 40;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(18px);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.16);
}

.chat-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid var(--cd-border);
}

.chat-panel__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.chat-panel__messages {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  overflow-y: auto;
  background: linear-gradient(180deg, rgba(79, 70, 229, 0.04), rgba(79, 70, 229, 0));
}

.chat-panel__composer {
  padding: 14px 16px;
  border-top: 1px solid var(--cd-border);
  background: #fff;
}

.chat-panel__composer-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 6px 6px 6px 18px;
  transition: all 0.2s ease;
}

.chat-panel__composer-box:focus-within {
  background: #fff;
  border-color: #4f46e5;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);
}

.chat-panel__composer-box :deep(.el-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 4px 8px !important;
  font-size: 13px;
  color: #1e293b;
  min-height: 20px !important;
  line-height: 1.5;
  resize: none !important;
}

.chat-panel__composer-box :deep(.el-textarea__inner)::placeholder {
  color: #94a3b8;
}

.send-btn {
  height: 28px;
  padding: 0 12px !important;
  border-radius: 14px !important;
  font-size: 12px;
  font-weight: 500;
  background: linear-gradient(135deg, #4f46e5, #1677ff) !important;
  border: none !important;
  color: #fff !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.send-btn:hover:not(:disabled) {
  opacity: 0.95;
  box-shadow: 0 4px 10px rgba(79, 70, 229, 0.25);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.97);
}

.slide-panel-enter-active,
.slide-panel-leave-active {
  transition: all 0.2s ease;
}

.slide-panel-enter-from,
.slide-panel-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.98);
}
</style>
