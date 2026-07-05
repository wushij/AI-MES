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
            <span class="code-text" @click="openProgressDialog(row)">{{ row.code }}</span>
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
      width="680px"
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

        <el-alert
          v-if="isOrderDone"
          type="success"
          show-icon
          :closable="false"
          title="工单已完成，无需更新进度"
          class="progress-done-alert"
        />

        <el-form
          v-else
          ref="progressFormRef"
          :model="progressForm"
          :rules="rules"
          label-position="top"
          class="custom-order-form"
        >
          <el-form-item label="生产进度" prop="progress">
            <div class="slider-wrapper-custom">
              <el-slider v-model="progressForm.progress" :min="0" :max="maxProgress" class="custom-slider-bar" />
              <div class="progress-percent-badge">{{ progressForm.progress }}%</div>
            </div>
            <div v-if="sortedProcessRecords.length" class="field-hint">
              <template v-if="isLastProcess && pendingProcessCount <= 1">
                当前为最后一道待执行工序，完成后请点击「完成最后一道工序并完工」
              </template>
              <template v-else>
                当前工序未完成前，进度最高 {{ maxProgress }}%{{ nextProcessName ? `；完成后请点击「进入下一道：${nextProcessName}」` : '' }}
              </template>
            </div>
          </el-form-item>

          <el-form-item label="当前工序" prop="currentProcess">
            <el-input :model-value="progressForm.currentProcess" disabled class="custom-input full-width" />
          </el-form-item>

          <el-form-item v-if="allowedDevices.length" label="使用设备">
            <ProcessBoundDevices v-if="isDeviceSelectionLocked" :devices="lockedDevices" />
            <el-select
              v-else
              v-model="progressForm.deviceIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择本工序可用设备"
              class="custom-input full-width device-select"
            >
              <el-option
                v-for="item in allowedDevices"
                :key="item.id"
                :label="`${item.deviceName} (${item.deviceCode})`"
                :value="normalizeDeviceId(item.id)!"
              />
            </el-select>
            <div v-if="!isDeviceSelectionLocked" class="field-hint">
              按设备类别可选范围内设备，请确认实际使用设备
            </div>
          </el-form-item>

          <div v-if="currentMaterials.length" class="process-bind-block">
            <div class="bind-title">工序物料</div>
            <el-table :data="currentMaterials" size="small" border>
              <el-table-column prop="materialName" label="物料" min-width="120" align="center" />
              <el-table-column prop="qty" label="用量" width="80" align="center" />
              <el-table-column prop="unit" label="单位" width="70" align="center" />
              <el-table-column label="类型" width="90" align="center">
                <template #default="{ row }">
                  <span class="material-type-badge" :class="row.materialType">{{ materialTypeLabel(row.materialType) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div v-if="currentParameters.length" class="process-bind-block">
            <div class="bind-title">工艺参数</div>
            <el-table :data="currentParameters" size="small" border>
              <el-table-column prop="paramName" label="参数名" min-width="120" align="center" />
              <el-table-column prop="paramValue" label="标准值" width="100" align="center">
                <template #default="{ row }">
                  <span class="value-highlight">{{ row.paramValue || '--' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="范围" width="130" align="center">
                <template #default="{ row }">
                  <span v-if="row.minValue || row.maxValue" class="range-highlight">{{ row.minValue ?? '--' }} ~ {{ row.maxValue ?? '--' }}</span>
                  <span v-else class="text-muted">--</span>
                </template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="80" align="center">
                <template #default="{ row }">{{ row.unit || '--' }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div v-if="currentSops.length" class="process-bind-block">
            <div class="bind-title">作业指导书 (SOP)</div>
            <div class="sop-links">
              <div
                v-for="sop in currentSops"
                :key="sop.id"
                class="sop-file-card"
                @click="previewExecutionSop(sop)"
              >
                <el-icon class="sop-file-icon"><Document /></el-icon>
                <span class="sop-file-name">{{ sop.fileName }}</span>
              </div>
            </div>
          </div>

          <div v-if="needsInspection" class="process-bind-block">
            <div class="bind-title">质量检验（报工前必填）</div>
            <el-table :data="inspectionRows" size="small" border>
              <el-table-column prop="itemName" label="检验项" min-width="120" align="center" />
              <el-table-column label="标准" min-width="100" align="center">
                <template #default="{ row }">
                  <span v-if="row.standard">{{ row.standard }}</span>
                  <span v-else-if="row.minValue || row.maxValue">{{ row.minValue ?? '--' }} ~ {{ row.maxValue ?? '--' }} {{ row.unit || '' }}</span>
                  <span v-else>--</span>
                </template>
              </el-table-column>
              <el-table-column label="实测值" min-width="120" align="center">
                <template #default="{ row }">
                  <el-input v-model="row.measuredValue" placeholder="录入实测值" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="结果" width="100" align="center">
                <template #default="{ row }">
                  <el-select v-model="row.result" size="small">
                    <el-option label="合格" value="pass" />
                    <el-option label="不合格" value="fail" />
                  </el-select>
                </template>
              </el-table-column>
            </el-table>
          </div>

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
          <el-button
            v-if="canSaveProgress"
            type="primary"
            plain
            :loading="submitting"
            class="btn-submit"
            @click="submitProgress(false)"
          >
            保存进度
          </el-button>
          <el-button
            v-if="canAdvanceProcess && !isLastProcess"
            type="primary"
            :loading="submitting"
            class="btn-submit"
            @click="submitProgress(true)"
          >
            完成本工序，进入下一道{{ nextProcessName ? `：${nextProcessName}` : '' }}
          </el-button>
          <el-button
            v-else-if="canAdvanceProcess && isLastProcess"
            type="success"
            :loading="submitting"
            class="btn-submit"
            @click="submitProgress(true)"
          >
            完成最后一道工序并完工
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="sopPreviewVisible" :title="previewSopItem?.fileName || 'SOP 预览'" width="80%" top="5vh">
      <div v-loading="previewLoading" class="sop-preview-box">
        <img v-if="previewBlobUrl && previewSopItem?.fileType === 'image'" :src="previewBlobUrl" alt="SOP" />
        <iframe v-else-if="previewBlobUrl && previewSopItem?.fileType === 'pdf'" :src="previewBlobUrl" />
        <video v-else-if="previewBlobUrl && previewSopItem?.fileType === 'video'" :src="previewBlobUrl" controls />
        <div v-else-if="previewBlobUrl" class="sop-download-hint">
          <el-link :href="previewBlobUrl" target="_blank" type="primary">点击下载/打开文件</el-link>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { TrendCharts, Document } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { getProcessTasks } from '@/api/process'
import { claimWorkOrder, getWorkOrderDetail, updateWorkOrderProgress } from '@/api/workOrders'
import { getProcessExecutionContext, getProcessOperationNames, materialTypeLabel, type ProcessOperation, type ProcessSop } from '@/api/processRoutes'
import { resolveErrorMessage, TOKEN_STORAGE_KEY } from '@/api/request'
import { getDeviceOptions } from '@/api/devices'
import { getInspectionPlansByProcess, submitInspectionRecords, type InspectionPlan } from '@/api/quality'
import { normalizePriority, priorityLabel } from '@/utils/labels'
import { useAppStore } from '@/stores/app'
import { type ProcessRecordItem } from '@/components/workorder/ProcessRecordTimeline.vue'
import ProcessBoundDevices from '@/components/workorder/ProcessBoundDevices.vue'

type BoardStatus = 'pendingClaim' | 'inProgress' | 'completedToday'

type ProcessRecordRaw = ProcessRecordItem & {
  device_id?: number | string
  deviceIds?: Array<number | string>
  device_ids?: string
}

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
const appStore = useAppStore()
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
const progressForm = reactive({
  progress: 0,
  currentProcess: '',
  remark: '',
  deviceId: undefined as number | string | undefined,
  deviceIds: [] as Array<number | string>
})
const processOptions = ref<string[]>(['备料', '装配', '检测', '包装'])
const executionOperations = ref<ProcessOperation[]>([])
const activeOrderQty = ref(1)
const processRecords = ref<ProcessRecordRaw[]>([])
const inspectionRows = ref<Array<InspectionPlan & { measuredValue: string; result: string }>>([])
const allDeviceOptions = ref<Array<{ id: number | string; deviceName: string; deviceCode: string; categoryId?: number | string }>>([])
const sopPreviewVisible = ref(false)
const previewLoading = ref(false)
const previewSopItem = ref<ProcessSop | null>(null)
const previewBlobUrl = ref('')
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

const currentOperation = computed(() =>
  executionOperations.value.find((item) => item.operationName === progressForm.currentProcess)
)

const sortedProcessRecords = computed(() =>
  [...processRecords.value].sort((a, b) => (Number(a.seqNo) || 0) - (Number(b.seqNo) || 0))
)

const currentProcessRecord = computed(() =>
  sortedProcessRecords.value.find((item) => item.processName === progressForm.currentProcess)
)

const isLastProcess = computed(() => {
  const records = sortedProcessRecords.value
  const current = currentProcessRecord.value
  if (!records.length || !current) return true
  const currentSeq = Number(current.seqNo) || 0
  const maxSeq = Math.max(...records.map((item) => Number(item.seqNo) || 0))
  return currentSeq >= maxSeq
})

const maxProgress = computed(() => {
  const records = sortedProcessRecords.value
  if (!records.length) return 100
  const total = records.length
  const done = records.filter((item) => item.status === 'done').length
  if (done >= total) return 100
  return Math.min(99, Math.floor(((done + 1) * 100) / total) - 1)
})

const nextProcessName = computed(() => {
  const current = currentProcessRecord.value
  if (!current) return ''
  const currentSeq = Number(current.seqNo) || 0
  return (
    sortedProcessRecords.value.find(
      (item) => (Number(item.seqNo) || 0) > currentSeq && item.status !== 'done'
    )?.processName ?? ''
  )
})

const pendingProcessCount = computed(() =>
  sortedProcessRecords.value.filter((item) => item.status !== 'done').length
)

const isOrderDone = computed(() => activeTask.value?.status === 'done')

const canSaveProgress = computed(() => {
  if (isOrderDone.value) return false
  if (sortedProcessRecords.value.length && pendingProcessCount.value === 0) return false
  if (isLastProcess.value && pendingProcessCount.value <= 1 && progressForm.progress >= maxProgress.value) return false
  return true
})

const canAdvanceProcess = computed(() => {
  if (isOrderDone.value) return false
  return sortedProcessRecords.value.length > 0
})

const needsInspection = computed(() => Number(currentOperation.value?.needCheck) === 1 && inspectionRows.value.length > 0)

const currentMaterials = computed(() => currentOperation.value?.materials ?? [])
const currentParameters = computed(() => currentOperation.value?.parameters ?? [])
const currentSops = computed(() => currentOperation.value?.sops ?? [])

const boundDeviceCount = computed(() => {
  const op = currentOperation.value
  if (!op?.devices?.length) return 0
  return op.devices.filter((bind) => bind.bindType === 'device' && bind.deviceId).length
})

const isDeviceSelectionLocked = computed(() => boundDeviceCount.value > 0)

type DeviceOption = { id: number | string; deviceName: string; deviceCode: string; categoryId?: number | string }

function normalizeDeviceId(id: number | string | undefined | null) {
  if (id == null || id === '') return undefined
  const num = Number(id)
  return Number.isNaN(num) ? id : num
}

function recordDeviceId(record?: { deviceId?: number | string; device_id?: number | string }) {
  return normalizeDeviceId(record?.deviceId ?? record?.device_id)
}

function parseDeviceIds(raw?: string | Array<number | string>) {
  if (Array.isArray(raw)) {
    return raw.map((id) => normalizeDeviceId(id)).filter((id): id is number | string => id != null)
  }
  if (!raw) return [] as Array<number | string>
  return raw
    .split(',')
    .map((part) => normalizeDeviceId(part.trim()))
    .filter((id): id is number | string => id != null)
}

function resolveDeviceIdsForProcess(processName: string) {
  const matched = processRecords.value.filter((item) => item.processName === processName)
  const withDevices = matched.find((item) => {
    const ids = item.deviceIds?.length ? item.deviceIds : parseDeviceIds(item.device_ids)
    return ids.length > 0 || recordDeviceId(item) != null
  })
  const record = withDevices ?? matched[0]
  if (!record) return [] as Array<number | string>
  if (record.deviceIds?.length) return record.deviceIds.map((id) => normalizeDeviceId(id)!).filter(Boolean)
  const fromRaw = parseDeviceIds(record.device_ids)
  if (fromRaw.length) return fromRaw
  const single = recordDeviceId(record)
  return single != null ? [single] : []
}

const allowedDevices = computed(() => {
  const op = currentOperation.value
  if (!op?.devices?.length) return [] as DeviceOption[]
  const result = new Map<number | string, DeviceOption>()
  const deviceIds = new Set<number | string>()
  const categoryIds = new Set<number | string>()
  op.devices.forEach((bind) => {
    if (bind.bindType === 'device') {
      const id = normalizeDeviceId(bind.deviceId)
      if (id == null) return
      deviceIds.add(id)
      result.set(id, {
        id,
        deviceName: bind.deviceName || bind.deviceCode || `设备${id}`,
        deviceCode: bind.deviceCode || String(id)
      })
    } else if (bind.bindType === 'category') {
      const id = normalizeDeviceId(bind.categoryId)
      if (id != null) categoryIds.add(id)
    }
  })
  allDeviceOptions.value.forEach((d) => {
    const id = normalizeDeviceId(d.id)
    if (id == null) return
    const categoryId = normalizeDeviceId(d.categoryId)
    if (deviceIds.has(id) || (categoryId != null && categoryIds.has(categoryId))) {
      result.set(id, { id, deviceName: d.deviceName, deviceCode: d.deviceCode, categoryId: d.categoryId })
    }
  })
  return Array.from(result.values())
})

const lockedDevices = computed(() => {
  const op = currentOperation.value
  if (!op?.devices?.length) return [] as DeviceOption[]
  const directIds = new Set(
    op.devices
      .filter((bind) => bind.bindType === 'device' && bind.deviceId)
      .map((bind) => normalizeDeviceId(bind.deviceId)!)
  )
  return allowedDevices.value.filter((item) => directIds.has(normalizeDeviceId(item.id)!))
})

function resolveDefaultDeviceIds() {
  const op = currentOperation.value
  const directIds = (op?.devices ?? [])
    .filter((bind) => bind.bindType === 'device' && bind.deviceId)
    .map((bind) => normalizeDeviceId(bind.deviceId)!)
    .filter((id) => allowedDevices.value.some((d) => normalizeDeviceId(d.id) === id))
  if (directIds.length) return directIds
  return allowedDevices.value.map((d) => normalizeDeviceId(d.id)!).filter(Boolean)
}

function syncProgressDeviceSelection() {
  if (!allowedDevices.value.length) {
    progressForm.deviceIds = []
    progressForm.deviceId = undefined
    return
  }
  if (isDeviceSelectionLocked.value) {
    progressForm.deviceIds = lockedDevices.value.map((d) => normalizeDeviceId(d.id)!).filter(Boolean)
    progressForm.deviceId = progressForm.deviceIds[0]
    return
  }
  const saved = resolveDeviceIdsForProcess(progressForm.currentProcess)
  const allowedSet = new Set(allowedDevices.value.map((d) => normalizeDeviceId(d.id)))
  progressForm.deviceIds =
    saved.length > 0
      ? saved.filter((id) => allowedSet.has(id))
      : resolveDefaultDeviceIds()
  progressForm.deviceId = progressForm.deviceIds[0]
}

onMounted(() => {
  void loadProcessOptions()
  void loadDeviceOptions()
  loadBoard()
})

async function loadDeviceOptions() {
  try {
    allDeviceOptions.value = await getDeviceOptions()
  } catch (error) {
    console.error(error)
  }
}

async function loadProcessOptions() {
  try {
    const names = await getProcessOperationNames()
    if (names.length) processOptions.value = names
  } catch (error) {
    console.error(error)
  }
}

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
  try {
    await ElMessageBox.confirm(
      `确认认领工单「${task.code}」？认领后请通过「更新进度」开始首道工序，届时将按工序领料。`,
      '确认认领',
      { type: 'warning', confirmButtonText: '确认认领', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionLoading.value = `claim-${task.id}`
  try {
    await claimWorkOrder(task.id)
    ElMessage.success('任务已认领，请更新进度开始首道工序')
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
  processRecords.value = []
  Object.assign(progressForm, {
    progress: task.progress,
    currentProcess: task.currentProcess,
    remark: '',
    deviceId: undefined,
    deviceIds: []
  })
  progressDialogVisible.value = true
  void loadExecutionContext(task.id)
}

function resolveProgressProcessName(orderProcessName: string) {
  if (orderProcessName) {
    const matched = processRecords.value.find(
      (item) => item.processName === orderProcessName && item.status !== 'done'
    )
    if (matched) return matched.processName
  }
  return processRecords.value.find((item) => item.status !== 'done')?.processName ?? orderProcessName
}

async function loadExecutionContext(workOrderId: string | number) {
  try {
    if (!allDeviceOptions.value.length) {
      await loadDeviceOptions()
    }
    const [ctx, detail] = await Promise.all([
      getProcessExecutionContext(workOrderId),
      getWorkOrderDetail(workOrderId)
    ])
    executionOperations.value = ctx.operations ?? []
    processRecords.value = ((detail as { processRecords?: typeof processRecords.value }).processRecords ?? []).map((item) => {
      const deviceIds = Array.isArray(item.deviceIds)
        ? item.deviceIds.map((id) => normalizeDeviceId(id)!).filter(Boolean)
        : parseDeviceIds(item.device_ids)
      const deviceId = recordDeviceId(item) ?? deviceIds[0]
      return { ...item, deviceId, deviceIds }
    })
    activeOrderQty.value = Number((detail as { orderQty?: number }).orderQty ?? 1)
    if (executionOperations.value.length) {
      processOptions.value = executionOperations.value.map((item) => item.operationName)
    }
    const orderProcessName = String(
      (detail as { processName?: string }).processName ?? activeTask.value?.currentProcess ?? ''
    )
    progressForm.currentProcess = resolveProgressProcessName(orderProcessName)
    if (progressForm.progress > maxProgress.value) {
      progressForm.progress = maxProgress.value
    }
    await nextTick()
    onProcessChange()
  } catch (error) {
    console.error(error)
    executionOperations.value = []
    processRecords.value = []
    inspectionRows.value = []
  }
}

function onProcessChange() {
  syncProgressDeviceSelection()
  void loadInspectionPlans()
}

watch(allowedDevices, () => {
  if (progressDialogVisible.value) {
    syncProgressDeviceSelection()
  }
})

async function loadInspectionPlans() {
  inspectionRows.value = []
  if (!activeTask.value || !progressForm.currentProcess) return
  const op = currentOperation.value
  if (!op || Number(op.needCheck) !== 1) return
  try {
    const plans = await getInspectionPlansByProcess(activeTask.value.id, progressForm.currentProcess)
    inspectionRows.value = plans.map((plan) => ({
      ...plan,
      measuredValue: '',
      result: 'pass'
    }))
  } catch (error) {
    console.error(error)
  }
}

async function previewExecutionSop(row: ProcessSop) {
  previewSopItem.value = row
  sopPreviewVisible.value = true
  previewLoading.value = true
  if (previewBlobUrl.value) URL.revokeObjectURL(previewBlobUrl.value)
  previewBlobUrl.value = ''
  try {
    const base = import.meta.env.VITE_API_BASE_URL || '/api'
    const res = await fetch(`${base}/process-routes/sop/${row.id}/file`, {
      headers: { Authorization: `Bearer ${localStorage.getItem(TOKEN_STORAGE_KEY) || ''}` }
    })
    if (!res.ok) throw new Error('加载失败')
    previewBlobUrl.value = URL.createObjectURL(await res.blob())
  } catch (error) {
    console.error(error)
    ElMessage.error('SOP 预览失败')
  } finally {
    previewLoading.value = false
  }
}

watch(maxProgress, (value) => {
  if (progressForm.progress > value) {
    progressForm.progress = value
  }
})

async function submitProgress(completeCurrentProcess = false) {
  const valid = await progressFormRef.value?.validate().catch(() => false)
  if (!valid || !activeTask.value) return

  const op = currentOperation.value
  if (needsInspection.value) {
    const missing = inspectionRows.value.some((row) => !row.measuredValue && row.result === 'pass')
    if (missing) {
      ElMessage.warning('请填写检验实测值，或将不合格项标记为不合格')
      return
    }
  }

  if (op) {
    const warnings: string[] = []
    if (Number(op.needCheck) === 1 && !needsInspection.value) {
      warnings.push('当前工序要求质检（need_check），请确认已完成检验或已知悉风险')
    }
    if (Number(op.needScan) === 1) {
      warnings.push('当前工序要求扫码（need_scan），请确认已完成扫码或已知悉风险')
    }
    if (warnings.length) {
      try {
        await ElMessageBox.confirm(warnings.join('\n'), '报工确认', {
          type: 'warning',
          confirmButtonText: '继续报工',
          cancelButtonText: '取消'
        })
      } catch {
        return
      }
    }
  }

  if (currentProcessNotStarted()) {
    const materials = currentOperation.value?.materials
    if (materials && materials.length) {
      const htmlContent = `
        <div style="font-size: 14px; line-height: 1.5; color: #334155;">
          <p style="margin: 0 0 10px 0; font-weight: 600; color: #1e293b; display: flex; align-items: center; justify-content: center; gap: 8px;">
            <svg viewBox="0 0 24 24" fill="none" style="width: 20px; height: 20px; flex-shrink: 0; transform: translateY(1px);">
              <circle cx="12" cy="12" r="10" fill="#e6a23c"></circle>
              <line x1="12" y1="8" x2="12" y2="12" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round"></line>
              <line x1="12" y1="16" x2="12" y2="16.01" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round"></line>
            </svg>
            本工序开工将领用以下物料：
          </p>
          ${formatMaterialPickHtml(materials, activeOrderQty.value)}
        </div>
      `
      try {
        await ElMessageBox.confirm(
          htmlContent,
          '领料确认',
          {
            confirmButtonText: '确认开工并领料',
            cancelButtonText: '取消',
            dangerouslyUseHTMLString: true,
            center: true
          }
        )
      } catch {
        return
      }
    }
  }

  if (completeCurrentProcess) {
    let confirmTitle = isLastProcess.value ? '完工确认' : '转序确认'
    let confirmBtnText = isLastProcess.value ? '确认完工' : '确认转序'
    
    let htmlContent = `
      <div style="font-size: 14px; line-height: 1.5; color: #334155;">
        <p style="margin: 0 0 10px 0; font-weight: 600; color: #1e293b; display: flex; align-items: center; justify-content: center; gap: 8px;">
          <svg viewBox="0 0 24 24" fill="none" style="width: 20px; height: 20px; flex-shrink: 0; transform: translateY(1px);">
            <circle cx="12" cy="12" r="10" fill="#e6a23c"></circle>
            <line x1="12" y1="8" x2="12" y2="12" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round"></line>
            <line x1="12" y1="16" x2="12" y2="16.01" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round"></line>
          </svg>
          ${isLastProcess.value ? '确认完成最后一道工序并整单完工？' : `确认完成「${progressForm.currentProcess}」并进入下一道「${nextProcessName.value}」？`}
        </p>
    `
    
    if (!isLastProcess.value && nextProcessName.value) {
      const nextOp = executionOperations.value.find((item) => item.operationName === nextProcessName.value)
      if (nextOp?.materials?.length) {
        htmlContent += `
          <p style="margin: 16px 0 8px 0; font-weight: 600; color: #475569;">下一道「${nextProcessName.value}」首次报工时将领用：</p>
          ${formatMaterialPickHtml(nextOp.materials, activeOrderQty.value)}
        `
      }
    }
    
    htmlContent += `</div>`

    try {
      await ElMessageBox.confirm(htmlContent, confirmTitle, {
        confirmButtonText: confirmBtnText,
        cancelButtonText: '取消',
        dangerouslyUseHTMLString: true,
        center: true
      })
    } catch {
      return
    }
  }

  submitting.value = true
  try {
    if (needsInspection.value) {
      const inspectionResult = await submitInspectionRecords({
        workOrderId: activeTask.value.id,
        processName: progressForm.currentProcess,
        items: inspectionRows.value.map((row) => ({
          planId: row.id,
          itemName: row.itemName,
          measuredValue: row.measuredValue,
          result: row.result
        }))
      })
      if (inspectionResult.hasFailure) {
        ElMessage.error('检验不合格，已自动创建质量异常')
        progressDialogVisible.value = false
        await loadBoard()
        return
      }
    }

    await updateWorkOrderProgress(activeTask.value.id, {
      progress: progressForm.progress,
      processName: progressForm.currentProcess,
      deviceIds: progressForm.deviceIds.map((id) => normalizeDeviceId(id)!).filter(Boolean),
      deviceId: normalizeDeviceId(progressForm.deviceIds[0]),
      remark: progressForm.remark,
      completeCurrentProcess
    })
    ElMessage.success(completeCurrentProcess ? (isLastProcess.value ? '工单已完工' : '本工序已完成，已进入下一道') : '进度已更新')
    progressDialogVisible.value = false
    await loadBoard()
    if (completeCurrentProcess && isLastProcess.value) {
      void appStore.loadWorkshopSummary()
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(resolveErrorMessage(error, '更新进度失败'))
  } finally {
    submitting.value = false
  }
}

function currentProcessNotStarted() {
  const record = processRecords.value.find((item) => item.processName === progressForm.currentProcess)
  return !record?.startTime
}

function formatMaterialPickHtml(materials: ProcessOperation['materials'], prodQty = 1) {
  if (!materials || !materials.length) return ''
  let html = `
    <div style="margin: 8px 0; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden;">
      <table style="width: 100%; border-collapse: collapse; text-align: left; font-size: 12px; background-color: #fff;">
        <thead>
          <tr style="background-color: #f8fafc; border-bottom: 1px solid #e2e8f0;">
            <th style="padding: 6px 10px; color: #475569; font-weight: 600;">物料名称</th>
            <th style="padding: 6px 10px; color: #475569; font-weight: 600; text-align: right;">单件用量</th>
            <th style="padding: 6px 10px; color: #475569; font-weight: 600; text-align: right;">领用总数</th>
          </tr>
        </thead>
        <tbody>
  `
  materials.forEach((mat) => {
    const name = mat.materialName || String(mat.materialId)
    const unitQty = Number(mat.qty) || 0
    const total = unitQty * prodQty
    const unit = mat.unit || '件'
    
    html += `
          <tr style="border-bottom: 1px solid #f1f5f9;">
            <td style="padding: 8px 10px; color: #1e293b; font-weight: 500;">${name}</td>
            <td style="padding: 8px 10px; color: #64748b; text-align: right;">${unitQty} ${unit}</td>
            <td style="padding: 8px 10px; color: #4f46e5; font-weight: 600; text-align: right;">${total} ${unit}</td>
          </tr>
    `
  })
  html += `
        </tbody>
      </table>
    </div>
    <p style="margin: 8px 0 0 0; font-size: 12px; color: #64748b; line-height: 1.5; text-align: center;">
      领用总数 = 单件用量 × 本工单生产数量（共 <strong style="color: #475569;">${prodQty}</strong> 件）
    </p>
  `
  return html
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
.progress-done-alert {
  margin-bottom: 8px;
}

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
  cursor: pointer;
  transition: all 0.2s ease;
}

.code-text:hover {
  color: #6366f1;
  text-decoration: underline;
  opacity: 0.9;
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

.process-bind-block {
  margin-bottom: 16px;
}

.bind-title {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
  font-weight: 600;
}

.sop-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sop-file-card {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.sop-file-card:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
  transform: translateY(-1px);
}
.sop-file-icon {
  font-size: 14px;
  color: #6366f1;
}
.sop-file-name {
  font-size: 12px;
  font-weight: 500;
  color: #334155;
}

.material-type-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
}
.material-type-badge.raw {
  background: #eff6ff;
  color: #1d4ed8;
  border-color: #bfdbfe;
}
.material-type-badge.semi {
  background: #f5f3ff;
  color: #6d28d9;
  border-color: #ddd6fe;
}
.material-type-badge.aux {
  background: #f0fdf4;
  color: #15803d;
  border-color: #bbf7d0;
}
.material-type-badge.tooling {
  background: #fffbeb;
  color: #d97706;
  border-color: #fde68a;
}

.value-highlight {
  font-weight: 600;
  color: #4f46e5;
}
.range-highlight {
  font-family: ui-monospace, monospace;
  color: #475569;
  font-size: 11px;
}
.text-muted {
  color: #cbd5e1;
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
}

.field-hint--info {
  color: #2563eb;
}

.device-select :deep(.el-select__wrapper) {
  min-height: 42px;
  border-radius: 10px;
}

.sop-preview-box {
  min-height: 360px;
}

.sop-preview-box img,
.sop-preview-box iframe,
.sop-preview-box video {
  width: 100%;
  min-height: 360px;
  border: none;
}

.sop-download-hint {
  padding: 24px;
  text-align: center;
}
</style>
