<template>
  <el-dialog
    :model-value="visible"
    title="点检 / 保养计划管理"
    width="960px"
    append-to-body
    destroy-on-close
    class="plan-manager-dialog"
    @update:model-value="emit('update:visible', $event)"
    @open="loadAll"
  >
    <el-tabs v-model="activeTab" class="plan-tabs">
      <el-tab-pane label="点检计划" name="inspection">
        <div class="plan-panel">
          <div class="plan-toolbar">
            <div class="plan-toolbar__filters">
              <el-input
                v-model="inspectionKeyword"
                placeholder="搜索编号/名称"
                clearable
                class="plan-search"
                @keyup.enter="loadInspectionPlans"
                @clear="loadInspectionPlans"
              />
              <el-button type="primary" class="plan-search-btn" @click="loadInspectionPlans">查询</el-button>
              <el-button class="plan-reset-btn" @click="resetInspectionSearch">重置</el-button>
            </div>
            <el-button type="primary" class="plan-create-btn" @click="openInspectionForm()">新建点检计划</el-button>
          </div>
          <el-table
            v-loading="inspectionLoading"
            :data="inspectionPlans"
            stripe
            border
            class="plan-table"
            :header-cell-style="tableHeaderStyle"
            max-height="420"
          >
            <template #empty>
              <el-empty description="暂无点检计划" :image-size="72" />
            </template>
            <el-table-column prop="planCode" label="编号" min-width="120" align="center" />
            <el-table-column prop="planName" label="名称" min-width="140" align="center" show-overflow-tooltip />
            <el-table-column label="分类" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" round>{{ row.categoryName || (row.deviceId ? '设备专属' : '通用') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="cycleTypeLabel" label="周期" width="80" align="center" />
            <el-table-column label="项目数" width="80" align="center">
              <template #default="{ row }">{{ row.checkItems?.length ?? 0 }}</template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="88" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled ? 'success' : 'info'" effect="light" round>
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="156" align="center" fixed="right">
              <template #default="{ row }">
                <div class="plan-actions">
                  <el-button size="small" class="plan-pill-btn plan-pill-btn--edit" @click="openInspectionForm(row)">编辑</el-button>
                  <el-button size="small" class="plan-pill-btn plan-pill-btn--delete" @click="removeInspectionPlan(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="保养计划" name="maintenance">
        <div class="plan-panel">
          <div class="plan-toolbar">
            <div class="plan-toolbar__filters">
              <el-input
                v-model="maintenanceKeyword"
                placeholder="搜索编号/名称"
                clearable
                class="plan-search"
                @keyup.enter="loadMaintenancePlans"
                @clear="loadMaintenancePlans"
              />
              <el-button type="primary" class="plan-search-btn" @click="loadMaintenancePlans">查询</el-button>
              <el-button class="plan-reset-btn" @click="resetMaintenanceSearch">重置</el-button>
            </div>
            <el-button type="primary" class="plan-create-btn" @click="openMaintenanceForm()">新建保养计划</el-button>
          </div>
          <el-table
            v-loading="maintenanceLoading"
            :data="maintenancePlans"
            stripe
            border
            class="plan-table"
            :header-cell-style="tableHeaderStyle"
            max-height="420"
          >
            <template #empty>
              <el-empty description="暂无保养计划" :image-size="72" />
            </template>
            <el-table-column prop="planCode" label="编号" min-width="120" align="center" />
            <el-table-column prop="planName" label="名称" min-width="140" align="center" show-overflow-tooltip />
            <el-table-column label="分类" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" round>{{ row.categoryName || (row.deviceId ? '设备专属' : '通用') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="cycleTypeLabel" label="周期" width="80" align="center" />
            <el-table-column prop="nextDueDate" label="下次到期" width="120" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.overdue" type="danger" size="small" effect="light" round>{{ row.nextDueDate }}</el-tag>
                <span v-else>{{ row.nextDueDate || '--' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="88" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled ? 'success' : 'info'" effect="light" round>
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="156" align="center" fixed="right">
              <template #default="{ row }">
                <div class="plan-actions">
                  <el-button size="small" class="plan-pill-btn plan-pill-btn--edit" @click="openMaintenanceForm(row)">编辑</el-button>
                  <el-button size="small" class="plan-pill-btn plan-pill-btn--delete" @click="removeMaintenancePlan(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="inspectionFormVisible"
      :title="inspectionEditingId ? '编辑点检计划' : '新建点检计划'"
      width="560px"
      append-to-body
      class="plan-form-dialog"
    >
      <el-form label-width="88px" class="plan-form">
        <el-form-item label="计划名称"><el-input v-model="inspectionForm.planName" placeholder="如：装配设备日点检" /></el-form-item>
        <el-form-item label="计划编号"><el-input v-model="inspectionForm.planCode" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="周期">
          <el-select v-model="inspectionForm.cycleType" class="full-width">
            <el-option label="每日" value="daily" /><el-option label="每周" value="weekly" /><el-option label="每月" value="monthly" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定分类">
          <el-select v-model="inspectionForm.categoryId" clearable placeholder="可选" class="full-width">
            <el-option v-for="cat in flatCategories" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定设备">
          <el-select v-model="inspectionForm.deviceId" clearable filterable placeholder="可选" class="full-width">
            <el-option v-for="dev in deviceOptions" :key="dev.id" :label="`${dev.deviceCode} ${dev.deviceName}`" :value="dev.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="点检项目">
          <div class="item-list">
            <div v-for="(item, index) in inspectionForm.checkItems" :key="index" class="item-row">
              <el-input v-model="inspectionForm.checkItems[index]" placeholder="项目名称" />
              <el-button size="small" class="row-pill-btn row-pill-btn--delete" @click="removeInspectionItem(index)">删除</el-button>
            </div>
            <el-button class="btn-add-item" @click="inspectionForm.checkItems.push('')">+ 添加项目</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="inspectionForm.remark" type="textarea" :rows="2" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="plan-dialog-footer">
          <el-button class="btn-cancel" @click="inspectionFormVisible = false">取消</el-button>
          <el-button type="primary" class="btn-submit" :loading="inspectionSubmitting" @click="submitInspectionPlan">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="maintenanceFormVisible"
      :title="maintenanceEditingId ? '编辑保养计划' : '新建保养计划'"
      width="560px"
      append-to-body
      class="plan-form-dialog"
    >
      <el-form label-width="88px" class="plan-form">
        <el-form-item label="计划名称"><el-input v-model="maintenanceForm.planName" placeholder="如：装配设备月度保养" /></el-form-item>
        <el-form-item label="计划编号"><el-input v-model="maintenanceForm.planCode" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="周期">
          <el-select v-model="maintenanceForm.cycleType" class="full-width">
            <el-option label="每日" value="daily" /><el-option label="每周" value="weekly" /><el-option label="每月" value="monthly" />
          </el-select>
        </el-form-item>
        <el-form-item label="下次到期">
          <el-date-picker v-model="maintenanceForm.nextDueDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
        </el-form-item>
        <el-form-item label="绑定分类">
          <el-select v-model="maintenanceForm.categoryId" clearable placeholder="可选" class="full-width">
            <el-option v-for="cat in flatCategories" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定设备">
          <el-select v-model="maintenanceForm.deviceId" clearable filterable placeholder="可选" class="full-width">
            <el-option v-for="dev in deviceOptions" :key="dev.id" :label="`${dev.deviceCode} ${dev.deviceName}`" :value="dev.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="保养项目">
          <div class="item-list">
            <div v-for="(item, index) in maintenanceForm.maintenanceItems" :key="index" class="item-row">
              <el-input v-model="maintenanceForm.maintenanceItems[index]" placeholder="项目名称" />
              <el-button size="small" class="row-pill-btn row-pill-btn--delete" @click="removeMaintenanceItem(index)">删除</el-button>
            </div>
            <el-button class="btn-add-item" @click="maintenanceForm.maintenanceItems.push('')">+ 添加项目</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="maintenanceForm.remark" type="textarea" :rows="2" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="plan-dialog-footer">
          <el-button class="btn-cancel" @click="maintenanceFormVisible = false">取消</el-button>
          <el-button type="primary" class="btn-submit" :loading="maintenanceSubmitting" @click="submitMaintenancePlan">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirmDelete'
import {
  createDeviceInspectionPlan, deleteDeviceInspectionPlan, getDeviceInspectionPlans, updateDeviceInspectionPlan, type DeviceInspectionPlan
} from '@/api/deviceInspections'
import {
  createDeviceMaintenancePlan, deleteDeviceMaintenancePlan, getDeviceMaintenancePlans, updateDeviceMaintenancePlan, type DeviceMaintenancePlan
} from '@/api/deviceMaintenances'
import { getDeviceCategories, getDeviceOptions, type DeviceCategoryItem } from '@/api/devices'

defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'changed'): void }>()

