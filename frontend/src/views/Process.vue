<template>
  <div class="view-page">
    <PageHeader title="工序看板" subtitle="本班组工单列表，支持按状态筛选、认领与进度更新。" />

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索工单号 / 产品"
            clearable
            class="toolbar__input"
            @keyup.enter="applyFilters"
            @clear="applyFilters"
          />
          <el-select v-model="filters.boardStatus" placeholder="任务状态" clearable class="toolbar__select" @change="applyFilters">
            <el-option label="全部" value="" />
            <el-option label="待认领" value="pendingClaim" />
            <el-option label="进行中" value="inProgress" />
            <el-option label="今日完工" value="completedToday" />
          </el-select>
        </div>
        <div class="toolbar__actions">
          <div class="summary-tags">
            <el-tag type="warning" effect="plain">待认领 {{ counts.pendingClaim }}</el-tag>
            <el-tag type="primary" effect="plain">进行中 {{ counts.inProgress }}</el-tag>
            <el-tag type="success" effect="plain">今日完工 {{ counts.completedToday }}</el-tag>
          </div>
          <el-tag effect="plain" type="info" class="today-tag">今日 {{ today }}</el-tag>
          <el-button type="primary" :loading="loading" class="action-btn-refresh" @click="loadBoard">刷新</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="hover">
      <el-table v-loading="loading" :data="pagedTasks" stripe border highlight-current-row :header-cell-style="tableHeaderStyle">
        <el-table-column prop="code" label="工单号" min-width="140" align="center">
          <template #default="{ row }">
            <span class="code-text">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip align="center" />
        <el-table-column prop="currentProcess" label="工序" min-width="100" align="center" />
        <el-table-column label="进度" min-width="160" align="center">
          <template #default="{ row }">
            <ProgressBar :percentage="row.progress" />
          </template>
        </el-table-column>
        <el-table-column prop="priorityLabel" label="优先级" width="90" align="center" />
        <el-table-column prop="dueDate" label="交期" min-width="150" align="center" />
        <el-table-column label="任务状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="boardTagType(row.boardStatus)" effect="light" round>{{ row.boardLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="240" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.boardStatus === 'pendingClaim'"
              size="small"
              class="action-btn action-btn--claim"
              :loading="actionLoading === `claim-${row.id}`"
              @click="claimTask(row)"
            >
              认领
            </el-button>
            <el-button
              v-if="row.boardStatus === 'inProgress'"
              size="small"
              class="action-btn action-btn--edit"
              @click="openProgressDialog(row)"
            >
              更新进度
            </el-button>
            <el-button
              v-if="row.boardStatus === 'inProgress'"
              size="small"
              class="action-btn action-btn--exception"
              @click="goToExceptions(row)"
            >
              上报异常
            </el-button>
            <span v-if="row.boardStatus === 'completedToday'" class="done-hint">已完工</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="emptyText" />
        </template>
      </el-table>
      <div class="table-pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          background
          layout="total, prev, pager, next"
          :total="pagination.total"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="progressDialogVisible"
      width="560px"
      class="custom-order-dialog"
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom">
          <el-icon class="header-icon"><TrendCharts /></el-icon>
          <span class="header-text">更新工序进度</span>
        </div>
      </template>

      <template v-if="activeTask">
        <div class="order-meta-grid">
          <div class="meta-item">
            <span class="meta-item__label">工单号</span>
            <span class="meta-item__val code-highlight">{{ activeTask.code }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">关联计划</span>
            <span class="meta-item__val code-highlight">{{ activeTask.planCode || '--' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">关联产品</span>
            <span class="meta-item__val text-highlight">{{ activeTask.productName }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">当前工序</span>
            <span class="meta-item__val">{{ activeTask.currentProcess }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">计划交期</span>
            <span class="meta-item__val text-secondary">{{ activeTask.dueDate }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">工单状态</span>
            <span class="meta-item__val val-status">
              <StatusTag :status="activeTask.status" />
            </span>
          </div>
        </div>

        <el-form
          ref="progressFormRef"
          :model="progressForm"
          :rules="rules"
          label-position="top"
          class="custom-order-form"
        >
          <el-form-item label="生产进度" prop="progress">
            <div class="slider-wrapper-custom">
              <el-slider v-model="progressForm.progress" :min="0" :max="100" class="custom-slider-bar" />
              <div class="progress-percent-badge">{{ progressForm.progress }}%</div>
            </div>
          </el-form-item>

          <el-form-item label="当前工序" prop="currentProcess">
            <el-input v-model="progressForm.currentProcess" placeholder="请输入当前所处的工序名称" class="custom-input" />
          </el-form-item>

          <el-form-item label="备注说明">
            <el-input
              v-model="progressForm.remark"
              type="textarea"
              :rows="3"
              maxlength="300"
              show-word-limit
              placeholder="请输入补充进度说明（可选）"
              class="custom-textarea"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <div class="dialog-footer-custom">
          <el-button class="btn-cancel" @click="progressDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" class="btn-submit" @click="submitProgress">提交进度</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { TrendCharts } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { getProcessTasks } from '@/api/process'
import { claimWorkOrder, updateWorkOrderProgress } from '@/api/workOrders'
import { normalizePriority, priorityLabel } from '@/utils/labels'

type BoardStatus = 'pendingClaim' | 'inProgress' | 'completedToday'

interface TaskRow {
  id: string | number
  code: string
  planCode?: string
  productName: string
  dueDate: string
  currentProcess: string
  progress: number
  priority: string
  priorityLabel: string
  status: string
  completedAt?: string
  boardStatus: BoardStatus
  boardLabel: string
}

const router = useRouter()
const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }

const loading = ref(false)
const submitting = ref(false)
const actionLoading = ref('')
const activeTask = ref<TaskRow | null>(null)
const progressDialogVisible = ref(false)
const progressFormRef = ref<FormInstance>()
const allTasks = ref<TaskRow[]>([])

const filters = reactive({ keyword: '', boardStatus: '' as '' | BoardStatus })
const pagination = reactive({ page: 1, size: 10, total: 0 })
const progressForm = reactive({ progress: 0, currentProcess: '', remark: '' })
const rules: FormRules = {
  progress: [{ required: true, message: '请输入进度', trigger: 'change' }],
  currentProcess: [{ required: true, message: '请输入工序', trigger: 'blur' }]
}

const today = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
})

const counts = computed(() => ({
  pendingClaim: allTasks.value.filter((item) => item.boardStatus === 'pendingClaim').length,
  inProgress: allTasks.value.filter((item) => item.boardStatus === 'inProgress').length,
  completedToday: allTasks.value.filter((item) => item.boardStatus === 'completedToday').length
}))

const filteredTasks = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return allTasks.value.filter((item) => {
    if (filters.boardStatus && item.boardStatus !== filters.boardStatus) return false
    if (!keyword) return true
    return item.code.toLowerCase().includes(keyword) || item.productName.toLowerCase().includes(keyword)
  })
})

const pagedTasks = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredTasks.value.slice(start, start + pagination.size)
})

