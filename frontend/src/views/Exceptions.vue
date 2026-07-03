<template>

  <div class="view-page">

    <PageHeader title="异常管理" subtitle="员工可上报异常，主管可在同一流程中处理。" />

    <el-card shadow="never" class="toolbar-card">

      <div class="toolbar">

        <div class="toolbar__filters">

          <el-input v-model="filters.keyword" placeholder="搜索异常编号" clearable class="toolbar__input" @keyup.enter="loadExceptions" @clear="loadExceptions" />

          <el-select v-model="filters.type" placeholder="类型" clearable class="toolbar__select" @change="loadExceptions"><el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>

          <el-select v-model="filters.status" placeholder="状态" clearable class="toolbar__select" @change="loadExceptions"><el-option label="待处理" value="open" /><el-option label="处理中" value="processing" /><el-option label="已关闭" value="closed" /></el-select>

          <el-date-picker v-model="filters.dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" class="toolbar__date" @change="loadExceptions" />

        </div>

        <div class="toolbar__actions"><el-button @click="resetFilters">重置</el-button><el-button type="primary" @click="openReportDialog">上报异常</el-button></div>

      </div>

    </el-card>

    <el-card shadow="hover">

      <el-table v-loading="loading" :data="exceptions" stripe border highlight-current-row :header-cell-style="tableHeaderStyle">

        <el-table-column prop="code" label="异常编号" min-width="120" align="center" />

        <el-table-column label="类型" min-width="120" align="center"><template #default="{ row }"><span :class="['exception-type', `exception-type--${row.type}`]">{{ row.typeLabel }}</span></template></el-table-column>

        <el-table-column prop="workOrderCode" label="工单号" min-width="130" align="center" />

        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip align="center" />

        <el-table-column prop="reporterName" label="上报人" min-width="110" align="center" />

        <el-table-column prop="reportedAt" label="上报时间" min-width="160" align="center" />

        <el-table-column label="状态" width="110" align="center"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>

        <el-table-column label="操作" fixed="right" width="250" align="center">
          <template #default="{ row }">
            <el-button v-if="canHandle && row.status !== 'closed'" size="small" class="action-btn action-btn--edit" @click="openHandleDialog(row)">处理</el-button>
            <el-button v-else size="small" class="action-btn action-btn--view" @click="openHandleDialog(row)">查看</el-button>
            <el-button
              v-if="canDelete"
              size="small"
              class="action-btn action-btn--delete"
              :loading="deleteLoading === row.id"
              @click="removeException(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>

        <template #empty><el-empty description="暂无异常记录" /></template>

      </el-table>

      <div class="table-pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          background
          layout="total, prev, pager, next"
          :total="pagination.total"
          @current-change="loadExceptions"
        />
      </div>

    </el-card>

    <el-dialog
      v-model="reportDialogVisible"
      width="580px"
      class="custom-report-dialog"
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom">
          <el-icon class="header-icon"><WarningFilled /></el-icon>
          <span class="header-text">上报生产异常</span>
        </div>
      </template>

      <el-form
        ref="reportFormRef"
        :model="reportForm"
        :rules="reportRules"
        label-position="top"
        class="custom-report-form"
      >
        <el-form-item label="异常类型" prop="type">
          <el-radio-group v-model="reportForm.type" class="report-type-radio-group">
            <el-radio-button label="device">
              <div class="radio-card-content">
                <el-icon><Cpu /></el-icon>
                <span>设备停机</span>
              </div>
            </el-radio-button>
            <el-radio-button label="material">
              <div class="radio-card-content">
                <el-icon><Box /></el-icon>
                <span>缺料</span>
              </div>
            </el-radio-button>
            <el-radio-button label="quality">
              <div class="radio-card-content">
                <el-icon><Warning /></el-icon>
                <span>质量异常</span>
              </div>
            </el-radio-button>
            <el-radio-button label="other">
              <div class="radio-card-content">
                <el-icon><More /></el-icon>
                <span>其他</span>
              </div>
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <div class="form-grid-2">
          <el-form-item label="关联工单" prop="workOrderId">
            <el-select
              v-model="reportForm.workOrderId"
              filterable
              clearable
              placeholder="请选择受影响的工单"
              class="full-width custom-select"
            >
              <el-option
                v-for="item in workOrderOptions"
                :key="item.id"
                :label="item.code"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关联设备 (可选)">
            <el-input
              v-model="reportForm.device"
              placeholder="填写设备名称或编号"
              class="custom-input"
            />
          </el-form-item>
        </div>

        <el-form-item label="发生时间" prop="occurredAt">
          <el-date-picker
            v-model="reportForm.occurredAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择异常发生时间"
            class="full-width custom-datetime-picker"
          />
        </el-form-item>

        <el-form-item label="异常详细描述" prop="description">
          <el-input
            v-model="reportForm.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请详细描述异常发生的情景、现象以及可能受影响的工序（如：A产线主轴温度过高停机保护，已通知维修...）"
            class="custom-textarea"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer-custom">
          <el-button class="btn-cancel" @click="reportDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="submittingReport"
            class="btn-submit"
            @click="submitReport"
          >
            提交上报
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="handleDialogVisible"
      width="600px"
      class="custom-handle-dialog"
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom">
          <el-icon class="header-icon"><WarningFilled /></el-icon>
          <span class="header-text">{{ isViewMode ? '查看异常详情' : '处理异常工单' }} - {{ activeException?.code }}</span>
        </div>
      </template>

      <div v-if="activeException" class="custom-dialog-body">
        <!-- Metadata cards -->
        <div class="meta-grid">
          <div class="meta-item">
            <div class="meta-item__label">
              <el-icon><InfoFilled /></el-icon>
              <span>异常类型</span>
            </div>
            <div class="meta-item__val">
              <span :class="['exception-badge', `exception-badge--${activeException.type}`]">
                {{ activeException.typeLabel }}
              </span>
            </div>
          </div>
          <div class="meta-item">
            <div class="meta-item__label">
              <el-icon><User /></el-icon>
              <span>上报人员</span>
            </div>
            <div class="meta-item__val text-highlight">
              {{ activeException.reporterName }}
            </div>
          </div>
          <div class="meta-item">
            <div class="meta-item__label">
              <el-icon><Clock /></el-icon>
              <span>上报时间</span>
            </div>
            <div class="meta-item__val text-secondary">
              {{ activeException.reportedAt }}
            </div>
          </div>
          <div class="meta-item">
            <div class="meta-item__label">
              <el-icon><Document /></el-icon>
              <span>关联工单</span>
            </div>
            <div class="meta-item__val code-highlight">
              {{ activeException.workOrderCode }}
            </div>
          </div>
        </div>

        <!-- Description -->
        <div class="desc-card">
          <div class="desc-card__title">异常详细描述</div>
          <div class="desc-card__content">
            {{ activeException.description }}
          </div>
        </div>

        <!-- Action Form -->
        <el-form
          ref="handleFormRef"
          :model="handleForm"
          :rules="handleRules"
          label-position="top"
          class="handle-action-form"
          :disabled="isViewMode"
        >
          <el-form-item label="处理措施" prop="measure">
            <el-input
              v-model="handleForm.measure"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="请输入针对该异常所采取的具体处理措施或方案..."
              class="custom-textarea"
            />
          </el-form-item>
          
          <el-form-item label="处理结果" prop="result">
            <el-radio-group v-model="handleForm.result" class="custom-radio-group">
              <el-radio-button label="resolved">
                <div class="radio-content">
                  <el-icon><CircleCheck /></el-icon>
                  <span>已解决 (恢复生产)</span>
                </div>
              </el-radio-button>
              <el-radio-button label="observing">
                <div class="radio-content">
                  <el-icon><View /></el-icon>
                  <span>继续观察</span>
                </div>
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer-custom">
          <el-button v-if="isViewMode" class="btn-cancel" @click="handleDialogVisible = false">确定</el-button>
          <template v-else>
            <el-button class="btn-cancel" @click="handleDialogVisible = false">取消</el-button>
            <el-button
              type="primary"
              :loading="submittingHandle"
              class="btn-submit"
              @click="submitHandle"
            >
              提交处理结果
            </el-button>
          </template>
        </div>
      </template>
    </el-dialog>

  </div>

