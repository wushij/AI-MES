<template>
  <div class="view-page">
    <PageHeader title="设备管理" subtitle="统一管理设备主数据，与异常、排产、驾驶舱数据互通。" />

    <el-card shadow="hover" class="main-card">
      <!-- 顶部指标面板 -->
      <div v-if="summary" class="metrics-panel">
        <div v-for="item in summaryCards" :key="item.label" class="metric-item" :style="{ borderColor: item.color }">
          <div class="metric-item__content">
            <div class="metric-item__label">{{ item.label }}</div>
            <div class="metric-item__value" :style="{ color: item.color }">{{ item.value }}</div>
          </div>
        </div>
      </div>

      <div class="main-card-divider" />

      <!-- 搜索过滤栏 -->
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-input v-model="filters.keyword" placeholder="搜索编号/名称/产线/车间" clearable class="toolbar__input" @keyup.enter="loadDevices" />
          <el-select v-model="filters.categoryId" placeholder="分类" clearable class="toolbar__select" @change="loadDevices">
            <el-option v-for="cat in flatCategories" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
          </el-select>
          <el-select v-model="filters.status" placeholder="状态" clearable class="toolbar__select" @change="loadDevices">
            <el-option v-for="item in DEVICE_STATUSES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="toolbar__actions">
          <el-button class="action-btn-reset" @click="resetFilters">重置</el-button>
          <el-button class="action-btn-category" @click="categoryDialogVisible = true">分类管理</el-button>
          <el-button type="primary" class="action-btn-create" @click="openCreate">新建设备</el-button>
          <el-button type="primary" class="action-btn-refresh" @click="loadDevices">刷新</el-button>
        </div>
      </div>

      <!-- 数据列表 -->
      <el-table v-loading="loading" :data="devices" stripe border :header-cell-style="tableHeaderStyle" class="main-table">
        <el-table-column prop="deviceCode" label="设备编号" min-width="110" align="center" />
        <el-table-column prop="deviceName" label="设备名称" min-width="140" align="center" />
        <el-table-column prop="categoryName" label="分类" min-width="100" align="center">
          <template #default="{ row }">{{ row.categoryName || row.deviceType || '--' }}</template>
        </el-table-column>
        <el-table-column prop="workshop" label="车间" min-width="90" align="center" />
        <el-table-column prop="lineName" label="产线" min-width="90" align="center" />
        <el-table-column prop="teamName" label="责任班组" min-width="100" align="center" />
        <el-table-column prop="status" label="状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="deviceStatusTagType(row.status)">{{ row.statusLabel ?? deviceStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="220" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button size="small" @click="goDetail(row)">详情</el-button>
              <el-button size="small" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" plain :loading="deletingId === row.id" @click="removeDevice(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建设备' : '编辑设备'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备编号"><el-input v-model="form.deviceCode" placeholder="留空自动生成" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" prop="deviceName"><el-input v-model="form.deviceName" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备分类">
              <el-select v-model="form.categoryId" clearable placeholder="可选" class="full-width">
                <el-option v-for="cat in flatCategories" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" class="full-width">
                <el-option v-for="item in DEVICE_STATUSES" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="品牌"><el-input v-model="form.brand" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="型号"><el-input v-model="form.model" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="序列号"><el-input v-model="form.serialNumber" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="车间"><el-input v-model="form.workshop" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产线"><el-input v-model="form.lineName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="工位"><el-input v-model="form.station" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-select v-model="form.managerId" clearable placeholder="可选" class="full-width">
                <el-option v-for="user in managerOptions" :key="user.id" :label="user.realName" :value="user.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="责任班组">
              <el-select v-model="form.teamId" clearable placeholder="可选" class="full-width">
                <el-option v-for="team in teamOptions" :key="team.id" :label="team.teamName" :value="team.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用日期">
              <el-date-picker v-model="form.enableDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保修截止">
              <el-date-picker v-model="form.warrantyDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="categoryDialogVisible" title="设备分类管理" width="520px">
      <div class="category-toolbar">
        <el-input v-model="categoryForm.categoryName" placeholder="新分类名称" style="flex:1" />
        <el-button type="primary" :loading="categorySubmitting" @click="submitCategory">添加</el-button>
      </div>
      <el-table :data="flatCategories" stripe border size="small" class="category-table" :header-cell-style="categoryTableHeaderStyle">
        <el-table-column prop="categoryName" label="分类名称" align="center" />
        <el-table-column prop="sortNo" label="排序" width="70" align="center" />
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button size="small" class="cat-delete-btn" @click="removeCategory(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 设备详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="设备详情" width="1000px" append-to-body destroy-on-close>
      <DeviceDetail v-if="activeDeviceId" :id="activeDeviceId" @close="detailDialogVisible = false" @save-success="loadDevices(); loadSummary();" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  createDevice, createDeviceCategory, deleteDevice, deleteDeviceCategory,
  getDeviceFormOptions, getDeviceCategories, getDeviceSummary, getDevices, updateDevice,
  type DeviceCategoryItem, type DeviceItem, type DeviceSummary
} from '@/api/devices'
import { DEVICE_STATUSES, deviceStatusLabel, deviceStatusTagType } from '@/utils/deviceLabels'
import DeviceDetail from './DeviceDetail.vue'

