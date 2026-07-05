<template>
  <el-dialog
    :model-value="visible"
    :title="`完成维修 — ${repair?.repairNo || ''}`"
    width="560px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="resetForm"
  >
    <el-descriptions v-if="repair" :column="1" border class="repair-brief">
      <el-descriptions-item label="故障原因">{{ repair.faultReason }}</el-descriptions-item>
      <el-descriptions-item label="报修人">{{ repair.reporterName || '--' }}</el-descriptions-item>
      <el-descriptions-item label="报修时间">{{ formatTime(repair.reportTime) }}</el-descriptions-item>
    </el-descriptions>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" style="margin-top: 16px;">
      <el-form-item label="维修措施" prop="repairAction">
        <el-input v-model="form.repairAction" type="textarea" :rows="3" placeholder="描述采取的维修措施" />
      </el-form-item>
      <el-form-item label="维修结果" prop="repairResult">
        <el-input v-model="form.repairResult" placeholder="如：已恢复正常" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button v-if="repair?.status === 'open'" :loading="starting" @click="startRepair">开始维修</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">完成维修</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { completeDeviceRepair, startDeviceRepair, type DeviceRepairOrder } from '@/api/deviceRepairs'

const props = defineProps<{
  visible: boolean
  repair: DeviceRepairOrder | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
  (e: 'started', repair: DeviceRepairOrder): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const starting = ref(false)
const form = reactive({
  repairAction: '',
  repairResult: '',
  remark: ''
})

const rules: FormRules = {
  repairAction: [{ required: true, message: '请输入维修措施', trigger: 'blur' }],
  repairResult: [{ required: true, message: '请输入维修结果', trigger: 'blur' }]
}

function formatTime(value?: string) {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 19)
}

function resetForm() {
  form.repairAction = ''
  form.repairResult = ''
  form.remark = ''
}

async function startRepair() {
  if (!props.repair) return
  starting.value = true
  try {
    const updated = await startDeviceRepair(props.repair.id)
    ElMessage.success('已开始维修')
    emit('started', updated)
  } catch (error) {
    console.error(error)
    ElMessage.error('开始维修失败')
  } finally {
    starting.value = false
  }
}

async function submit() {
  if (!props.repair) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await completeDeviceRepair(props.repair.id, {
      repairAction: form.repairAction.trim(),
      repairResult: form.repairResult.trim(),
      remark: form.remark.trim() || undefined
    })
    ElMessage.success('维修已完成')
    emit('update:visible', false)
    emit('success')
  } catch (error) {
    console.error(error)
    ElMessage.error('完成维修失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.repair-brief { margin-bottom: 4px; }
</style>
