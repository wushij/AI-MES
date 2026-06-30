<template>
  <div v-if="!initialized" class="app-boot">
    <div class="app-boot__panel">
      <div class="app-boot__spinner" />
      <p class="app-boot__text">正在连接系统…</p>
    </div>
  </div>
  <router-view v-else />
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const { initialized } = storeToRefs(userStore)
</script>

<style>
:root {
  color-scheme: light;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  margin: 0;
  height: 100%;
  background: var(--theme-bg, #f3f5f8);
  color: var(--theme-text-base, #1e293b);
}

body {
  min-width: 1280px;
  overflow: hidden;
}

a {
  color: inherit;
}

.app-boot {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: var(--theme-bg, #f3f5f8);
}

.app-boot__panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  color: #64748b;
}

.app-boot__spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #e2e8f0;
  border-top-color: #4f46e5;
  border-radius: 50%;
  animation: app-boot-spin 0.8s linear infinite;
}

.app-boot__text {
  margin: 0;
  font-size: 14px;
}

@keyframes app-boot-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