const router = useRouter()
const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600', textAlign: 'center' as const }
const categoryTableHeaderStyle = { background: '#F5F7FA', fontWeight: '600', textAlign: 'center' }
const loading = ref(false)
const submitting = ref(false)
const deletingId = ref<string | number | null>(null)
const devices = ref<DeviceItem[]>([])
const summary = ref<DeviceSummary | null>(null)
const flatCategories = ref<DeviceCategoryItem[]>([])
const teamOptions = ref<Array<{ id: string | number; teamName: string }>>([])
const managerOptions = ref<Array<{ id: string | number; realName: string }>>([])
const dialogVisible = ref(false)
const categoryDialogVisible = ref(false)
const categorySubmitting = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<string | number | null>(null)
const detailDialogVisible = ref(false)
const activeDeviceId = ref<string | number | null>(null)
const formRef = ref<FormInstance>()
const categoryForm = reactive({ categoryName: '', parentId: 0, sortNo: 0 })

const filters = reactive({ keyword: '', status: '', categoryId: undefined as number | string | undefined })
const form = reactive({
  deviceCode: '', deviceName: '', categoryId: undefined as number | string | undefined,
  brand: '', model: '', serialNumber: '', workshop: '', lineName: '', station: '',
  managerId: undefined as number | string | undefined, teamId: undefined as number | string | undefined,
  enableDate: '', warrantyDate: '', status: 'idle', remark: ''
})

const rules: FormRules = { deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }] }

const summaryCards = computed(() => {
  if (!summary.value) return []
  const s = summary.value
  return [
    { label: '设备总数', value: s.totalCount, color: '#334155', borderClass: 'stat-total' },
    { label: '运行中', value: s.runningCount, color: '#16a34a', borderClass: 'stat-running' },
    { label: '空闲', value: s.idleCount, color: '#2563eb', borderClass: 'stat-idle' },
    { label: '故障/维修', value: s.faultCount, color: '#dc2626', borderClass: 'stat-fault' },
    { label: '保养中', value: s.maintenanceCount, color: '#d97706', borderClass: 'stat-maintenance' },
    { label: '今日报警', value: s.todayAlertCount, color: '#7c3aed', borderClass: 'stat-alert' }
  ]
})

async function loadDevices() {
  loading.value = true
  try {
    devices.value = await getDevices({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      categoryId: filters.categoryId
    })
  } catch (error) {
    console.error(error)
    ElMessage.error('加载设备失败')
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  try { summary.value = await getDeviceSummary() } catch (error) { console.error(error) }
}

async function loadFormOptions() {
  try {
    const data = await getDeviceFormOptions()
    flatCategories.value = data.categories ?? []
    teamOptions.value = data.teams ?? []
    managerOptions.value = data.managers ?? []
  } catch (error) {
    console.error(error)
  }
}

async function loadCategories() {
  try {
    const tree = await getDeviceCategories()
    flatCategories.value = flattenCategories(tree)
  } catch (error) {
    console.error(error)
  }
}

function flattenCategories(items: DeviceCategoryItem[], prefix = ''): DeviceCategoryItem[] {
  const result: DeviceCategoryItem[] = []
  for (const item of items) {
    result.push({ ...item, categoryName: prefix ? `${prefix} / ${item.categoryName}` : item.categoryName })
    if (item.children?.length) result.push(...flattenCategories(item.children, prefix ? `${prefix} / ${item.categoryName}` : item.categoryName))
  }
  return result
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.categoryId = undefined
  loadDevices()
}

function resetForm() {
  Object.assign(form, {
    deviceCode: '', deviceName: '', categoryId: undefined, brand: '', model: '',
    serialNumber: '', workshop: '', lineName: '', station: '', managerId: undefined, teamId: undefined,
    enableDate: '', warrantyDate: '', status: 'idle', remark: ''
  })
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: DeviceItem) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    deviceCode: row.deviceCode, deviceName: row.deviceName, categoryId: row.categoryId,
    brand: row.brand ?? '', model: row.model ?? '', serialNumber: row.serialNumber ?? '', workshop: row.workshop ?? '',
    lineName: row.lineName ?? '', station: row.station ?? '', managerId: row.managerId,
    teamId: row.teamId, enableDate: row.enableDate ?? '', warrantyDate: row.warrantyDate ?? '', status: row.status ?? 'idle', remark: row.remark ?? ''
  })
  dialogVisible.value = true
}

