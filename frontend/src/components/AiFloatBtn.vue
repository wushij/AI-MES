<script setup lang="ts">
import { ChatDotRound } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { computed } from 'vue'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const chatStore = useChatStore()
const hidden = computed(() => route.path === '/login' || route.path === '/ai-chat')
</script>

<template>
  <el-button v-if="!hidden" class="ai-float-btn" type="primary" circle @click="chatStore.toggle()">
    <el-icon :size="22"><ChatDotRound /></el-icon>
  </el-button>
</template>

<style scoped>
.ai-float-btn {
  position: fixed;
  right: 32px;
  bottom: 32px;
  width: 56px;
  height: 56px;
  z-index: 41;
  box-shadow: 0 18px 36px rgba(79, 70, 229, 0.24);
}
</style>
