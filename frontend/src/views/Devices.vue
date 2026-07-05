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
          <el-button class="action-btn-category" @click="planManagerVisible = true">计划管理</el-button>
          <el-button type="primary" class="action-btn-create" @click="openCreate">新建设备</el-button>
          <el-button type="primary" class="action-btn-refresh" @click="loadDevices">刷新</el-button>
        </div>
      </div>

      <!-- 数据列表 -->
      <el-table v-loading="loading" :data="pagedDevices" stripe border :header-cell-style="tableHeaderStyle" class="main-table">
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
        <el-table-column label="今日运行" min-width="100" align="center">
          <template #default="{ row }">
            <span class="duration-text duration-text--run">{{ row.todayRunLabel || formatDurationMinutes(row.todayRunMinutes) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="todayAlertCount" label="今日报警" width="100" align="center">
          <template #default="{ row }">
            <button
              v-if="(row.todayAlertCount ?? 0) > 0"
              type="button"
              class="alert-badge alert-badge--danger"
              @click="openAlertDrawer(row)"
            >
              {{ row.todayAlertCount }}
            </button>
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>
        <el-table-column prop="maintenanceOverdueCount" label="保养到期" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="(row.maintenanceOverdueCount ?? 0) > 0" type="warning" size="small" effect="dark">{{ row.maintenanceOverdueCount }}</el-tag>
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" min-width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="300" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button size="small" class="action-btn action-btn--view" @click="goDetail(row)">详情</el-button>
              <el-button size="small" class="action-btn action-btn--inspect" @click="openInspection(row)">点检</el-button>
              <el-button size="small" class="action-btn action-btn--maintain" @click="openMaintenance(row)">保养</el-button>
              <el-button size="small" class="action-btn action-btn--repair" @click="openRepair(row)">维修</el-button>
              <el-dropdown trigger="click" @command="(cmd: string) => handleMoreAction(cmd, row)">
                <el-button size="small" class="action-btn action-btn--more">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建设备' : '编辑设备'" width="640px" class="device-form-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="device-form">
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
            <el-form-item label="购买日期">
              <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安装日期">
              <el-date-picker v-model="form.installDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
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
            <el-form-item label="备注" class="form-item--textarea"><el-input v-model="form.remark" type="textarea" :rows="2" class="full-width" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="categoryDialogVisible"
      title="设备分类管理"
      width="680px"
      append-to-body
      class="category-dialog"
      @open="loadCategories"
    >
      <div class="category-add-panel">
        <div class="category-section-title">新增分类</div>
        <div class="category-add-form">
          <div class="category-field">
            <span class="category-field__label">父分类</span>
            <el-tree-select
              v-model="categoryForm.parentId"
              :data="categoryTreeOptions"
              :props="{ label: 'categoryName', value: 'id', children: 'children' }"
              check-strictly
              placeholder="选择父分类"
              class="category-field__control"
            />
          </div>
          <div class="category-field category-field--grow">
            <span class="category-field__label">分类名称</span>
            <el-input v-model="categoryForm.categoryName" placeholder="如：装配设备" class="category-field__control" />
          </div>
          <div class="category-field category-field--sort">
            <span class="category-field__label">排序</span>
            <el-input-number v-model="categoryForm.sortNo" :min="0" :max="999" controls-position="right" class="category-field__control" />
          </div>
          <el-button type="primary" class="category-add-btn" :loading="categorySubmitting" @click="submitCategory">添加</el-button>
        </div>
      </div>

      <div class="category-list-panel">
        <div class="category-section-title category-section-title--list">
          <span>分类列表</span>
          <span class="category-count">{{ flatCategories.length }} 项</span>
        </div>
        <el-empty v-if="!categoryTree.length" description="暂无分类，请在上方添加" :image-size="72" />
        <el-tree
          v-else
          :data="categoryTree"
          :props="{ label: 'categoryName', children: 'children' }"
          node-key="id"
          default-expand-all
          class="category-tree"
        >
          <template #default="{ data, node }">
            <div class="category-tree-node" :class="{ 'is-child': node.level > 1 }">
              <div class="category-tree-node__main">
                <span class="category-tree-node__name">{{ data.categoryName }}</span>
                <el-tag size="small" type="info" effect="plain" round class="category-sort-tag">排序 {{ data.sortNo ?? 0 }}</el-tag>
              </div>
              <div class="category-tree-node__actions">
                <el-button size="small" class="cat-pill-btn cat-pill-btn--edit" @click.stop="openEditCategory(data)">编辑</el-button>
                <el-button size="small" class="cat-pill-btn cat-pill-btn--delete" @click.stop="removeCategory(data)">删除</el-button>
              </div>
            </div>
          </template>
        </el-tree>
      </div>
    </el-dialog>

    <el-dialog v-model="categoryEditVisible" title="编辑分类" width="480px" append-to-body class="category-edit-dialog">
      <el-form label-width="80px" class="category-edit-form">
        <el-form-item label="父分类">
          <el-tree-select
            v-model="categoryEditForm.parentId"
            :data="categoryEditTreeOptions"
            :props="{ label: 'categoryName', value: 'id', children: 'children' }"
            check-strictly
            placeholder="根分类"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="分类名称">
          <el-input v-model="categoryEditForm.categoryName" placeholder="分类名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryEditForm.sortNo" :min="0" :max="999" class="full-width" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="category-dialog-footer">
          <el-button class="btn-cancel" @click="categoryEditVisible = false">取消</el-button>
          <el-button type="primary" class="btn-submit" :loading="categoryEditSubmitting" @click="submitEditCategory">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 设备详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="设备详情" width="1000px" append-to-body destroy-on-close>
      <DeviceDetail
        v-if="activeDeviceId"
        :id="activeDeviceId"
        :initial-tab="detailInitialTab"
        @close="detailDialogVisible = false"
        @save-success="loadDevices(); loadSummary();"
      />
    </el-dialog>

    <DeviceInspectionDialog
      v-if="inspectionDevice"
      v-model:visible="inspectionDialogVisible"
      :device-id="inspectionDevice.id"
      :device-name="inspectionDevice.deviceName"
      @success="onInspectionSuccess"
    />
    <DeviceMaintenanceDialog
      v-if="maintenanceDevice"
      v-model:visible="maintenanceDialogVisible"
      :device-id="maintenanceDevice.id"
      :device-name="maintenanceDevice.deviceName"
      @success="onMaintenanceSuccess"
    />
    <DeviceRepairDialog
      v-if="repairDevice"
      v-model:visible="repairDialogVisible"
      :device-id="repairDevice.id"
      :device-name="repairDevice.deviceName"
      @success="onRepairSuccess"
    />

    <DevicePlanManager v-model:visible="planManagerVisible" @changed="loadDevices(); loadSummary();" />

    <el-drawer v-model="alertDrawerVisible" :title="`今日报警 — ${alertDrawerDevice?.deviceName || ''}`" size="420px" append-to-body destroy-on-close>
      <DeviceAlertPanel
        v-if="alertDrawerDevice"
        :alerts="alertDrawerAlerts"
        empty-text="今日暂无设备报警"
        @view="handleDrawerAlertView"
      />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  createDevice, createDeviceCategory, deleteDevice, deleteDeviceCategory,
  getDeviceFormOptions, getDeviceCategories, getDeviceSummary, getDevices, updateDevice, updateDeviceCategory,
  type DeviceCategoryItem, type DeviceItem, type DeviceSummary
} from '@/api/devices'
import { DEVICE_STATUSES, deviceStatusLabel, deviceStatusTagType } from '@/utils/deviceLabels'
import DeviceDetail from './DeviceDetail.vue'
import DeviceInspectionDialog from '@/components/device/DeviceInspectionDialog.vue'
import DeviceMaintenanceDialog from '@/components/device/DeviceMaintenanceDialog.vue'
import DeviceRepairDialog from '@/components/device/DeviceRepairDialog.vue'
import DeviceAlertPanel from '@/components/device/DeviceAlertPanel.vue'
import DevicePlanManager from '@/components/device/DevicePlanManager.vue'
import { confirmDelete } from '@/utils/confirmDelete'
import { formatDurationMinutes } from '@/utils/duration'
import { getDeviceTodayAlerts, type DeviceTodayAlert } from '@/api/devices'
import { useNotificationStore } from '@/stores/notifications'

const router = useRouter()
const notificationStore = useNotificationStore()
const { deviceAlertVersion, lastDeviceAlert } = storeToRefs(notificationStore)
const planManagerVisible = ref(false)
const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600', textAlign: 'center' as const }
const loading = ref(false)
const submitting = ref(false)
const deletingId = ref<string | number | null>(null)
const devices = ref<DeviceItem[]>([])
const summary = ref<DeviceSummary | null>(null)
const teamOptions = ref<Array<{ id: string | number; teamName: string }>>([])
const managerOptions = ref<Array<{ id: string | number; realName: string }>>([])
const dialogVisible = ref(false)
const categoryDialogVisible = ref(false)
const categoryEditVisible = ref(false)
const categorySubmitting = ref(false)
const categoryEditSubmitting = ref(false)
const categoryTree = ref<DeviceCategoryItem[]>([])
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<string | number | null>(null)
const detailDialogVisible = ref(false)
const detailInitialTab = ref('basic')
const activeDeviceId = ref<string | number | null>(null)
const inspectionDialogVisible = ref(false)
const inspectionDevice = ref<DeviceItem | null>(null)
const maintenanceDialogVisible = ref(false)
const maintenanceDevice = ref<DeviceItem | null>(null)
const repairDialogVisible = ref(false)
const repairDevice = ref<DeviceItem | null>(null)
const alertDrawerVisible = ref(false)
const alertDrawerDevice = ref<DeviceItem | null>(null)
const alertDrawerAlerts = ref<DeviceTodayAlert[]>([])
const formRef = ref<FormInstance>()
const categoryForm = reactive({ categoryName: '', parentId: 0 as number | string, sortNo: 0 })
const categoryEditForm = reactive({ id: 0 as number | string, categoryName: '', parentId: 0 as number | string, sortNo: 0 })

const filters = reactive({ keyword: '', status: '', categoryId: undefined as number | string | undefined })
const pagination = reactive({ page: 1, size: 10, total: 0 })
const form = reactive({
  deviceCode: '', deviceName: '', categoryId: undefined as number | string | undefined,
  brand: '', model: '', serialNumber: '', workshop: '', lineName: '', station: '',
  managerId: undefined as number | string | undefined, teamId: undefined as number | string | undefined,
  purchaseDate: '', installDate: '', enableDate: '', warrantyDate: '', status: 'idle', remark: ''
})

const rules: FormRules = { deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }] }