</template>

<script setup lang="ts">

import { ElMessage, ElMessageBox } from 'element-plus'

import type { FormInstance, FormRules } from 'element-plus'

import { computed, onMounted, reactive, ref } from 'vue'

import PageHeader from '@/components/common/PageHeader.vue'

import StatusTag from '@/components/common/StatusTag.vue'

import { WarningFilled, InfoFilled, User, Clock, Document, CircleCheck, View, Cpu, Box, Warning, More } from '@element-plus/icons-vue'

import { useUserStore } from '@/stores/user'

import { exceptionTypeLabel } from '@/utils/labels'

interface WorkOrderOption { id: string | number; code: string }

interface ExceptionRow { 
  id: string | number; 
  code: string; 
  type: string; 
  typeLabel: string; 
  workOrderCode: string; 
  description: string; 
  reporterName: string; 
  reportedAt: string; 
  status: string;
  handleAction?: string;
  handleResult?: string;
}

const userStore = useUserStore()

const loading = ref(false)

const submittingReport = ref(false)

const submittingHandle = ref(false)
const deleteLoading = ref<string | number | null>(null)

const reportDialogVisible = ref(false)

const handleDialogVisible = ref(false)

const isViewMode = ref(false)

const reportFormRef = ref<FormInstance>()

const handleFormRef = ref<FormInstance>()

