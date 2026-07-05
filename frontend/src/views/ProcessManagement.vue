<template>
  <div class="view-page">
    <PageHeader title="工艺管理" subtitle="维护工艺路线与工序，支持 SOP、设备/物料绑定与审批发布。" />

    <el-card shadow="hover" class="main-card">
      <!-- 搜索过滤与操作栏 -->
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索编号/名称/适用产品"
            clearable
            class="toolbar__input"
            @keyup.enter="loadRoutes"
          />
        </div>
        <div class="toolbar__actions">
          <el-button class="action-btn-reset" @click="resetFilters">重置</el-button>
          <el-button type="primary" class="action-btn-create" @click="openCreate">新建工艺</el-button>
          <el-button type="primary" class="action-btn-refresh" @click="loadRoutes">刷新</el-button>
        </div>
      </div>

      <!-- 表格数据 -->
      <el-table v-loading="loading" :data="pagedRoutes" stripe border :header-cell-style="tableHeaderStyle" class="main-table">
        <el-table-column prop="routeCode" label="工艺编号" min-width="120" align="center" />
        <el-table-column prop="routeName" label="工艺名称" min-width="140" align="center" />
        <el-table-column prop="productName" label="适用产品" min-width="120" align="center">
          <template #default="{ row }">{{ row.productName || '通用' }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column label="审批状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" size="small" type="success">默认</el-tag>
            <el-tag v-else :type="routeStatusType(row.status)" size="small">{{ routeStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalStandardHours" label="总工时(h)" width="100" align="center">
          <template #default="{ row }">{{ row.totalStandardHours ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="工序数" width="80" align="center">
          <template #default="{ row }">{{ row.operations?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="200" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button size="small" class="action-btn-pill" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" class="action-btn-pill" @click="openDetailDrawer(row)">工序配置</el-button>
              <el-dropdown trigger="click" popper-class="route-action-dropdown" @command="(cmd: any) => handleCommand(cmd, row)">
                <el-button size="small" class="action-btn-pill more-btn">
                  更多 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="canSubmitRoute(row)" command="submit">提交审批</el-dropdown-item>
                    <el-dropdown-item v-if="canPublishRoute(row)" command="publish">直接发布</el-dropdown-item>
                    <el-dropdown-item v-if="canApproveRoute(row)" command="approve">审批通过</el-dropdown-item>
                    <el-dropdown-item v-if="canApproveRoute(row)" command="reject" class="route-action-dropdown__item--danger">驳回</el-dropdown-item>
                    <el-dropdown-item command="copy">复制</el-dropdown-item>
                    <el-dropdown-item v-if="!row.isDefault && row.status === 'published'" command="setDefault">设默认</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'published' || row.status === 'disabled'" command="toggle">
                      {{ row.enabled === false ? '启用' : '停用' }}
                    </el-dropdown-item>
                    <el-dropdown-item v-if="!row.isDefault" command="delete" divided class="route-action-dropdown__item--danger">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
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

    <!-- 工序配置与工艺设计弹窗（同页面弹窗展示） -->
    <el-dialog
      v-model="drawerVisible"
      title="工序配置与工艺设计"
      width="1100px"
      destroy-on-close
    >
      <ProcessRouteDetail :id="selectedRouteId" @close="drawerVisible = false" @save-success="loadRoutes" />
    </el-dialog>

    <!-- 弹窗表单：新建/编辑工艺基础信息 -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建工艺' : '编辑工艺'" width="540px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="工艺编号">
          <el-input v-model="form.routeCode" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="工艺名称" prop="routeName">
          <el-input v-model="form.routeName" placeholder="请输入工艺名称" />
        </el-form-item>
        <el-form-item label="适用产品">
          <ProductSelect
            v-model="form.productId"
            placeholder="留空表示通用工艺"
            @change="onProductChange"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="action-btn-reset" @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" class="action-btn-refresh" :loading="saving" @click="handleSubmit">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import ProductSelect from '@/components/common/ProductSelect.vue'
import ProcessRouteDetail from './ProcessRouteDetail.vue'
import { confirmDelete } from '@/utils/confirmDelete'
import { useUserStore } from '@/stores/user'
import {
  approveProcessRoute, buildOperationPayload, copyProcessRoute, deleteProcessRoute, getProcessRoute,
  getProcessRoutes, rejectProcessRoute, routeStatusLabel, routeStatusType, submitProcessRoute,
  setDefaultProcessRoute, toggleProcessRoute, type ProcessRoute,
  createProcessRoute, updateProcessRoute
} from '@/api/processRoutes'

const router = useRouter()
const userStore = useUserStore()
const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600', textAlign: 'center' as const }
const loading = ref(false)
const routes = ref<ProcessRoute[]>([])

// 抽屉状态
const drawerVisible = ref(false)
const selectedRouteId = ref<string | number>('')

function openDetailDrawer(row: ProcessRoute) {
  selectedRouteId.value = row.id
  drawerVisible.value = true
}

// 筛选过滤
const filters = ref({
  keyword: ''
})

const pagination = reactive({ page: 1, size: 10, total: 0 })

const filteredRoutes = computed(() => {
  const kw = filters.value.keyword.trim().toLowerCase()
  if (!kw) return routes.value
  return routes.value.filter(
    (item) =>
      (item.routeCode && item.routeCode.toLowerCase().includes(kw)) ||
      (item.routeName && item.routeName.toLowerCase().includes(kw)) ||
      (item.productName && item.productName.toLowerCase().includes(kw))
  )
})

const pagedRoutes = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredRoutes.value.slice(start, start + pagination.size)
})

