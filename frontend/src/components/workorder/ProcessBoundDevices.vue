<script setup lang="ts">
import { Monitor } from '@element-plus/icons-vue'

defineProps<{
  devices: Array<{ id: number | string; deviceName: string; deviceCode: string }>
}>()
</script>

<template>
  <div class="process-bound-devices">
    <div v-for="item in devices" :key="item.id" class="process-bound-devices__item">
      <div class="process-bound-devices__icon-wrap">
        <el-icon class="process-bound-devices__icon"><Monitor /></el-icon>
      </div>
      <div class="process-bound-devices__text">
        <span class="process-bound-devices__name">{{ item.deviceName }}</span>
        <span class="process-bound-devices__code">{{ item.deviceCode }}</span>
      </div>
      <el-tag size="small" type="primary" effect="light" round>工序绑定</el-tag>
    </div>
  </div>
</template>

<style scoped>
.process-bound-devices {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  gap: 12px;
  width: 100%;
  overflow-x: auto;
}

.process-bound-devices__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fbff 0%, #eff6ff 100%);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  flex-shrink: 0;
  min-width: 220px;
}

.process-bound-devices__icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #bfdbfe;
  flex-shrink: 0;
}

.process-bound-devices__icon {
  font-size: 18px;
  color: #2563eb;
}

.process-bound-devices__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.process-bound-devices__name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.3;
}

.process-bound-devices__code {
  font-size: 12px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
</style>