const emptyText = computed(() => {
  if (filters.boardStatus === 'pendingClaim') return '暂无待认领任务'
  if (filters.boardStatus === 'inProgress') return '暂无进行中的任务'
  if (filters.boardStatus === 'completedToday') return '今日暂无完工任务'
  return '暂无工单任务'
})

onMounted(loadBoard)

watch(
  () => filteredTasks.value.length,
  (count) => {
    pagination.total = count
    if ((pagination.page - 1) * pagination.size >= count && pagination.page > 1) {
      pagination.page = 1
    }
  },
  { immediate: true }
)

function applyFilters() {
  pagination.page = 1
}

function boardTagType(status: BoardStatus) {
  if (status === 'pendingClaim') return 'warning'
  if (status === 'inProgress') return 'primary'
  return 'success'
}

async function loadBoard() {
  loading.value = true
  try {
    const payload = await getProcessTasks()
    const pending = normalizeList(payload.pending ?? (payload as any).pendingClaim).map((item) => mapTask(item, 'pendingClaim'))
    const producing = normalizeList(payload.producing ?? (payload as any).inProgress).map((item) => mapTask(item, 'inProgress'))
    const doneToday = normalizeList(payload.doneToday ?? (payload as any).completedToday).map((item) =>
      mapTask(item, 'completedToday')
    )
    allTasks.value = [...pending, ...producing, ...doneToday]
  } catch (error) {
    console.error(error)
    ElMessage.error('加载工序看板失败')
  } finally {
    loading.value = false
  }
}

async function claimTask(task: TaskRow) {
  actionLoading.value = `claim-${task.id}`
  try {
    await claimWorkOrder(task.id)
    ElMessage.success('任务已认领')
    await loadBoard()
  } catch (error) {
    console.error(error)
    ElMessage.error('认领任务失败')
  } finally {
    actionLoading.value = ''
  }
}

function openProgressDialog(task: TaskRow) {
  activeTask.value = task
  Object.assign(progressForm, { progress: task.progress, currentProcess: task.currentProcess, remark: '' })
  progressDialogVisible.value = true
}

