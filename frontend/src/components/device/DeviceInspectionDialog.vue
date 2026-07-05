<template>
  <el-dialog
    :model-value="visible"
    :title="`设备点检 — ${deviceName || ''}`"
    width="720px"
    append-to-body
    destroy-on-close
    class="device-inspection-dialog"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="dialog-body">
      <el-form label-width="88px" class="inspection-form">
        <el-form-item label="点检计划">
          <el-select v-model="selectedPlanId" clearable placeholder="选择计划自动填充点检项" class="full-width" :loading="loading" :disabled="loading" @change="applyPlan">
            <el-option v-for="plan in plans" :key="plan.id" :label="`${plan.planName}（${plan.cycleTypeLabel || plan.cycleType}）`" :value="plan.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table :data="items" border stripe class="dialog-table" :header-cell-style="tableHeaderStyle">
        <el-table-column prop="itemName" label="点检项目" min-width="180" align="center" header-align="center">
          <template #default="{ row }">
            <el-input v-model="row.itemName" placeholder="点检项名称" />
          </template>
        </el-table-column>
        <el-table-column label="是否正常" width="130" align="center" header-align="center">
          <template #default="{ row }">
            <el-switch v-model="row.isNormal" active-text="正常" inactive-text="异常" inline-prompt />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160" align="center" header-align="center">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="86" align="center" header-align="center">
          <template #default="{ $index }">
            <el-button size="small" class="row-pill-btn row-pill-btn--delete" :disabled="items.length <= 1" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="dialog-toolbar">
        <el-button class="btn-add-item" @click="addItem">+ 添加点检项</el-button>
      </div>

      <el-form label-width="88px" class="remark-form">
        <el-form-item label="整体备注">
          <el-input v-model="remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="emit('update:visible', false)">取消</el-button>
        <el-button type="primary" class="btn-submit" :loading="submitting" @click="submit">提交点检</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirmDelete'
import {
  getDeviceInspectionPlansForDevice,
  submitDeviceInspection,
  type DeviceInspectionItemResult,
  type DeviceInspectionPlan
} from '@/api/deviceInspections'

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
const plans = ref<DeviceInspectionPlan[]>([])
const selectedPlanId = ref<number | string | undefined>()
const items = ref<DeviceInspectionItemResult[]>([{ itemName: '', isNormal: true, remark: '' }])
const remark = ref('')

function addItem() {
  items.value.push({ itemName: '', isNormal: true, remark: '' })
}

async function removeItem(index: number) {
  const item = items.value[index]
  const label = item.itemName?.trim() || `第 ${index + 1} 项`
  const ok = await confirmDelete({
    title: '删除点检项',
    message: `确认删除点检项「${label}」？`
  })
  if (!ok) return
  items.value.splice(index, 1)
}

function applyPlan(planId?: number | string) {
  if (!planId) return
  const plan = plans.value.find((p) => p.id === planId)
  if (!plan?.checkItems?.length) return
  items.value = plan.checkItems.map((name) => ({ itemName: name, isNormal: true, remark: '' }))
}

async function loadDialogData() {
  selectedPlanId.value = undefined
  remark.value = ''
  items.value = [{ itemName: '', isNormal: true, remark: '' }]
  plans.value = []
  loading.value = true
  try {
    plans.value = await getDeviceInspectionPlansForDevice(props.deviceId)
    if (plans.value.length === 1) {
      selectedPlanId.value = plans.value[0].id
      applyPlan(plans.value[0].id)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('加载点检计划失败')
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
    ElMessage.warning('请至少填写一个点检项')
    return
  }
  submitting.value = true
  try {
    await submitDeviceInspection({
      deviceId: props.deviceId,
      planId: selectedPlanId.value,
      items: validItems.map((item) => ({
        itemName: item.itemName.trim(),
        isNormal: item.isNormal,
        remark: item.remark?.trim() || undefined
      })),
      remark: remark.value.trim() || undefined
    })
    emit('update:visible', false)
    emit('success')
  } catch (error) {
    console.error(error)
    ElMessage.error('提交点检失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.full-width { width: 100%; }
.dialog-body { min-height: 120px; }
.dialog-toolbar { margin-top: 10px; }
.remark-form { margin-top: 12px; }
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.btn-add-item {
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
.btn-cancel {
  border-radius: 18px !important;
  font-weight: 600 !important;
}
.btn-submit {
  border-radius: 18px !important;
  font-weight: 600 !important;
  background: #0f172a !important;
  border: none !important;
}
.btn-submit:hover {
  background: #1e293b !important;
}
.dialog-table :deep(.el-table__header .cell),
.dialog-table :deep(.el-table__body .cell) {
  text-align: center;
}
.dialog-table :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}
.dialog-table :deep(.el-input__inner) {
  text-align: center;
}
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
.inspection-form :deep(.el-textarea__inner),
.remark-form :deep(.el-textarea__inner) {
  border-radius: 12px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}
</style>
