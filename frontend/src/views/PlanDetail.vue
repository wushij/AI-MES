<template>
  <div class="view-page" v-loading="loading">
    <PageHeader title="计划详情" :subtitle="subtitle">
      <el-button @click="router.push('/plans')">返回</el-button>
    </PageHeader>

    <template v-if="detail">
      <el-row :gutter="16" class="summary-row">
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-card__label">执行状态</div>
            <StatusTag :status="executionStatus" />
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-card__label">完成进度</div>
            <div class="stat-card__value">{{ completionProgress }}%</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-card__label">关联工单</div>
            <div class="stat-card__value">{{ workOrders.length }}</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-card__label">计划数量</div>
            <div class="stat-card__value">{{ detail.planQty ?? '--' }} 件</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="hover" class="detail-card">
        <template #header>基本信息</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="计划编号">{{ detail.planNo }}</el-descriptions-item>
          <el-descriptions-item label="产品">
            {{ detail.productName }}
            <el-tag v-if="productInfo?.productCode" size="small" type="info" style="margin-left:8px">{{ productInfo.productCode }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="BOM 状态">
            <el-tag :type="detail.hasBom ? 'success' : 'info'" size="small">{{ detail.hasBom ? '已配置' : '未配置' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="计划日期">{{ detail.planDate }}</el-descriptions-item>
          <el-descriptions-item label="计划状态"><StatusTag :status="String(detail.status ?? '')" /></el-descriptions-item>
          <el-descriptions-item label="下发时间">{{ formatDate(detail.releaseTime) }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.createdByName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(detail.createdTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDate(detail.updatedTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="hover" class="detail-card">
        <template #header>
          <div class="card-header-row">
            <span>完成进度</span>
            <span class="progress-text">{{ completionProgress }}%</span>
          </div>
        </template>
        <ProgressBar :percentage="completionProgress" />
      </el-card>

      <el-card shadow="hover" class="detail-card">
        <template #header>关联工单</template>
        <el-table :data="workOrders" stripe border :header-cell-style="tableHeaderStyle">
          <el-table-column prop="orderNo" label="工单号" min-width="140" align="center">
            <template #default="{ row }">
              <el-button link type="primary" style="font-weight: 600;" @click="goWorkOrder(row.id)">
                {{ row.orderNo }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip align="center" />
          <el-table-column prop="processName" label="当前工序" min-width="100" align="center" />
          <el-table-column prop="teamName" label="班组" min-width="100" align="center">
            <template #default="{ row }">{{ row.teamName || '--' }}</template>
          </el-table-column>
          <el-table-column label="进度" min-width="160" align="center">
            <template #default="{ row }">
              <ProgressBar :percentage="row.progress ?? 0" />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <StatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="交期" min-width="150" align="center">
            <template #default="{ row }">{{ formatDate(row.deadline) }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无关联工单，下发计划后将自动生成" />
          </template>
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
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { getPlan, type PlanDetail } from '@/api/plans'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<PlanDetail | null>(null)
const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }

const subtitle = computed(() => String(detail.value?.planNo ?? ''))
const productInfo = computed(() => detail.value?.product as Record<string, unknown> | undefined)

const workOrders = computed(() => detail.value?.workOrders ?? [])

const completionProgress = computed(() => Number(detail.value?.completionProgress ?? 0))

const executionStatus = computed(() => {
  const status = detail.value?.executionStatus ?? detail.value?.status ?? 'draft'
  return String(status)
})

function formatDate(value: unknown) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 16)
}

function goWorkOrder(id: number | string) {
  router.push(`/work-orders/${id}`)
}

async function loadDetail() {
  const id = route.params.id
  if (!id || Array.isArray(id)) return
  loading.value = true
  try {
    detail.value = await getPlan(id)
  } catch (error) {
    console.error(error)
    ElMessage.error('加载计划详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-row {
  margin-bottom: 0;
}

.stat-card {
  margin-bottom: 0;
}

.stat-card__label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}

.stat-card__value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.detail-card {
  margin-bottom: 0;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.progress-text {
  font-size: 14px;
  font-weight: 700;
  color: #4f46e5;
}
</style>
