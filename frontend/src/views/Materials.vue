<template>
  <div class="view-page">
    <PageHeader title="物料管理" subtitle="监控库存健康度，突出缺料预警，快速更新库存。" />
    
    <el-row :gutter="16">
      <el-col v-for="card in statCards" :key="card.label" :xs="24" :sm="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__label">{{ card.label }}</div>
          <div class="stat-card__value" :class="card.className">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索物料编号或名称"
            clearable
            class="toolbar__input"
            @keyup.enter="loadMaterials"
          />
          <el-select
            v-model="filters.status"
            placeholder="状态"
            clearable
            class="toolbar__select"
            @change="loadMaterials"
          >
            <el-option label="正常" value="normal" />
            <el-option label="预警" value="warning" />
          </el-select>
        </div>
        <div class="toolbar__actions">
          <el-button class="action-btn-reset" @click="resetFilters">重置</el-button>
          <el-button type="primary" @click="openCreateDialog">新增物料</el-button>
          <el-button type="primary" class="action-btn-refresh" @click="loadMaterials">刷新</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="hover">
      <el-table
        v-loading="loading"
        :data="pagedMaterials"
        stripe
        border
        highlight-current-row
        :header-cell-style="tableHeaderStyle"
        :row-class-name="rowClassName"
      >
        <el-table-column prop="code" label="物料编号" min-width="130" align="center" />
        <el-table-column prop="name" label="物料名称" min-width="180" show-overflow-tooltip align="center" />
        <el-table-column label="当前库存" min-width="120" align="center">
          <template #default="{ row }">
            <span :class="{ 'stock-warning': row.status === 'warning' }">{{ row.stockQty }} {{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="安全库存" min-width="120" align="center">
          <template #default="{ row }">{{ row.safetyStock }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="缺口" min-width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.gap > 0" class="gap-warning">{{ row.gap }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="320" align="center">
          <template #default="{ row }">
            <el-button size="small" class="action-btn action-btn--detail" @click="openDetail(row)">详情</el-button>
            <el-button size="small" class="action-btn action-btn--edit-info" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" class="action-btn action-btn--edit" @click="openStockDialog(row)">更新库存</el-button>
            <el-button size="small" class="action-btn action-btn--delete" :loading="deleteLoading === row.id" @click="removeMaterial(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="filters.status === 'warning' ? '所有物料库存正常' : '暂无物料数据'">
            <el-button type="primary" @click="openCreateDialog">新增物料</el-button>
          </el-empty>
        </template>
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

    <el-dialog v-model="createDialogVisible" :title="isEditMode ? '编辑物料' : '新增物料'" width="480px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="96px">
        <el-form-item label="物料编号">
          <el-input v-model="createForm.materialCode" :disabled="isEditMode" placeholder="留空自动生成，如 MAT-004" />
        </el-form-item>
        <el-form-item label="物料名称" prop="materialName">
          <el-input v-model="createForm.materialName" placeholder="如：精密螺丝 M4" />
        </el-form-item>
        <el-form-item v-if="!isEditMode" label="当前库存" prop="stockQty">
          <el-input-number v-model="createForm.stockQty" :min="0" :step="1" class="full-width" />
        </el-form-item>
        <el-form-item label="安全库存" prop="safetyStock">
          <el-input-number v-model="createForm.safetyStock" :min="0" :step="1" class="full-width" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-select v-model="createForm.unit" class="full-width">
            <el-option label="个" value="个" />
            <el-option label="件" value="件" />
            <el-option label="瓶" value="瓶" />
            <el-option label="kg" value="kg" />
            <el-option label="米" value="米" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" class="form-item--textarea">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingCreate" @click="submitCreate">{{ isEditMode ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="stockDialogVisible"
      width="500px"
      class="custom-stock-dialog"
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom">
          <el-icon class="header-icon"><Box /></el-icon>
          <span class="header-text">库存更新 — {{ currentMaterial?.name }}</span>
        </div>
      </template>

      <template v-if="currentMaterial">
        <div class="stock-meta-cards">
          <div class="meta-card" :class="{ 'meta-card--warning': currentMaterial.status === 'warning' }">
            <span class="meta-card__label">当前库存</span>
            <span class="meta-card__value">
              {{ currentMaterial.stockQty }} <span class="meta-card__unit">{{ currentMaterial.unit }}</span>
            </span>
          </div>
          <div class="meta-card">
            <span class="meta-card__label">安全库存</span>
            <span class="meta-card__value">
              {{ currentMaterial.safetyStock }} <span class="meta-card__unit">{{ currentMaterial.unit }}</span>
            </span>
          </div>
        </div>
      </template>

      <el-form
        ref="stockFormRef"
        :model="stockForm"
        :rules="stockRules"
        label-position="top"
        class="custom-stock-form"
      >
        <el-form-item label="操作方式">
          <el-radio-group v-model="stockForm.type" class="custom-radio-group">
            <el-radio-button label="inbound">
              <div class="radio-content">
                <el-icon><Plus /></el-icon>
                <span>入库</span>
              </div>
            </el-radio-button>
            <el-radio-button label="outbound">
              <div class="radio-content">
                <el-icon><Minus /></el-icon>
                <span>出库</span>
              </div>
            </el-radio-button>
            <el-radio-button label="correct">
              <div class="radio-content">
                <el-icon><EditPen /></el-icon>
                <span>库存修正</span>
              </div>
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item :label="quantityLabel" prop="quantity">
          <el-input-number
            v-model="stockForm.quantity"
            :min="quantityMin"
            :max="quantityMax"
            :step="1"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="备注" class="form-item--textarea">
          <el-input
            v-model="stockForm.remark"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="请输入补充备注说明（可选）"
            class="custom-remark-input"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer-custom">
          <el-button class="btn-cancel" @click="stockDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" class="btn-submit" @click="submitStock">
            {{ submitButtonText }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      width="720px"
      class="custom-material-detail-dialog"
      append-to-body
      destroy-on-close
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom dialog-header-custom--detail">
          <el-icon class="header-icon"><Box /></el-icon>
          <div class="header-text-group">
            <span class="header-text">{{ currentMaterial?.name || '物料详情' }}</span>
            <span v-if="currentMaterial" class="header-subtext">{{ currentMaterial.code }}</span>
          </div>
        </div>
      </template>

      <template v-if="currentMaterial">
        <div class="detail-meta-grid">
          <div class="meta-item">
            <span class="meta-item__label">物料编号</span>
            <span class="meta-item__val code-highlight">{{ currentMaterial.code }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">单位</span>
            <span class="meta-item__val">{{ currentMaterial.unit }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">物料名称</span>
            <span class="meta-item__val text-highlight">{{ currentMaterial.name }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">库存状态</span>
            <span class="meta-item__val">
              <StatusTag :status="currentMaterial.status" />
            </span>
          </div>
        </div>

        <div class="detail-kpi-row">
          <div class="detail-kpi-card" :class="{ 'detail-kpi-card--warning': currentMaterial.status === 'warning' }">
            <span class="detail-kpi-card__label">当前库存</span>
            <span class="detail-kpi-card__value">
              {{ currentMaterial.stockQty }}
              <span class="detail-kpi-card__unit">{{ currentMaterial.unit }}</span>
            </span>
          </div>
          <div class="detail-kpi-card">
            <span class="detail-kpi-card__label">安全库存</span>
            <span class="detail-kpi-card__value">
              {{ currentMaterial.safetyStock }}
              <span class="detail-kpi-card__unit">{{ currentMaterial.unit }}</span>
            </span>
          </div>
          <div class="detail-kpi-card" :class="{ 'detail-kpi-card--gap': currentMaterial.gap > 0 }">
            <span class="detail-kpi-card__label">库存缺口</span>
            <span class="detail-kpi-card__value">
              {{ currentMaterial.gap > 0 ? currentMaterial.gap : '—' }}
              <span v-if="currentMaterial.gap > 0" class="detail-kpi-card__unit">{{ currentMaterial.unit }}</span>
            </span>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section__header">
            <span class="detail-section__title">库存流水</span>
            <span class="detail-section__count">共 {{ transactions.length }} 条</span>
          </div>
          <el-table
            v-loading="txnLoading"
            :data="transactions"
            border
            stripe
            size="small"
            max-height="280"
            :header-cell-style="tableHeaderStyle"
            class="detail-txn-table"
          >
            <template #empty>
              <el-empty description="暂无库存流水记录" :image-size="64" />
            </template>
            <el-table-column prop="createdTime" label="时间" min-width="150" align="center" />
            <el-table-column label="类型" width="88" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="txnTypeTagType(row.txnType)" effect="light" round>
                  {{ txnTypeLabel(row.txnType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="qty" label="数量" width="80" align="center" />
            <el-table-column label="库存变化" min-width="130" align="center">
              <template #default="{ row }">
                <span class="txn-change">{{ row.beforeQty }} → {{ row.afterQty }}</span>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="120" align="center">
              <template #default="{ row }">
                <el-tooltip v-if="row.remark" :content="row.remark" placement="top" :show-after="200">
                  <span class="txn-remark">{{ row.remark }}</span>
                </el-tooltip>
                <span v-else class="txn-remark-placeholder">—</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>

      <template #footer>
        <div class="dialog-footer-custom">
          <el-button class="btn-cancel" @click="detailVisible = false">关闭</el-button>
          <el-button type="primary" class="btn-submit" @click="openStockFromDetail">更新库存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Box, Plus, Minus, EditPen } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { normalizeList } from '@/utils/normalizeList'
import { confirmDelete } from '@/utils/confirmDelete'
import type { MaterialTransaction } from '@/api/materials'

interface MaterialRow { id: string | number; code: string; name: string; unit: string; stockQty: number; safetyStock: number; gap: number; status: string; remark?: string }

const loading = ref(false)
const submitting = ref(false)
const submittingCreate = ref(false)
const deleteLoading = ref<string | number | null>(null)
const materials = ref<MaterialRow[]>([])
const currentMaterial = ref<MaterialRow | null>(null)
const stockDialogVisible = ref(false)
const detailVisible = ref(false)
const txnLoading = ref(false)
const transactions = ref<MaterialTransaction[]>([])
const createDialogVisible = ref(false)
const isEditMode = ref(false)
const editingId = ref<number | string | null>(null)
const stockFormRef = ref<FormInstance>()
const createFormRef = ref<FormInstance>()
const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }
const stats = reactive({ total: 0, normal: 0, warning: 0 })
const filters = reactive({ keyword: '', status: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })
const stockForm = reactive({ type: 'inbound', quantity: 1, remark: '' })

const createForm = reactive({
  materialCode: '',
  materialName: '',
  stockQty: 0,
  safetyStock: 100,
  unit: '件',
  remark: ''
})

const createRules: FormRules = {
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  stockQty: [{ required: true, message: '请输入当前库存', trigger: 'change' }],
  safetyStock: [{ required: true, message: '请输入安全库存', trigger: 'change' }],
  unit: [{ required: true, message: '请选择单位', trigger: 'change' }]
}

const quantityLabel = computed(() => {
  if (stockForm.type === 'inbound') return '入库数量'
  if (stockForm.type === 'outbound') return '出库数量'
  return '修正后的实际库存'
})

const quantityMin = computed(() => {
  if (stockForm.type === 'correct') return 0
  return 1
})

const quantityMax = computed(() => {
  if (stockForm.type === 'outbound' && currentMaterial.value) {
    return currentMaterial.value.stockQty
  }
  return undefined
})

const submitButtonText = computed(() => {
  if (stockForm.type === 'inbound') return '确认入库'
  if (stockForm.type === 'outbound') return '确认出库'
  return '确认修正'
})

const stockRules: FormRules = {
  quantity: [
    { required: true, message: '请输入更新数量', trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        if (stockForm.type === 'outbound' && currentMaterial.value && value > currentMaterial.value.stockQty) {
          callback(new Error('出库数量不能大于当前库存'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

watch(() => stockForm.type, (newType) => {
  if (newType === 'correct' && currentMaterial.value) {
    stockForm.quantity = currentMaterial.value.stockQty
  } else {
    stockForm.quantity = 1
  }
})

const statCards = computed(() => [
  { label: '物料总数', value: stats.total, className: '' },
  { label: '正常物料', value: stats.normal, className: '' },
  { label: '预警物料', value: stats.warning, className: 'warning-text' }
])

const pagedMaterials = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return materials.value.slice(start, start + pagination.size)
})

onMounted(() => {
  loadStats()
  loadMaterials()
})

async function loadStats() {
  try {
    const api = (await import('@/api/materials')) as Record<string, any>
    const response = await (api.getMaterials?.(filters) ?? api.getMaterialStats?.())
    const summary = response?.summary ?? response?.data?.summary ?? response ?? {}
    stats.total = Number(summary.total ?? 0)
    stats.normal = Number(summary.normal ?? 0)
    stats.warning = Number(summary.warning ?? 0)
  } catch (error) {
    console.error(error)
  }
}

async function loadMaterials() {
  loading.value = true
  try {
    const api = (await import('@/api/materials')) as Record<string, any>
    const response = await (api.getMaterials?.(filters) ?? api.getMaterialList?.(filters))
    materials.value = normalizeList(response).map((item: any) => {
      const stockQty = Number(item.stockQty ?? 0)
      const safetyStock = Number(item.safetyStock ?? 0)
      const gap = Number(item.gap ?? Math.max(safetyStock - stockQty, 0))
      return {
        id: item.id ?? item.materialId ?? item.code,
        code: String(item.materialCode ?? item.code ?? '--'),
        name: String(item.materialName ?? item.name ?? '--'),
        unit: String(item.unit ?? '件'),
        stockQty,
        safetyStock,
        gap,
        status: String(item.alertStatus ?? (gap > 0 ? 'warning' : 'normal')),
        remark: item.remark || ''
      }
    })
    if (response?.summary) {
      stats.total = response.summary.total ?? 0
      stats.normal = response.summary.normal ?? 0
      stats.warning = response.summary.warning ?? 0
    }
    pagination.total = materials.value.length
    if ((pagination.page - 1) * pagination.size >= pagination.total && pagination.page > 1) {
      pagination.page = 1
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('加载物料失败')
  } finally {
    loading.value = false
  }
}

function openStockDialog(row: MaterialRow) {
  currentMaterial.value = row
  stockForm.type = 'inbound'
  stockForm.quantity = 1
  stockForm.remark = ''
  stockDialogVisible.value = true
}

async function openDetail(row: MaterialRow) {
  currentMaterial.value = row
  detailVisible.value = true
  txnLoading.value = true
  try {
    const api = await import('@/api/materials')
    transactions.value = await api.getMaterialTransactions(row.id)
  } catch (error) {
    console.error(error)
    ElMessage.error('加载库存流水失败')
    transactions.value = []
  } finally {
    txnLoading.value = false
  }
}

function openStockFromDetail() {
  if (!currentMaterial.value) return
  detailVisible.value = false
  openStockDialog(currentMaterial.value)
}

function txnTypeLabel(type: string) {
  const map: Record<string, string> = { in: '入库', out: '出库', pick: '领料', return: '退料', adjust: '调整' }
  return map[type] || type
}

function txnTypeTagType(type: string): 'success' | 'warning' | 'info' | 'danger' | '' {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | ''> = {
    in: 'success',
    out: 'warning',
    pick: 'info',
    return: '',
    adjust: 'danger'
  }
  return map[type] ?? ''
}

function openCreateDialog() {
  isEditMode.value = false
  editingId.value = null
  Object.assign(createForm, {
    materialCode: '',
    materialName: '',
    stockQty: 0,
    safetyStock: 100,
    unit: '件',
    remark: ''
  })
  createDialogVisible.value = true
}

function openEditDialog(row: MaterialRow) {
  isEditMode.value = true
  editingId.value = row.id
  Object.assign(createForm, {
    materialCode: row.code,
    materialName: row.name,
    stockQty: row.stockQty,
    safetyStock: row.safetyStock,
    unit: row.unit,
    remark: row.remark || ''
  })
  createDialogVisible.value = true
}

async function submitCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submittingCreate.value = true
  try {
    const { createMaterial, updateMaterial } = await import('@/api/materials')
    if (isEditMode.value && editingId.value) {
      const payload = {
        materialName: createForm.materialName.trim(),
        safetyStock: createForm.safetyStock,
        unit: createForm.unit,
        remark: createForm.remark.trim() || ''
      }
      await updateMaterial(editingId.value, payload)
      ElMessage.success('物料已更新')
    } else {
      const payload: {
        materialName: string
        stockQty: number
        safetyStock: number
        unit: string
        remark?: string
        materialCode?: string
      } = {
        materialName: createForm.materialName.trim(),
        stockQty: createForm.stockQty,
        safetyStock: createForm.safetyStock,
        unit: createForm.unit,
        remark: createForm.remark.trim() || undefined
      }
      if (createForm.materialCode.trim()) {
        payload.materialCode = createForm.materialCode.trim()
      }
      await createMaterial(payload)
      ElMessage.success('物料已创建')
    }
    createDialogVisible.value = false
    await loadStats()
    await loadMaterials()
  } catch (error) {
    console.error(error)
    ElMessage.error(error instanceof Error ? error.message : (isEditMode.value ? '更新物料失败' : '创建物料失败'))
  } finally {
    submittingCreate.value = false
  }
}

async function submitStock() {
  const valid = await stockFormRef.value?.validate().catch(() => false)
  if (!valid || !currentMaterial.value) return
  submitting.value = true
  try {
    const api = (await import('@/api/materials')) as Record<string, any>
    const payload: any = {
      remark: stockForm.remark
    }
    if (stockForm.type === 'inbound') {
      payload.inboundQty = stockForm.quantity
    } else if (stockForm.type === 'outbound') {
      payload.inboundQty = -stockForm.quantity
    } else if (stockForm.type === 'correct') {
      payload.stockQty = stockForm.quantity
    }
    await (api.updateMaterial?.(currentMaterial.value.id, payload) ?? api.updateMaterialStock?.(currentMaterial.value.id, payload))
    ElMessage.success('库存已更新')
    stockDialogVisible.value = false
    loadStats()
    loadMaterials()
  } catch (error) {
    console.error(error)
    ElMessage.error('更新库存失败')
  } finally {
    submitting.value = false
  }
}

async function removeMaterial(row: MaterialRow) {
  const ok = await confirmDelete({
    title: '删除物料',
    message: `确认删除物料「${row.name}」（${row.code}）？此操作不可恢复。`
  })
  if (!ok) return
  deleteLoading.value = row.id
  try {
    const { deleteMaterial } = await import('@/api/materials')
    await deleteMaterial(row.id)
    ElMessage.success('物料已删除')
    await loadStats()
    await loadMaterials()
  } catch (error) {
    console.error(error)
    ElMessage.error(error instanceof Error ? error.message : '删除物料失败')
  } finally {
    deleteLoading.value = null
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  pagination.page = 1
  loadMaterials()
}

function rowClassName({ row }: { row: MaterialRow }) {
  return row.status === 'warning' ? 'warning-row' : ''
}
</script>

<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.stat-card {
  border-radius: 16px;
}
:deep(.stat-card .el-card__body) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px !important;
}
.stat-card__label {
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
}
.stat-card__value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}
.warning-text {
  color: #fa8c16;
}
.toolbar-card {
  border-radius: 16px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.toolbar__filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar__input {
  width: 220px;
}
.toolbar__select {
  width: 140px;
}
.stock-meta {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 12px;
}
.full-width {
  width: 100%;
}
.stock-warning {
  color: #f5222d;
  font-weight: 700;
}
.gap-warning {
  color: #fa8c16;
  font-weight: 700;
}
:deep(.warning-row) td.el-table__cell {
  background-color: rgba(250, 140, 22, 0.06) !important;
}

.toolbar__input :deep(.el-input__wrapper),
.toolbar__select :deep(.el-input__wrapper) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 4px 16px !important;
  transition: all 0.3s ease !important;
}

.toolbar__input :deep(.el-input__wrapper.is-focus),
.toolbar__select :deep(.el-input__wrapper.is-focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

.action-btn-reset {
  border-radius: 20px !important;
  padding: 8px 20px !important;
  border: 1px solid #e2e8f0 !important;
  transition: all 0.2s ease !important;
}
.action-btn-reset:hover {
  background-color: #f8fafc !important;
  border-color: #cbd5e1 !important;
}

.action-btn-refresh {
  border-radius: 20px !important;
  padding: 8px 20px !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%) !important;
  border: none !important;
  color: white !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
  transition: all 0.2s ease !important;
}
.action-btn-refresh:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.3) !important;
}

.action-btn {
  border-radius: 8px !important;
  font-weight: 500 !important;
  transition: all 0.2s ease !important;
  background: #fff !important;
}

.action-btn--edit {
  border: 1px solid rgba(79, 70, 229, 0.3) !important;
  color: #4f46e5 !important;
}
.action-btn--edit:hover {
  background: rgba(79, 70, 229, 0.05) !important;
  border-color: #4f46e5 !important;
}

.action-btn--edit-info {
  border: 1px solid rgba(13, 148, 136, 0.3) !important;
  color: #0d9488 !important;
}
.action-btn--edit-info:hover {
  background: rgba(13, 148, 136, 0.05) !important;
  border-color: #0d9488 !important;
}

.action-btn--delete {
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #ef4444 !important;
}
.action-btn--delete:hover {
  background: rgba(239, 68, 68, 0.05) !important;
  border-color: #ef4444 !important;
}

/* Custom Stock Dialog Styles */
.custom-stock-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08) !important;
}

.custom-stock-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #f1f5f9;
  background-color: #f8fafc;
}

.dialog-header-custom {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 20px;
  color: #4f46e5;
}

.header-text {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.dialog-header-custom--detail {
  width: 100%;
}

.header-text-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-subtext {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  font-family: ui-monospace, monospace;
}

.stock-meta-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 20px;
  padding: 4px 0;
}

.meta-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  transition: all 0.2s ease;
}

.meta-card--warning {
  background: rgba(239, 68, 68, 0.03);
  border-color: rgba(239, 68, 68, 0.15);
}

.meta-card__label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.meta-card__value {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.meta-card--warning .meta-card__value {
  color: #ef4444;
}

.meta-card__unit {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  margin-left: 2px;
}

.meta-card--warning .meta-card__unit {
  color: rgba(239, 68, 68, 0.6);
}

.custom-stock-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #1e293b;
  padding-bottom: 8px !important;
  font-size: 13px;
}

.custom-remark-input :deep(.el-textarea__inner) {
  border-radius: 10px !important;
  padding: 10px 14px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  background-color: #f8fafc !important;
  font-size: 13px;
}

.custom-remark-input :deep(.el-textarea__inner:focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

.dialog-footer-custom {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 10px 0;
}

.btn-cancel {
  border-radius: 20px !important;
  padding: 8px 22px !important;
  border: 1px solid #e2e8f0 !important;
}

.btn-submit {
  border-radius: 20px !important;
  padding: 8px 22px !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%) !important;
  border: none !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
  font-weight: 600;
}

.btn-submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.3) !important;
}

.table-pagination {
  display: flex;
  justify-content: flex-start;
  margin-top: 12px;
}

/* Custom radio option button cards */
.custom-radio-group {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  width: 100%;
}

.custom-radio-group :deep(.el-radio-button) {
  width: 100%;
}

.custom-radio-group :deep(.el-radio-button__inner) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  border: 1px solid #e2e8f0 !important;
  border-radius: 12px !important;
  background: #fff !important;
  padding: 10px 12px !important;
  box-shadow: none !important;
  transition: all 0.2s ease;
  font-size: 13px;
}

.custom-radio-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: #4f46e5 !important;
  background: rgba(79, 70, 229, 0.05) !important;
  color: #4f46e5 !important;
  font-weight: 600;
}

.radio-content {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* Material detail dialog */
.custom-material-detail-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08) !important;
}

.custom-material-detail-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #f1f5f9;
  background-color: #f8fafc;
}

