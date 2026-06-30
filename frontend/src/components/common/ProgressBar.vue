<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    value?: number
    percentage?: number
    strokeWidth?: number
    showText?: boolean
  }>(),
  {
    strokeWidth: 8,
    showText: true
  }
)

const progressValue = computed(() => props.value ?? props.percentage ?? 0)

const color = computed(() => {
  if (progressValue.value >= 90) return '#ef4444'
  if (progressValue.value >= 70) return '#f59e0b'
  return 'var(--theme-primary)'
})
</script>

<template>
  <el-progress
    :percentage="progressValue"
    :stroke-width="strokeWidth"
    :show-text="showText"
    :color="color"
    striped
    striped-flow
  />
</template>