function goDetail(row: DeviceItem) {
  activeDeviceId.value = row.id
  detailDialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = { ...form }
    if (dialogMode.value === 'create') {
      await createDevice(payload)
      ElMessage.success('设备已创建')
    } else if (editingId.value != null) {
      await updateDevice(editingId.value, payload)
      ElMessage.success('设备已更新')
    }
    dialogVisible.value = false
    await Promise.all([loadDevices(), loadSummary()])
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

async function removeDevice(row: DeviceItem) {
  await ElMessageBox.confirm(`确认删除设备「${row.deviceName}」？`, '删除设备', { type: 'warning' })
  deletingId.value = row.id
  try {
    await deleteDevice(row.id)
    ElMessage.success('设备已删除')
    await Promise.all([loadDevices(), loadSummary()])
  } catch (error) {
    console.error(error)
    ElMessage.error('删除失败')
  } finally {
    deletingId.value = null
  }
}

async function submitCategory() {
  if (!categoryForm.categoryName.trim()) return
  categorySubmitting.value = true
  try {
    await createDeviceCategory({ categoryName: categoryForm.categoryName.trim(), parentId: 0, sortNo: flatCategories.value.length + 1 })
    categoryForm.categoryName = ''
    await loadCategories()
    ElMessage.success('分类已添加')
  } catch (error) {
    console.error(error)
    ElMessage.error('添加分类失败')
  } finally {
    categorySubmitting.value = false
  }
}

async function removeCategory(row: DeviceCategoryItem) {
  await ElMessageBox.confirm(`确认删除分类「${row.categoryName}」？`, '删除分类', { type: 'warning' })
  try {
    await deleteDeviceCategory(row.id)
    await loadCategories()
    ElMessage.success('分类已删除')
  } catch (error) {
    console.error(error)
    ElMessage.error('删除失败')
  }
}

onMounted(async () => {
  await Promise.all([loadFormOptions(), loadCategories(), loadSummary()])
  loadDevices()
})
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
.metrics-panel {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  padding: 2px 0 8px;
}
.metric-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 18px;
  border-radius: 20px;
  background: #f8fafc;
  transition: all 0.25s ease;
  border: 1.5px solid #f1f5f9;
}
.metric-item:hover {
  background: #fff;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05);
}
.metric-item__content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.metric-item__label {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}
.metric-item__value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}
.main-card-divider {
  height: 1px;
  background-color: #f1f5f9;
  margin: 4px 0 12px;
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
.toolbar__select {
  width: 140px;
}

/* Capsule inputs and selects styling */
.toolbar__input :deep(.el-input__wrapper),
.toolbar__select :deep(.el-input__wrapper),
.toolbar__select :deep(.el-select__wrapper) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 4px 16px !important;
  transition: all 0.3s ease !important;
  height: 38px;
}
.toolbar__input :deep(.el-input__wrapper.is-focus),
.toolbar__select :deep(.el-input__wrapper.is-focus),
.toolbar__select :deep(.el-select__wrapper.is-focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

/* Action buttons styling */
.action-btn-reset, .action-btn-category {
  border-radius: 20px !important;
  padding: 8px 20px !important;
  border: 1px solid #e2e8f0 !important;
  transition: all 0.2s ease !important;
  height: 38px !important;
  font-weight: 600 !important;
}
.action-btn-reset:hover, .action-btn-category:hover {
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

.full-width {
  width: 100%;
}
.category-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.category-table {
  margin-top: 8px;
}
.cat-delete-btn {
  border-radius: 20px !important;
  padding: 4px 14px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  height: 26px !important;
  background: #fff !important;
  color: #ef4444 !important;
  border: 1.5px solid #fecaca !important;
  transition: all 0.2s ease !important;
}
.cat-delete-btn:hover {
  background: #fee2e2 !important;
  color: #b91c1c !important;
  border-color: #fca5a5 !important;
}
.main-table :deep(.el-table__header .cell) {
  text-align: center;
}
.main-table :deep(.el-table__body .cell) {
  text-align: center;
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
</style>