watch(
  () => filteredRoutes.value.length,
  (count) => {
    pagination.total = count
    if ((pagination.page - 1) * pagination.size >= count && pagination.page > 1) {
      pagination.page = 1
    }
  },
  { immediate: true }
)

watch(() => filters.value.keyword, () => {
  pagination.page = 1
})

function resetFilters() {
  filters.value.keyword = ''
  pagination.page = 1
}

// 弹窗表单状态
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref()
const saving = ref(false)

const form = ref({
  id: '',
  routeCode: '',
  routeName: '',
  productId: undefined as number | string | undefined,
  productName: '',
  remark: ''
})

function onProductChange(payload: { productId?: number | string; productName: string }) {
  form.value.productId = payload.productId
  form.value.productName = payload.productName
}

const rules = {
  routeName: [
    { required: true, message: '请输入工艺名称', trigger: 'blur' }
  ]
}

function canSubmitRoute(row: ProcessRoute) {
  return row.status === 'draft' || row.status === 'rejected'
}

function canPublishRoute(row: ProcessRoute) {
  return canSubmitRoute(row) && (userStore.fullAccess || userStore.canAccessPermission('工艺审批'))
}

function canApproveRoute(row: ProcessRoute) {
  return row.status === 'pending_approval' && (userStore.fullAccess || userStore.canAccessPermission('工艺审批'))
}

async function loadRoutes() {
  loading.value = true
  try {
    routes.value = await getProcessRoutes()
  } catch (error) {
    console.error(error)
    ElMessage.error('加载工艺列表失败')
  } finally {
    loading.value = false
  }
}

function handleCommand(command: string, row: ProcessRoute) {
  if (command === 'copy') {
    handleCopy(row)
  } else if (command === 'submit') {
    void handleSubmitRoute(row)
  } else if (command === 'publish') {
    void handlePublishRoute(row)
  } else if (command === 'approve') {
    void handleApproveRoute(row)
  } else if (command === 'reject') {
    void handleRejectRoute(row)
  } else if (command === 'setDefault') {
    handleSetDefault(row)
  } else if (command === 'toggle') {
    handleToggle(row)
  } else if (command === 'delete') {
    handleDelete(row)
  }
}

function goDetail(row: ProcessRoute) {
  router.push(`/process-management/${row.id}`)
}

function openCreate() {
  dialogMode.value = 'create'
  form.value = {
    id: '',
    routeCode: '',
    routeName: '',
    productId: undefined,
    productName: '',
    remark: ''
  }
  dialogVisible.value = true
}

