<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps<{
  title: string
  subtitle?: string
  section?: string
}>()

const route = useRoute()
const sectionLabel = computed(() => props.section ?? (route.meta.section as string | undefined))
</script>

<template>
  <div class="page-banner">
    <div class="page-banner__pattern" />
    <div class="page-banner__content">
      <div class="page-banner__line">
        <span v-if="sectionLabel" class="page-banner__section">{{ sectionLabel }}</span>
        <h1 class="page-banner__title">{{ title }}</h1>
        <template v-if="subtitle">
          <span class="page-banner__divider" aria-hidden="true">·</span>
          <p class="page-banner__subtitle">{{ subtitle }}</p>
        </template>
      </div>
      <div v-if="$slots.default" class="page-banner__actions">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-banner {
  position: relative;
  margin-bottom: 0;
  padding: 10px 18px;
  border-radius: 10px;
  color: #fff;
  overflow: hidden;
  background: linear-gradient(
    135deg,
    var(--theme-primary, #010710) 0%,
    var(--theme-primary-hover, #1e293b) 100%
  );
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.1);
}

.page-banner__pattern {
  position: absolute;
  inset: 0;
  opacity: 0.06;
  background-image: radial-gradient(circle at 20% 50%, #fff 1px, transparent 1px);
  background-size: 24px 24px;
  pointer-events: none;
}

.page-banner__content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 28px;
}

.page-banner__line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.page-banner__section {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: rgba(255, 255, 255, 0.65);
  text-transform: uppercase;
}

.page-banner__title {
  margin: 0;
  flex-shrink: 0;
  font-size: 16px;
  line-height: 1.4;
  font-weight: 700;
  white-space: nowrap;
}

.page-banner__divider {
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.45);
  font-size: 14px;
  line-height: 1;
}

.page-banner__subtitle {
  margin: 0;
  min-width: 0;
  font-size: 13px;
  line-height: 1.4;
  font-weight: 400;
  color: rgba(255, 255, 255, 0.78);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-banner__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 10px;
}

.page-banner__actions :deep(.el-button:not(.el-button--primary)) {
  height: 32px !important;
  padding: 0 16px !important;
  border: 1.5px solid rgba(255, 255, 255, 0.75) !important;
  background: transparent !important;
  color: #fff !important;
  font-weight: 600 !important;
  box-shadow: none !important;
}

.page-banner__actions :deep(.el-button:not(.el-button--primary):hover),
.page-banner__actions :deep(.el-button:not(.el-button--primary):focus) {
  background: rgba(255, 255, 255, 0.12) !important;
  border-color: #fff !important;
  color: #fff !important;
}

.page-banner__actions :deep(.el-button--primary) {
  background: #fff !important;
  border-color: #fff !important;
  color: var(--theme-primary, #0f172a) !important;
  font-weight: 600 !important;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.22) !important;
}

.page-banner__actions :deep(.el-button--primary:hover),
.page-banner__actions :deep(.el-button--primary:focus) {
  background: #f1f5f9 !important;
  border-color: #fff !important;
  color: var(--theme-primary, #0f172a) !important;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.28) !important;
  transform: translateY(-1px);
}
</style>