const flatCategories = computed(() => flattenCategories(categoryTree.value))

const categoryTreeOptions = computed(() => [
  { id: 0, categoryName: '根分类', children: categoryTree.value }
])

const categoryEditTreeOptions = computed(() => {
  const excludeId = categoryEditForm.id
  return [{ id: 0, categoryName: '根分类', children: filterCategoryTree(categoryTree.value, excludeId) }]
})

function filterCategoryTree(items: DeviceCategoryItem[], excludeId: number | string): DeviceCategoryItem[] {
  return items
    .filter((item) => item.id !== excludeId)
    .map((item) => ({
      ...item,
      children: item.children?.length ? filterCategoryTree(item.children, excludeId) : undefined
    }))
}

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

const pagedDevices = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return devices.value.slice(start, start + pagination.size)
})

async function loadDevices() {
  loading.value = true
  try {
    devices.value = await getDevices({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      categoryId: filters.categoryId
    })
    pagination.total = devices.value.length
    if ((pagination.page - 1) * pagination.size >= pagination.total && pagination.page > 1) {
      pagination.page = 1
    }
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
    teamOptions.value = data.teams ?? []
    managerOptions.value = data.managers ?? []
  } catch (error) {
    console.error(error)
  }
}

async function loadCategories() {
  try {
    categoryTree.value = await getDeviceCategories()
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
  pagination.page = 1
  loadDevices()
}

function resetForm() {
  Object.assign(form, {
    deviceCode: '', deviceName: '', categoryId: undefined, brand: '', model: '',
    serialNumber: '', workshop: '', lineName: '', station: '', managerId: undefined, teamId: undefined,
    purchaseDate: '', installDate: '', enableDate: '', warrantyDate: '', status: 'idle', remark: ''
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
    teamId: row.teamId, purchaseDate: row.purchaseDate ?? '', installDate: row.installDate ?? '',
    enableDate: row.enableDate ?? '', warrantyDate: row.warrantyDate ?? '', status: row.status ?? 'idle', remark: row.remark ?? ''
  })
  dialogVisible.value = true
}

function goDetail(row: DeviceItem) {
  detailInitialTab.value = 'basic'
  activeDeviceId.value = row.id
  detailDialogVisible.value = true
}

function formatTime(value?: string) {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 19)
}

