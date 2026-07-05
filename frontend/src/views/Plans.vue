<template>

  <div v-if="isSupervisor" class="view-page">

    <PageHeader title="生产计划" subtitle="创建、编辑、下发并跟踪计划执行。" />

    <el-card shadow="never" class="toolbar-card">

      <div class="toolbar">

        <div class="toolbar__filters">

          <el-input v-model="filters.keyword" placeholder="搜索计划编号或产品" clearable class="toolbar__input" @keyup.enter="loadPlans" @clear="loadPlans" />

          <el-select v-model="filters.status" placeholder="状态" clearable class="toolbar__select" @change="loadPlans">

            <el-option label="草稿" value="draft" />

            <el-option label="已下发" value="released" />

            <el-option label="已完成" value="done" />

          </el-select>

          <el-date-picker v-model="filters.dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" class="toolbar__date" @change="loadPlans" />

        </div>

        <div class="toolbar__actions">
          <el-button class="action-btn-reset" @click="resetFilters">重置</el-button>
          <el-button type="primary" class="action-btn-refresh" @click="openCreate">新建计划</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="hover">
      <el-table v-loading="loading" :data="plans" stripe border highlight-current-row :header-cell-style="tableHeaderStyle">
        <el-table-column label="计划编号" min-width="140" align="center">
          <template #default="{ row }">
            <el-button link type="primary" style="font-weight: 600;" @click="goDetail(row)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品" min-width="180" show-overflow-tooltip align="center" />
        <el-table-column prop="quantity" label="数量" min-width="100" align="center">
          <template #default="{ row }">{{ row.quantity }} 件</template>
        </el-table-column>
        <el-table-column prop="planDate" label="计划日期" min-width="120" align="center" />
        <el-table-column prop="workOrderCount" label="工单数" min-width="100" align="center" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" min-width="100" align="center" />
        <el-table-column label="操作" fixed="right" width="280" align="center">
          <template #default="{ row }">
            <el-button size="small" class="action-btn action-btn--detail" @click="goDetail(row)">详情</el-button>
            <el-button size="small" class="action-btn action-btn--edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'draft'" size="small" class="action-btn action-btn--release" :loading="actionLoading === row.id" @click="releasePlan(row)">下发</el-button>
            <el-button size="small" class="action-btn action-btn--delete" :loading="actionLoading === `delete-${row.id}`" @click="removePlan(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无生产计划">
            <el-button type="primary" class="action-btn-refresh" @click="openCreate">创建第一个计划</el-button>
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
          @current-change="loadPlans"
        />
      </div>

    </el-card>

    <el-dialog
      v-model="dialogVisible"
      width="540px"
      class="custom-plan-dialog"
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom">
          <el-icon class="header-icon"><Calendar /></el-icon>
          <span class="header-text">{{ dialogMode === 'create' ? '新建生产计划' : '编辑生产计划' }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="formModel"
        :rules="rules"
        label-position="top"
        class="custom-plan-form"
      >
        <el-form-item label="产品" prop="productId">
          <ProductSelect
            v-model="formModel.productId"
            placeholder="选择产品主数据"
            @change="onProductChange"
          />
        </el-form-item>

        <div class="form-grid-2">
          <el-form-item label="计划生产数量" prop="quantity">
            <el-input-number
              v-model="formModel.quantity"
              :min="1"
              :step="1"
              class="full-width custom-number-input"
            />
          </el-form-item>
          <el-form-item label="计划日期" prop="planDate">
            <el-date-picker
              v-model="formModel.planDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择计划生产日期"
              class="full-width custom-date-picker"
            />
          </el-form-item>
        </div>

        <el-form-item label="备注说明">
          <el-input
            v-model="formModel.remark"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请输入补充备注信息（可选）"
            class="custom-textarea"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer-custom">
          <el-button class="btn-cancel" @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" class="btn-submit" @click="submitForm">
            {{ dialogMode === 'create' ? '立即创建' : '保存修改' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="releasePreviewVisible" title="下发预览" width="560px">
      <el-alert v-if="releasePreview?.bomWarning" :title="releasePreview.bomWarning" type="warning" show-icon :closable="false" class="preview-alert" />
      <p v-if="releasePreview" class="preview-summary">
        计划数量 <strong>{{ releasePreview.plan.planQty }}</strong> 件，按每单
        <strong>{{ releasePreview.splitQty }}</strong> 件拆分，将生成
        <strong>{{ releasePreview.workOrderCount }}</strong> 张工单：
      </p>
      <el-table
        v-if="releasePreview"
        :data="releasePreview.workOrders"
        border
        size="small"
        stripe
        class="preview-table"
        :header-cell-style="tableHeaderStyle"
      >
        <el-table-column prop="batchNo" label="批次" width="70" align="center" />
        <el-table-column prop="productName" label="产品" min-width="140" align="center" />
        <el-table-column prop="quantity" label="数量" width="90" align="center">
          <template #default="{ row }">{{ row.quantity }} 件</template>
        </el-table-column>
        <el-table-column prop="deadline" label="交期" min-width="150" align="center" />
      </el-table>
      <template #footer>
        <el-button @click="releasePreviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading !== ''" @click="confirmRelease">确认下发</el-button>
      </template>
    </el-dialog>

  </div>

  <Forbidden v-else />

</template>



<script setup lang="ts">

import { ElMessage, ElMessageBox } from 'element-plus'

import type { FormInstance, FormRules } from 'element-plus'

import { computed, onMounted, reactive, ref } from 'vue'

import { useRouter } from 'vue-router'

import { Calendar } from '@element-plus/icons-vue'

import Forbidden from './Forbidden.vue'

import PageHeader from '@/components/common/PageHeader.vue'

import StatusTag from '@/components/common/StatusTag.vue'
import ProductSelect from '@/components/common/ProductSelect.vue'

import { useUserStore } from '@/stores/user'
import { confirmDelete } from '@/utils/confirmDelete'

interface PlanRow { id: string | number; code: string; productName: string; quantity: number; planDate: string; workOrderCount: number; status: string; creatorName: string; remark?: string }

const userStore = useUserStore()

const router = useRouter()

const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }

const loading = ref(false)

const saving = ref(false)

const actionLoading = ref<string | number>('')

const plans = ref<PlanRow[]>([])

const dialogVisible = ref(false)

const dialogMode = ref<'create' | 'edit'>('create')

const editingId = ref<string | number | null>(null)

const releasePreviewVisible = ref(false)
const releasePreview = ref<import('@/api/plans').ReleasePreview | null>(null)
const releasingPlanId = ref<string | number | null>(null)

const formRef = ref<FormInstance>()

const isSupervisor = computed(() => userStore.isAdmin || userStore.isSupervisor)

const filters = reactive({ keyword: '', status: '', dateRange: [] as string[] })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const formModel = reactive({ productId: undefined as number | string | undefined, productName: '', quantity: 1, planDate: '', remark: '' })

const rules: FormRules = { productId: [{ required: true, message: '请选择产品', trigger: 'change' }], quantity: [{ required: true, message: '请输入数量', trigger: 'change' }], planDate: [{ required: true, message: '请选择日期', trigger: 'change' }, { validator: (_, value, callback) => { if (!value) return callback(); if (value < currentDate()) callback(new Error('计划日期不能早于今天')); else callback() }, trigger: 'change' }] }

function onProductChange(payload: { productId?: number | string; productName: string }) {
  formModel.productId = payload.productId
  formModel.productName = payload.productName
}

onMounted(() => { if (isSupervisor.value) loadPlans() })

async function loadPlans() {
  loading.value = true
  try {
    const api = await import('@/api/plans')
    const response = await api.getPlans({
      keyword: filters.keyword,
      status: filters.status,
      page: pagination.page,
      size: pagination.size
    })
    
    let rawList = normalizeList(response)
    
    // Front-end date range filter fallback
    if (filters.dateRange && filters.dateRange.length === 2) {
      const [startStr, endStr] = filters.dateRange
      const startTime = new Date(startStr + 'T00:00:00').getTime()
      const endTime = new Date(endStr + 'T23:59:59').getTime()
      rawList = rawList.filter((item: any) => {
        const dateStr = item.planDate ?? item.date
        if (!dateStr) return false
        const t = new Date(dateStr.replace(' ', 'T')).getTime()
        return t >= startTime && t <= endTime
      })
    }
    
    pagination.total = Number((response as any)?.total ?? 0)
    plans.value = rawList.map((item: any) => ({
      id: item.id ?? item.planId ?? item.code,
      code: String(item.code ?? item.planCode ?? item.planNo ?? '--'),
      productName: String(item.productName ?? item.product ?? '--'),
      quantity: Number(item.quantity ?? item.planQty ?? 0),
      planDate: String(item.planDate ?? item.date ?? '--'),
      workOrderCount: Number(item.workOrderCount ?? item.generatedWorkOrders ?? 0),
      status: String(item.status ?? 'draft'),
      creatorName: String(item.creatorName ?? item.createdByName ?? '--'),
      remark: String(item.remark ?? '')
    }))
  } catch (error) {
    console.error(error)
    ElMessage.error('加载计划失败')
  } finally {
    loading.value = false
  }
}

function openCreate() { dialogMode.value = 'create'; editingId.value = null; Object.assign(formModel, { productId: undefined, productName: '', quantity: 1, planDate: currentDate(), remark: '' }); dialogVisible.value = true }

function goDetail(row: PlanRow) {
  router.push(`/plans/${row.id}`)
}

function openEdit(row: PlanRow) { dialogMode.value = 'edit'; editingId.value = row.id; Object.assign(formModel, { productName: row.productName, quantity: row.quantity, planDate: row.planDate, remark: row.remark ?? '' }); dialogVisible.value = true }

async function submitForm() { const valid = await formRef.value?.validate().catch(() => false); if (!valid) return; saving.value = true; try { const api = await import('@/api/plans'); const payload = { productId: formModel.productId, productName: formModel.productName, planQty: formModel.quantity, planDate: formModel.planDate, remark: formModel.remark }; if (dialogMode.value === 'create') { await api.createPlan(payload); ElMessage.success('计划已创建') } else if (editingId.value != null) { await api.updatePlan(editingId.value, payload); ElMessage.success('计划已更新') } dialogVisible.value = false; loadPlans() } catch (error) { console.error(error); ElMessage.error('保存计划失败') } finally { saving.value = false } }

async function releasePlan(row: PlanRow) {
  releasingPlanId.value = row.id
  try {
    const api = await import('@/api/plans')
    releasePreview.value = await api.previewPlanRelease(row.id)
    releasePreviewVisible.value = true
  } catch (error) {
    console.error(error)
    ElMessage.error('加载下发预览失败')
  }
}

async function confirmRelease() {
  if (releasingPlanId.value == null) return
  actionLoading.value = releasingPlanId.value
  try {
    const api = await import('@/api/plans')
    const result = await api.releasePlan(releasingPlanId.value) as {
      generatedWorkOrders?: Array<{ orderNo?: string }>
      generatedWorkOrder?: { orderNo?: string }
    }
    const orders = result?.generatedWorkOrders ?? []
    const count = orders.length || (result?.generatedWorkOrder ? 1 : 0)
    ElMessage.success(count > 1 ? `计划已下发，已生成 ${count} 张工单` : `计划已下发，已生成工单 ${orders[0]?.orderNo ?? result?.generatedWorkOrder?.orderNo ?? ''}`)
    releasePreviewVisible.value = false
    await loadPlans()
  } catch (error) {
    console.error(error)
    const message = error instanceof Error ? error.message : '下发计划失败'
    ElMessage.error(message.includes('Duplicate') || message.includes('uk_work_order_no')
      ? '下发失败：工单号冲突，请重启后端后重试'
      : message.length > 80 ? '下发计划失败，请查看控制台或联系管理员' : message)
  } finally {
    actionLoading.value = ''
    releasingPlanId.value = null
  }
}

async function removePlan(row: PlanRow) {
  const ok = await confirmDelete({
    title: '删除计划',
    message: `确认删除计划「${row.code}」？此操作不可恢复。`
  })
  if (!ok) return
  actionLoading.value = `delete-${row.id}`
  try {
    const api = await import('@/api/plans')
    await api.deletePlan(row.id)
    ElMessage.success('计划已删除')
    loadPlans()
  } catch (error) {
    console.error(error)
    ElMessage.error('删除计划失败')
  } finally {
    actionLoading.value = ''
  }
}

function resetFilters() { filters.keyword = ''; filters.status = ''; filters.dateRange = []; pagination.page = 1; loadPlans() }

async function callPlansApi(methods: string[], payload?: Record<string, any>) { const api = (await import('@/api/plans')) as Record<string, any>; for (const method of methods) if (typeof api[method] === 'function') return api[method](payload); return null }

function normalizeList(value: any) { const payload = value?.data ?? value; if (Array.isArray(payload)) return payload; if (Array.isArray(payload?.records)) return payload.records; if (Array.isArray(payload?.list)) return payload.list; if (Array.isArray(payload?.items)) return payload.items; return [] }

function currentDate() { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}` }

</script>



<style scoped>

.view-page { display: flex; flex-direction: column; gap: 16px; }

.toolbar-card { border-radius: 16px; }

.toolbar { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; }

.toolbar__filters, .toolbar__actions { display: flex; gap: 12px; flex-wrap: wrap; }

.toolbar__input { width: 240px; }

.toolbar__select { width: 140px; }

.toolbar__filters :deep(.el-date-editor) { width: 280px !important; }

.dialog-footer { display: flex; justify-content: flex-end; }

.full-width { width: 100%; }

.table-pagination { display: flex; justify-content: flex-start; margin-top: 12px; }

/* Capsule inputs styling */
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

.action-btn--detail {
  border: 1px solid rgba(59, 130, 246, 0.3) !important;
  color: #3b82f6 !important;
}
.action-btn--detail:hover {
  background: rgba(59, 130, 246, 0.05) !important;
  border-color: #3b82f6 !important;
}

.action-btn--edit {
  border: 1px solid rgba(79, 70, 229, 0.3) !important;
  color: #4f46e5 !important;
}
.action-btn--edit:hover {
  background: rgba(79, 70, 229, 0.05) !important;
  border-color: #4f46e5 !important;
}

.action-btn--release {
  border: 1px solid rgba(250, 140, 22, 0.3) !important;
  color: #fa8c16 !important;
}
.action-btn--release:hover {
  background: rgba(250, 140, 22, 0.05) !important;
  border-color: #fa8c16 !important;
}

.action-btn--delete {
  border: 1px solid rgba(245, 34, 45, 0.3) !important;
  color: #f5222d !important;
}
.action-btn--delete:hover {
  background: rgba(245, 34, 45, 0.05) !important;
  border-color: #f5222d !important;
}

/* Custom Plan Dialog Styles */
.custom-plan-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08) !important;
}

.custom-plan-dialog :deep(.el-dialog__header) {
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

.custom-plan-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #1e293b;
  padding-bottom: 8px !important;
  font-size: 13px;
}

.form-grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  width: 100%;
}

.custom-input :deep(.el-input__wrapper),
.custom-select :deep(.el-input__wrapper),
.custom-date-picker :deep(.el-input__wrapper) {
  border-radius: 20px !important;
  padding: 6px 18px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  background-color: #f8fafc !important;
  transition: all 0.3s ease !important;
}

.custom-input :deep(.el-input__wrapper.is-focus),
.custom-select :deep(.el-input__wrapper.is-focus),
.custom-date-picker :deep(.el-input__wrapper.is-focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

.custom-date-picker {
  width: 100% !important;
}

.custom-number-input :deep(.el-input__inner) {
  padding-left: 48px !important;
  padding-right: 48px !important;
}

.custom-textarea :deep(.el-textarea__inner) {
  border-radius: 16px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 12px 18px !important;
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

.preview-table :deep(.el-table__header .cell),
.preview-table :deep(.el-table__body .cell) {
  text-align: center;
}
</style>

