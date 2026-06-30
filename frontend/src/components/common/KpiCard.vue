<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import { CaretTop, CaretBottom, SemiSelect } from '@element-plus/icons-vue'

const props = defineProps<{
  title: string
  value: string | number
  unit?: string
  icon?: Component
  color?: string
  trend?: 'up' | 'down' | 'flat'
  trendValue?: string
}>()

const trendIcon = computed(() => {
  if (props.trend === 'up') return CaretTop
  if (props.trend === 'down') return CaretBottom
  return SemiSelect
})
</script>

<template>
  <el-card shadow="hover" class="kpi-card">
    <div class="kpi-card__container">
      <div 
        class="kpi-card__icon" 
        :style="{ 
          background: color ? `linear-gradient(135deg, ${color} 0%, ${color}dd 100%)` : 'var(--cd-primary-gradient)',
          boxShadow: color ? `0 8px 20px ${color}33` : '0 8px 20px rgba(79, 70, 229, 0.15)'
        }"
      >
        <el-icon v-if="icon" :size="24"><component :is="icon" /></el-icon>
        <slot v-else name="icon" />
      </div>
      
      <div class="kpi-card__content">
        <span class="kpi-card__title">{{ title }}</span>
        <div class="kpi-card__value-row">
          <strong class="kpi-card__value">{{ value }}</strong>
          <span v-if="unit" class="kpi-card__unit">{{ unit }}</span>
        </div>
        
        <div v-if="trendValue" class="kpi-card__trend">
          <span :class="['trend-badge', trend || 'flat']">
            <el-icon class="trend-badge__icon"><component :is="trendIcon" /></el-icon>
            <span class="trend-badge__text">{{ trendValue }}</span>
          </span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.kpi-card {
  border-radius: 16px;
  border: 1px solid #f1f5f9;
  background: #fff;
  transition: all 0.3s ease;
  width: 100%;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.06);
}

.kpi-card :deep(.el-card__body) {
  padding: 20px 24px;
}

.kpi-card__container {
  display: flex;
  align-items: center;
  gap: 20px;
}

.kpi-card__icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.kpi-card__content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-grow: 1;
  min-width: 0;
}

.kpi-card__title {
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-card__value-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.kpi-card__value {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
}

.kpi-card__unit {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}

.kpi-card__trend {
  display: flex;
  align-items: center;
  margin-top: 4px;
}

.trend-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
}

.trend-badge.up {
  background-color: #f0fdf4;
  color: #16a34a;
}

.trend-badge.down {
  background-color: #fef2f2;
  color: #dc2626;
}

.trend-badge.flat {
  background-color: #f8fafc;
  color: #64748b;
}

.trend-badge__icon {
  font-size: 12px;
}

.trend-badge__text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