const exceptions = ref<ExceptionRow[]>([])

const workOrderOptions = ref<WorkOrderOption[]>([])

const activeException = ref<ExceptionRow | null>(null)

const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }

const canHandle = computed(() => userStore.isAdmin || userStore.isSupervisor)
const canDelete = computed(() => userStore.isAdmin)

const typeOptions = [{ label: '设备停机', value: 'device' }, { label: '缺料', value: 'material' }, { label: '质量异常', value: 'quality' }, { label: '其他', value: 'other' }]

const filters = reactive({ keyword: '', type: '', status: '', dateRange: [] as string[] })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const reportForm = reactive({ type: 'device', workOrderId: '', device: '', occurredAt: '', description: '' })

const handleForm = reactive({ measure: '', result: 'resolved' })

const reportRules: FormRules = { type: [{ required: true, message: '请选择类型', trigger: 'change' }], workOrderId: [{ required: true, message: '请选择工单', trigger: 'change' }], occurredAt: [{ required: true, message: '请选择时间', trigger: 'change' }], description: [{ required: true, message: '请输入描述', trigger: 'blur' }] }

const handleRules: FormRules = { measure: [{ required: true, message: '请输入处理措施', trigger: 'blur' }], result: [{ required: true, message: '请选择处理结果', trigger: 'change' }] }

onMounted(() => { loadExceptions(); loadWorkOrderOptions() })

import { normalizeList } from '@/utils/normalizeList'

async function loadExceptions() {
  loading.value = true
  try {
    const api = (await import('@/api/exceptions')) as Record<string, any>
    const response = await (api.getExceptions?.({
      page: pagination.page,
      size: pagination.size,
      keyword: filters.keyword,
      type: filters.type,
      status: filters.status
    }) ?? api.getExceptionList?.({
      keyword: filters.keyword,
      type: filters.type,
      status: filters.status
    }))
    let rawList = normalizeList(response)
    pagination.total = Number((response as any)?.total ?? rawList.length)
    
    // Frontend date range filter fallback
    if (filters.dateRange && filters.dateRange.length === 2) {
      const [startStr, endStr] = filters.dateRange
      const startTime = new Date(startStr + 'T00:00:00').getTime()
      const endTime = new Date(endStr + 'T23:59:59').getTime()
      rawList = rawList.filter((item: any) => {
        const timeStr = item.occurTime ?? item.reportedAt
        if (!timeStr) return false
        const t = new Date(timeStr.replace(' ', 'T')).getTime()
        return t >= startTime && t <= endTime
      })
    }

    exceptions.value = rawList.map((item: any) => {
      const type = String(item.eventType ?? item.type ?? 'other')
      return {
        id: item.id ?? item.exceptionId ?? item.code,
        code: String(item.eventNo ?? item.code ?? '--'),
        type,
        typeLabel: String(item.typeLabel ?? exceptionTypeLabel(type)),
        workOrderCode: String(item.workOrderNo ?? item.workOrderCode ?? '--'),
        description: String(item.description ?? '--'),
        reporterName: String(item.reporterName ?? '--'),
        reportedAt: String(item.occurTime ?? item.reportedAt ?? '--'),
        status: String(item.status ?? 'open'),
        handleAction: item.handleAction ?? null,
        handleResult: item.handleResult ?? null
      }
    })
  } catch (error) {
    console.error(error)
    ElMessage.error('加载异常记录失败')
  } finally {
    loading.value = false
  }
}

