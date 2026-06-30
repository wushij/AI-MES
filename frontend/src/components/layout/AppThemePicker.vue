<script setup lang="ts">
import { Brush, Check } from '@element-plus/icons-vue'
import { themePresetList } from '@/utils/theme'
import type { ThemePresetId } from '@/types'

defineProps<{
  currentColor: string
  activePresetId?: ThemePresetId | 'custom'
}>()

defineEmits<{
  presetSelect: [presetId: ThemePresetId]
  colorChange: [color: string | null]
}>()
</script>

<template>
  <el-popover trigger="click" placement="bottom-end" :width="320" :show-arrow="false">
    <template #reference>
      <el-icon class="theme-icon" :size="20"><Brush /></el-icon>
    </template>
    <div class="theme-picker-content">
      <div class="theme-picker-header">
        <span class="theme-picker-title">主题风格</span>
        <span class="theme-picker-hint">选择品牌主色</span>
      </div>
      <div class="preset-colors">
        <button
          v-for="item in themePresetList"
          :key="item.id"
          type="button"
          class="preset-color-btn"
          :class="{ active: activePresetId === item.id }"
          @click="$emit('presetSelect', item.id)"
        >
          <span class="preset-swatch" :style="{ background: item.primary }">
            <el-icon v-if="activePresetId === item.id" class="preset-check"><Check /></el-icon>
          </span>
          <span class="preset-label">{{ item.label }}</span>
        </button>
      </div>
      <div class="theme-custom-row">
        <span class="theme-custom-label">自定义</span>
        <el-color-picker
          :model-value="currentColor"
          :show-alpha="false"
          size="small"
          @update:model-value="$emit('colorChange', $event)"
        />
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.theme-icon {
  cursor: pointer;
  color: var(--theme-text-base, #1f2937);
  transition: color 0.3s;
}

.theme-icon:hover {
  color: var(--theme-primary, #010710);
}

.theme-picker-content {
  padding: 4px 2px 8px;
}

.theme-picker-header {
  padding: 0 4px 14px;
  border-bottom: 1px solid var(--theme-border, #e2e8f0);
  margin-bottom: 14px;
}

.theme-picker-title {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-base, #1e293b);
}

.theme-picker-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--theme-text-secondary, #64748b);
}

.preset-colors {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px 8px;
  margin-bottom: 14px;
}

.preset-color-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 10px;
}

.preset-color-btn.active .preset-swatch {
  box-shadow: 0 0 0 2px #fff, 0 0 0 4px var(--theme-primary, #010710);
}

.preset-swatch {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
}

.preset-check {
  font-size: 18px;
  color: #fff;
}

.preset-label {
  font-size: 11px;
  color: var(--theme-text-secondary, #64748b);
}

.theme-custom-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 4px 0;
  border-top: 1px solid var(--theme-border, #e2e8f0);
}

.theme-custom-label {
  font-size: 13px;
  color: var(--theme-text-secondary, #64748b);
}
</style>
