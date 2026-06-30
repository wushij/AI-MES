<script setup lang="ts">
import { Monitor, DataLine } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
</script>

<template>
  <div class="cd-storage-card">
    <div class="cd-storage-head">
      <span class="cd-storage-head-label">车间总览</span>
      <span class="cd-storage-badge">{{ appStore.workshopSummary.shiftName }}</span>
    </div>

    <div class="cd-storage-usage">
      <strong>{{ appStore.workshopSummary.todayOutput }}</strong>
      <span class="cd-storage-quota">今日产出</span>
    </div>

    <el-progress
      :percentage="appStore.workshopSummary.onTimeRate"
      :stroke-width="5"
      :color="appStore.workshopSummary.onTimeRate >= 90 ? '#22c55e' : '#f59e0b'"
      :show-text="false"
      class="cd-storage-progress"
    />

    <div class="summary-row">
      <div class="cd-storage-type">
        <el-icon :size="12"><Monitor /></el-icon>
        <span>运行产线 {{ appStore.workshopSummary.activeLines }} 条</span>
      </div>
      <div class="cd-storage-type">
        <el-icon :size="12"><DataLine /></el-icon>
        <span>准时率 {{ appStore.workshopSummary.onTimeRate }}%</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.summary-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