const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600', textAlign: 'center' as const }
const activeTab = ref('inspection')
const inspectionKeyword = ref('')
const maintenanceKeyword = ref('')
const inspectionLoading = ref(false)
const maintenanceLoading = ref(false)
const inspectionPlans = ref<DeviceInspectionPlan[]>([])
const maintenancePlans = ref<DeviceMaintenancePlan[]>([])
const categoryTree = ref<DeviceCategoryItem[]>([])
const deviceOptions = ref<Array<{ id: number | string; deviceCode: string; deviceName: string }>>([])
const inspectionFormVisible = ref(false)
const maintenanceFormVisible = ref(false)
const inspectionSubmitting = ref(false)
const maintenanceSubmitting = ref(false)
const inspectionEditingId = ref<number | string | null>(null)
const maintenanceEditingId = ref<number | string | null>(null)

const inspectionForm = reactive({
  planCode: '', planName: '', cycleType: 'daily', categoryId: undefined as number | string | undefined,
  deviceId: undefined as number | string | undefined, checkItems: [''] as string[], remark: '', enabled: true
})
const maintenanceForm = reactive({
  planCode: '', planName: '', cycleType: 'monthly', nextDueDate: '', categoryId: undefined as number | string | undefined,
  deviceId: undefined as number | string | undefined, maintenanceItems: [''] as string[], remark: '', enabled: true
})