async function loadWorkOrderOptions() {
  try {
    const { getWorkOrders } = await import('@/api/workOrders')
    const response = await getWorkOrders({ page: 1, size: 200 })
    workOrderOptions.value = normalizeList(response).map((item: any) => ({
      id: item.id ?? item.workOrderId ?? item.code,
      code: String(item.code ?? item.workOrderCode ?? item.orderNo ?? '--')
    }))
  } catch (error) {
    console.error(error)
  }
}

function openReportDialog() { Object.assign(reportForm, { type: 'device', workOrderId: '', device: '', occurredAt: currentDateTime(), description: '' }); reportDialogVisible.value = true }

async function submitReport() {
  const valid = await reportFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submittingReport.value = true
  try {
    const api = (await import('@/api/exceptions')) as Record<string, any>
    const payload = {
      eventType: reportForm.type,
      workOrderId: Number(reportForm.workOrderId),
      occurTime: reportForm.occurredAt,
      description: reportForm.description.trim()
    }
    await (api.reportException?.(payload) ?? api.createException?.(payload))
    ElMessage.success('异常已上报')
    reportDialogVisible.value = false
    await loadExceptions()
  } catch (error) {
    console.error(error)
    ElMessage.error(error instanceof Error ? error.message : '上报异常失败')
  } finally {
    submittingReport.value = false
  }
}

function openHandleDialog(row: ExceptionRow) {
  activeException.value = row
  if (row.status === 'closed') {
    isViewMode.value = true
    Object.assign(handleForm, {
      measure: row.handleAction ?? '',
      result: row.handleResult?.includes('恢复') || row.handleResult === 'resolved' ? 'resolved' : 'observing'
    })
  } else {
    isViewMode.value = false
    Object.assign(handleForm, { measure: '', result: 'resolved' })
  }
  handleDialogVisible.value = true
}

