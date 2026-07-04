<script setup lang="ts">
import type { GanttItem } from '@/utils/schedulingHelpers'

defineProps<{
  items: GanttItem[]
}>()
</script>

<template>
  <div v-if="items.length" class="scheduling-gantt">
    <div class="scheduling-gantt__axis">
      <span v-for="item in items" :key="`${item.workOrderCode}-start`" class="scheduling-gantt__tick">
        {{ item.startLabel }}
      </span>
      <span class="scheduling-gantt__tick scheduling-gantt__tick--end">{{ items[items.length - 1].endLabel }}</span>
    </div>
    <div class="scheduling-gantt__rows">
      <div v-for="item in items" :key="item.workOrderCode" class="scheduling-gantt__row">
        <div class="scheduling-gantt__label">
          <strong>{{ item.workOrderCode }}</strong>
          <span>{{ item.teamName }} · {{ item.hours }}h</span>
        </div>
        <div class="scheduling-gantt__track">
          <div
            class="scheduling-gantt__bar"
            :style="{ left: `${item.leftPercent}%`, width: `${item.widthPercent}%` }"
            :title="`${item.startLabel} - ${item.endLabel}`"
          >
            <span>{{ item.startLabel }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
  <el-empty v-else description="暂无时间轴数据" :image-size="56" />
</template>

<style scoped>
.scheduling-gantt {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.scheduling-gantt__axis {
  display: flex;
  justify-content: space-between;
  padding: 0 120px 0 120px;
  font-size: 11px;
  color: #94a3b8;
}

.scheduling-gantt__tick--end {
  margin-left: auto;
}

.scheduling-gantt__rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.scheduling-gantt__row {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 10px;
  align-items: center;
}

.scheduling-gantt__label {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: #64748b;
}

.scheduling-gantt__label strong {
  color: #0f172a;
  font-size: 13px;
}

.scheduling-gantt__track {
  position: relative;
  height: 34px;
  background: linear-gradient(180deg, #f8fafc, #f1f5f9);
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
}

.scheduling-gantt__bar {
  position: absolute;
  top: 4px;
  bottom: 4px;
  min-width: 56px;
  border-radius: 8px;
  background: linear-gradient(135deg, #4f46e5, #1677ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  box-shadow: 0 4px 10px rgba(79, 70, 229, 0.25);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 8px;
}
</style>
