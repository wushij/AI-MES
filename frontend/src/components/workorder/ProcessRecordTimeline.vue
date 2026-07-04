<script setup lang="ts">
export interface ProcessRecordItem {
  id?: number | string
  processName: string
  operationCode?: string
  seqNo?: number
  status?: string
  startTime?: string | null
  endTime?: string | null
  remark?: string | null
}

defineProps<{
  records: ProcessRecordItem[]
}>()

function statusLabel(status?: string) {
  switch (status) {
    case 'running':
      return '进行中'
    case 'done':
      return '已完成'
    case 'paused':
      return '已暂停'
    case 'waiting':
    default:
      return '待开始'
  }
}

function statusType(status?: string) {
  switch (status) {
    case 'running':
      return 'primary'
    case 'done':
      return 'success'
    case 'paused':
      return 'warning'
    default:
      return 'info'
  }
}

function formatTime(value?: string | null) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div v-if="records.length" class="process-timeline">
    <div class="process-timeline__title">工序进度</div>
    <el-steps :active="records.findIndex((item) => item.status === 'running')" finish-status="success" align-center>
        <el-step
          v-for="item in records"
          :key="`${item.seqNo}-${item.processName}`"
          :title="item.operationCode ? `${item.operationCode} ${item.processName}` : item.processName"
        :status="item.status === 'done' ? 'success' : item.status === 'running' ? 'process' : item.status === 'paused' ? 'error' : 'wait'"
      >
        <template #description>
          <div class="step-desc">
            <el-tag size="small" :type="statusType(item.status)">{{ statusLabel(item.status) }}</el-tag>
            <span v-if="item.startTime">开始：{{ formatTime(item.startTime) }}</span>
            <span v-if="item.endTime">结束：{{ formatTime(item.endTime) }}</span>
            <span v-if="item.remark">{{ item.remark }}</span>
          </div>
        </template>
      </el-step>
    </el-steps>
  </div>
  <el-empty v-else description="暂无工序记录" :image-size="64" />
</template>

<style scoped>
.process-timeline {
  margin: 16px 0;
  padding: 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.process-timeline__title {
  margin-bottom: 16px;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.step-desc {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}
</style>
