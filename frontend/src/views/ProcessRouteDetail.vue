<template>
  <div :class="['view-page', { 'is-prop-mode': isPropMode }]" v-loading="loading">
    <PageHeader v-if="!isPropMode" :title="isNew ? '新建工艺' : (form.routeName || '工艺详情')" :subtitle="form.routeCode || '配置工序、参数、设备、物料与SOP'">
      <el-button @click="handleBack">返回</el-button>
    </PageHeader>

    <el-alert v-if="form.status === 'rejected' && form.rejectedReason" type="error" :closable="false" class="status-alert">
      审批驳回：{{ form.rejectedReason }}
    </el-alert>
    <el-alert v-else-if="form.status === 'pending_approval'" type="warning" :closable="false" class="status-alert">
      工艺已提交审批，等待主管审核
    </el-alert>

    <div :class="['detail-content-card', { 'is-flat': isPropMode }]">
      <div class="status-bar-premium">
        <el-tag :type="routeStatusType(form.status)" effect="dark" round class="status-badge">
          {{ routeStatusLabel(form.status) }}
        </el-tag>
        <el-tag v-if="form.isDefault" type="primary" effect="light" round class="status-badge">
          默认工艺
        </el-tag>
        <el-tag type="info" effect="plain" round class="status-badge version-badge">
          版本 {{ form.version || 'V1.0' }}
        </el-tag>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <div class="tab-content-box">
            <el-form label-width="96px" class="basic-form">
              <el-row :gutter="16">
                <el-col :span="12"><el-form-item label="工艺编号"><el-input v-model="form.routeCode" placeholder="留空自动生成" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="工艺名称" required><el-input v-model="form.routeName" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="适用产品"><el-input v-model="form.productName" placeholder="留空表示通用工艺" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="当前版本"><el-tag round>{{ form.version || 'V1.0' }}</el-tag></el-form-item></el-col>
                <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
              </el-row>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`工序 (${form.operations.length})`" name="operations">
          <div class="ops-toolbar">
            <span>配置工序后可在行内维护参数、设备、物料与 SOP</span>
            <el-button size="small" @click="addOperation">添加工序</el-button>
          </div>
          <el-table :data="form.operations" border stripe>
            <el-table-column prop="seqNo" label="序号" width="70" align="center" header-align="center" />
            <el-table-column prop="operationCode" label="工序编号" width="120" align="center" header-align="center" />
            <el-table-column prop="operationName" label="工序名称" min-width="140" align="center" header-align="center" show-overflow-tooltip />
            <el-table-column label="标准工时" width="110" align="center" header-align="center">
              <template #default="{ row }">
                <span class="standard-hours-text">{{ row.standardHours ?? 0 }}h</span>
                <div v-if="row.prepHours > 0 || row.changeoverHours > 0" class="extra-hours-text">
                  (准:{{ row.prepHours }}h/换:{{ row.changeoverHours }}h)
                </div>
              </template>
            </el-table-column>
            <el-table-column label="绑定" width="260" align="center" header-align="center">
              <template #default="{ row }">
                <div class="bind-badges">
                  <span class="bind-badge category" :class="{ active: getCategoryBindCount(row) > 0 }">
                    分类 {{ getCategoryBindCount(row) }}
                  </span>
                  <span class="bind-badge device" :class="{ active: getDeviceBindCount(row) > 0 }">
                    设备 {{ getDeviceBindCount(row) }}
                  </span>
                  <span class="bind-badge material" :class="{ active: getBindCount(row, 'material') > 0 }">
                    物料 {{ getBindCount(row, 'material') }}
                  </span>
                  <span class="bind-badge sop" :class="{ active: getBindCount(row, 'sop') > 0 }">
                    SOP {{ getBindCount(row, 'sop') }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="380" fixed="right" align="center" header-align="center">
              <template #default="{ row, $index }">
                <div class="table-actions-flex">
                  <el-button size="small" class="op-pill-btn btn-edit" @click="openOpEdit(row, $index)">编辑</el-button>
                  <el-button size="small" class="op-pill-btn btn-param" @click="editParameters(row)">参数</el-button>
                  <el-button size="small" class="op-pill-btn btn-device" @click="editDevices(row)">设备</el-button>
                  <el-button size="small" class="op-pill-btn btn-material" @click="editMaterials(row)">物料</el-button>
                  <el-button size="small" class="op-pill-btn btn-sop" @click="editSops(row)">SOP</el-button>
                  <el-button size="small" class="op-pill-btn btn-delete" @click="removeOperation(row, $index)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="!isNew" :label="`版本记录 (${history.length})`" name="history">
          <div class="tab-content-box history-panel">
            <div v-if="history.length" class="history-list">
              <div
                v-for="(item, index) in history"
                :key="item.id"
                class="history-card"
                :class="{ 'is-last': index === history.length - 1 }"
              >
                <div class="history-card__rail">
                  <div class="history-card__dot" :class="routingActionClass(item.actionType)" />
                  <div v-if="index < history.length - 1" class="history-card__line" />
                </div>
                <div class="history-card__content">
                  <div class="history-card__row">
                    <span class="history-badge" :class="routingActionClass(item.actionType)">
                      {{ routingActionLabel(item.actionType) }}
                    </span>
                    <el-tag v-if="item.version" size="small" effect="plain" round class="history-version-tag">
                      {{ item.version }}
                    </el-tag>
                    <el-tooltip
                      v-if="item.actionDesc"
                      :content="item.actionDesc"
                      placement="top"
                      :show-after="200"
                      :disabled="item.actionDesc.length < 48"
                    >
                      <span class="history-card__desc">{{ item.actionDesc }}</span>
                    </el-tooltip>
                    <span v-if="item.operatorName" class="history-card__operator">{{ item.operatorName }}</span>
                    <span class="history-card__time">{{ formatTime(item.createTime) }}</span>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无版本记录" />
          </div>
        </el-tab-pane>
      </el-tabs>

      <div class="footer-actions">
        <el-button :loading="saving" @click="saveRoute('draft')">保存草稿</el-button>
        <el-button v-if="canSubmit" type="warning" plain :loading="saving" @click="saveRoute('submit')">提交审批</el-button>
        <el-button v-if="canPublish" type="primary" :loading="saving" @click="saveRoute('publish')">保存并发布</el-button>
        <el-button v-if="canApprove" type="success" :loading="saving" @click="handleApprove">审批通过</el-button>
        <el-button v-if="canApprove" type="danger" plain @click="rejectDialogVisible = true">驳回</el-button>
      </div>
    </div>

    <!-- 工序属性编辑弹窗 -->
    <el-dialog v-model="opEditDialogVisible" title="编辑工序" width="500px" append-to-body>
      <el-form :model="opForm" label-width="96px">
        <el-form-item label="序号" required>
          <el-input-number v-model="opForm.seqNo" :min="1" :max="99" :controls="false" class="full-width" />
        </el-form-item>
        <el-form-item label="工序编号" required>
          <el-input v-model="opForm.operationCode" placeholder="请输入工序编号，例如 OP10" />
        </el-form-item>
        <el-form-item label="工序名称" required>
          <el-input v-model="opForm.operationName" placeholder="请输入工序名称，例如 装配" />
        </el-form-item>
        <el-form-item label="标准工时(h)">
          <el-input-number v-model="opForm.standardHours" :min="0" :step="0.5" :controls="false" class="full-width" />
        </el-form-item>
        <el-form-item label="准备工时(h)">
          <el-input-number v-model="opForm.prepHours" :min="0" :step="0.1" :controls="false" class="full-width" />
        </el-form-item>
        <el-form-item label="换型工时(h)">
          <el-input-number v-model="opForm.changeoverHours" :min="0" :step="0.1" :controls="false" class="full-width" />
        </el-form-item>
        <el-form-item label="报工设置">
          <el-checkbox v-model="opForm.needReport" :true-label="1" :false-label="0">需要报工</el-checkbox>
          <el-checkbox v-model="opForm.needCheck" :true-label="1" :false-label="0">需要质检</el-checkbox>
          <el-checkbox v-model="opForm.needScan" :true-label="1" :false-label="0">需要扫码</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="opEditDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveOpEdit">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 参数 -->
    <el-dialog v-model="paramDialogVisible" :title="`工艺参数 - ${activeOperation?.operationName || ''}`" width="760px" append-to-body>
      <div class="dialog-toolbar">
        <el-button size="small" class="op-pill-btn btn-add" @click="addParameter">添加参数</el-button>
      </div>
      <el-table :data="activeOperation?.parameters || []" border class="param-table">
        <el-table-column label="参数名" min-width="160" header-align="center"><template #default="{ row }"><el-input v-model="row.paramName" /></template></el-table-column>
        <el-table-column label="标准值" width="110" align="center"><template #default="{ row }"><el-input v-model="row.paramValue" /></template></el-table-column>
        <el-table-column label="下限" width="100" align="center"><template #default="{ row }"><el-input v-model="row.minValue" /></template></el-table-column>
        <el-table-column label="上限" width="100" align="center"><template #default="{ row }"><el-input v-model="row.maxValue" /></template></el-table-column>
        <el-table-column label="单位" width="90" align="center"><template #default="{ row }"><el-input v-model="row.unit" /></template></el-table-column>
        <el-table-column label="操作" width="95" align="center">
          <template #default="{ $index }">
            <el-button size="small" class="op-pill-btn btn-delete" @click="removeParameter($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 设备绑定 -->
    <el-dialog v-model="deviceDialogVisible" :title="`设备绑定 - ${activeOperation?.operationName || ''}`" width="560px" append-to-body>
      <el-form label-width="96px">
        <el-form-item label="设备分类">
          <el-select v-model="deviceForm.categoryIds" multiple filterable placeholder="按分类允许设备" class="full-width">
            <el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="具体设备">
          <el-select v-model="deviceForm.deviceIds" multiple filterable placeholder="指定可用设备" class="full-width">
            <el-option v-for="item in deviceOptions" :key="item.id" :label="`${item.deviceName} (${item.deviceCode})`" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deviceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDeviceBindings">确定</el-button>
      </template>
    </el-dialog>

    <!-- 物料绑定 -->
    <el-dialog v-model="materialDialogVisible" :title="`物料绑定 - ${activeOperation?.operationName || ''}`" width="840px" append-to-body>
      <div class="dialog-toolbar">
        <el-button size="small" class="op-pill-btn btn-add" @click="addMaterialRow">添加物料</el-button>
      </div>
      <el-table :data="materialForm.rows" border class="param-table">
        <el-table-column label="物料" min-width="220" header-align="center">
          <template #default="{ row }">
            <el-select
              v-model="row.materialId"
              filterable
              placeholder="选择物料"
              class="full-width"
              @change="(id: string | number) => onMaterialRowChange(row, id)"
            >
              <el-option v-for="item in materialOptions" :key="item.id" :label="`${item.materialName} (${item.materialCode})`" :value="item.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="用量" width="140" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0.0001" :step="0.1" :controls="false" class="full-width" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="90" align="center">
          <template #default="{ row }">
            <el-input v-model="row.unit" placeholder="自动带出" readonly />
          </template>
        </el-table-column>
        <el-table-column label="类型" width="130" align="center">
          <template #default="{ row }">
            <el-select v-model="row.materialType" class="full-width">
              <el-option label="原材料" value="raw" />
              <el-option label="半成品" value="semi" />
              <el-option label="辅料" value="aux" />
              <el-option label="工装" value="tooling" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="95" align="center">
          <template #default="{ $index }">
            <el-button size="small" class="op-pill-btn btn-delete" @click="removeMaterialRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="materialDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMaterialBindings">确定</el-button>
      </template>
    </el-dialog>

    <!-- SOP -->
    <el-dialog v-model="sopDialogVisible" :title="`SOP - ${activeOperation?.operationName || ''}`" width="640px" append-to-body>
      <div v-if="!activeOperation?.id" class="sop-hint">请先保存工艺草稿后再上传 SOP</div>
      <template v-else>
        <div class="dialog-toolbar">
          <el-upload :show-file-list="false" :http-request="handleSopUpload" accept=".pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.doc,.docx,.txt">
            <el-button size="small" class="op-pill-btn btn-add" :loading="sopUploading">上传 SOP</el-button>
          </el-upload>
        </div>
        <el-table :data="activeOperation?.sops || []" border class="param-table">
          <el-table-column prop="fileName" label="文件名" min-width="160" header-align="center" />
          <el-table-column label="类型" width="90" align="center">
            <template #default="{ row }">
              <span class="sop-type-badge">{{ row.fileType }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row }">
              <div class="table-actions-flex">
                <el-button size="small" class="op-pill-btn btn-sop" @click="previewSop(row)">预览</el-button>
                <el-button size="small" class="op-pill-btn btn-delete" @click="removeSop(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>

    <!-- SOP 预览 -->
    <el-dialog v-model="sopPreviewVisible" :title="previewSopItem?.fileName || 'SOP 预览'" width="80%" top="5vh" append-to-body>
      <div v-loading="previewLoading" class="sop-preview">
        <img v-if="previewBlobUrl && previewSopItem?.fileType === 'image'" :src="previewBlobUrl" alt="SOP" />
        <iframe v-else-if="previewBlobUrl && previewSopItem?.fileType === 'pdf'" :src="previewBlobUrl" />
        <video v-else-if="previewBlobUrl && previewSopItem?.fileType === 'video'" :src="previewBlobUrl" controls />
        <div v-else-if="previewBlobUrl" class="sop-download-hint">
          <el-link :href="previewBlobUrl" target="_blank" type="primary">点击下载/打开文件</el-link>
        </div>
      </div>
    </el-dialog>

    <!-- 驳回 -->
    <el-dialog v-model="rejectDialogVisible" title="驳回工艺" width="480px" append-to-body>
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入驳回原因" />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="handleReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { confirmDelete } from '@/utils/confirmDelete'
import { getDeviceCategories, getDeviceOptions } from '@/api/devices'
import { getMaterialOptions } from '@/api/materials'
import { useUserStore } from '@/stores/user'
import { TOKEN_STORAGE_KEY } from '@/api/request'
import {
  approveProcessRoute, buildOperationPayload, createProcessRoute, deleteOperationSop,
  getProcessRoute, rejectProcessRoute, routeStatusLabel, routeStatusType,
  updateProcessRoute, uploadOperationSop,
  type ProcessOperation, type ProcessRouteHistory, type ProcessSop, type SaveMode
} from '@/api/processRoutes'

const props = defineProps<{
  id?: string | number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'save-success'): void
}>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const sopUploading = ref(false)
const previewLoading = ref(false)
const activeTab = ref('basic')
const paramDialogVisible = ref(false)
const deviceDialogVisible = ref(false)
const materialDialogVisible = ref(false)
const sopDialogVisible = ref(false)
const sopPreviewVisible = ref(false)
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const activeOperation = ref<ProcessOperation | null>(null)
const history = ref<ProcessRouteHistory[]>([])
const deviceOptions = ref<Array<{ id: number | string; deviceName: string; deviceCode: string }>>([])
const categories = ref<Array<{ id: number | string; categoryName: string }>>([])
const materialOptions = ref<Array<{ id: number | string; materialCode: string; materialName: string; unit: string }>>([])
const previewSopItem = ref<ProcessSop | null>(null)
const previewBlobUrl = ref('')