async function submitProgress() {
  const valid = await progressFormRef.value?.validate().catch(() => false)
  if (!valid || !activeTask.value) return
  submitting.value = true
  try {
    await updateWorkOrderProgress(activeTask.value.id, {
      progress: progressForm.progress,
      processName: progressForm.currentProcess,
      remark: progressForm.remark
    })
    ElMessage.success('进度已更新')
    progressDialogVisible.value = false
    await loadBoard()
  } catch (error) {
    console.error(error)
    ElMessage.error('更新进度失败')
  } finally {
    submitting.value = false
  }
}

function goToExceptions(task: TaskRow) {
  router.push({ path: '/exceptions', query: { workOrderId: String(task.id), workOrderCode: task.code } })
}

function normalizeList(value: unknown) {
  if (Array.isArray(value)) return value
  if (value && typeof value === 'object') {
    const record = value as { records?: unknown; list?: unknown; items?: unknown }
    if (Array.isArray(record.records)) return record.records
    if (Array.isArray(record.list)) return record.list
    if (Array.isArray(record.items)) return record.items
  }
  return []
}

function mapTask(item: Record<string, unknown>, boardStatus: BoardStatus): TaskRow {
  const dateStr = item.dueDate ?? item.deadline
  const formattedDate = dateStr ? String(dateStr).replace('T', ' ').substring(0, 16) : '--'
  const boardLabel =
    boardStatus === 'pendingClaim' ? '待认领' : boardStatus === 'inProgress' ? '进行中' : '今日完工'
  return {
    id: (item.id ?? item.workOrderId ?? item.code) as string | number,
    code: String(item.orderNo ?? item.code ?? item.workOrderCode ?? '--'),
    planCode: String(item.planNo ?? item.planCode ?? '--'),
    productName: String(item.productName ?? item.product ?? '--'),
    dueDate: formattedDate,
    currentProcess: String(item.currentProcess ?? item.processName ?? '--'),
    progress: Number(item.progress ?? item.completionRate ?? 0),
    priority: String(normalizePriority(item.priority as string | number | null | undefined)),
    priorityLabel: priorityLabel(item.priority as string | number | null | undefined),
    status: String(item.status ?? 'pending'),
    completedAt: String(item.completedAt ?? item.finishTime ?? '--'),
    boardStatus,
    boardLabel
  }
}
</script>

<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: nowrap;
}

.toolbar__filters,
.toolbar__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap;
}

.toolbar__input {
  width: 220px;
}

.toolbar__select {
  width: 140px;
}

.summary-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.today-tag {
  border-radius: 12px;
  padding: 4px 12px;
  font-weight: 500;
}

.code-text {
  font-weight: 600;
  color: #4f46e5;
  font-family: Consolas, monospace;
}

.done-hint {
  color: #64748b;
  font-size: 13px;
}

.action-btn-refresh {
  border-radius: 20px !important;
  padding: 8px 20px !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%) !important;
  border: none !important;
  color: white !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
}

.action-btn {
  border-radius: 8px !important;
  font-weight: 500 !important;
  font-size: 12px !important;
  padding: 6px 12px !important;
  height: 32px !important;
  margin: 0 4px !important;
  background: #fff !important;
}

.action-btn--edit {
  border: 1px solid rgba(79, 70, 229, 0.3) !important;
  color: #4f46e5 !important;
}

.action-btn--claim {
  border: 1px solid rgba(250, 140, 22, 0.3) !important;
  color: #fa8c16 !important;
}

.action-btn--exception {
  border: 1px solid rgba(245, 34, 45, 0.3) !important;
  color: #f5222d !important;
}

.custom-order-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
}

.custom-order-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #f1f5f9;
  background-color: #f8fafc;
}

.dialog-header-custom {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 20px;
  color: #4f46e5;
}

.header-text {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.order-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1px;
  background: #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  margin-bottom: 24px;
}

.meta-item {
  background: #f8fafc;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-item__label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.meta-item__val {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.slider-wrapper-custom {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.custom-slider-bar {
  flex: 1;
}

.progress-percent-badge {
  background: rgba(79, 70, 229, 0.08);
  color: #4f46e5;
  padding: 4px 12px;
  border-radius: 20px;
  font-weight: 700;
  font-size: 14px;
  min-width: 52px;
  text-align: center;
}

.code-highlight {
  font-family: monospace;
  color: #4f46e5;
}

.text-highlight {
  color: #0f172a;
}

.text-secondary {
  color: #475569;
}

.custom-order-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #1e293b;
}

.custom-input :deep(.el-input__wrapper),
.custom-textarea :deep(.el-textarea__inner) {
  border-radius: 10px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}

.dialog-footer-custom {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn-cancel {
  border-radius: 20px !important;
}

.btn-submit {
  border-radius: 20px !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%) !important;
  border: none !important;
}

.table-pagination {
  display: flex;
  justify-content: flex-start;
  margin-top: 12px;
}
</style>