const flatCategories = computed(() => flattenCategories(categoryTree.value))

function flattenCategories(items: DeviceCategoryItem[], acc: DeviceCategoryItem[] = []) {
  for (const item of items) {
    acc.push(item)
    if (item.children?.length) flattenCategories(item.children, acc)
  }
  return acc
}

async function loadAll() {
  await Promise.all([loadInspectionPlans(), loadMaintenancePlans(), loadMeta()])
}

async function loadMeta() {
  const [categories, devices] = await Promise.all([getDeviceCategories(), getDeviceOptions()])
  categoryTree.value = categories
  deviceOptions.value = devices
}

async function loadInspectionPlans() {
  inspectionLoading.value = true
  try {
    inspectionPlans.value = await getDeviceInspectionPlans({ keyword: inspectionKeyword.value.trim() || undefined })
  } finally {
    inspectionLoading.value = false
  }
}

async function loadMaintenancePlans() {
  maintenanceLoading.value = true
  try {
    maintenancePlans.value = await getDeviceMaintenancePlans({ keyword: maintenanceKeyword.value.trim() || undefined })
  } finally {
    maintenanceLoading.value = false
  }
}

function resetInspectionSearch() {
  inspectionKeyword.value = ''
  void loadInspectionPlans()
}

function resetMaintenanceSearch() {
  maintenanceKeyword.value = ''
  void loadMaintenancePlans()
}

function openInspectionForm(row?: DeviceInspectionPlan) {
  inspectionEditingId.value = row?.id ?? null
  inspectionForm.planCode = row?.planCode ?? ''
  inspectionForm.planName = row?.planName ?? ''
  inspectionForm.cycleType = row?.cycleType ?? 'daily'
  inspectionForm.categoryId = row?.categoryId
  inspectionForm.deviceId = row?.deviceId
  inspectionForm.checkItems = row?.checkItems?.length ? [...row.checkItems] : ['']
  inspectionForm.remark = row?.remark ?? ''
  inspectionFormVisible.value = true
}