// 工序编辑弹窗状态与方法
const opEditDialogVisible = ref(false)
const editingOpIndex = ref<number>(-1)
const opForm = ref({
  seqNo: 1,
  operationCode: '',
  operationName: '',
  standardHours: 1,
  prepHours: 0,
  changeoverHours: 0,
  needReport: 1,
  needCheck: 0,
  needScan: 0
})

function openOpEdit(row: ProcessOperation, index: number) {
  editingOpIndex.value = index
  opForm.value = {
    seqNo: row.seqNo,
    operationCode: row.operationCode || '',
    operationName: row.operationName || '',
    standardHours: row.standardHours ?? 1,
    prepHours: row.prepHours ?? 0,
    changeoverHours: row.changeoverHours ?? 0,
    needReport: row.needReport ?? 1,
    needCheck: row.needCheck ?? 0,
    needScan: row.needScan ?? 0
  }
  opEditDialogVisible.value = true
}

function saveOpEdit() {
  if (editingOpIndex.value > -1) {
    const op = form.operations[editingOpIndex.value]
    op.seqNo = opForm.value.seqNo
    op.operationCode = opForm.value.operationCode
    op.operationName = opForm.value.operationName
    op.standardHours = opForm.value.standardHours
    op.prepHours = opForm.value.prepHours
    op.changeoverHours = opForm.value.changeoverHours
    op.needReport = opForm.value.needReport
    op.needCheck = opForm.value.needCheck
    op.needScan = opForm.value.needScan
  } else {
    const newOp: ProcessOperation = {
      seqNo: opForm.value.seqNo,
      operationCode: opForm.value.operationCode || `OP${String((form.operations.length + 1) * 10).padStart(2, '0')}`,
      operationName: opForm.value.operationName,
      standardHours: opForm.value.standardHours,
      prepHours: opForm.value.prepHours,
      changeoverHours: opForm.value.changeoverHours,
      needReport: opForm.value.needReport,
      needCheck: opForm.value.needCheck,
      needScan: opForm.value.needScan,
      parameters: [],
      deviceIds: [],
      categoryIds: [],
      materials: [],
      sops: []
    }
    form.operations.push(newOp)
  }
  opEditDialogVisible.value = false
}

