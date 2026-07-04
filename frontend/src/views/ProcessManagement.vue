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
      <el-table v-loading="loading" :data="filteredRoutes" stripe border :header-cell-style="tableHeaderStyle" class="main-table">
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
              <el-dropdown trigger="click" @command="(cmd: any) => handleCommand(cmd, row)">
                <el-button size="small" class="action-btn-pill more-btn">
                  更多 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="copy">复制</el-dropdown-item>
                    <el-dropdown-item v-if="!row.isDefault && row.status === 'published'" command="setDefault">设默认</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'published' || row.status === 'disabled'" command="toggle">
                      {{ row.enabled === false ? '启用' : '停用' }}
                    </el-dropdown-item>
                    <el-dropdown-item v-if="!row.isDefault" command="delete" divided>
                      <span style="color: var(--el-color-danger)">删除</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
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
          <el-input v-model="form.productName" placeholder="留空表示通用工艺" />
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
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import ProcessRouteDetail from './ProcessRouteDetail.vue'
import {
  copyProcessRoute, deleteProcessRoute, getProcessRoutes, routeStatusLabel, routeStatusType,
  setDefaultProcessRoute, toggleProcessRoute, type ProcessRoute,
  createProcessRoute, updateProcessRoute
} from '@/api/processRoutes'

const router = useRouter()
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

function resetFilters() {
  filters.value.keyword = ''
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
  productName: '',
  remark: ''
})

const rules = {
  routeName: [
    { required: true, message: '请输入工艺名称', trigger: 'blur' }
  ]
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
        await createProcessRoute({
          routeCode: form.value.routeCode || undefined,
          routeName: form.value.routeName,
          productName: form.value.productName || undefined,
          remark: form.value.remark || undefined,
          operations: []
        })
        ElMessage.success('新建工艺成功')
      } else {
        await updateProcessRoute(form.value.id, {
          routeCode: form.value.routeCode || undefined,
          routeName: form.value.routeName,
          productName: form.value.productName || undefined,
          remark: form.value.remark || undefined
        })
        ElMessage.success('更新工艺成功')
      }
      dialogVisible.value = false
      await loadRoutes()
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
  await ElMessageBox.confirm(`确认删除工艺「${row.routeName}」？`, '删除工艺', { type: 'warning' })
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
</style>