function openMaintenanceForm(row?: DeviceMaintenancePlan) {
  maintenanceEditingId.value = row?.id ?? null
  maintenanceForm.planCode = row?.planCode ?? ''
  maintenanceForm.planName = row?.planName ?? ''
  maintenanceForm.cycleType = row?.cycleType ?? 'monthly'
  maintenanceForm.nextDueDate = row?.nextDueDate ?? ''
  maintenanceForm.categoryId = row?.categoryId
  maintenanceForm.deviceId = row?.deviceId
  maintenanceForm.maintenanceItems = row?.maintenanceItems?.length ? [...row.maintenanceItems] : ['']
  maintenanceForm.remark = row?.remark ?? ''
  maintenanceFormVisible.value = true
}

async function submitInspectionPlan() {
  const items = inspectionForm.checkItems.map((s) => s.trim()).filter(Boolean)
  if (!inspectionForm.planName.trim() || !items.length) {
    ElMessage.warning('请填写计划名称和至少一个点检项')
    return
  }
  inspectionSubmitting.value = true
  try {
    const payload = { ...inspectionForm, checkItems: items, planCode: inspectionForm.planCode.trim() || undefined }
    if (inspectionEditingId.value) await updateDeviceInspectionPlan(inspectionEditingId.value, payload)
    else await createDeviceInspectionPlan(payload)
    ElMessage.success('点检计划已保存')
    inspectionFormVisible.value = false
    await loadInspectionPlans()
    emit('changed')
  } catch (e) {
    console.error(e)
    ElMessage.error('保存失败')
  } finally {
    inspectionSubmitting.value = false
  }
}

async function submitMaintenancePlan() {
  const items = maintenanceForm.maintenanceItems.map((s) => s.trim()).filter(Boolean)
  if (!maintenanceForm.planName.trim() || !items.length) {
    ElMessage.warning('请填写计划名称和至少一个保养项')
    return
  }
  maintenanceSubmitting.value = true
  try {
    const payload = { ...maintenanceForm, maintenanceItems: items, planCode: maintenanceForm.planCode.trim() || undefined }
    if (maintenanceEditingId.value) await updateDeviceMaintenancePlan(maintenanceEditingId.value, payload)
    else await createDeviceMaintenancePlan(payload)
    ElMessage.success('保养计划已保存')
    maintenanceFormVisible.value = false
    await loadMaintenancePlans()
    emit('changed')
  } catch (e) {
    console.error(e)
    ElMessage.error('保存失败')
  } finally {
    maintenanceSubmitting.value = false
  }
}

async function removeInspectionItem(index: number) {
  const label = inspectionForm.checkItems[index]?.trim() || `第 ${index + 1} 项`
  const ok = await confirmDelete({
    title: '删除点检项',
    message: `确认删除点检项「${label}」？`
  })
  if (!ok) return
  inspectionForm.checkItems.splice(index, 1)
}

async function removeMaintenanceItem(index: number) {
  const label = maintenanceForm.maintenanceItems[index]?.trim() || `第 ${index + 1} 项`
  const ok = await confirmDelete({
    title: '删除保养项',
    message: `确认删除保养项「${label}」？`
  })
  if (!ok) return
  maintenanceForm.maintenanceItems.splice(index, 1)
}

async function removeInspectionPlan(row: DeviceInspectionPlan) {
  const ok = await confirmDelete({
    title: '删除点检计划',
    message: `确定删除点检计划「${row.planName}」？`
  })
  if (!ok) return
  await deleteDeviceInspectionPlan(row.id)
  ElMessage.success('已删除')
  await loadInspectionPlans()
  emit('changed')
}

async function removeMaintenancePlan(row: DeviceMaintenancePlan) {
  const ok = await confirmDelete({
    title: '删除保养计划',
    message: `确定删除保养计划「${row.planName}」？`
  })
  if (!ok) return
  await deleteDeviceMaintenancePlan(row.id)
  ElMessage.success('已删除')
  await loadMaintenancePlans()
  emit('changed')
}
</script>