function handleMoreAction(command: string, row: DeviceItem) {
  if (command === 'edit') {
    openEdit(row)
    return
  }
  if (command === 'delete') {
    removeDevice(row)
  }
}

async function openAlertDrawer(row: DeviceItem) {
  alertDrawerDevice.value = row
  alertDrawerVisible.value = true
  try {
    alertDrawerAlerts.value = await getDeviceTodayAlerts(row.id)
  } catch (error) {
    console.error(error)
    alertDrawerAlerts.value = []
    ElMessage.error('加载今日报警失败')
  }
}

function handleDrawerAlertView(alert: DeviceTodayAlert) {
  alertDrawerVisible.value = false
  if (alert.source === 'exc_event') {
    router.push('/exceptions')
    return
  }
  if (alert.source === 'dev_maintenance_plan') {
    maintenanceDevice.value = alertDrawerDevice.value
    maintenanceDialogVisible.value = true
    return
  }
  if (alert.source === 'dev_repair_order') {
    detailInitialTab.value = 'repairs'
    activeDeviceId.value = alertDrawerDevice.value?.id ?? null
    detailDialogVisible.value = true
  }
}

function openInspection(row: DeviceItem) {
  inspectionDialogVisible.value = false
  inspectionDevice.value = row
  nextTick(() => {
    inspectionDialogVisible.value = true
  })
}

