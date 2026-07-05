<template>
  <el-dialog
    :model-value="visible"
    :title="`设备保养 — ${deviceName || ''}`"
    width="720px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading">
      <el-alert v-if="duePlans.length" type="warning" :closable="false" show-icon class="due-alert">
        该设备有 {{ duePlans.length }} 项保养计划已到期，请尽快执行。
      </el-alert>

      <el-form label-width="88px" class="maintenance-form">
        <el-form-item label="保养计划">
          <el-select v-model="selectedPlanId" clearable placeholder="选择计划自动填充保养项" class="full-width" :loading="loading" :disabled="loading" @change="applyPlan">
            <el-option
              v-for="plan in plans"
              :key="plan.id"
              :label="`${plan.planName}（${plan.cycleTypeLabel || plan.cycleType}${plan.overdue ? ' · 已到期' : ''}）`"
              :value="plan.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table :data="items" border stripe :header-cell-style="tableHeaderStyle">
        <el-table-column prop="itemName" label="保养项目" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.itemName" placeholder="保养项名称" />
          </template>
        </el-table-column>
        <el-table-column label="是否完成" width="120" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.done" active-text="完成" inactive-text="未完成" inline-prompt />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="" width="86" align="center">
          <template #default="{ $index }">
            <el-button size="small" class="row-pill-btn row-pill-btn--delete" :disabled="items.length <= 1" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="dialog-toolbar">
        <el-button @click="addItem">添加保养项</el-button>
      </div>

      <el-form label-width="88px" style="margin-top: 12px;">
        <el-form-item label="整体备注">
          <el-input v-model="remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交保养</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirmDelete'
import {
  getDeviceMaintenancePlansForDevice,
  getDueDeviceMaintenancePlans,
  submitDeviceMaintenance,
  type DeviceMaintenanceItemResult,
  type DeviceMaintenancePlan
} from '@/api/deviceMaintenances'

const props = defineProps<{
  visible: boolean
  deviceId: number | string
  deviceName?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600', textAlign: 'center' as const }
const loading = ref(false)
const submitting = ref(false)
const plans = ref<DeviceMaintenancePlan[]>([])
const duePlans = ref<DeviceMaintenancePlan[]>([])
const selectedPlanId = ref<number | string | undefined>()
const items = ref<DeviceMaintenanceItemResult[]>([{ itemName: '', done: true, remark: '' }])
const remark = ref('')

function addItem() {
  items.value.push({ itemName: '', done: true, remark: '' })
}

async function removeItem(index: number) {
  const item = items.value[index]
  const label = item.itemName?.trim() || `第 ${index + 1} 项`
  const ok = await confirmDelete({
    title: '删除保养项',
    message: `确认删除保养项「${label}」？`
  })
  if (!ok) return
  items.value.splice(index, 1)
}

function applyPlan(planId?: number | string) {
  if (!planId) return
  const plan = plans.value.find((p) => p.id === planId)
  if (!plan?.maintenanceItems?.length) return
  items.value = plan.maintenanceItems.map((name) => ({ itemName: name, done: true, remark: '' }))
}

async function loadDialogData() {
  selectedPlanId.value = undefined
  remark.value = ''
  items.value = [{ itemName: '', done: true, remark: '' }]
  plans.value = []
  duePlans.value = []
  loading.value = true
  try {
    const [planList, dueList] = await Promise.all([
      getDeviceMaintenancePlansForDevice(props.deviceId),
      getDueDeviceMaintenancePlans(props.deviceId)
    ])
    plans.value = planList
    duePlans.value = dueList
    const preferred = dueList[0] ?? planList[0]
    if (preferred) {
      selectedPlanId.value = preferred.id
      applyPlan(preferred.id)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('加载保养计划失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) void loadDialogData()
  },
  { immediate: true }
)

async function submit() {
  const validItems = items.value.filter((item) => item.itemName?.trim())
  if (!validItems.length) {
    ElMessage.warning('请至少填写一个保养项')
    return
  }
  submitting.value = true
  try {
    await submitDeviceMaintenance({
      deviceId: props.deviceId,
      planId: selectedPlanId.value,
      items: validItems.map((item) => ({
        itemName: item.itemName.trim(),
        done: item.done,
        remark: item.remark?.trim() || undefined
      })),
      remark: remark.value.trim() || undefined
    })
    emit('update:visible', false)
    emit('success')
  } catch (error) {
    console.error(error)
    ElMessage.error('提交保养失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.full-width { width: 100%; }
.dialog-toolbar { margin-top: 8px; }
.due-alert { margin-bottom: 12px; }
.row-pill-btn {
  border-radius: 20px !important;
  padding: 4px 12px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  height: 26px !important;
  background: #fff !important;
  transition: all 0.2s ease !important;
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
</style>