<style scoped>
.plan-manager-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
}
.plan-manager-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 18px 22px 12px;
  border-bottom: 1px solid #f1f5f9;
}
.plan-manager-dialog :deep(.el-dialog__body) {
  padding: 12px 22px 20px;
  background: #f8fafc;
}
.plan-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.plan-tabs :deep(.el-tabs__item) {
  font-weight: 600;
  color: #64748b;
}
.plan-tabs :deep(.el-tabs__item.is-active) {
  color: #4f46e5;
}
.plan-tabs :deep(.el-tabs__active-bar) {
  background-color: #4f46e5;
  height: 3px;
  border-radius: 2px;
}
.plan-panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 16px 16px;
}
.plan-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}
.plan-toolbar__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.plan-search {
  width: 240px;
  max-width: 100%;
}
.plan-search-btn {
  border-radius: 18px !important;
  height: 36px !important;
  padding: 0 18px !important;
  font-weight: 600 !important;
}
.plan-reset-btn {
  border-radius: 18px !important;
  height: 36px !important;
  padding: 0 16px !important;
  font-weight: 600 !important;
  color: #64748b !important;
  border: 1px solid #e2e8f0 !important;
  background: #fff !important;
}
.plan-reset-btn:hover {
  color: #334155 !important;
  border-color: #cbd5e1 !important;
  background: #f8fafc !important;
}
.plan-search :deep(.el-input__wrapper) {
  border-radius: 20px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}
.plan-create-btn {
  border-radius: 18px !important;
  height: 38px !important;
  padding: 0 20px !important;
  font-weight: 600 !important;
  background: #0f172a !important;
  border: none !important;
}
.plan-create-btn:hover {
  background: #1e293b !important;
}
.plan-table :deep(.el-table__header .cell),
.plan-table :deep(.el-table__body .cell) {
  text-align: center;
}
.plan-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.plan-pill-btn {
  border-radius: 16px !important;
  padding: 4px 12px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  height: 26px !important;
  background: #fff !important;
  transition: all 0.2s ease !important;
}
.plan-pill-btn--edit {
  color: #4f46e5 !important;
  border: 1px solid #c7d2fe !important;
}
.plan-pill-btn--edit:hover {
  background: #eef2ff !important;
  border-color: #a5b4fc !important;
}
.plan-pill-btn--delete {
  color: #ef4444 !important;
  border: 1px solid #fecaca !important;
}
.plan-pill-btn--delete:hover {
  background: #fee2e2 !important;
  color: #b91c1c !important;
  border-color: #fca5a5 !important;
}
.full-width { width: 100%; }
.item-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.item-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.row-pill-btn {
  border-radius: 20px !important;
  padding: 4px 12px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  height: 26px !important;
  background: #fff !important;
  transition: all 0.2s ease !important;
  flex-shrink: 0;
}
.row-pill-btn--delete {
  color: #ef4444 !important;
  border: 1px solid #fecaca !important;
}
.row-pill-btn--delete:hover:not(:disabled) {
  background: #fee2e2 !important;
  color: #b91c1c !important;
  border-color: #fca5a5 !important;
}
.btn-add-item {
  align-self: flex-start;
  border-radius: 16px !important;
  border: 1px dashed #c7d2fe !important;
  color: #4f46e5 !important;
  background: #f8fafc !important;
  font-weight: 600 !important;
}
.btn-add-item:hover {
  border-color: #a5b4fc !important;
  background: #eef2ff !important;
}
.plan-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.plan-form-dialog .btn-cancel,
.plan-dialog-footer .btn-cancel {
  border-radius: 18px !important;
  font-weight: 600 !important;
}
.plan-form-dialog .btn-submit,
.plan-dialog-footer .btn-submit {
  border-radius: 18px !important;
  font-weight: 600 !important;
  background: #0f172a !important;
  border: none !important;
}
.plan-form-dialog .btn-submit:hover,
.plan-dialog-footer .btn-submit:hover {
  background: #1e293b !important;
}
.plan-form :deep(.el-input__wrapper),
.plan-form :deep(.el-textarea__inner) {
  border-radius: 10px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}
</style>