const deviceForm = reactive({ deviceIds: [] as Array<number | string>, categoryIds: [] as Array<number | string> })
const materialForm = reactive({ rows: [] as Array<{ materialId: number | string; qty: number; unit: string; materialType: string; remark?: string }> })

const isPropMode = computed(() => props.id != null && props.id !== '')

const routeId = computed(() => {
  if (isPropMode.value) {
    return String(props.id)
  }
  return route.params.id as string
})

const isNew = computed(() => routeId.value === 'new')
const canApprove = computed(() => !isNew.value && form.status === 'pending_approval' && userStore.canAccessPermission('工艺审批'))
const canSubmit = computed(() => isNew.value || form.status === 'draft' || form.status === 'rejected')
const canPublish = computed(() => userStore.canAccessPermission('工艺审批') || userStore.fullAccess)

const form = reactive({
  routeCode: '',
  routeName: '',
  productName: '',
  version: 'V1.0',
  status: 'draft',
  rejectedReason: '',
  isDefault: false,
  remark: '',
  operations: [] as ProcessOperation[]
})

function formatTime(value?: string) {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 19)
}

function routingActionLabel(actionType?: string) {
  switch (actionType) {
    case 'create': return '新建'
    case 'publish': return '发布'
    case 'submit': return '提交审批'
    case 'approve': return '审批通过'
    case 'reject': return '审批驳回'
    case 'update': return '更新'
    case 'status': return '状态变更'
    case 'sop': return 'SOP'
    case 'default': return '设默认'
    default: return actionType ?? '操作'
  }
}

