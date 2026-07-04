<template>
  <div class="view-page">
    <AdminSubNav />
    <PageHeader title="工艺路线配置" subtitle="维护默认工艺路线，计划下发与工单生成将按此工序链创建记录。" />

    <el-card v-loading="loading" shadow="hover">
      <el-form label-width="96px" class="route-form">
        <el-form-item label="路线名称">
          <el-input v-model="form.routeName" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <div class="ops-header">
        <span>工序列表</span>
        <el-button size="small" @click="addOperation">添加工序</el-button>
      </div>

      <el-table :data="form.operations" border>
        <el-table-column label="序号" width="90">
          <template #default="{ row }">
            <el-input-number v-model="row.seqNo" :min="1" :max="99" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="工序名称" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.operationName" />
          </template>
        </el-table-column>
        <el-table-column label="标准工时(h)" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.standardHours" :min="0" :step="0.5" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ $index }">
            <el-button link type="danger" @click="form.operations.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="actions">
        <el-button type="primary" :loading="saving" @click="saveRoute">保存路线</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminSubNav from '@/components/admin/AdminSubNav.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { getDefaultProcessRoute, updateDefaultProcessRoute, type ProcessOperation } from '@/api/processRoutes'

const loading = ref(false)
const saving = ref(false)
const form = reactive<{ routeName: string; remark: string; operations: ProcessOperation[] }>({
  routeName: '标准装配路线',
  remark: '',
  operations: []
})

function addOperation() {
  form.operations.push({
    seqNo: form.operations.length + 1,
    operationName: '',
    standardHours: 1
  })
}

async function loadRoute() {
  loading.value = true
  try {
    const route = await getDefaultProcessRoute()
    form.routeName = route.routeName
    form.remark = route.remark ?? ''
    form.operations = (route.operations ?? []).map((item) => ({ ...item }))
  } catch (error) {
    console.error(error)
    ElMessage.error('加载工艺路线失败')
  } finally {
    loading.value = false
  }
}

async function saveRoute() {
  if (!form.routeName.trim()) {
    ElMessage.warning('请填写路线名称')
    return
  }
  if (!form.operations.length || form.operations.some((item) => !item.operationName?.trim())) {
    ElMessage.warning('请完善工序名称')
    return
  }
  saving.value = true
  try {
    await updateDefaultProcessRoute({
      routeName: form.routeName.trim(),
      remark: form.remark,
      operations: form.operations.map((item, index) => ({
        seqNo: item.seqNo ?? index + 1,
        operationName: item.operationName.trim(),
        standardHours: item.standardHours,
        remark: item.remark
      }))
    })
    ElMessage.success('工艺路线已保存')
    await loadRoute()
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadRoute)
</script>

<style scoped>
.route-form { max-width: 640px; margin-bottom: 8px; }
.ops-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0 12px;
  font-weight: 600;
  color: #334155;
}
.actions { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
