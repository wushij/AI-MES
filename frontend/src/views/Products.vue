<template>
  <div class="view-page">
    <PageHeader title="产品管理" subtitle="维护产品主数据；BOM 由工艺路线工序物料自动汇总。" />

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索产品编码或名称"
            clearable
            class="toolbar__input"
            @keyup.enter="loadProducts"
          />
          <el-select v-model="filters.status" placeholder="状态" clearable class="toolbar__select" @change="loadProducts">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </div>
        <div class="toolbar__actions">
          <el-button class="action-btn-reset" @click="resetFilters">重置</el-button>
          <el-button type="primary" @click="openCreate">新增产品</el-button>
          <el-button type="primary" class="action-btn-refresh" @click="loadProducts">刷新</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="hover">
      <el-table
        v-loading="loading"
        :data="products"
        stripe
        border
        highlight-current-row
        :header-cell-style="tableHeaderStyle"
      >
        <el-table-column prop="productCode" label="产品编码" min-width="120" align="center" />
        <el-table-column prop="productName" label="产品名称" min-width="160" show-overflow-tooltip align="center" />
        <el-table-column prop="spec" label="规格" min-width="140" show-overflow-tooltip align="center" />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column label="成品库存" width="110" align="center">
          <template #default="{ row }">
            {{ row.stockQty ?? 0 }} {{ row.unit }}
          </template>
        </el-table-column>
        <el-table-column label="工艺物料" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hasBom ? 'success' : 'info'" size="small">{{ row.hasBom ? '已配置' : '未配置' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status === 'active' ? 'normal' : 'disabled'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="280" align="center">
          <template #default="{ row }">
            <div class="action-cell">
              <el-button size="small" class="action-btn action-btn--detail" @click="openDetail(row)">详情</el-button>
              <el-button size="small" class="action-btn action-btn--edit" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" class="action-btn action-btn--delete" :loading="deleteLoading === row.id" @click="removeProduct(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无产品数据">
            <el-button type="primary" @click="openCreate">新增产品</el-button>
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
          @current-change="loadProducts"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="formVisible"
      width="480px"
      class="custom-product-form-dialog"
      append-to-body
      destroy-on-close
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom">
          <el-icon class="header-icon"><Goods /></el-icon>
          <span class="header-text">{{ formMode === 'create' ? '新增产品' : '编辑产品' }}</span>
        </div>
      </template>
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="96px" class="product-form">
        <el-form-item v-if="formMode === 'create'" label="产品编码">
          <el-input v-model="formModel.productCode" placeholder="留空自动生成，如 PRD-003" class="full-width" />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="formModel.productName" maxlength="80" class="full-width" />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="formModel.spec" maxlength="100" class="full-width" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="formModel.unit" class="full-width">
            <el-option label="件" value="件" />
            <el-option label="个" value="个" />
            <el-option label="套" value="套" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="formModel.status" class="full-width">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" class="form-item--textarea">
          <el-input v-model="formModel.remark" type="textarea" :rows="2" maxlength="200" class="full-width" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer-custom">
          <el-button class="btn-cancel" @click="formVisible = false">取消</el-button>
          <el-button type="primary" class="btn-submit" :loading="saving" @click="submitForm">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      width="760px"
      class="custom-product-detail-dialog"
      append-to-body
      destroy-on-close
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom dialog-header-custom--detail">
          <el-icon class="header-icon"><Goods /></el-icon>
          <div class="header-text-group">
            <span class="header-text">{{ detailProduct?.productName || '产品详情' }}</span>
            <span v-if="detailProduct" class="header-subtext">{{ detailProduct.productCode }}</span>
          </div>
        </div>
      </template>

      <template v-if="detailProduct">
        <div class="detail-meta-grid">
          <div class="meta-item">
            <span class="meta-item__label">产品编码</span>
            <span class="meta-item__val code-highlight">{{ detailProduct.productCode }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">单位</span>
            <span class="meta-item__val">{{ detailProduct.unit }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">规格型号</span>
            <span class="meta-item__val text-highlight">{{ detailProduct.spec || '—' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">状态</span>
            <span class="meta-item__val">
              <StatusTag :status="detailProduct.status === 'active' ? 'normal' : 'disabled'" />
            </span>
          </div>
        </div>

        <div class="detail-kpi-row">
          <div class="detail-kpi-card detail-kpi-card--stock">
            <span class="detail-kpi-card__label">成品库存</span>
            <span class="detail-kpi-card__value">
              {{ detailProduct.stockQty ?? 0 }}
              <span class="detail-kpi-card__unit">{{ detailProduct.unit }}</span>
            </span>
          </div>
          <div class="detail-kpi-card" :class="{ 'detail-kpi-card--success': detailProduct.hasBom }">
            <span class="detail-kpi-card__label">工艺物料</span>
            <span class="detail-kpi-card__value">{{ detailProduct.hasBom ? '已配置' : '未配置' }}</span>
          </div>
          <div class="detail-kpi-card">
            <span class="detail-kpi-card__label">汇总行数</span>
            <span class="detail-kpi-card__value">{{ bomItems.length }}</span>
          </div>
          <div class="detail-kpi-card">
            <span class="detail-kpi-card__label">来源工艺</span>
            <span class="detail-kpi-card__value detail-kpi-card__value--sm">{{ bomMeta.routeName || '—' }}</span>
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
            max-height="240"
            :header-cell-style="tableHeaderStyle"
            class="detail-txn-table"
          >
            <template #empty>
              <el-empty description="暂无库存流水，工单完工后将自动入库" :image-size="64" />
            </template>
            <el-table-column prop="createdTime" label="时间" min-width="150" align="center" />
            <el-table-column label="类型" width="92" align="center">
              <template #default="{ row }">
                <span class="txn-type-badge" :class="row.txnType">{{ txnTypeLabel(row.txnType) }}</span>
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

        <div class="detail-section">
          <div class="detail-section__header">
            <span class="detail-section__title">BOM 物料清单（工艺汇总）</span>
          </div>
          <div v-if="bomMeta.remark" class="bom-source-hint">
            <el-icon class="bom-source-icon"><InfoFilled /></el-icon>
            <span class="bom-source-text">{{ bomMeta.remark }}</span>
          </div>
          <el-table :data="bomItems" border stripe size="small" max-height="260" :header-cell-style="tableHeaderStyle" class="bom-table">
            <template #empty>
              <el-empty description="暂无工序物料，请前往工艺管理为各工序配置物料" :image-size="64" />
            </template>
            <el-table-column label="物料" min-width="200" align="center">
              <template #default="{ row }">
                <div class="bom-material-cell">
                  <span class="bom-material-cell__name">{{ row.materialName || row.materialCode }}</span>
                  <span v-if="row.materialCode" class="bom-material-cell__code">{{ row.materialCode }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="单件用量" width="100" align="center" prop="qty" />
            <el-table-column label="单位" width="80" align="center" prop="unit" />
            <el-table-column label="来源工序" min-width="160" align="center">
              <template #default="{ row }">
                <span class="bom-op-label">{{ row.operationNamesLabel || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <span v-if="row.materialType" class="material-type-badge" :class="row.materialType">{{ materialTypeLabel(row.materialType) }}</span>
                <span v-else>—</span>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="bomDetails.length" class="detail-subsection">
            <div class="detail-subsection__title">工序明细</div>
            <el-table :data="bomDetails" border stripe size="small" max-height="220" :header-cell-style="tableHeaderStyle">
              <el-table-column prop="operationName" label="工序" width="100" align="center" />
              <el-table-column label="物料" min-width="180" align="center">
                <template #default="{ row }">
                  <div class="bom-material-cell">
                    <span class="bom-material-cell__name">{{ row.materialName || row.materialCode }}</span>
                    <span v-if="row.materialCode" class="bom-material-cell__code">{{ row.materialCode }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="qty" label="单件用量" width="100" align="center" />
              <el-table-column prop="unit" label="单位" width="80" align="center" />
            </el-table>
          </div>
        </div>
      </template>

      <template #footer>
        <div class="dialog-footer-custom">
          <el-button class="btn-cancel" @click="detailVisible = false">关闭</el-button>
          <el-button type="primary" class="btn-submit" @click="openEditFromDetail">编辑产品</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { Goods, InfoFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import type { BomDetailItem, BomItem, Product, ProductBom, ProductTransaction } from '@/api/products'
import { createProduct, deleteProduct, getProduct, getProductTransactions, getProducts, updateProduct } from '@/api/products'
import { materialTypeLabel } from '@/api/processRoutes'
import { normalizeList } from '@/utils/normalizeList'
import { confirmDelete } from '@/utils/confirmDelete'

const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }
const loading = ref(false)
const saving = ref(false)
const deleteLoading = ref<string | number | null>(null)
const products = ref<Product[]>([])
const filters = reactive({ keyword: '', status: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<string | number | null>(null)
const formRef = ref<FormInstance>()
const formModel = reactive({
  productCode: '',
  productName: '',
  spec: '',
  unit: '件',
  status: 'active',
  remark: ''
})
const formRules: FormRules = {
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }]
}

const detailVisible = ref(false)
const detailProduct = ref<Product | null>(null)
const transactions = ref<ProductTransaction[]>([])
const txnLoading = ref(false)
const bomItems = ref<BomItem[]>([])
const bomDetails = ref<BomDetailItem[]>([])
const bomMeta = reactive({
  routeName: '',
  version: '',
  remark: ''
})

onMounted(() => {
  loadProducts()
})

async function loadProducts() {
  loading.value = true
  try {
    const response = await getProducts({
      keyword: filters.keyword,
      status: filters.status,
      page: pagination.page,
      size: pagination.size
    })
    pagination.total = Number((response as any)?.total ?? 0)
    products.value = normalizeList(response) as Product[]
  } catch (error) {
    console.error(error)
    ElMessage.error('加载产品列表失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  pagination.page = 1
  loadProducts()
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  Object.assign(formModel, { productCode: '', productName: '', spec: '', unit: '件', status: 'active', remark: '' })
  formVisible.value = true
}

function openEdit(row: Product) {
  formMode.value = 'edit'
  editingId.value = row.id
  Object.assign(formModel, {
    productCode: row.productCode,
    productName: row.productName,
    spec: row.spec ?? '',
    unit: row.unit,
    status: row.status,
    remark: row.remark ?? ''
  })
  formVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...formModel }
    if (formMode.value === 'create') {
      await createProduct(payload)
      ElMessage.success('产品已创建')
    } else if (editingId.value != null) {
      await updateProduct(editingId.value, payload)
      ElMessage.success('产品已更新')
    }
    formVisible.value = false
    loadProducts()
  } catch (error) {
    console.error(error)
    ElMessage.error('保存产品失败')
  } finally {
    saving.value = false
  }
}

async function openDetail(row: Product) {
  try {
    const detail = await getProduct(row.id)
    detailProduct.value = detail
    const bom = detail.bom as ProductBom | undefined
    bomItems.value = (bom?.items ?? []).map((item) => ({
      ...item,
      qty: Number(item.qty ?? 0),
      operationNamesLabel: item.operationNamesLabel || (item.operationNames ?? []).join('、')
    }))
    bomDetails.value = bom?.details ?? []
    bomMeta.routeName = bom?.routeName ? `${bom.routeName}${bom.version ? ` (${bom.version})` : ''}` : ''
    bomMeta.version = bom?.version ?? ''
    bomMeta.remark = bom?.remark ?? ''
    detailVisible.value = true
    txnLoading.value = true
    try {
      transactions.value = await getProductTransactions(row.id)
    } catch (error) {
      console.error(error)
      transactions.value = []
    } finally {
      txnLoading.value = false
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('加载产品详情失败')
  }
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

function openEditFromDetail() {
  if (!detailProduct.value) return
  detailVisible.value = false
  openEdit(detailProduct.value)
}

async function removeProduct(row: Product) {
  const ok = await confirmDelete({
    title: '删除产品',
    message: `确认删除产品「${row.productName}」？此操作不可恢复。`
  })
  if (!ok) return
  deleteLoading.value = row.id
  try {
    await deleteProduct(row.id)
    ElMessage.success('产品已删除')
    loadProducts()
  } catch (error) {
    console.error(error)
    ElMessage.error('删除产品失败')
  } finally {
    deleteLoading.value = null
  }
}

</script>

<style scoped>
.view-page { display: flex; flex-direction: column; gap: 16px; }
.toolbar-card { border-radius: 16px; }
.toolbar { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.toolbar__filters, .toolbar__actions { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.toolbar__input { width: 240px; }
.toolbar__select { width: 140px; }
.table-pagination { display: flex; justify-content: flex-start; margin-top: 12px; }
.full-width { width: 100%; }

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

.action-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
  align-items: center;
}

.action-btn {
  border-radius: 8px !important;
  font-weight: 500 !important;
  transition: all 0.2s ease !important;
  background: #fff !important;
  margin: 0 !important;
}

.action-btn--detail {
  border: 1px solid rgba(14, 165, 233, 0.3) !important;
  color: #0284c7 !important;
}
.action-btn--detail:hover {
  background: rgba(14, 165, 233, 0.05) !important;
  border-color: #0284c7 !important;
}

.action-btn--edit {
  border: 1px solid rgba(79, 70, 229, 0.3) !important;
  color: #4f46e5 !important;
}
.action-btn--edit:hover {
  background: rgba(79, 70, 229, 0.05) !important;
  border-color: #4f46e5 !important;
}

.action-btn--delete {
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #ef4444 !important;
}
.action-btn--delete:hover {
  background: rgba(239, 68, 68, 0.05) !important;
  border-color: #ef4444 !important;
}

/* Product detail dialog */
.custom-product-detail-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08) !important;
}

.custom-product-detail-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #f1f5f9;
  background-color: #f8fafc;
}

.custom-product-detail-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.custom-product-detail-dialog :deep(.el-dialog__footer) {
  padding: 12px 24px 20px;
  border-top: 1px solid #f1f5f9;
}

.dialog-header-custom {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dialog-header-custom--detail {
  width: 100%;
}

.header-icon {
  font-size: 20px;
  color: #4f46e5;
}

.header-text-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-text {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.header-subtext {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  font-family: ui-monospace, monospace;
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
  grid-template-columns: repeat(4, 1fr);
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
  border-left: 4px solid #94a3b8;
  transition: all 0.2s ease;
}

.detail-kpi-card--success {
  border-left-color: #22c55e;
  background: rgba(34, 197, 94, 0.03);
}

.detail-kpi-card--success .detail-kpi-card__value {
  color: #15803d;
}

.detail-kpi-card--stock {
  border-left-color: #6366f1;
  background: rgba(99, 102, 241, 0.04);
}

.detail-kpi-card--stock .detail-kpi-card__value {
  color: #4338ca;
}

.detail-kpi-card__unit {
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  margin-left: 4px;
}

.detail-kpi-card__label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.detail-kpi-card__value {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.detail-kpi-card__value--sm {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  word-break: break-all;
}

.detail-section {
  margin-top: 24px;
}

.detail-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
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
}

.txn-remark-placeholder {
  color: #cbd5e1;
}

.bom-source-hint {
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: 12px;
  background: #f0f9ff;
  border: 1px solid #e0f2fe;
  font-size: 12px;
  color: #0369a1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.bom-source-icon {
  font-size: 16px;
  color: #0284c7;
  flex-shrink: 0;
}

.bom-source-text {
  font-weight: 500;
  line-height: 1.5;
}

.material-type-badge {
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
.material-type-badge.raw {
  background: #eff6ff;
  color: #1d4ed8;
  border-color: #bfdbfe;
}
.material-type-badge.semi {
  background: #f5f3ff;
  color: #6d28d9;
  border-color: #ddd6fe;
}
.material-type-badge.aux {
  background: #f0fdf4;
  color: #15803d;
  border-color: #bbf7d0;
}
.material-type-badge.tooling {
  background: #fffbeb;
  color: #d97706;
  border-color: #fde68a;
}

.txn-type-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
}
.txn-type-badge.in { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }
.txn-type-badge.out { background: #fffbeb; color: #b45309; border: 1px solid #fde68a; }
.txn-type-badge.pick { background: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe; }
.txn-type-badge.return { background: #f5f3ff; color: #6d28d9; border: 1px solid #ddd6fe; }
.txn-type-badge.adjust { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }

.bom-material-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
}

.bom-material-cell__name {
  font-weight: 600;
  color: #1e293b;
}

.bom-material-cell__code {
  font-size: 12px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.bom-op-label {
  font-size: 12px;
  color: #334155;
}

.detail-subsection {
  margin-top: 16px;
}

.detail-subsection__title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.bom-table {
  border-radius: 12px;
}

.bom-table :deep(.el-table__inner-wrapper) {
  border-radius: 12px;
  overflow: hidden;
}

.bom-table :deep(.el-table__cell) {
  padding: 10px 8px !important;
}

.bom-table :deep(.el-table__empty-block) {
  min-height: 120px;
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

/* Product form dialog */
.custom-product-form-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08) !important;
}

.custom-product-form-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #f1f5f9;
  background-color: #f8fafc;
}

.custom-product-form-dialog :deep(.el-dialog__body) {
  padding: 20px 24px 8px;
}

.custom-product-form-dialog :deep(.el-dialog__footer) {
  padding: 12px 24px 20px;
  border-top: 1px solid #f1f5f9;
}

.product-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #475569;
}

.product-form :deep(.el-textarea__inner) {
  border-radius: 20px !important;
  padding: 10px 16px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  background-color: #fff !important;
  transition: all 0.25s ease;
  resize: none;
}

.product-form :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px #a5b4fc inset !important;
}

.product-form :deep(.el-textarea__inner:focus) {
  background-color: #fff !important;
  box-shadow:
    0 0 0 1px #4f46e5 inset,
    0 0 0 3px rgba(79, 70, 229, 0.12) !important;
  outline: none !important;
}
</style>
