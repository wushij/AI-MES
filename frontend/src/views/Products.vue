<template>
  <div class="view-page">
    <PageHeader title="产品管理" subtitle="维护产品主数据与单层 BOM 物料清单。" />

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
        <el-table-column label="BOM" width="90" align="center">
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
              <el-button size="small" class="action-btn action-btn--detail" @click="openDetail(row)">详情/BOM</el-button>
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
          <div class="detail-kpi-card" :class="{ 'detail-kpi-card--success': detailProduct.hasBom }">
            <span class="detail-kpi-card__label">BOM 配置</span>
            <span class="detail-kpi-card__value">{{ detailProduct.hasBom ? '已配置' : '未配置' }}</span>
          </div>
          <div class="detail-kpi-card">
            <span class="detail-kpi-card__label">物料行数</span>
            <span class="detail-kpi-card__value">{{ bomItems.length }}</span>
          </div>
          <div class="detail-kpi-card">
            <span class="detail-kpi-card__label">备注</span>
            <span class="detail-kpi-card__value detail-kpi-card__value--sm">{{ detailProduct.remark || '—' }}</span>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section__header">
            <span class="detail-section__title">BOM 物料清单</span>
            <div class="bom-toolbar">
              <el-button size="small" class="btn-outline" @click="addBomRow">添加物料</el-button>
              <el-button size="small" type="primary" class="btn-submit-sm" :loading="savingBom" @click="saveBom">保存 BOM</el-button>
            </div>
          </div>
          <el-table :data="bomItems" border stripe size="small" max-height="300" :header-cell-style="tableHeaderStyle" class="bom-table">
            <template #empty>
              <el-empty description="暂无 BOM 物料，点击「添加物料」开始配置" :image-size="64" />
            </template>
            <el-table-column label="物料" min-width="220" align="center">
              <template #default="{ row }">
                <el-select v-model="row.materialId" filterable placeholder="选择物料" class="bom-field full-width">
                  <el-option
                    v-for="m in materialOptions"
                    :key="m.id"
                    :label="`${m.materialCode} - ${m.materialName}`"
                    :value="m.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="用量" width="110" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.qty" :min="0.0001" :step="0.1" :controls="false" class="bom-field full-width" />
              </template>
            </el-table-column>
            <el-table-column label="单位" width="88" align="center">
              <template #default="{ row }">
                <el-input v-model="row.unit" class="bom-field" />
              </template>
            </el-table-column>
            <el-table-column label="损耗%" width="100" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.lossRate" :min="0" :max="100" :controls="false" class="bom-field full-width" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button size="small" class="bom-delete-btn" @click="bomItems.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { Goods } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import type { BomItem, Product } from '@/api/products'
import { createProduct, deleteProduct, getProduct, getProducts, saveProductBom, updateProduct } from '@/api/products'
import { getMaterialOptions } from '@/api/materials'
import { normalizeList } from '@/utils/normalizeList'

const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }
const loading = ref(false)
const saving = ref(false)
const savingBom = ref(false)
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
const bomItems = ref<BomItem[]>([])
const materialOptions = ref<Array<{ id: number | string; materialCode: string; materialName: string; unit: string }>>([])

onMounted(() => {
  loadProducts()
  loadMaterialOptions()
})

async function loadMaterialOptions() {
  try {
    materialOptions.value = await getMaterialOptions()
  } catch (error) {
    console.error(error)
  }
}

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
    bomItems.value = (detail.bom?.items ?? []).map((item) => ({
      materialId: item.materialId,
      qty: Number(item.qty ?? 1),
      unit: item.unit || '件',
      lossRate: Number(item.lossRate ?? 0),
      remark: item.remark
    }))
    detailVisible.value = true
  } catch (error) {
    console.error(error)
    ElMessage.error('加载产品详情失败')
  }
}

function openEditFromDetail() {
  if (!detailProduct.value) return
  detailVisible.value = false
  openEdit(detailProduct.value)
}

function addBomRow() {
  bomItems.value.push({ materialId: '', qty: 1, unit: '件', lossRate: 0 })
}

async function saveBom() {
  if (!detailProduct.value) return
  const items = bomItems.value.filter((row) => row.materialId)
  if (!items.length) {
    ElMessage.warning('请至少添加一条 BOM 行')
    return
  }
  savingBom.value = true
  try {
    await saveProductBom(detailProduct.value.id, { items })
    ElMessage.success('BOM 已保存')
    detailProduct.value.hasBom = true
    loadProducts()
  } catch (error) {
    console.error(error)
    ElMessage.error('保存 BOM 失败')
  } finally {
    savingBom.value = false
  }
}

async function removeProduct(row: Product) {
  await ElMessageBox.confirm(`确认删除产品 ${row.productName}？`, '删除产品', { type: 'warning' })
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

.bom-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.bom-table {
  border-radius: 12px;
}

.bom-table :deep(.el-table__inner-wrapper) {
  border-radius: 12px;
  overflow: hidden;
}

.bom-table :deep(.el-table__cell) {
  padding: 12px 8px !important;
  overflow: visible !important;
}

.bom-table :deep(.cell) {
  overflow: visible !important;
  line-height: normal;
  padding: 2px 4px;
}

.bom-table :deep(.el-table__empty-block) {
  min-height: 120px;
}

/* Compact inputs inside BOM table — override global pill padding */
.bom-table :deep(.bom-field.el-input-number) {
  width: 100%;
  line-height: normal;
  border-radius: 20px !important;
  overflow: hidden;
}

.bom-table :deep(.bom-field.el-input-number .el-input__wrapper) {
  padding-left: 8px !important;
  padding-right: 8px !important;
}

.bom-table :deep(.bom-field .el-input__wrapper),
.bom-table :deep(.bom-field.el-select .el-select__wrapper) {
  border-radius: 20px !important;
  min-height: 32px;
  padding: 4px 12px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  background-color: #fff !important;
}

.bom-table :deep(.bom-field.el-select .el-select__selection) {
  flex: 1;
  justify-content: center;
}

.bom-table :deep(.bom-field.el-select .el-select__selected-item),
.bom-table :deep(.bom-field.el-select .el-select__placeholder) {
  text-align: center;
}

.bom-table :deep(.bom-field.el-input) {
  width: 100%;
}

.bom-table :deep(.bom-field .el-input__wrapper.is-focus),
.bom-table :deep(.bom-field.el-select .el-select__wrapper.is-focused),
.bom-table :deep(.bom-field.el-input-number:focus-within) {
  box-shadow: 0 0 0 1px #4f46e5 inset !important;
}

.bom-table :deep(.bom-field.el-input-number:focus-within) {
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 2px rgba(79, 70, 229, 0.12) !important;
}

.bom-table :deep(.bom-field .el-input__inner) {
  text-align: center;
  height: 24px;
  line-height: 24px;
}

.btn-outline {
  border-radius: 16px !important;
  border: 1px solid #e2e8f0 !important;
}

.btn-submit-sm {
  border-radius: 16px !important;
  font-weight: 600;
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

.bom-table :deep(.bom-delete-btn.el-button) {
  border-radius: 9999px !important;
  padding: 5px 16px !important;
  border: 1px solid rgba(239, 68, 68, 0.4) !important;
  color: #ef4444 !important;
  background-color: #fff !important;
  font-weight: 500;
  box-shadow: none !important;
  height: auto !important;
  min-height: 28px;
  transition: all 0.2s ease;
}

.bom-table :deep(.bom-delete-btn.el-button:hover) {
  background-color: rgba(239, 68, 68, 0.06) !important;
  border-color: #ef4444 !important;
  color: #dc2626 !important;
}
</style>