function routingActionClass(actionType?: string) {
  switch (actionType) {
    case 'create': return 'action-create'
    case 'publish':
    case 'approve': return 'action-handle'
    case 'submit': return 'action-status'
    case 'reject': return 'action-exception'
    case 'update': return 'action-update'
    case 'status': return 'action-status'
    case 'sop': return 'action-inspection'
    case 'default': return 'action-maintenance'
    default: return 'action-default'
  }
}

function bindSummary(row: ProcessOperation) {
  const cat = getCategoryBindCount(row)
  const dev = getDeviceBindCount(row)
  const mat = row.materials?.length ?? 0
  const sop = row.sops?.length ?? 0
  const devicePart = cat && dev ? `分类${cat}+设备${dev}` : cat ? `分类${cat}` : dev ? `设备${dev}` : '设备0'
  return `${devicePart} · 物料${mat} · SOP${sop}`
}

function getCategoryBindCount(row: ProcessOperation) {
  return row.categoryIds?.length ?? row.devices?.filter((d) => d.bindType === 'category').length ?? 0
}

function getDeviceBindCount(row: ProcessOperation) {
  return row.deviceIds?.length ?? row.devices?.filter((d) => d.bindType === 'device').length ?? 0
}

function getBindCount(row: ProcessOperation, type: 'material' | 'sop') {
  if (type === 'material') {
    return row.materials?.length ?? 0
  }
  if (type === 'sop') {
    return row.sops?.length ?? 0
  }
  return 0
}