async function submitHandle() {
  const valid = await handleFormRef.value?.validate().catch(() => false)
  if (!valid || !activeException.value) return
  submittingHandle.value = true
  try {
    const api = (await import('@/api/exceptions')) as Record<string, any>
    const payload = {
      handleAction: handleForm.measure,
      handleResult: handleForm.result === 'resolved' ? '已解决并恢复生产' : '继续观察'
    }
    await (api.handleException?.(activeException.value.id, payload) ?? api.processException?.(activeException.value.id, payload));
    ElMessage.success('异常已处理')
    handleDialogVisible.value = false
    loadExceptions()
  } catch (error) {
    console.error(error)
    ElMessage.error('处理异常失败')
  } finally {
    submittingHandle.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.type = ''
  filters.status = ''
  filters.dateRange = []
  pagination.page = 1
  loadExceptions()
}

async function removeException(row: ExceptionRow) {
  try {
    await ElMessageBox.confirm(
      `确认删除异常记录 ${row.code}？删除后不可恢复。${row.status !== 'closed' ? '若该工单无其他未处理异常，将自动恢复工单状态。' : ''}`,
      '删除异常记录',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  deleteLoading.value = row.id
  try {
    const { deleteException } = await import('@/api/exceptions')
    await deleteException(row.id)
    ElMessage.success('异常记录已删除')
    await loadExceptions()
  } catch (error) {
    console.error(error)
    ElMessage.error(error instanceof Error ? error.message : '删除异常记录失败')
  } finally {
    deleteLoading.value = null
  }
}

function currentDateTime() {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}
</script>

<style scoped>
.view-page { display: flex; flex-direction: column; gap: 16px; }

.toolbar-card { border-radius: 16px; }

.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }

.toolbar__filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }

.toolbar__actions { display: flex; gap: 10px; align-items: center; }

.toolbar__input { width: 160px; }

.toolbar__select { width: 110px; }

.toolbar__filters :deep(.el-date-editor) {
  width: 280px !important;
}

.table-pagination {
  display: flex;
  justify-content: flex-start;
  margin-top: 12px;
}

.full-width { width: 100%; }

.exception-type { font-weight: 600; }

.exception-type--device { color: #f5222d; }

.exception-type--material { color: #fa8c16; }

.exception-type--quality { color: #faad14; }

.exception-type--other { color: #94a3b8; }

.handle-meta { display: grid; gap: 10px; margin-bottom: 16px; color: #334155; }

.handle-meta__desc { padding: 12px; background: #f8fafc; border-radius: 12px; }

/* Custom capsule input borders */
.toolbar__input :deep(.el-input__wrapper),
.toolbar__select :deep(.el-input__wrapper),
.toolbar__filters :deep(.el-date-editor) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 4px 16px !important;
  transition: all 0.3s ease !important;
}

.toolbar__input :deep(.el-input__wrapper.is-focus),
.toolbar__select :deep(.el-input__wrapper.is-focus),
.toolbar__filters :deep(.el-date-editor.is-active) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

.toolbar__actions :deep(.el-button) {
  border-radius: 20px !important;
  padding: 8px 20px !important;
  transition: all 0.2s ease !important;
}

.toolbar__actions :deep(.el-button--primary) {
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%) !important;
  border: none !important;
  color: white !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
}

.toolbar__actions :deep(.el-button--primary:hover) {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.3) !important;
}

/* Table Action Buttons with Borders */
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

.action-btn--delete {
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #ef4444 !important;
}
.action-btn--delete:hover {
  background: rgba(239, 68, 68, 0.05) !important;
  border-color: #ef4444 !important;
}

/* Premium Dialog Styles */
.custom-handle-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08) !important;
}

.custom-handle-dialog :deep(.el-dialog__header) {
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
  color: #fa8c16;
}

.header-text {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.custom-dialog-body {
  padding: 10px 4px;
}

.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 18px;
}

.meta-item {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-item__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}

.meta-item__val {
  font-size: 14px;
  font-weight: 500;
}

.exception-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.exception-badge--device { background: #fef2f2; color: #ef4444; }
.exception-badge--material { background: #fffbeb; color: #d97706; }
.exception-badge--quality { background: #fffbeb; color: #faad14; }
.exception-badge--other { background: #f1f5f9; color: #64748b; }

.text-highlight {
  color: #0f172a;
}

.text-secondary {
  color: #475569;
}

.code-highlight {
  font-family: monospace;
  font-weight: 600;
  color: #4f46e5;
}

.desc-card {
  background: rgba(79, 70, 229, 0.03);
  border: 1px solid rgba(79, 70, 229, 0.1);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 20px;
}

.desc-card__title {
  font-size: 13px;
  font-weight: 600;
  color: #4f46e5;
  margin-bottom: 6px;
}

.desc-card__content {
  font-size: 14px;
  color: #334155;
  line-height: 1.5;
}

.handle-action-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #334155;
  padding-bottom: 6px !important;
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

/* Custom radio option button cards */
.custom-radio-group {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}

.custom-radio-group :deep(.el-radio-button) {
  width: 100%;
}

.custom-radio-group :deep(.el-radio-button__inner) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  border: 1px solid #e2e8f0 !important;
  border-radius: 12px !important;
  background: #fff !important;
  padding: 14px 16px !important;
  box-shadow: none !important;
  transition: all 0.2s ease;
}

.custom-radio-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: #4f46e5 !important;
  background: rgba(79, 70, 229, 0.05) !important;
  color: #4f46e5 !important;
  font-weight: 600;
}

.radio-content {
  display: flex;
  align-items: center;
  gap: 8px;
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

/* Custom Report Dialog Styles */
.custom-report-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08) !important;
}

.custom-report-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #f1f5f9;
  background-color: #f8fafc;
}

.custom-report-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #1e293b;
  padding-bottom: 8px !important;
  font-size: 13px;
}

.report-type-radio-group {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  width: 100%;
}

.report-type-radio-group :deep(.el-radio-button) {
  width: 100%;
}

.report-type-radio-group :deep(.el-radio-button__inner) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  border: 1px solid #e2e8f0 !important;
  border-radius: 12px !important;
  background: #fff !important;
  padding: 16px 12px !important;
  box-shadow: none !important;
  transition: all 0.2s ease;
}

.report-type-radio-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: #4f46e5 !important;
  background: rgba(79, 70, 229, 0.05) !important;
  color: #4f46e5 !important;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.08) !important;
}

.radio-card-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.radio-card-content .el-icon {
  font-size: 20px;
  color: #64748b;
  transition: color 0.2s ease;
}

.report-type-radio-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) .radio-card-content .el-icon {
  color: #4f46e5;
}

.form-grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  width: 100%;
}

.custom-select :deep(.el-input__wrapper),
.custom-input :deep(.el-input__wrapper),
.custom-datetime-picker :deep(.el-input__wrapper) {
  border-radius: 10px !important;
  padding: 4px 12px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  background-color: #f8fafc !important;
  transition: all 0.3s ease !important;
}

.custom-select :deep(.el-input__wrapper.is-focus),
.custom-input :deep(.el-input__wrapper.is-focus),
.custom-datetime-picker :deep(.el-input__wrapper.is-focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}
</style>
