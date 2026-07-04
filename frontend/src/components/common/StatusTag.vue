<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value?: string
  status?: string
}>()

const STATUS_LABELS: Record<string, string> = {
  pending: '待分配',
  assigned: '待领取',
  producing: '生产中',
  exception: '异常处理中',
  done: '已完成',
  completed: '已完成',
  closed: '已解决',
  open: '待处理',
  processing: '处理中',
  warning: '预警',
  draft: '草稿',
  released: '已下发',
  in_progress: '进行中',
  paused: '已暂停',
  normal: '正常',
  shutdown: '设备停机',
  shortage: '缺料',
  quality: '质量异常',
  other: '其他',
  success: '成功',
  critical: '严重',
  high: '高',
  medium: '中',
  low: '低'
}

const displayValue = computed(() => props.value ?? props.status ?? '')

const displayLabel = computed(() => {
  const key = displayValue.value.toLowerCase()
  return STATUS_LABELS[key] ?? displayValue.value
})

const preset = computed(() => {
  const value = displayValue.value.toLowerCase()

  if (['done', 'completed', 'closed', 'success', 'released', 'normal'].includes(value)) {
    return { type: 'success', label: displayLabel.value }
  }
  if (['warning', 'paused', 'processing', 'medium', 'in_progress'].includes(value)) {
    return { type: 'warning', label: displayLabel.value }
  }
  if (['exception', 'critical', 'danger', 'open', 'shutdown', 'high'].includes(value)) {
    return { type: 'danger', label: displayLabel.value }
  }
  return { type: 'info', label: displayLabel.value }
})
</script>

<template>
  <el-tag :type="preset.type as never" effect="light" round>{{ preset.label }}</el-tag>
</template>