async function removeOperation(row: ProcessOperation, index: number) {
  const label = [row.operationCode, row.operationName].filter(Boolean).join(' · ') || `第 ${index + 1} 道工序`
  const bindParts: string[] = []
  if (row.parameters?.length) bindParts.push(`${row.parameters.length} 个参数`)
  const categoryCount = getCategoryBindCount(row)
  const deviceCount = getDeviceBindCount(row)
  const materialCount = getBindCount(row, 'material')
  const sopCount = getBindCount(row, 'sop')
  if (categoryCount) bindParts.push(`${categoryCount} 个设备分类`)
  if (deviceCount) bindParts.push(`${deviceCount} 台具体设备`)
  if (materialCount) bindParts.push(`${materialCount} 项物料`)
  if (sopCount) bindParts.push(`${sopCount} 个 SOP`)
  const bindHint = bindParts.length ? `关联的 ${bindParts.join('、')} 将一并移除。` : ''

  const ok = await confirmDelete({
    title: '删除工序',
    message: `确认删除工序「${label}」？${bindHint ? `\n${bindHint}` : ''}\n删除后请点击「保存草稿」或「保存并发布」生效。`
  })
  if (!ok) return
  form.operations.splice(index, 1)
  ElMessage.success('工序已移除')
}

async function removeParameter(index: number) {
  const param = activeOperation.value?.parameters?.[index]
  const label = param?.paramName?.trim() || `第 ${index + 1} 个参数`
  const ok = await confirmDelete({
    title: '删除参数',
    message: `确认删除参数「${label}」？`
  })
  if (!ok) return
  activeOperation.value?.parameters?.splice(index, 1)
}

async function removeMaterialRow(index: number) {
  const row = materialForm.rows[index]
  const material = materialOptions.value.find((m) => String(m.id) === String(row?.materialId))
  const label = material ? `${material.materialName} (${material.materialCode})` : `第 ${index + 1} 行`
  const ok = await confirmDelete({
    title: '删除物料',
    message: `确认删除物料「${label}」？`
  })
  if (!ok) return
  materialForm.rows.splice(index, 1)
}

function addOperation() {
  editingOpIndex.value = -1
  opForm.value = {
    seqNo: form.operations.length + 1,
    operationCode: `OP${String((form.operations.length + 1) * 10).padStart(2, '0')}`,
    operationName: '',
    standardHours: 1,
    prepHours: 0,
    changeoverHours: 0,
    needReport: 1,
    needCheck: 0,
    needScan: 0
  }
  opEditDialogVisible.value = true
}

function editParameters(row: ProcessOperation) {
  if (!row.parameters) row.parameters = []
  activeOperation.value = row
  paramDialogVisible.value = true
}

function editDevices(row: ProcessOperation) {
  activeOperation.value = row
  deviceForm.deviceIds = [...(row.deviceIds ?? row.devices?.filter((d) => d.bindType === 'device').map((d) => d.deviceId!) ?? [])]
  deviceForm.categoryIds = [...(row.categoryIds ?? row.devices?.filter((d) => d.bindType === 'category').map((d) => d.categoryId!) ?? [])]
  deviceDialogVisible.value = true
}

function saveDeviceBindings() {
  if (!activeOperation.value) return
  activeOperation.value.deviceIds = deviceForm.deviceIds.map(Number)
  activeOperation.value.categoryIds = deviceForm.categoryIds.map(Number)
  deviceDialogVisible.value = false
}

function editMaterials(row: ProcessOperation) {
  activeOperation.value = row
  materialForm.rows = (row.materials ?? []).map((m) => {
    const mat = materialOptions.value.find((item) => String(item.id) === String(m.materialId))
    return {
      materialId: m.materialId,
      qty: Number(m.qty ?? 1),
      unit: m.unit || mat?.unit || '',
      materialType: m.materialType ?? 'raw',
      remark: m.remark
    }
  })
  if (!materialForm.rows.length) addMaterialRow()
  materialDialogVisible.value = true
}

function onMaterialRowChange(row: { materialId: number | string; unit: string }, materialId: number | string) {
  const material = materialOptions.value.find((m) => String(m.id) === String(materialId))
  if (material?.unit) {
    row.unit = material.unit
  }
}

function addMaterialRow() {
  materialForm.rows.push({ materialId: '', qty: 1, unit: '', materialType: 'raw' })
}