function openEdit(row: ProcessRoute) {
  dialogMode.value = 'edit'
  form.value = {
    id: String(row.id),
    routeCode: row.routeCode || '',
    routeName: row.routeName,
    productId: row.productId,
    productName: row.productName || '',
    remark: row.remark || ''
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    saving.value = true
    try {
      if (dialogMode.value === 'create') {
        const created = await createProcessRoute({
          routeCode: form.value.routeCode || undefined,
          routeName: form.value.routeName,
          productId: form.value.productId,
          productName: form.value.productName || undefined,
          remark: form.value.remark || undefined
        })
        ElMessage.success('新建工艺成功')
        dialogVisible.value = false
        router.push(`/process-management/${created.id}`)
      } else {
        await updateProcessRoute(form.value.id, {
          routeCode: form.value.routeCode || undefined,
          routeName: form.value.routeName,
          productId: form.value.productId,
          productName: form.value.productName || undefined,
          remark: form.value.remark || undefined
        })
        ElMessage.success('更新工艺成功')
      }
      if (dialogMode.value !== 'create') {
        dialogVisible.value = false
        await loadRoutes()
      }
    } catch (error) {
      console.error(error)
      ElMessage.error(dialogMode.value === 'create' ? '新建失败' : '更新失败')
    } finally {
      saving.value = false
    }
  })
}

async function handleCopy(row: ProcessRoute) {
  try {
    await copyProcessRoute(row.id)
    ElMessage.success('工艺已复制为草稿')
    await loadRoutes()
  } catch (error) {
    console.error(error)
    ElMessage.error('复制失败')
  }
}

async function handleSubmitRoute(row: ProcessRoute) {
  try {
    await ElMessageBox.confirm(`确认提交工艺「${row.routeName}」审批？`, '提交审批', { type: 'warning' })
    await submitProcessRoute(row.id)
    ElMessage.success('已提交审批')
    await loadRoutes()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
      ElMessage.error('提交失败')
    }
  }
}

async function handlePublishRoute(row: ProcessRoute) {
  try {
    await ElMessageBox.confirm(`确认直接发布工艺「${row.routeName}」？`, '直接发布', { type: 'warning' })
    const detail = await getProcessRoute(row.id)
    await updateProcessRoute(row.id, {
      routeName: detail.routeName,
      productId: detail.productId,
      productName: detail.productName,
      remark: detail.remark,
      saveMode: 'publish',
      operations: buildOperationPayload(detail.operations)
    })
    ElMessage.success('工艺已发布')
    await loadRoutes()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
      ElMessage.error('发布失败')
    }
  }
}

async function handleApproveRoute(row: ProcessRoute) {
  try {
    await ElMessageBox.confirm(`确认审批通过工艺「${row.routeName}」？`, '审批通过', { type: 'warning' })
    await approveProcessRoute(row.id)
    ElMessage.success('审批通过，工艺已发布')
    await loadRoutes()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
      ElMessage.error('审批失败')
    }
  }
}

async function handleRejectRoute(row: ProcessRoute) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回工艺', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入驳回原因',
      inputValidator: (val) => (val?.trim() ? true : '请填写驳回原因'),
      inputType: 'textarea'
    })
    await rejectProcessRoute(row.id, value.trim())
    ElMessage.success('已驳回')
    await loadRoutes()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
      ElMessage.error('驳回失败')
    }
  }
}

async function handleSetDefault(row: ProcessRoute) {
  try {
    await setDefaultProcessRoute(row.id)
    ElMessage.success('已设为默认工艺')
    await loadRoutes()
  } catch (error) {
    console.error(error)
    ElMessage.error('操作失败')
  }
}

async function handleToggle(row: ProcessRoute) {
  try {
    await toggleProcessRoute(row.id)
    ElMessage.success('状态已更新')
    await loadRoutes()
  } catch (error) {
    console.error(error)
    ElMessage.error('操作失败')
  }
}

async function handleDelete(row: ProcessRoute) {
  const ok = await confirmDelete({
    title: '删除工艺',
    message: `确认删除工艺「${row.routeName}」？此操作不可恢复。`
  })
  if (!ok) return
  try {
    await deleteProcessRoute(row.id)
    ElMessage.success('工艺已删除')
    await loadRoutes()
  } catch (error) {
    console.error(error)
    ElMessage.error('删除失败')
  }
}

onMounted(loadRoutes)
</script>

