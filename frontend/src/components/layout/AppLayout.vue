<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import AiChatPanel from '@/components/AiChatPanel.vue'
import AiFloatBtn from '@/components/AiFloatBtn.vue'
import AppHeader from './AppHeader.vue'
import AppSidebar from './AppSidebar.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notifications'
import '@/assets/styles/layout-shell.css'

const appStore = useAppStore()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const { backendUnavailable, token } = storeToRefs(userStore)

onMounted(() => {
  void appStore.loadWorkshopSummary()
})

watch(
  token,
  (nextToken) => {
    if (nextToken) {
      void notificationStore.fetchAll()
      notificationStore.connect(nextToken)
      return
    }
    notificationStore.reset()
  },
  { immediate: true }
)
</script>

<template>
  <div class="app-layout-container">
    <el-alert
      v-if="backendUnavailable"
      type="warning"
      title="后端服务暂时不可用，请确认后端已启动；部分数据可能无法加载。"
      show-icon
      :closable="false"
      class="app-layout__offline-alert"
    />
    <div class="app-layout">
      <AppSidebar />
      <div class="app-layout__main">
        <AppHeader />
        <main class="app-layout__content">
          <router-view />
        </main>
      </div>
      <AiChatPanel />
      <AiFloatBtn />
    </div>
  </div>
</template>

<style scoped>
.app-layout-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}

.app-layout {
  flex: 1;
  min-height: 0;
  display: flex;
}

.app-layout__offline-alert {
  margin: 0;
  border-radius: 0;
  flex-shrink: 0;
}
</style>