function saveMaterialBindings() {
  if (!activeOperation.value) return
  activeOperation.value.materials = materialForm.rows
    .filter((r) => r.materialId)
    .map((r) => {
      const mat = materialOptions.value.find((m) => m.id === r.materialId)
      return {
        materialId: r.materialId,
        qty: r.qty,
        unit: r.unit || mat?.unit || '',
        materialType: r.materialType,
        materialCode: mat?.materialCode,
        materialName: mat?.materialName
      }
    })
  materialDialogVisible.value = false
}

function editSops(row: ProcessOperation) {
  if (!row.sops) row.sops = []
  activeOperation.value = row
  sopDialogVisible.value = true
}

function addParameter() {
  if (!activeOperation.value) return
  if (!activeOperation.value.parameters) activeOperation.value.parameters = []
  activeOperation.value.parameters.push({ paramName: '', paramValue: '', minValue: '', maxValue: '', unit: '' })
}

function buildPayload(saveMode: SaveMode) {
  return {
    routeCode: form.routeCode.trim() || undefined,
    routeName: form.routeName.trim(),
    productName: form.productName.trim() || undefined,
    remark: form.remark,
    saveMode,
    operations: buildOperationPayload(form.operations)
  }
}

async function loadOptions() {
  try {
    const [devices, cats, mats] = await Promise.all([
      getDeviceOptions(),
      getDeviceCategories(),
      getMaterialOptions()
    ])
    deviceOptions.value = devices
    categories.value = cats
    materialOptions.value = mats
  } catch (error) {
    console.error(error)
  }
}

async function loadDetail() {
  if (isNew.value) {
    addOperation()
    return
  }
  loading.value = true
  try {
    const data = await getProcessRoute(routeId.value)
    form.routeCode = data.routeCode ?? ''
    form.routeName = data.routeName
    form.productName = data.productName ?? ''
    form.version = data.version ?? 'V1.0'
    form.status = data.status ?? 'draft'
    form.rejectedReason = data.rejectedReason ?? ''
    form.isDefault = !!data.isDefault
    form.remark = data.remark ?? ''
    form.operations = (data.operations ?? []).map((item) => ({
      ...item,
      needReport: item.needReport ?? 1,
      needCheck: item.needCheck ?? 0,
      needScan: item.needScan ?? 0,
      parameters: item.parameters ?? [],
      deviceIds: item.devices?.filter((d) => d.bindType === 'device').map((d) => Number(d.deviceId)) ?? [],
      categoryIds: item.devices?.filter((d) => d.bindType === 'category').map((d) => Number(d.categoryId)) ?? [],
      materials: item.materials ?? [],
      sops: item.sops ?? []
    }))
    history.value = data.history ?? []
  } catch (error) {
    console.error(error)
    ElMessage.error('加载工艺详情失败')
  } finally {
    loading.value = false
  }
}