<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.main-card {
  border-radius: var(--cd-radius-xl);
  border: 1px solid var(--cd-border) !important;
  box-shadow: var(--cd-shadow-card) !important;
}
.main-card :deep(.el-card__body) {
  padding: 16px 20px !important;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.toolbar__filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar__input {
  width: 240px;
}

/* Capsule inputs and selects styling */
.toolbar__input :deep(.el-input__wrapper) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 4px 16px !important;
  transition: all 0.3s ease !important;
  height: 38px;
}
.toolbar__input :deep(.el-input__wrapper.is-focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

/* Action buttons styling */
.action-btn-reset {
  border-radius: 20px !important;
  padding: 8px 20px !important;
  border: 1px solid #e2e8f0 !important;
  transition: all 0.2s ease !important;
  height: 38px !important;
  font-weight: 600 !important;
  background: white !important;
  color: #475569 !important;
}
.action-btn-reset:hover {
  background-color: #f8fafc !important;
  border-color: #cbd5e1 !important;
}

.action-btn-create {
  border-radius: 20px !important;
  padding: 8px 20px !important;
  background: #0f172a !important; /* Premium dark navy */
  border: none !important;
  color: white !important;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.15) !important;
  transition: all 0.2s ease !important;
  height: 38px !important;
  font-weight: 600 !important;
}
.action-btn-create:hover {
  background: #1e293b !important;
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.25) !important;
}

.action-btn-refresh {
  border-radius: 20px !important;
  padding: 8px 20px !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%) !important;
  border: none !important;
  color: white !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
  transition: all 0.2s ease !important;
  height: 38px !important;
  font-weight: 600 !important;
}
.action-btn-refresh:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.3) !important;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.table-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: fit-content;
  max-width: 100%;
  margin: 0 auto;
}
.table-actions :deep(.el-button + .el-button) {
  margin-left: 0 !important;
}
.table-actions :deep(.el-dropdown) {
  display: inline-flex;
  vertical-align: middle;
}
.more-btn {
  display: inline-flex;
  align-items: center;
}
.action-btn-pill {
  border-radius: 20px !important;
  padding: 4px 10px !important;
  border: 1px solid #e2e8f0 !important;
  background: white !important;
  color: #475569 !important;
  font-weight: 500 !important;
  transition: all 0.2s ease !important;
  height: 28px !important;
  margin: 0 !important;
}
.action-btn-pill:hover {
  background-color: #f8fafc !important;
  border-color: #cbd5e1 !important;
  color: #0f172a !important;
}
.main-table :deep(.el-table__header .cell) {
  text-align: center;
}
.main-table :deep(.el-table__body .cell) {
  text-align: center;
}

.table-pagination {
  display: flex;
  justify-content: flex-start;
  margin-top: 12px;
}
</style>

<style>
/* 下拉菜单挂载到 body，需全局样式 */
.route-action-dropdown.el-popper {
  border-radius: 14px !important;
  border: 1px solid #e2e8f0 !important;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1) !important;
  padding: 6px !important;
  min-width: 112px;
}

.route-action-dropdown .el-popper__arrow::before {
  border-color: #e2e8f0 !important;
}

.route-action-dropdown .el-dropdown-menu {
  padding: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  border: none !important;
}

.route-action-dropdown .el-dropdown-menu__item {
  display: flex !important;
  justify-content: center !important;
  align-items: center !important;
  padding: 7px 14px !important;
  margin: 1px 0 !important;
  border-radius: 10px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  color: #475569 !important;
  line-height: 1.4 !important;
  text-align: center !important;
}

.route-action-dropdown .el-dropdown-menu__item:not(.is-disabled):hover,
.route-action-dropdown .el-dropdown-menu__item:not(.is-disabled):focus {
  background: #f1f5f9 !important;
  color: #0f172a !important;
}

.route-action-dropdown .route-action-dropdown__item--danger {
  color: #ef4444 !important;
}

.route-action-dropdown .route-action-dropdown__item--danger:not(.is-disabled):hover,
.route-action-dropdown .route-action-dropdown__item--danger:not(.is-disabled):focus {
  background: #fef2f2 !important;
  color: #dc2626 !important;
}

.route-action-dropdown .el-dropdown-menu__item--divided {
  margin-top: 5px !important;
  border-top: none !important;
}

.route-action-dropdown .el-dropdown-menu__item--divided::before {
  display: block;
  content: '';
  height: 1px;
  background: #e2e8f0;
  margin: -3px 6px 5px;
}
</style>
