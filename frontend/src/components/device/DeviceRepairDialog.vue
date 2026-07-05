<template>
  <el-dialog
    :model-value="visible"
    :title="`设备报修 — ${deviceName || ''}`"
    width="560px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
      <el-form-item label="故障原因" prop="faultReason">
        <el-input v-model="form.faultReason" placeholder="如：伺服电机异响" />
      </el-form-item>
      <el-form-item label="故障代码">
        <el-input v-model="form.faultCode" placeholder="可选，如 E-SERVO-01" />
      </el-form-item>
      <el-form-item label="故障描述">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="详细描述故障现象" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="danger" :loading="submitting" @click="submit">提交报修</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createDeviceRepair } from '@/api/deviceRepairs'

const props = defineProps<{
  visible: boolean
  deviceId: number | string
  deviceName?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  faultReason: '',
  faultCode: '',
  description: '',
  remark: ''
})

const rules: FormRules = {
  faultReason: [{ required: true, message: '请输入故障原因', trigger: 'blur' }]
}

function resetForm() {
  form.faultReason = ''
  form.faultCode = ''
  form.description = ''
  form.remark = ''
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createDeviceRepair({
      deviceId: props.deviceId,
      faultReason: form.faultReason.trim(),
      faultCode: form.faultCode.trim() || undefined,
      description: form.description.trim() || undefined,
      remark: form.remark.trim() || undefined
    })
    ElMessage.success('维修单已创建')
    emit('update:visible', false)
    emit('success')
  } catch (error) {
    console.error(error)
    ElMessage.error('提交报修失败')
  } finally {
    submitting.value = false
  }
}
</script>