async function saveRoute(saveMode: SaveMode) {
  if (!form.routeName.trim()) {
    ElMessage.warning('请填写工艺名称')
    return
  }
  if (!form.operations.length || form.operations.some((item) => !item.operationName?.trim())) {
    ElMessage.warning('请完善工序名称')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload(saveMode)
    if (isNew.value) {
      const created = await createProcessRoute(payload)
      ElMessage.success(saveMode === 'draft' ? '草稿已保存' : '工艺已提交')
      if (isPropMode.value) {
        emit('save-success')
        emit('close')
      } else {
        router.replace(`/process-management/${created.id}`)
      }
    } else {
      await updateProcessRoute(routeId.value, payload)
      ElMessage.success(saveMode === 'draft' ? '草稿已保存' : saveMode === 'submit' ? '已提交审批' : '工艺已发布')
      if (isPropMode.value) {
        emit('save-success')
      }
      await loadDetail()
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleApprove() {
  saving.value = true
  try {
    await approveProcessRoute(routeId.value)
    ElMessage.success('审批通过，工艺已发布')
    await loadDetail()
  } catch (error) {
    console.error(error)
    ElMessage.error('审批失败')
  } finally {
    saving.value = false
  }
}

async function handleReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  saving.value = true
  try {
    await rejectProcessRoute(routeId.value, rejectReason.value.trim())
    ElMessage.success('已驳回')
    rejectDialogVisible.value = false
    rejectReason.value = ''
    await loadDetail()
  } catch (error) {
    console.error(error)
    ElMessage.error('驳回失败')
  } finally {
    saving.value = false
  }
}

async function handleSopUpload(options: UploadRequestOptions) {
  if (!activeOperation.value?.id) return
  sopUploading.value = true
  try {
    const sop = await uploadOperationSop(activeOperation.value.id, options.file as File)
    if (!activeOperation.value.sops) activeOperation.value.sops = []
    activeOperation.value.sops.unshift(sop)
    ElMessage.success('SOP 上传成功')
  } catch (error) {
    console.error(error)
    ElMessage.error('上传失败')
  } finally {
    sopUploading.value = false
  }
}

async function removeSop(row: ProcessSop) {
  const ok = await confirmDelete({
    title: '删除 SOP',
    message: `确认删除 SOP「${row.fileName}」？此操作不可恢复。`
  })
  if (!ok) return
  await deleteOperationSop(row.id)
  if (activeOperation.value?.sops) {
    activeOperation.value.sops = activeOperation.value.sops.filter((s) => s.id !== row.id)
  }
  ElMessage.success('已删除')
}

async function previewSop(row: ProcessSop) {
  previewSopItem.value = row
  sopPreviewVisible.value = true
  previewLoading.value = true
  if (previewBlobUrl.value) URL.revokeObjectURL(previewBlobUrl.value)
  previewBlobUrl.value = ''
  try {
    const base = import.meta.env.VITE_API_BASE_URL || '/api'
    const res = await fetch(`${base}/process-routes/sop/${row.id}/file`, {
      headers: { Authorization: `Bearer ${localStorage.getItem(TOKEN_STORAGE_KEY) || ''}` }
    })
    if (!res.ok) throw new Error('加载失败')
    const blob = await res.blob()
    previewBlobUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    console.error(error)
    ElMessage.error('预览加载失败')
  } finally {
    previewLoading.value = false
  }
}

function handleBack() {
  if (isPropMode.value) {
    emit('close')
  } else {
    router.back()
  }
}

onMounted(async () => {
  activeTab.value = isPropMode.value ? 'operations' : 'basic'
  await loadOptions()
  await loadDetail()
})
</script>

<style scoped>
.basic-form { max-width: 800px; }
.tab-content-box {
  border: 1px solid var(--el-table-border-color, #ebeef5);
  border-radius: 4px;
  padding: 20px 24px;
  background: #fff;
}
.status-alert { margin-bottom: 12px; }
.status-bar-premium {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}
.status-badge {
  font-weight: 600 !important;
  padding: 4px 12px !important;
  height: 28px !important;
  font-size: 12px !important;
}
.version-badge {
  border-color: #cbd5e1 !important;
  color: #64748b !important;
  background: #f8fafc !important;
}
.ops-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; color: #64748b; font-size: 13px; }
.footer-actions { margin-top: 20px; display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
.param-table { margin-top: 12px; }
.history-panel {
  padding: 16px 20px;
}
.history-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.history-card {
  display: flex;
  gap: 16px;
  min-height: auto;
  align-items: flex-start;
}
.history-card__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 20px;
  flex-shrink: 0;
  padding-top: 4px;
}
.history-card__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px currentColor;
  flex-shrink: 0;
  z-index: 1;
}
.history-card__dot.action-create { color: #16a34a; background: #dcfce7; }
.history-card__dot.action-update { color: #4f46e5; background: #e0e7ff; }
.history-card__dot.action-status { color: #2563eb; background: #dbeafe; }
.history-card__dot.action-exception { color: #dc2626; background: #fee2e2; }
.history-card__dot.action-handle { color: #0d9488; background: #ccfbf1; }
.history-card__dot.action-inspection { color: #7c3aed; background: #ede9fe; }
.history-card__dot.action-maintenance { color: #d97706; background: #fef3c7; }
.history-card__dot.action-default { color: #64748b; background: #f1f5f9; }
.history-card__line {
  flex: 1;
  width: 2px;
  margin: 6px 0;
  background: linear-gradient(180deg, #e2e8f0 0%, #f1f5f9 100%);
  border-radius: 1px;
}
.history-card__content {
  flex: 1;
  padding: 0 0 14px;
  min-width: 0;
}
.history-card.is-last .history-card__content {
  padding-bottom: 2px;
}
.history-card__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  line-height: 1.5;
}
.history-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
  flex-shrink: 0;
}
.history-badge.action-create { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }
.history-badge.action-update { background: #eef2ff; color: #4338ca; border: 1px solid #c7d2fe; }
.history-badge.action-status { background: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe; }
.history-badge.action-exception { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }
.history-badge.action-handle { background: #f0fdfa; color: #0f766e; border: 1px solid #99f6e4; }
.history-badge.action-inspection { background: #f5f3ff; color: #6d28d9; border: 1px solid #ddd6fe; }
.history-badge.action-maintenance { background: #fffbeb; color: #b45309; border: 1px solid #fde68a; }
.history-badge.action-default { background: #f8fafc; color: #475569; border: 1px solid #e2e8f0; }
.history-version-tag {
  flex-shrink: 0;
  font-weight: 600 !important;
  color: #475569 !important;
  border-color: #e2e8f0 !important;
  background: #f8fafc !important;
}
.history-card__desc {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  flex: 1 1 240px;
  min-width: 0;
  line-height: 1.5;
}
.history-card__operator {
  font-size: 12px;
  color: #94a3b8;
}
.history-card__operator::before {
  content: '·';
  margin-right: 4px;
  color: #cbd5e1;
}
.history-card__time {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  margin-left: auto;
}
.bind-summary { font-size: 12px; color: #64748b; }
.full-width { width: 100%; }
.sop-hint { color: #94a3b8; padding: 12px 0; }
.sop-preview { min-height: 360px; }
.sop-preview img, .sop-preview iframe, .sop-preview video { width: 100%; min-height: 360px; border: none; }
.sop-download-hint { padding: 24px; text-align: center; }

.detail-content-card {
  border-radius: 16px;
  border: 1px solid #f1f5f9;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
  background: #fff;
  padding: 24px;
}
.detail-content-card.is-flat {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 !important;
}
.footer-actions :deep(.el-button),
.ops-toolbar :deep(.el-button) {
  border-radius: 20px !important;
  padding: 6px 16px !important;
  font-weight: 600 !important;
  transition: all 0.2s ease !important;
  height: 32px !important;
}

/* Premium Bind Badges */
.bind-badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
}
.bind-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  background: #f1f5f9;
  color: #94a3b8;
  border: 1px solid #e2e8f0;
  transition: all 0.2s ease;
}
.bind-badge.device.active {
  background: #eff6ff;
  color: #1d4ed8;
  border-color: #bfdbfe;
}
.bind-badge.category.active {
  background: #f5f3ff;
  color: #6d28d9;
  border-color: #ddd6fe;
}
.bind-badge.material.active {
  background: #f0fdf4;
  color: #15803d;
  border-color: #bbf7d0;
}
.bind-badge.sop.active {
  background: #faf5ff;
  color: #7e22ce;
  border-color: #e9d5ff;
}

/* Table Action Pill Buttons */
.table-actions-flex {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

.op-pill-btn {
  border-radius: 20px !important;
  padding: 4px 10px !important;
  font-size: 11px !important;
  font-weight: 600 !important;
  height: 24px !important;
  background: #fff !important;
  border: 1.5px solid #e2e8f0 !important;
  transition: all 0.2s ease !important;
  margin: 0 !important;
}

.op-pill-btn.btn-edit { color: #6366f1 !important; border-color: #e0e7ff !important; }
.op-pill-btn.btn-edit:hover { background: #e0e7ff !important; }

.op-pill-btn.btn-param { color: #2563eb !important; border-color: #dbeafe !important; }
.op-pill-btn.btn-param:hover { background: #dbeafe !important; }

.op-pill-btn.btn-device { color: #0d9488 !important; border-color: #ccfbf1 !important; }
.op-pill-btn.btn-device:hover { background: #ccfbf1 !important; }

.op-pill-btn.btn-material { color: #ea580c !important; border-color: #ffedd5 !important; }
.op-pill-btn.btn-material:hover { background: #ffedd5 !important; }

.op-pill-btn.btn-sop { color: #7e22ce !important; border-color: #f3e8ff !important; }
.op-pill-btn.btn-sop:hover { background: #f3e8ff !important; }

.op-pill-btn.btn-delete { color: #ef4444 !important; border-color: #fee2e2 !important; }
.op-pill-btn.btn-delete:hover { background: #fee2e2 !important; color: #b91c1c !important; }

.dialog-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: flex-start;
}

.op-pill-btn.btn-add {
  background: #0f172a !important;
  color: #fff !important;
  border-color: #0f172a !important;
}
.op-pill-btn.btn-add:hover {
  background: #1e293b !important;
  border-color: #1e293b !important;
}

.sop-type-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
}

/* Premium capsule/pill inputs for all form fields and table cells */
.view-page :deep(.el-input__wrapper) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: all 0.3s ease !important;
  padding: 4px 16px !important;
}
.view-page :deep(.el-textarea__inner) {
  border-radius: 16px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: all 0.3s ease !important;
  padding: 8px 16px !important;
}
.view-page :deep(.el-input__wrapper.is-focus),
.view-page :deep(.el-textarea__inner:focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}
.view-page :deep(.el-input__wrapper:focus),
.view-page :deep(.el-input__inner:focus),
.view-page :deep(.el-input__wrapper.is-focus:focus) {
  outline: none !important;
}

/* Premium Tabs Style */
.view-page :deep(.el-tabs__item) {
  font-weight: 600 !important;
  font-size: 14px !important;
  color: #64748b !important;
}
.view-page :deep(.el-tabs__item.is-active) {
  color: #4f46e5 !important;
}
.view-page :deep(.el-tabs__active-bar) {
  background-color: #4f46e5 !important;
  height: 3px !important;
  border-radius: 2px !important;
}

.standard-hours-text {
  font-weight: 600;
  color: #334155;
  font-size: 13px;
}
.extra-hours-text {
  font-size: 10px;
  color: #94a3b8;
  margin-top: 1px;
}

:deep(.el-input__inner) {
  text-align: center !important;
}

:deep(.el-tabs__content) {
  min-height: 240px;
}

/* Fixed-height scrollable dialog container rules for prop mode to completely resolve jitter */
.is-prop-mode .detail-content-card {
  height: 520px !important;
  display: flex !important;
  flex-direction: column !important;
  box-sizing: border-box !important;
}
.is-prop-mode :deep(.el-tabs) {
  flex: 1 !important;
  display: flex !important;
  flex-direction: column !important;
  min-height: 0 !important;
}
.is-prop-mode :deep(.el-tabs__content) {
  flex: 1 !important;
  overflow-y: auto !important;
  min-height: 0 !important;
}
</style>