.custom-material-detail-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.custom-material-detail-dialog :deep(.el-dialog__footer) {
  padding: 12px 24px 20px;
  border-top: 1px solid #f1f5f9;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1px;
  background: #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  margin-bottom: 16px;
}

.meta-item {
  background: #f8fafc;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-item__label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.meta-item__val {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.code-highlight {
  font-family: ui-monospace, monospace;
  color: #4f46e5;
}

.text-highlight {
  color: #0f172a;
}

.detail-kpi-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.detail-kpi-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  border-left: 4px solid #4f46e5;
  transition: all 0.2s ease;
}

.detail-kpi-card--warning {
  background: rgba(239, 68, 68, 0.03);
  border-color: rgba(239, 68, 68, 0.15);
  border-left-color: #ef4444;
}

.detail-kpi-card--gap {
  border-left-color: #f59e0b;
}

.detail-kpi-card--gap .detail-kpi-card__value {
  color: #d97706;
}

.detail-kpi-card__label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.detail-kpi-card__value {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.detail-kpi-card--warning .detail-kpi-card__value {
  color: #ef4444;
}

.detail-kpi-card__unit {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  margin-left: 2px;
}

.detail-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.detail-section__title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.detail-section__count {
  font-size: 12px;
  color: #94a3b8;
}

.detail-txn-table {
  border-radius: 12px;
  overflow: hidden;
}

.detail-txn-table :deep(.el-table__empty-block) {
  min-height: 120px;
}

.txn-change {
  font-family: ui-monospace, monospace;
  font-size: 13px;
  color: #475569;
}

.txn-remark {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  color: #475569;
  cursor: default;
}

.txn-remark-placeholder {
  color: #cbd5e1;
}
</style>