function openMaintenance(row: DeviceItem) {
  maintenanceDialogVisible.value = false
  maintenanceDevice.value = row
  nextTick(() => {
    maintenanceDialogVisible.value = true
  })
}

function openRepair(row: DeviceItem) {
  repairDevice.value = row
  repairDialogVisible.value = true
}

function onInspectionSuccess() {
  const deviceId = inspectionDevice.value?.id
  loadDevices()
  loadSummary()
  if (deviceId != null) {
    detailInitialTab.value = 'inspections'
    activeDeviceId.value = deviceId
    detailDialogVisible.value = true
    ElMessage.success('点检已提交，已为您打开点检记录')
  }
}

function onMaintenanceSuccess() {
  const deviceId = maintenanceDevice.value?.id
  loadDevices()
  loadSummary()
  if (deviceId != null) {
    detailInitialTab.value = 'maintenances'
    activeDeviceId.value = deviceId
    detailDialogVisible.value = true
    ElMessage.success('保养已提交，已为您打开保养记录')
  }
}

function onRepairSuccess() {
  loadDevices()
  loadSummary()
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
  const ok = await confirmDelete({
    title: '删除设备',
    message: `确认删除设备「${row.deviceName}」？此操作不可恢复。`
  })
  if (!ok) return
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
  if (!categoryForm.categoryName.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  categorySubmitting.value = true
  try {
    await createDeviceCategory({
      categoryName: categoryForm.categoryName.trim(),
      parentId: categoryForm.parentId || 0,
      sortNo: categoryForm.sortNo
    })
    categoryForm.categoryName = ''
    categoryForm.parentId = 0
    categoryForm.sortNo = 0
    await loadCategories()
    ElMessage.success('分类已添加')
  } catch (error) {
    console.error(error)
    ElMessage.error('添加分类失败')
  } finally {
    categorySubmitting.value = false
  }
}

function openEditCategory(row: DeviceCategoryItem) {
  categoryEditForm.id = row.id
  categoryEditForm.categoryName = row.categoryName
  categoryEditForm.parentId = row.parentId ?? 0
  categoryEditForm.sortNo = row.sortNo ?? 0
  categoryEditVisible.value = true
}

async function submitEditCategory() {
  if (!categoryEditForm.categoryName.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  categoryEditSubmitting.value = true
  try {
    await updateDeviceCategory(categoryEditForm.id, {
      categoryName: categoryEditForm.categoryName.trim(),
      parentId: categoryEditForm.parentId || 0,
      sortNo: categoryEditForm.sortNo
    })
    categoryEditVisible.value = false
    await loadCategories()
    ElMessage.success('分类已更新')
  } catch (error) {
    console.error(error)
    ElMessage.error('更新分类失败')
  } finally {
    categoryEditSubmitting.value = false
  }
}

async function removeCategory(row: DeviceCategoryItem) {
  const ok = await confirmDelete({
    title: '删除分类',
    message: `确认删除分类「${row.categoryName}」？此操作不可恢复。`
  })
  if (!ok) return
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

async function refreshOnDeviceAlert() {
  await Promise.all([loadDevices(), loadSummary()])

  const alertDeviceId = lastDeviceAlert.value?.deviceId
  if (alertDeviceId == null) {
    return
  }

  if (alertDrawerVisible.value && alertDrawerDevice.value?.id === alertDeviceId) {
    try {
      alertDrawerAlerts.value = await getDeviceTodayAlerts(alertDeviceId)
    } catch (error) {
      console.error(error)
    }
  }
}

watch(deviceAlertVersion, () => {
  void refreshOnDeviceAlert()
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

.device-form :deep(.el-textarea__inner) {
  border-radius: 20px !important;
  border: none !important;
  padding: 10px 16px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  background-color: #fff !important;
  resize: none;
}

.device-form :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px #a5b4fc inset !important;
}

.device-form :deep(.el-textarea__inner:focus) {
  box-shadow:
    0 0 0 1px #4f46e5 inset,
    0 0 0 3px rgba(79, 70, 229, 0.12) !important;
  outline: none !important;
}

.category-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
}
.category-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 18px 22px 14px;
  border-bottom: 1px solid #f1f5f9;
}
.category-dialog :deep(.el-dialog__body) {
  padding: 16px 22px 20px;
  background: #f8fafc;
}
.category-section-title {
  font-size: 13px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 10px;
}
.category-section-title--list {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.category-count {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
}
.category-add-panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 16px 16px;
  margin-bottom: 14px;
}
.category-add-form {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  flex-wrap: wrap;
}
.category-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 140px;
}
.category-field--grow {
  flex: 1;
  min-width: 160px;
}
.category-field--sort {
  width: 108px;
  min-width: 108px;
}
.category-field__label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}
.category-field__control {
  width: 100%;
}
.category-add-btn {
  border-radius: 18px !important;
  height: 38px !important;
  padding: 0 22px !important;
  font-weight: 600 !important;
  background: #0f172a !important;
  border: none !important;
  margin-bottom: 1px;
}
.category-add-btn:hover {
  background: #1e293b !important;
}
.category-list-panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 12px 10px;
}
.category-tree {
  max-height: 340px;
  overflow-y: auto;
  background: transparent;
}
.category-tree :deep(.el-tree-node__content) {
  height: auto;
  min-height: 42px;
  padding: 4px 8px;
  border-radius: 10px;
  transition: background 0.15s ease;
}
.category-tree :deep(.el-tree-node__content:hover) {
  background: #f8fafc;
}
.category-tree :deep(.el-tree-node__expand-icon) {
  color: #94a3b8;
  font-size: 14px;
}
.category-tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 34px;
  padding: 2px 4px 2px 0;
}
.category-tree-node.is-child .category-tree-node__name {
  font-weight: 500;
  color: #475569;
}
.category-tree-node__main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.category-tree-node__name {
  font-weight: 600;
  color: #1e293b;
  font-size: 14px;
}
.category-sort-tag {
  border: none !important;
  background: #f1f5f9 !important;
  color: #64748b !important;
  font-weight: 600 !important;
}
.category-tree-node__actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.cat-pill-btn {
  border-radius: 16px !important;
  padding: 4px 12px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  height: 26px !important;
  background: #fff !important;
  transition: all 0.2s ease !important;
}
.cat-pill-btn--edit {
  color: #4f46e5 !important;
  border: 1px solid #c7d2fe !important;
}
.cat-pill-btn--edit:hover {
  background: #eef2ff !important;
  border-color: #a5b4fc !important;
}
.cat-pill-btn--delete {
  color: #ef4444 !important;
  border: 1px solid #fecaca !important;
}
.cat-pill-btn--delete:hover {
  background: #fee2e2 !important;
  color: #b91c1c !important;
  border-color: #fca5a5 !important;
}
.category-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.category-edit-dialog .btn-cancel,
.category-dialog-footer .btn-cancel {
  border-radius: 18px !important;
  font-weight: 600 !important;
}
.category-edit-dialog .btn-submit,
.category-dialog-footer .btn-submit {
  border-radius: 18px !important;
  font-weight: 600 !important;
  background: #0f172a !important;
  border: none !important;
}
.category-edit-dialog .btn-submit:hover,
.category-dialog-footer .btn-submit:hover {
  background: #1e293b !important;
}
.category-add-panel :deep(.el-input__wrapper),
.category-edit-form :deep(.el-input__wrapper) {
  border-radius: 10px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}
