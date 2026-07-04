<template>
  <div class="view-page" v-loading="loading">
    <PageHeader title="工单详情" :subtitle="subtitle">
      <el-button @click="router.back()">返回</el-button>
      <el-button v-if="detail" type="primary" @click="router.push('/work-orders')">工单列表</el-button>
    </PageHeader>

    <template v-if="detail">
      <el-card shadow="hover" class="detail-card">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="产品">
            <span>{{ detail.productName }}</span>
            <el-tag v-if="productInfo?.productCode" size="small" type="info" style="margin-left:8px">{{ productInfo.productCode }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="工单数量">{{ detail.orderQty ?? 1 }} {{ productInfo?.unit || '件' }}</el-descriptions-item>
          <el-descriptions-item label="工艺路线">{{ detail.routeVersion ? `${detail.routeVersion}` : '--' }}</el-descriptions-item>
          <el-descriptions-item label="关联计划">{{ detail.planNo || '--' }}</el-descriptions-item>
          <el-descriptions-item label="班组">{{ detail.teamName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="当前工序">{{ detail.processName }}</el-descriptions-item>
          <el-descriptions-item label="进度">{{ detail.progress ?? 0 }}%</el-descriptions-item>
          <el-descriptions-item label="状态"><StatusTag :status="String(detail.status ?? '')" /></el-descriptions-item>
          <el-descriptions-item label="交期">{{ formatDate(detail.deadline) }}</el-descriptions-item>
          <el-descriptions-item v-if="hasSchedulingInfo" label="建议开工">{{ formatDate(detail.scheduledStartTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="hasSchedulingInfo" label="预计工时">{{ formatEstimatedHours(detail.estimatedHours) }}</el-descriptions-item>
          <el-descriptions-item v-if="hasSchedulingInfo" label="排产优先级">{{ formatSchedulingRank(detail.schedulingRank) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="bomPreview.length" shadow="hover" class="detail-card">
        <template #header>
          <div class="card-header-row">
            <span>理论用料（BOM × {{ detail.orderQty ?? 1 }} 件）</span>
            <el-tag type="success" size="small">已配置 BOM</el-tag>
          </div>
        </template>
        <el-table :data="bomPreview" stripe border size="small">
          <el-table-column prop="materialCode" label="物料编码" min-width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" />
          <el-table-column prop="unitQty" label="单件用量" width="100" align="center" />
          <el-table-column prop="requiredQty" label="理论用量" width="110" align="center" />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
        </el-table>
      </el-card>

      <el-card shadow="hover" class="detail-card">
        <ProcessRecordTimeline :records="processRecords" />
      </el-card>

      <el-card v-if="exceptions.length" shadow="hover" class="detail-card">
        <template #header>关联异常</template>
        <el-table :data="exceptions" stripe border>
          <el-table-column prop="eventNo" label="异常编号" min-width="120" />
          <el-table-column prop="eventType" label="类型" min-width="100">
            <template #default="{ row }">{{ exceptionTypeLabel(row.eventType) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" min-width="100">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import ProcessRecordTimeline, { type ProcessRecordItem } from '@/components/workorder/ProcessRecordTimeline.vue'
import { getWorkOrderDetail } from '@/api/workOrders'
import { exceptionTypeLabel } from '@/utils/labels'

interface BomPreviewRow {
  materialCode?: string
  materialName?: string
  unitQty?: number
  requiredQty?: number
  unit?: string
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<Record<string, unknown> | null>(null)

const subtitle = computed(() => String(detail.value?.orderNo ?? ''))
const productInfo = computed(() => detail.value?.product as Record<string, unknown> | undefined)
const bomPreview = computed(() => (detail.value?.bomPreview as BomPreviewRow[] | undefined) ?? [])

const processRecords = computed(() =>
  (detail.value?.processRecords as ProcessRecordItem[] | undefined) ?? []
)

const exceptions = computed(() =>
  (detail.value?.exceptions as Array<Record<string, unknown>> | undefined) ?? []
)

const hasSchedulingInfo = computed(() => {
  const d = detail.value
  if (!d) return false
  return Boolean(d.scheduledStartTime || d.estimatedHours != null || d.schedulingRank != null)
})

function formatDate(value: unknown) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatEstimatedHours(value: unknown) {
  if (value == null || value === '') return '--'
  return `${value} 小时`
}

function formatSchedulingRank(value: unknown) {
  if (value == null || value === '') return '--'
  const rank = Number(value)
  if (rank === 1) return '高 (#1)'
  if (rank === 2) return '中 (#2)'
  if (rank === 3) return '低 (#3)'
  return `#${value}`
}

async function loadDetail() {
  const id = route.params.id
  if (!id || Array.isArray(id)) return
  loading.value = true
  try {
    detail.value = (await getWorkOrderDetail(id)) as unknown as Record<string, unknown>
  } catch (error) {
    console.error(error)
    ElMessage.error('加载工单详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-card {
  margin-bottom: 16px;
}
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
