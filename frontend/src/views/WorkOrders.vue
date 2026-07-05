<template>

  <div class="view-page">

    <PageHeader title="工单管理" subtitle="支持新建、编辑、派工、认领、进度更新与完工。" />

    <el-card shadow="never" class="toolbar-card">

      <div class="toolbar">

        <div class="toolbar__filters">

          <el-input v-model="filters.keyword" placeholder="搜索工单" clearable class="toolbar__input" @keyup.enter="loadOrders" />

          <el-select v-model="filters.status" placeholder="状态" clearable class="toolbar__select" @change="handleFilterChange">

            <el-option label="待派工" value="pending" /><el-option label="已派工" value="assigned" /><el-option label="生产中" value="producing" /><el-option label="异常" value="exception" /><el-option label="已完成" value="done" />

          </el-select>

          <el-select
            v-if="isSupervisor"
            v-model="filters.teamId"
            placeholder="班组"
            clearable
            class="toolbar__select"
            @change="handleFilterChange"
          >
            <el-option v-for="team in teamOptions" :key="team.id" :label="team.name" :value="team.id" />
          </el-select>

          <el-switch v-else v-model="filters.onlyMine" inline-prompt active-text="我的" inactive-text="全部" @change="loadOrders" />

        </div>

        <div class="toolbar__actions">
          <el-button class="action-btn-reset" @click="resetFilters">重置</el-button>
          <el-button v-if="isSupervisor" type="primary" @click="openCreateDialog">新建工单</el-button>
          <el-button v-if="isSupervisor" type="primary" class="action-btn-refresh" @click="loadOrders">刷新</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="hover">
      <el-table v-loading="loading" :data="orders" stripe border highlight-current-row :header-cell-style="tableHeaderStyle">
        <el-table-column prop="code" label="工单号" min-width="150" align="center">
          <template #default="{ row }">
            <el-button link type="primary" style="font-weight: 600;" @click="goToDetail(row)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="planCode" label="计划" min-width="140" align="center" />
        <el-table-column prop="productName" label="产品" min-width="160" show-overflow-tooltip align="center" />
        <el-table-column prop="teamName" label="班组" min-width="100" align="center" />
        <el-table-column prop="currentProcess" label="当前工序" min-width="120" align="center" />
        <el-table-column label="进度" min-width="180" align="center">
          <template #default="{ row }">
            <ProgressBar :percentage="row.progress" />
          </template>
        </el-table-column>
        <el-table-column prop="priorityLabel" label="优先级" width="90" align="center" />
        <el-table-column prop="dueDate" label="交期" min-width="120" align="center">
          <template #default="{ row }">
            <span :class="{ overdue: row.overdue }">{{ row.dueDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="380" align="center">
          <template #default="{ row }">
            <template v-if="isSupervisor">
              <el-button v-if="row.status === 'pending'" size="small" class="action-btn action-btn--assign" @click="openAssignDialog(row)">派工</el-button>
              <el-button v-if="row.status !== 'done'" size="small" class="action-btn action-btn--edit" @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" class="action-btn action-btn--view" @click="openProgressDrawer(row)">详情</el-button>
              <el-button size="small" class="action-btn action-btn--exception" @click="goToExceptions(row)">上报异常</el-button>
              <el-button size="small" class="action-btn action-btn--delete" :loading="actionLoading === `delete-${row.id}`" @click="removeOrder(row)">删除</el-button>
            </template>
            <template v-else>
              <el-button v-if="row.status === 'assigned'" size="small" class="action-btn action-btn--claim" :loading="actionLoading === `claim-${row.id}`" @click="claimOrder(row)">认领</el-button>
              <el-button v-if="row.status === 'producing'" size="small" class="action-btn action-btn--edit" @click="openProgressDrawer(row)">更新进度</el-button>
              <el-button v-if="row.status === 'producing'" size="small" class="action-btn action-btn--complete" :loading="actionLoading === `complete-${row.id}`" @click="completeOrder(row)">完工</el-button>
              <el-button size="small" class="action-btn action-btn--exception" @click="goToExceptions(row)">上报异常</el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无工单">
            <el-button v-if="isSupervisor" type="primary" @click="openCreateDialog">新建工单</el-button>
          </el-empty>
        </template>
      </el-table>
      <div class="table-pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          background
          layout="total, prev, pager, next"
          :total="pagination.total"
          @current-change="loadOrders"
        />
      </div>

    </el-card>

    <el-dialog v-model="createDialogVisible" title="新建工单" width="480px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="88px">
        <el-form-item label="产品" prop="productId">
          <ProductSelect v-model="createForm.productId" @change="onCreateProductChange" />
        </el-form-item>
        <el-form-item label="生产数量" prop="orderQty">
          <el-input-number v-model="createForm.orderQty" :min="1" class="full-width" />
        </el-form-item>
        <el-form-item label="起始工序" prop="processName">
          <el-select v-model="createForm.processName" class="full-width" :disabled="!createProcessOptions.length">
            <el-option v-for="name in createProcessOptions" :key="name" :label="name" :value="name" />
          </el-select>
          <div v-if="createProcessHint" class="field-hint">{{ createProcessHint }}</div>
          <div v-else-if="createForm.productId && !createProcessOptions.length" class="field-hint">该产品暂无已发布工艺路线</div>
          <div v-else-if="createProcessOptions.length > 1" class="field-hint">前置工序将视为已完成，仅执行起始工序及之后步骤</div>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="createForm.priority" class="full-width">
            <el-option label="高" :value="1" />
            <el-option label="中" :value="2" />
            <el-option label="低" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="交期" prop="deadline">
          <el-date-picker
            v-model="createForm.deadline"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择交期"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="班组">
          <el-select v-model="createForm.teamId" placeholder="不选则为待派工" clearable class="full-width">
            <el-option v-for="team in teamOptions" :key="team.id" :label="team.name" :value="team.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingCreate" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="编辑工单" width="480px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="88px">
        <el-form-item label="工单号">
          <el-input :model-value="editingCode" disabled />
        </el-form-item>
        <el-form-item label="产品" prop="productId">
          <ProductSelect v-model="editForm.productId" @change="onEditProductChange" />
        </el-form-item>
        <el-form-item label="生产数量" prop="orderQty">
          <el-input-number v-model="editForm.orderQty" :min="1" class="full-width" />
        </el-form-item>
        <el-form-item label="当前工序" prop="processName">
          <el-select v-model="editForm.processName" class="full-width">
            <el-option v-for="name in processOptions" :key="name" :label="name" :value="name" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="editForm.priority" class="full-width">
            <el-option label="高" :value="1" />
            <el-option label="中" :value="2" />
            <el-option label="低" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="交期" prop="deadline">
          <el-date-picker
            v-model="editForm.deadline"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择交期"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="班组">
          <el-select v-model="editForm.teamId" placeholder="不选则为待派工" clearable class="full-width">
            <el-option v-for="team in teamOptions" :key="team.id" :label="team.name" :value="team.id" />
          </el-select>
        </el-form-item>

        <div v-if="hasSchedulingInfo" class="scheduling-info-block">
          <div class="scheduling-info-block__title">AI 排产信息</div>
          <div class="scheduling-info-grid">
            <div v-if="schedulingInfo.teamName" class="scheduling-info-item">
              <span class="scheduling-info-item__label">建议班组</span>
              <strong>{{ schedulingInfo.teamName }}</strong>
            </div>
            <div v-if="schedulingInfo.scheduledStartTime" class="scheduling-info-item">
              <span class="scheduling-info-item__label">建议开工</span>
              <strong>{{ schedulingInfo.scheduledStartTime }}</strong>
            </div>
            <div v-if="schedulingInfo.estimatedHours" class="scheduling-info-item">
              <span class="scheduling-info-item__label">预计工时</span>
              <strong>{{ schedulingInfo.estimatedHours }} 小时</strong>
            </div>
          </div>
          <div v-if="schedulingInfo.schedulingReason" class="scheduling-info-conclusion">
            <div class="scheduling-info-conclusion__label">排产结论</div>
            <p class="scheduling-info-reason">{{ schedulingInfo.schedulingReason }}</p>
          </div>
        </div>

        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingEdit" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignDialogVisible" title="派工" width="460px">

      <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-width="88px">

        <el-form-item label="工单号"><el-input :model-value="currentOrder?.code || ''" disabled /></el-form-item>

        <el-form-item label="班组" prop="teamId"><el-select v-model="assignForm.teamId" placeholder="选择班组" class="full-width"><el-option v-for="team in teamOptions" :key="team.id" :label="team.name" :value="team.id" /></el-select></el-form-item>

        <el-form-item label="优先级" prop="priority">
          <el-select v-model="assignForm.priority" class="full-width">
            <el-option label="高" :value="1" />
            <el-option label="中" :value="2" />
            <el-option label="低" :value="3" />
          </el-select>
        </el-form-item>

      </el-form>

      <template #footer><el-button @click="assignDialogVisible = false">取消</el-button><el-button type="primary" :loading="submittingAssign" @click="submitAssign">派工</el-button></template>

    </el-dialog>

    <el-dialog
      v-model="progressDrawerVisible"
      width="560px"
      class="custom-order-dialog"
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom">
          <el-icon class="header-icon"><TrendCharts /></el-icon>
          <span class="header-text">{{ isSupervisor ? '工单详情与进度' : '更新工序进度' }}</span>
        </div>
      </template>

      <template v-if="currentOrder">
        <div class="order-meta-grid">
          <div class="meta-item">
            <span class="meta-item__label">工单号</span>
            <span class="meta-item__val code-highlight">{{ currentOrder.code }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">关联计划</span>
            <span class="meta-item__val code-highlight">{{ currentOrder.planCode || '--' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">关联产品</span>
            <span class="meta-item__val text-highlight">{{ currentOrder.productName }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">当前工序</span>
            <span class="meta-item__val">{{ currentOrder.currentProcess }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">计划交期</span>
            <span class="meta-item__val text-secondary">{{ currentOrder.dueDate }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">工单状态</span>
            <span class="meta-item__val val-status">
              <StatusTag :status="currentOrder.status" />
            </span>
          </div>
        </div>

        <ProcessRecordTimeline :records="processRecords" />

        <div v-if="hasSchedulingInfo" class="scheduling-info-block scheduling-info-block--detail">
          <div class="scheduling-info-block__title">AI 排产信息</div>
          <div class="scheduling-info-grid">
            <div v-if="schedulingInfo.teamName" class="scheduling-info-item">
              <span class="scheduling-info-item__label">建议班组</span>
              <strong>{{ schedulingInfo.teamName }}</strong>
            </div>
            <div v-if="schedulingInfo.scheduledStartTime" class="scheduling-info-item">
              <span class="scheduling-info-item__label">建议开工</span>
              <strong>{{ schedulingInfo.scheduledStartTime }}</strong>
            </div>
            <div v-if="schedulingInfo.estimatedHours" class="scheduling-info-item">
              <span class="scheduling-info-item__label">预计工时</span>
              <strong>{{ schedulingInfo.estimatedHours }} 小时</strong>
            </div>
          </div>
          <div v-if="schedulingInfo.schedulingReason" class="scheduling-info-conclusion">
            <div class="scheduling-info-conclusion__label">排产结论</div>
            <p class="scheduling-info-reason">{{ schedulingInfo.schedulingReason }}</p>
          </div>
        </div>

        <el-form
          ref="progressFormRef"
          :model="progressForm"
          :rules="progressRules"
          label-position="top"
          class="custom-order-form"
        >
          <el-form-item label="生产进度" prop="progress">
            <div class="slider-wrapper-custom">
              <el-slider
                v-model="progressForm.progress"
                :min="0"
                :max="maxProgress"
                class="custom-slider-bar"
              />
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
          <el-button class="btn-cancel" @click="progressDrawerVisible = false">关闭</el-button>
          <el-button type="primary" plain :loading="submittingProgress" class="btn-submit" @click="submitProgress(false)">
            保存进度
          </el-button>
          <el-button
            v-if="sortedProcessRecords.length && !isLastProcess"
            type="primary"
            :loading="submittingProgress"
            class="btn-submit"
            @click="submitProgress(true)"
          >
            完成本工序，进入下一道{{ nextProcessName ? `：${nextProcessName}` : '' }}
          </el-button>
          <el-button
            v-else-if="sortedProcessRecords.length && isLastProcess"
            type="success"
            :loading="submittingProgress"
            class="btn-submit"
            @click="submitProgress(true)"
          >
            完成最后一道工序并完工
          </el-button>
        </div>
      </template>
    </el-dialog>

  </div>

</template>



<script setup lang="ts">

import { ElMessage, ElMessageBox } from 'element-plus'

import type { FormInstance, FormRules } from 'element-plus'

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { TrendCharts } from '@element-plus/icons-vue'

import { useRouter, useRoute } from 'vue-router'

import PageHeader from '@/components/common/PageHeader.vue'

import ProgressBar from '@/components/common/ProgressBar.vue'

import StatusTag from '@/components/common/StatusTag.vue'
import ProductSelect from '@/components/common/ProductSelect.vue'

import ProcessRecordTimeline, { type ProcessRecordItem } from '@/components/workorder/ProcessRecordTimeline.vue'
import ProcessBoundDevices from '@/components/workorder/ProcessBoundDevices.vue'

import { getProcessExecutionContext, getProcessOperationNames, resolveProcessRouteForProduct, type ProcessOperation } from '@/api/processRoutes'
import { getDeviceOptions } from '@/api/devices'

import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

import { normalizePriority, priorityLabel } from '@/utils/labels'
import { confirmDelete } from '@/utils/confirmDelete'
import { hasAiSchedulingInfo } from '@/utils/schedulingHelpers'

interface TeamOption { id: string | number; name: string }

interface WorkOrderRow { id: string | number; code: string; planCode: string; productName: string; teamName: string; teamId?: string | number; currentProcess: string; progress: number; priority: number; priorityLabel: string; dueDate: string; status: string; overdue: boolean; remark?: string }

const userStore = useUserStore()
const appStore = useAppStore()

const router = useRouter()
const route = useRoute()

const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }

const loading = ref(false)

const submittingAssign = ref(false)

const submittingCreate = ref(false)

const submittingEdit = ref(false)

const submittingProgress = ref(false)

const actionLoading = ref('')

const orders = ref<WorkOrderRow[]>([])

const teamOptions = ref<TeamOption[]>([])

const processOptions = ref<string[]>(['备料', '装配', '检测', '包装'])

const createProcessOptions = ref<string[]>([])
const createProcessHint = ref('')

const processRecords = ref<ProcessRecordItem[]>([])

const progressOrderQty = ref(1)

const executionOperations = ref<ProcessOperation[]>([])

const allDeviceOptions = ref<Array<{ id: number | string; deviceName: string; deviceCode: string; categoryId?: number | string }>>([])

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

const boundDeviceCount = computed(() => {
  const op = currentOperation.value
  if (!op?.devices?.length) return 0
  return op.devices.filter((bind) => bind.bindType === 'device' && bind.deviceId).length
})

const isDeviceSelectionLocked = computed(() => boundDeviceCount.value > 0)

const allowedDevices = computed(() => {
  const op = currentOperation.value
  if (!op?.devices?.length) return [] as Array<{ id: number | string; deviceName: string; deviceCode: string; categoryId?: number | string }>
  const result = new Map<number | string, { id: number | string; deviceName: string; deviceCode: string; categoryId?: number | string }>()
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
  if (!op?.devices?.length) return [] as Array<{ id: number | string; deviceName: string; deviceCode: string }>
  const directIds = new Set(
    op.devices
      .filter((bind) => bind.bindType === 'device' && bind.deviceId)
      .map((bind) => normalizeDeviceId(bind.deviceId)!)
  )
  return allowedDevices.value.filter((item) => directIds.has(normalizeDeviceId(item.id)!))
})

const progressProcessOptions = computed(() => {
  if (processRecords.value.length) {
    return processRecords.value.map((item) => item.processName)
  }
  return processOptions.value
})

const currentOrder = ref<WorkOrderRow | null>(null)

const assignDialogVisible = ref(false)

const createDialogVisible = ref(false)

const editDialogVisible = ref(false)

const progressDrawerVisible = ref(false)

const editingId = ref<string | number | null>(null)

const editingCode = ref('')

const assignFormRef = ref<FormInstance>()

const createFormRef = ref<FormInstance>()

const editFormRef = ref<FormInstance>()

const progressFormRef = ref<FormInstance>()

const isSupervisor = computed(() => userStore.isAdmin || userStore.isSupervisor)

const filters = reactive<{ keyword: string; status: string; teamId: number | undefined; onlyMine: boolean }>({
  keyword: '',
  status: '',
  teamId: undefined,
  onlyMine: true
})
const pagination = reactive({ page: 1, size: 10, total: 0 })

const assignForm = reactive<{ teamId: number | undefined; priority: number }>({ teamId: undefined, priority: 2 })

const createForm = reactive<{
  productId: number | string | undefined
  productName: string
  orderQty: number
  processName: string
  priority: number
  deadline: string
  teamId: number | undefined
  remark: string
}>({
  productId: undefined,
  productName: '',
  orderQty: 1,
  processName: '备料',
  priority: 2,
  deadline: '',
  teamId: undefined,
  remark: ''
})

const editForm = reactive<{
  productId: number | string | undefined
  productName: string
  orderQty: number
  processName: string
  priority: number
  deadline: string
  teamId: number | undefined
  remark: string
}>({
  productId: undefined,
  productName: '',
  orderQty: 1,
  processName: '备料',
  priority: 2,
  deadline: '',
  teamId: undefined,
  remark: ''
})

function onCreateProductChange(payload: { productId?: number | string; productName: string }) {
  createForm.productId = payload.productId
  createForm.productName = payload.productName
  void loadCreateProcessOptions(payload.productId, payload.productName)
}

async function loadCreateProcessOptions(productId?: number | string, productName?: string) {
  createProcessOptions.value = []
  createProcessHint.value = ''
  if (productId == null || productId === '') return
  try {
    const resolved = await resolveProcessRouteForProduct(productId, productName)
    const names = (resolved.operations ?? [])
      .slice()
      .sort((a, b) => (Number(a.seqNo) || 0) - (Number(b.seqNo) || 0))
      .map((item) => item.operationName)
      .filter(Boolean)
    createProcessOptions.value = names
    if (resolved.isDefault && names.length) {
      createProcessHint.value = `未配置专用工艺，将使用通用路线「${resolved.routeName || '默认路线'}」`
    }
    if (names.length) {
      createForm.processName = names[0]
    }
  } catch (error) {
    console.error(error)
  }
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

function onEditProductChange(payload: { productId?: number | string; productName: string }) {
  editForm.productId = payload.productId
  editForm.productName = payload.productName
}

const schedulingInfo = reactive({
  teamName: '',
  scheduledStartTime: '',
  estimatedHours: '',
  schedulingReason: ''
})

const hasSchedulingInfo = computed(() =>
  hasAiSchedulingInfo({
    scheduledStartTime: schedulingInfo.scheduledStartTime,
    estimatedHours: schedulingInfo.estimatedHours,
    schedulingReason: schedulingInfo.schedulingReason
  })
)

const progressForm = reactive({
  progress: 0,
  currentProcess: '',
  remark: '',
  deviceId: undefined as number | string | undefined,
  deviceIds: [] as Array<number | string>
})

const assignRules: FormRules = { teamId: [{ required: true, message: '请选择班组', trigger: 'change' }], priority: [{ required: true, message: '请选择优先级', trigger: 'change' }] }

const createRules: FormRules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  orderQty: [{ required: true, message: '请输入生产数量', trigger: 'change' }],
  processName: [{ required: true, message: '请选择工序', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }]
}

const editRules = createRules

const progressRules: FormRules = { progress: [{ required: true, message: '请输入进度', trigger: 'change' }], currentProcess: [{ required: true, message: '请输入工序', trigger: 'blur' }] }

onMounted(() => {
  if (route.query.keyword) {
    filters.keyword = String(route.query.keyword)
  }
  loadTeamOptions()
  loadProcessOptions()
  void loadDeviceOptions()
  loadOrders()
})

async function loadDeviceOptions() {
  try {
    allDeviceOptions.value = await getDeviceOptions()
  } catch (error) {
    console.error(error)
  }
}

function normalizeDeviceId(id: number | string | undefined | null) {
  if (id == null || id === '') return undefined
  const num = Number(id)
  return Number.isNaN(num) ? id : num
}

type ProcessRecordRaw = ProcessRecordItem & {
  device_id?: number | string
  deviceIds?: Array<number | string>
  device_ids?: string
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

function recordDeviceId(record?: ProcessRecordRaw) {
  return normalizeDeviceId(record?.deviceId ?? record?.device_id)
}

function resolveDeviceIdsForProcess(processName: string) {
  const matched = processRecords.value.filter((item) => item.processName === processName)
  const withDevices = matched.find((item) => {
    const raw = item as ProcessRecordRaw
    const ids = raw.deviceIds?.length ? raw.deviceIds : parseDeviceIds(raw.device_ids)
    return ids.length > 0 || recordDeviceId(raw) != null
  })
  const record = (withDevices ?? matched[0]) as ProcessRecordRaw | undefined
  if (!record) return [] as Array<number | string>
  if (record.deviceIds?.length) return record.deviceIds.map((id) => normalizeDeviceId(id)!).filter(Boolean)
  const fromRaw = parseDeviceIds(record.device_ids)
  if (fromRaw.length) return fromRaw
  const single = recordDeviceId(record)
  return single != null ? [single] : []
}

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

function onProcessChange() {
  syncProgressDeviceSelection()
}

watch(allowedDevices, () => {
  if (progressDrawerVisible.value) {
    syncProgressDeviceSelection()
  }
})

watch(maxProgress, (value) => {
  if (progressForm.progress > value) {
    progressForm.progress = value
  }
})

async function loadProcessOptions() {
  try {
    const names = await getProcessOperationNames()
    if (names.length) {
      processOptions.value = names
      if (!processOptions.value.includes(createForm.processName)) {
        createForm.processName = processOptions.value[0]
      }
    }
  } catch (error) {
    console.error(error)
  }
}

function goToDetail(row: WorkOrderRow) {
  router.push({ name: 'work-order-detail', params: { id: row.id } })
}

async function loadOrders() {
  loading.value = true
  try {
    const api = await import('@/api/workOrders')
    const response = await api.getWorkOrders({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      teamId: filters.teamId,
      page: pagination.page,
      size: pagination.size
    })
    pagination.total = Number((response as any)?.total ?? 0)
    orders.value = normalizeList(response).map((item: any) => mapWorkOrderRow(item))
  } catch (error) {
    console.error(error)
    ElMessage.error('加载工单失败')
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  pagination.page = 1
  void loadOrders()
}

async function loadTeamOptions() { try { const { getTeams } = await import('@/api/teams'); const list = await getTeams(); teamOptions.value = (Array.isArray(list) ? list : []).map((item: any) => ({ id: Number(item.id), name: String(item.teamName ?? '--') })) } catch (error) { console.error(error) } }

function openCreateDialog() {
  createProcessOptions.value = []
  createProcessHint.value = ''
  Object.assign(createForm, {
    productId: undefined,
    productName: '',
    orderQty: 1,
    processName: '备料',
    priority: 2,
    deadline: '',
    teamId: undefined,
    remark: ''
  })
  createDialogVisible.value = true
}

async function submitCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submittingCreate.value = true
  try {
    const api = await import('@/api/workOrders')
    const payload: Parameters<typeof api.createWorkOrder>[0] = {
      productId: createForm.productId,
      productName: createForm.productName.trim(),
      orderQty: createForm.orderQty,
      processName: createForm.processName,
      priority: createForm.priority,
      remark: createForm.remark.trim() || undefined
    }
    if (createForm.deadline) payload.deadline = createForm.deadline
    if (createForm.teamId != null) payload.teamId = createForm.teamId
    const created = await api.createWorkOrder(payload)
    const createdRow = await resolveCreatedWorkOrderRow(created)
    const code = createdRow?.code.startsWith('WO-') ? createdRow.code : ''
    ElMessage.success(code ? `工单 ${code} 已创建` : '工单已创建')
    createDialogVisible.value = false
    if (createdRow) {
      const exists = orders.value.some((item) => String(item.id) === String(createdRow.id))
      if (!exists) {
        orders.value = [createdRow, ...orders.value]
      }
    } else {
      await loadOrders()
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('创建工单失败')
  } finally {
    submittingCreate.value = false
  }
}

async function openEditDialog(row: WorkOrderRow) {
  editingId.value = row.id
  editingCode.value = row.code
  resetSchedulingInfo()
  editDialogVisible.value = true
  try {
    const api = await import('@/api/workOrders')
    const detail = (await api.getWorkOrderDetail(row.id)) as unknown as Record<string, unknown>
    Object.assign(editForm, {
      productId: detail.productId != null ? detail.productId as number | string : undefined,
      productName: String(detail.productName ?? row.productName),
      orderQty: Number(detail.orderQty ?? 1),
      processName: String(detail.processName ?? row.currentProcess),
      priority: normalizePriority(detail.priority as string | number | null | undefined ?? row.priority),
      deadline: formatDeadlineForPicker(String(detail.deadline ?? row.dueDate ?? '')),
      teamId: detail.teamId != null ? Number(detail.teamId) : undefined,
      remark: String(detail.remark ?? row.remark ?? '')
    })
    loadSchedulingInfo(detail)
  } catch (error) {
    console.error(error)
    Object.assign(editForm, {
      productId: undefined,
      productName: row.productName,
      orderQty: 1,
      processName: row.currentProcess,
      priority: normalizePriority(row.priority),
      deadline: formatDeadlineForPicker(row.dueDate),
      teamId: row.teamId != null ? Number(row.teamId) : undefined,
      remark: row.remark ?? ''
    })
  }
}

async function submitEdit() {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid || editingId.value == null) return
  submittingEdit.value = true
  try {
    const api = await import('@/api/workOrders')
    const payload: Parameters<typeof api.updateWorkOrder>[1] = {
      productId: editForm.productId,
      productName: editForm.productName.trim(),
      orderQty: editForm.orderQty,
      processName: editForm.processName,
      priority: editForm.priority,
      remark: editForm.remark.trim() || undefined
    }
    if (editForm.deadline) payload.deadline = editForm.deadline
    if (editForm.teamId != null) payload.teamId = editForm.teamId
    await api.updateWorkOrder(editingId.value, payload)
    ElMessage.success('工单已更新')
    editDialogVisible.value = false
    await loadOrders()
  } catch (error) {
    console.error(error)
    ElMessage.error('更新工单失败')
  } finally {
    submittingEdit.value = false
  }
}

async function resolveCreatedWorkOrderRow(created: any): Promise<WorkOrderRow | null> {
  const direct = created?.orderNo || created?.workOrderNo || created?.id
    ? created
    : created?.data
  if (direct) {
    const row = mapWorkOrderRow(direct)
    if (row.code !== '--') return row
  }

  const id = direct?.id ?? created?.id ?? created?.data?.id
  if (id == null) return null
  try {
    const api = await import('@/api/workOrders')
    const detail = await api.getWorkOrderDetail(id)
    return mapWorkOrderRow(detail as any)
  } catch {
    return null
  }
}

function openAssignDialog(row: WorkOrderRow) {
  currentOrder.value = row
  assignForm.teamId = row.teamId != null ? Number(row.teamId) : undefined
  assignForm.priority = normalizePriority(row.priority)
  assignDialogVisible.value = true
}

async function submitAssign() {
  const valid = await assignFormRef.value?.validate().catch(() => false)
  if (!valid || !currentOrder.value || assignForm.teamId == null) return
  submittingAssign.value = true
  try {
    const api = await import('@/api/workOrders')
    await api.assignWorkOrder(currentOrder.value.id, {
      teamId: assignForm.teamId,
      priority: assignForm.priority
    })
    ElMessage.success('工单已派工')
    assignDialogVisible.value = false
    loadOrders()
  } catch (error) {
    console.error(error)
    ElMessage.error('派工失败，请确认已选择班组')
  } finally {
    submittingAssign.value = false
  }
}

async function claimOrder(row: WorkOrderRow) {
  try {
    await ElMessageBox.confirm(
      `确认认领工单「${row.code}」？认领后请通过「更新进度」开始首道工序，届时将按工序领料。`,
      '确认认领',
      { type: 'warning', confirmButtonText: '确认认领', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionLoading.value = `claim-${row.id}`
  try {
    const api = await import('@/api/workOrders')
    await api.claimWorkOrder(row.id)
    ElMessage.success('工单已认领，请更新进度开始首道工序')
    loadOrders()
  } catch (error) {
    console.error(error)
    ElMessage.error('认领失败')
  } finally {
    actionLoading.value = ''
  }
}

async function openProgressDrawer(row: WorkOrderRow) {
  currentOrder.value = row
  resetSchedulingInfo()
  processRecords.value = []
  executionOperations.value = []
  Object.assign(progressForm, {
    progress: row.progress,
    currentProcess: row.currentProcess,
    remark: row.remark ?? '',
    deviceId: undefined,
    deviceIds: []
  })
  progressDrawerVisible.value = true
  try {
    if (!allDeviceOptions.value.length) {
      await loadDeviceOptions()
    }
    const api = await import('@/api/workOrders')
    const detail = (await api.getWorkOrderDetail(row.id)) as unknown as Record<string, unknown>
    progressOrderQty.value = Number(detail.orderQty ?? 1)
    loadSchedulingInfo(detail)
    processRecords.value = ((detail.processRecords as ProcessRecordRaw[] | undefined) ?? []).map((item) => {
      const deviceIds = Array.isArray(item.deviceIds)
        ? item.deviceIds.map((id) => normalizeDeviceId(id)!).filter(Boolean)
        : parseDeviceIds(item.device_ids)
      const deviceId = recordDeviceId(item) ?? deviceIds[0]
      return { ...item, deviceId, deviceIds }
    })
    executionOperations.value = (detail.routingContext as { operations?: ProcessOperation[] } | undefined)?.operations ?? []
    if (!executionOperations.value.length) {
      try {
        const ctx = await getProcessExecutionContext(row.id)
        executionOperations.value = ctx.operations ?? []
      } catch (error) {
        console.error(error)
      }
    }
    const active = processRecords.value.find((item) => item.status !== 'done')
    progressForm.currentProcess = resolveProgressProcessName(
      String(detail.processName ?? row.currentProcess ?? active?.processName ?? '')
    )
    if (progressForm.progress > maxProgress.value) {
      progressForm.progress = maxProgress.value
    }
    await nextTick()
    syncProgressDeviceSelection()
  } catch (error) {
    console.error(error)
  }
}

async function submitProgress(completeCurrentProcess = false) {
  const valid = await progressFormRef.value?.validate().catch(() => false)
  if (!valid || !currentOrder.value) return

  if (currentProcessNotStarted()) {
    const materials = currentOperation.value?.materials
    if (materials && materials.length) {
      const htmlContent = `
        <div style="font-size: 14px; line-height: 1.5; color: #334155;">
          <p style="margin: 0 0 10px 0; font-weight: 600; color: #1e293b;">本工序开工将领用以下物料：</p>
          ${formatMaterialPickHtml(materials, progressOrderQty.value)}
          <p style="margin: 14px 0 0 0; color: #d97706; font-weight: 600;">确认开工并继续？</p>
        </div>
      `
      try {
        await ElMessageBox.confirm(
          htmlContent,
          '工序开工领料确认',
          {
            type: 'warning',
            confirmButtonText: '确认开工并领料',
            cancelButtonText: '取消',
            dangerouslyUseHTMLString: true
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
        <p style="margin: 0; font-weight: 600; color: #1e293b;">
          ${isLastProcess.value ? '确认完成最后一道工序并整单完工？' : `确认完成「${progressForm.currentProcess}」并进入下一道「${nextProcessName.value}」？`}
        </p>
    `
    
    if (!isLastProcess.value && nextProcessName.value) {
      const nextOp = executionOperations.value.find((item) => item.operationName === nextProcessName.value)
      if (nextOp?.materials?.length) {
        htmlContent += `
          <p style="margin: 16px 0 8px 0; font-weight: 600; color: #475569;">下一道「${nextProcessName.value}」首次报工时将领用：</p>
          ${formatMaterialPickHtml(nextOp.materials, progressOrderQty.value)}
        `
      }
    }
    
    htmlContent += `</div>`

    try {
      await ElMessageBox.confirm(htmlContent, confirmTitle, {
        type: 'warning',
        confirmButtonText: confirmBtnText,
        cancelButtonText: '取消',
        dangerouslyUseHTMLString: true
      })
    } catch {
      return
    }
  }

  submittingProgress.value = true
  try {
    const api = await import('@/api/workOrders')
    await api.updateWorkOrderProgress(currentOrder.value.id, {
      progress: progressForm.progress,
      processName: progressForm.currentProcess,
      deviceIds: allowedDevices.value.length
        ? progressForm.deviceIds.map((id) => normalizeDeviceId(id)!).filter(Boolean)
        : undefined,
      deviceId: allowedDevices.value.length ? normalizeDeviceId(progressForm.deviceIds[0]) : undefined,
      remark: progressForm.remark,
      completeCurrentProcess
    })
    ElMessage.success(completeCurrentProcess ? (isLastProcess.value ? '工单已完工' : '本工序已完成，已进入下一道') : '进度已更新')
    progressDrawerVisible.value = false
    loadOrders()
    if (completeCurrentProcess && isLastProcess.value) void appStore.loadWorkshopSummary()
  } catch (error) {
    console.error(error)
    const { resolveErrorMessage } = await import('@/api/request')
    ElMessage.error(resolveErrorMessage(error, '更新进度失败'))
  } finally {
    submittingProgress.value = false
  }
}

async function completeOrder(row: WorkOrderRow) {
  try {
    await ElMessageBox.confirm(`确认将工单 ${row.code} 标记为完工？`, '确认完工', { type: 'warning' })
  } catch {
    return
  }
  actionLoading.value = `complete-${row.id}`
  try {
    const api = await import('@/api/workOrders')
    await api.completeWorkOrder(row.id)
    ElMessage.success('工单已完工')
    loadOrders()
    void appStore.loadWorkshopSummary()
  } catch (error) {
    console.error(error)
    const { resolveErrorMessage } = await import('@/api/request')
    ElMessage.error(resolveErrorMessage(error, '完工操作失败'))
  } finally {
    actionLoading.value = ''
  }
}

function goToExceptions(row: WorkOrderRow) { router.push({ path: '/exceptions', query: { workOrderId: String(row.id), workOrderCode: row.code } }) }

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
            <td style="padding: 8px 10px; color: #64748b; text-align: right;">${unitQty} ${unit}/件</td>
            <td style="padding: 8px 10px; color: #4f46e5; font-weight: 600; text-align: right;">${total} ${unit}</td>
          </tr>
    `
  })
  html += `
        </tbody>
      </table>
    </div>
  `
  return html
}

async function removeOrder(row: WorkOrderRow) {
  const ok = await confirmDelete({
    title: '删除工单',
    message: `确认删除工单「${row.code}」？此操作将永久删除该工单及其关联的工序记录和异常记录，不可恢复！`
  })
  if (!ok) return
  actionLoading.value = `delete-${row.id}`
  try {
    const api = await import('@/api/workOrders')
    await api.deleteWorkOrder(row.id)
    ElMessage.success('工单已删除')
    await loadOrders()
  } catch (error) {
    console.error(error)
    ElMessage.error('删除工单失败')
  } finally {
    actionLoading.value = ''
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.teamId = undefined
  filters.onlyMine = true
  pagination.page = 1
  void loadOrders()
}

async function callWorkOrderApi(methods: string[], payload?: Record<string, any>) { const api = (await import('@/api/workOrders')) as Record<string, any>; for (const method of methods) if (typeof api[method] === 'function') return api[method](payload); return null }

function normalizeList(value: any) { const payload = value?.data ?? value; if (Array.isArray(payload)) return payload; if (Array.isArray(payload?.records)) return payload.records; if (Array.isArray(payload?.list)) return payload.list; if (Array.isArray(payload?.items)) return payload.items; return [] }

function mapWorkOrderRow(item: any): WorkOrderRow {
  const dateStr = item.dueDate ?? item.deadline
  const formattedDate = dateStr ? String(dateStr).replace('T', ' ').substring(0, 16) : '--'
  return {
    id: item.id ?? item.workOrderId,
    code: String(item.code ?? item.workOrderCode ?? item.orderNo ?? '--'),
    planCode: String(item.planCode ?? item.planNo ?? '--'),
    productName: String(item.productName ?? item.product ?? '--'),
    teamName: String(item.teamName ?? item.team?.name ?? '未分配'),
    teamId: item.teamId ?? item.team?.id,
    currentProcess: String(item.currentProcess ?? item.processName ?? '--'),
    progress: Number(item.progress ?? item.completionRate ?? 0),
    priority: normalizePriority(item.priority),
    priorityLabel: priorityLabel(item.priority),
    dueDate: formattedDate,
    status: String(item.status ?? 'pending'),
    overdue: Boolean(item.overdue ?? isOverdue(item.dueDate ?? item.deadline, item.status)),
    remark: String(item.remark ?? '')
  }
}

function isOverdue(dateText?: string, status?: string) { if (!dateText || status === 'done') return false; return new Date(dateText).getTime() < Date.now() }

function formatDeadlineForPicker(value: string) {
  if (!value || value === '--') return ''
  const normalized = value.replace('T', ' ')
  if (normalized.length === 16) return `${normalized}:00`
  return normalized.substring(0, 19)
}

function resetSchedulingInfo() {
  Object.assign(schedulingInfo, {
    teamName: '',
    scheduledStartTime: '',
    estimatedHours: '',
    schedulingReason: ''
  })
}

function loadSchedulingInfo(detail: Record<string, unknown>) {
  Object.assign(schedulingInfo, {
    teamName: String(detail.teamName ?? '').trim(),
    scheduledStartTime: formatDeadlineForPicker(String(detail.scheduledStartTime ?? '')),
    estimatedHours: detail.estimatedHours != null && detail.estimatedHours !== '' ? String(detail.estimatedHours) : '',
    schedulingReason: String(detail.schedulingReason ?? '').trim()
  })
}

</script>



<style scoped>

.view-page { display: flex; flex-direction: column; gap: 16px; }

.toolbar-card { border-radius: 16px; }

.toolbar { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; }

.toolbar__filters, .toolbar__actions { display: flex; gap: 12px; flex-wrap: wrap; }

.toolbar__input { width: 220px; }

.toolbar__select { width: 140px; }

.full-width { width: 100%; }

.scheduling-info-block {
  margin: 4px 0 14px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.scheduling-info-block--detail {
  margin: 0 0 18px;
}

.scheduling-info-block__title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: #4f46e5;
}

.scheduling-info-grid {
  display: flex;
  flex-wrap: nowrap;
  align-items: flex-start;
  gap: 12px 16px;
}

.scheduling-info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 0;
  min-width: 0;
}

.scheduling-info-item:nth-child(2) {
  flex: 1.35 1 0;
}

.scheduling-info-item__label {
  font-size: 12px;
  color: #94a3b8;
}

.scheduling-info-conclusion {
  margin-top: 10px;
}

.scheduling-info-conclusion__label {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.scheduling-info-reason {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
}

.table-pagination {
  display: flex;
  justify-content: flex-start;
  margin-top: 12px;
}

.drawer-meta { display: grid; gap: 10px; padding: 0 0 16px; color: #334155; }

.drawer-form { padding-top: 8px; }

.drawer-footer { display: flex; justify-content: flex-end; gap: 12px; }

.overdue { color: #f5222d; font-weight: 600; }

/* Capsule inputs styling */
.toolbar__input :deep(.el-input__wrapper),
.toolbar__select :deep(.el-input__wrapper) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 4px 16px !important;
  transition: all 0.3s ease !important;
}

.toolbar__input :deep(.el-input__wrapper.is-focus),
.toolbar__select :deep(.el-input__wrapper.is-focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

.action-btn-reset {
  border-radius: 20px !important;
  padding: 8px 20px !important;
  border: 1px solid #e2e8f0 !important;
  transition: all 0.2s ease !important;
}
.action-btn-reset:hover {
  background-color: #f8fafc !important;
  border-color: #cbd5e1 !important;
}

.action-btn-refresh {
  border-radius: 20px !important;
  padding: 8px 20px !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%) !important;
  border: none !important;
  color: white !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
  transition: all 0.2s ease !important;
}
.action-btn-refresh:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.3) !important;
}

/* Action Buttons with Outlines */
.action-btn {
  border-radius: 8px !important;
  font-weight: 500 !important;
  transition: all 0.2s ease !important;
  background: #fff !important;
}

.action-btn--edit {
  border: 1px solid rgba(79, 70, 229, 0.3) !important;
  color: #4f46e5 !important;
}
.action-btn--edit:hover {
  background: rgba(79, 70, 229, 0.05) !important;
  border-color: #4f46e5 !important;
}

.action-btn--view {
  border: 1px solid #e2e8f0 !important;
  color: #64748b !important;
}
.action-btn--view:hover {
  background: #f8fafc !important;
  border-color: #cbd5e1 !important;
  color: #334155 !important;
}

.action-btn--assign {
  border: 1px solid rgba(22, 119, 255, 0.3) !important;
  color: #1677ff !important;
}
.action-btn--assign:hover {
  background: rgba(22, 119, 255, 0.05) !important;
  border-color: #1677ff !important;
}

.action-btn--claim {
  border: 1px solid rgba(250, 140, 22, 0.3) !important;
  color: #fa8c16 !important;
}
.action-btn--claim:hover {
  background: rgba(250, 140, 22, 0.05) !important;
  border-color: #fa8c16 !important;
}

.action-btn--complete {
  border: 1px solid rgba(82, 196, 26, 0.3) !important;
  color: #52c41a !important;
}
.action-btn--complete:hover {
  background: rgba(82, 196, 26, 0.05) !important;
  border-color: #52c41a !important;
}

.action-btn--exception {
  border: 1px solid rgba(245, 34, 45, 0.3) !important;
  color: #f5222d !important;
}
.action-btn--exception:hover {
  background: rgba(245, 34, 45, 0.05) !important;
  border-color: #f5222d !important;
}

.action-btn--delete {
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #ef4444 !important;
}
.action-btn--delete:hover {
  background: rgba(239, 68, 68, 0.05) !important;
  border-color: #ef4444 !important;
}

/* Custom Order Detail Dialog Styles */
.custom-order-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08) !important;
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

/* Custom Slider Styling */
.slider-wrapper-custom {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 4px 0;
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
  padding-bottom: 8px !important;
  font-size: 13px;
}

.custom-input :deep(.el-input__wrapper) {
  border-radius: 10px !important;
  padding: 4px 12px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  background-color: #f8fafc !important;
  transition: all 0.3s ease !important;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

.custom-textarea :deep(.el-textarea__inner) {
  border-radius: 12px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 10px 14px !important;
  transition: all 0.3s ease;
}

.custom-textarea :deep(.el-textarea__inner:focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

.dialog-footer-custom {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 10px 0;
}

.btn-cancel {
  border-radius: 20px !important;
  padding: 8px 22px !important;
  border: 1px solid #e2e8f0 !important;
}

.btn-submit {
  border-radius: 20px !important;
  padding: 8px 22px !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%) !important;
  border: none !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
  font-weight: 600;
}

.btn-submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.3) !important;
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

</style>

