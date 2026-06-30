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
  margin-bottom: 16px;
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
  gap: 8px;
}

.page-banner__actions :deep(.el-button) {
  height: 28px;
  padding: 0 12px;
  border-color: rgba(255, 255, 255, 0.35);
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.page-banner__actions :deep(.el-button:hover) {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.5);
  color: #fff;
}

.page-banner__actions :deep(.el-button--primary) {
  background: #fff;
  border-color: #fff;
  color: var(--theme-primary, #010710);
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
}

.page-banner__actions :deep(.el-button--primary:hover) {
  background: #f8fafc;
  border-color: #fff;
  color: var(--theme-primary, #010710);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.22);
}
</style>
