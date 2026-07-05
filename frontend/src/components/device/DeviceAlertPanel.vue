<template>
  <div class="alert-panel">
    <div v-if="alerts.length" class="alert-list">
      <div v-for="item in alerts" :key="`${item.source}-${item.id}`" class="alert-card" :class="`alert-card--${item.alertType}`">
        <div class="alert-card__header">
          <span class="alert-card__type">{{ item.alertTypeLabel }}</span>
          <el-tag size="small" :type="alertStatusTag(item.status)">{{ item.statusLabel || item.status }}</el-tag>
        </div>
        <div class="alert-card__title">{{ item.title }}</div>
        <div class="alert-card__meta">
          <span class="alert-card__ref">{{ item.refNo }}</span>
          <span class="alert-card__time">{{ formatTime(item.occurTime) }}</span>
        </div>
        <div class="alert-card__actions">
          <el-button size="small" link type="primary" @click="emit('view', item)">查看详情</el-button>
        </div>
      </div>
    </div>
    <el-empty v-else :description="emptyText" :image-size="72" />
  </div>
</template>

<script setup lang="ts">
import type { DeviceTodayAlert } from '@/api/devices'

defineProps<{
  alerts: DeviceTodayAlert[]
  emptyText?: string
}>()

const emit = defineEmits<{
  (e: 'view', item: DeviceTodayAlert): void
}>()

function formatTime(value?: string) {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 19)
}

function alertStatusTag(status?: string) {
  switch (status) {
    case 'open': return 'danger'
    case 'processing': return 'warning'
    case 'closed':
    case 'completed': return 'success'
    default: return 'info'
  }
}
</script>

<style scoped>
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.alert-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 16px;
  background: linear-gradient(135deg, #fff 0%, #f8fafc 100%);
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}
.alert-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.08);
}
.alert-card--exception {
  border-left: 4px solid #dc2626;
}
.alert-card--repair {
  border-left: 4px solid #d97706;
}
.alert-card--maintenance_due {
  border-left: 4px solid #ea580c;
}
.alert-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.alert-card__type {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.02em;
}
.alert-card__title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.5;
  margin-bottom: 8px;
}
.alert-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: #94a3b8;
}
.alert-card__ref {
  font-variant-numeric: tabular-nums;
}
.alert-card__actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>