.category-add-panel :deep(.el-input__wrapper:hover),
.category-edit-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c7d2fe inset !important;
}
.text-muted {
  color: #94a3b8;
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
.action-btn {
  border-radius: 16px !important;
  font-weight: 600 !important;
  padding: 4px 12px !important;
}
.action-btn--view {
  color: #334155 !important;
  border-color: #cbd5e1 !important;
  background: #fff !important;
}
.action-btn--inspect {
  color: #4f46e5 !important;
  border-color: #c7d2fe !important;
  background: #eef2ff !important;
}
.action-btn--maintain {
  color: #b45309 !important;
  border-color: #fde68a !important;
  background: #fffbeb !important;
}
.action-btn--repair {
  color: #b91c1c !important;
  border-color: #fecaca !important;
  background: #fef2f2 !important;
}
.action-btn--more {
  color: #64748b !important;
  border-color: #e2e8f0 !important;
  background: #f8fafc !important;
}
.duration-text {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.duration-text--run {
  color: #16a34a;
}
.alert-badge {
  border: none;
  border-radius: 999px;
  min-width: 28px;
  height: 24px;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}
.alert-badge--danger {
  background: #dc2626;
  color: #fff;
  box-shadow: 0 2px 8px rgba(220, 38, 38, 0.25);
}
.alert-badge--danger:hover {
  background: #b91c1c;
}

.table-pagination {
  display: flex;
  justify-content: flex-start;
  margin-top: 12px;
}
</style>
