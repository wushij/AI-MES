<template>
  <div :class="['view-page', { 'is-prop-mode': isPropMode }]" v-loading="loading">
    <PageHeader v-if="!isPropMode" :title="detail?.deviceName ?? '设备详情'" :subtitle="detail?.deviceCode ?? ''">
      <el-button @click="router.back()">返回</el-button>
      <el-button v-if="detail" type="primary" @click="router.push('/devices')">设备管理</el-button>
    </PageHeader>

    <template v-if="detail">
      <el-row :gutter="16" class="summary-row">
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-card__label">当前状态</div>
            <el-tag :type="deviceStatusTagType(detail.status)" effect="dark" round>{{ detail.statusLabel ?? deviceStatusLabel(detail.status) }}</el-tag>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-card__label">未处理异常</div>
            <div class="stat-card__value">{{ detail.runtime?.openExceptionCount ?? 0 }}</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-card__label">今日报警</div>
            <div class="stat-card__value">{{ detail.runtime?.todayAlertCount ?? 0 }}</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-card__label">所属产线</div>
            <div class="stat-card__text">{{ detail.lineName || '--' }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="detail-tabs-card" shadow="hover">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基本信息" name="basic">
            <div class="tab-content-box">
              <el-descriptions :column="2" border>
              <el-descriptions-item label="设备编号">{{ detail.deviceCode }}</el-descriptions-item>
              <el-descriptions-item label="设备名称">{{ detail.deviceName }}</el-descriptions-item>
              <el-descriptions-item label="分类">{{ detail.categoryName || detail.deviceType || '--' }}</el-descriptions-item>
              <el-descriptions-item label="品牌/型号">{{ [detail.brand, detail.model].filter(Boolean).join(' / ') || '--' }}</el-descriptions-item>
              <el-descriptions-item label="序列号">{{ detail.serialNumber || '--' }}</el-descriptions-item>
              <el-descriptions-item label="负责人">{{ detail.managerName || '--' }}</el-descriptions-item>
              <el-descriptions-item label="购买日期">{{ detail.purchaseDate || '--' }}</el-descriptions-item>
              <el-descriptions-item label="安装日期">{{ detail.installDate || '--' }}</el-descriptions-item>
              <el-descriptions-item label="车间">{{ detail.workshop || '--' }}</el-descriptions-item>
              <el-descriptions-item label="产线">{{ detail.lineName || '--' }}</el-descriptions-item>
              <el-descriptions-item label="工位">{{ detail.station || '--' }}</el-descriptions-item>
              <el-descriptions-item label="责任班组">{{ detail.teamName || '--' }}</el-descriptions-item>
              <el-descriptions-item label="启用日期">{{ detail.enableDate || '--' }}</el-descriptions-item>
              <el-descriptions-item label="保修截止">{{ detail.warrantyDate || '--' }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </el-tab-pane>

          <el-tab-pane label="运行状态" name="status">
            <div class="tab-content-box status-tab-container">
              <el-row :gutter="32">
                <!-- 左侧：当前状态大看板 -->
                <el-col :span="10">
                  <div :class="['current-status-display', detail.status]">
                    <div class="status-display-label">当前设备状态</div>
                    <div class="status-display-badge">
                      <span class="status-dot"></span>
                      {{ detail.statusLabel ?? deviceStatusLabel(detail.status) }}
                    </div>
                    <div class="status-display-subtext">如需调整，请在右侧提交变更</div>
                  </div>
                </el-col>

                <!-- 右侧：变更状态表单 -->
                <el-col :span="14">
                  <el-form label-width="80px" class="premium-status-form" label-position="left">
                    <el-form-item label="变更状态">
                      <el-select v-model="statusForm.status" placeholder="选择新状态" class="full-width">
                        <el-option v-for="item in DEVICE_STATUSES.filter(s => s.value !== 'scrapped' || detail?.status === 'scrapped')" :key="item.value" :label="item.label" :value="item.value" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="备注信息">
                      <el-input v-model="statusForm.remark" type="textarea" :rows="3" placeholder="请输入状态变更的原因或相关说明（可选）" class="full-width" />
                    </el-form-item>
                    <el-form-item style="margin-bottom: 0;">
                      <el-button type="primary" class="status-submit-btn" :loading="statusSubmitting" @click="submitStatus">确认更新状态</el-button>
                    </el-form-item>
                  </el-form>
                </el-col>
              </el-row>
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`运行记录 (${processRecords.length})`" name="records">
            <div class="tab-content-box">
              <el-table v-if="processRecords.length" :data="processRecords" stripe border :header-cell-style="tableHeaderStyle">
                <el-table-column prop="orderNo" label="工单号" min-width="120" />
                <el-table-column prop="productName" label="产品" min-width="120" show-overflow-tooltip />
                <el-table-column prop="processName" label="工序" min-width="100" />
                <el-table-column prop="status" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="recordStatusTag(row.status)">{{ row.statusLabel || row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="startTime" label="开始时间" min-width="160">
                  <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
                </el-table-column>
                <el-table-column prop="endTime" label="结束时间" min-width="160">
                  <template #default="{ row }">{{ formatTime(row.endTime) }}</template>
                </el-table-column>
                <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
              </el-table>
              <el-empty v-else description="暂无运行/报工记录" />
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`维修记录 (${exceptions.length})`" name="exceptions">
            <div class="tab-content-box">
              <el-table v-if="exceptions.length" :data="exceptions" stripe border :header-cell-style="tableHeaderStyle">
              <el-table-column prop="eventNo" label="异常编号" min-width="120" />
              <el-table-column prop="description" label="故障描述" min-width="220" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }"><StatusTag :status="row.status" /></template>
              </el-table-column>
              <el-table-column prop="occurTime" label="发生时间" min-width="160" />
              <el-table-column prop="handleResult" label="处理结果" min-width="120" />
              </el-table>
              <el-empty v-else description="暂无维修/异常记录" />
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`履历 (${history.length})`" name="history">
            <div class="tab-content-box history-panel">
              <div v-if="history.length" class="history-list">
                <div
                  v-for="(item, index) in history"
                  :key="item.id"
                  class="history-card"
                  :class="{ 'is-last': index === history.length - 1 }"
                >
                  <div class="history-card__rail">
                    <div class="history-card__dot" :class="actionClass(item.actionType)" />
                    <div v-if="index < history.length - 1" class="history-card__line" />
                  </div>
                  <div class="history-card__content">
                    <div class="history-card__row">
                      <span class="history-badge" :class="actionClass(item.actionType)">
                        {{ deviceActionLabel(item.actionType) }}
                      </span>
                      <span class="history-card__desc">{{ item.actionDesc }}</span>
                      <span v-if="hasStatusChange(item)" class="history-card__change-inline">
                        {{ changeBefore(item) }} → {{ changeAfter(item) }}
                      </span>
                      <span v-if="item.operatorName" class="history-card__operator">{{ item.operatorName }}</span>
                      <span class="history-card__time">{{ formatTime(item.createTime) }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无履历记录" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { getDeviceFullDetail, getDeviceProcessRecords, updateDeviceStatus, type DeviceFullDetail, type DeviceHistoryItem, type DeviceProcessRecordItem } from '@/api/devices'
import { DEVICE_STATUSES, deviceActionLabel, deviceStatusLabel, deviceStatusTagType } from '@/utils/deviceLabels'

const props = defineProps<{
  id?: string | number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'save-success'): void
}>()

const route = useRoute()
const router = useRouter()
const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600', textAlign: 'center' as const }
const loading = ref(false)
const statusSubmitting = ref(false)
const activeTab = ref('basic')
const detail = ref<DeviceFullDetail | null>(null)
const processRecords = ref<DeviceProcessRecordItem[]>([])
const statusForm = reactive({ status: '', remark: '' })

const isPropMode = computed(() => props.id != null && props.id !== '')
const deviceId = computed(() => {
  if (isPropMode.value) {
    return String(props.id)
  }
  return route.params.id as string
})

const exceptions = computed(() => detail.value?.exceptions ?? [])
const history = computed(() => detail.value?.history ?? [])

function formatTime(value?: string) {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 19)
}

function recordStatusTag(status?: string) {
  switch (status) {
    case 'running': return 'success'
    case 'paused': return 'warning'
    case 'done': return 'info'
    default: return ''
  }
}

function actionClass(actionType?: string) {
  switch (actionType) {
    case 'create': return 'action-create'
    case 'update': return 'action-update'
    case 'status': return 'action-status'
    case 'exception': return 'action-exception'
    case 'handle': return 'action-handle'
    default: return 'action-default'
  }
}

function changeBefore(item: DeviceHistoryItem) {
  if (item.beforeValue == null) return '--'
  return item.beforeLabel || item.beforeValue
}

function changeAfter(item: DeviceHistoryItem) {
  if (item.afterValue == null) return '--'
  return item.afterLabel || item.afterValue
}

function hasStatusChange(item: DeviceHistoryItem) {
  return item.beforeValue != null || item.afterValue != null
}

async function loadDetail() {
  const id = deviceId.value
  if (!id) return
  loading.value = true
  try {
    const [detailData, records] = await Promise.all([
      getDeviceFullDetail(id),
      getDeviceProcessRecords(id)
    ])
    detail.value = detailData
    processRecords.value = records
    statusForm.status = detail.value.status ?? 'idle'
  } catch (error) {
    console.error(error)
    ElMessage.error('加载设备详情失败')
  } finally {
    loading.value = false
  }
}

async function submitStatus() {
  if (!detail.value || !statusForm.status) return
  statusSubmitting.value = true
  try {
    await updateDeviceStatus(detail.value.id, { status: statusForm.status, remark: statusForm.remark || undefined })
    ElMessage.success('设备状态已更新')
    statusForm.remark = ''
    emit('save-success')
    await loadDetail()
  } catch (error) {
    console.error(error)
    ElMessage.error('更新状态失败')
  } finally {
    statusSubmitting.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.summary-row { margin-bottom: 8px !important; }
.stat-card {
  text-align: center;
  border-radius: 12px !important;
  border: 1px solid #f1f5f9 !important;
  height: 92px !important;
  display: flex !important;
  flex-direction: column !important;
  justify-content: center !important;
  align-items: center !important;
  box-sizing: border-box !important;
}
.stat-card :deep(.el-card__body) {
  padding: 0 !important;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  width: 100%;
}
.stat-card__label { color: #64748b; font-size: 13px; margin-bottom: 6px; }
.stat-card__value { font-size: 28px; font-weight: 700; color: #0f172a; line-height: 1.2; }
.stat-card__text { font-size: 16px; font-weight: 600; color: #334155; line-height: 1.2; }
.status-tab-container {
  padding: 4px 0;
}
.tab-content-box {
  border: 1px solid var(--el-table-border-color, #ebeef5);
  border-radius: 8px;
  padding: 20px 24px;
  background: #fff;
}
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
.history-badge.action-default { background: #f8fafc; color: #475569; border: 1px solid #e2e8f0; }
.history-card__desc {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
}
.history-card__change-inline {
  font-size: 13px;
  color: #64748b;
  white-space: nowrap;
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
.current-status-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  border-radius: 16px;
  border: 1px solid #f1f5f9;
  text-align: center;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.02);
}
.current-status-display.running {
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
  border-color: #bbf7d0;
}
.current-status-display.idle {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border-color: #bfdbfe;
}
.current-status-display.fault {
  background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
  border-color: #fecaca;
}
.current-status-display.maintenance {
  background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
  border-color: #fde68a;
}
.current-status-display.scrapped {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-color: #e2e8f0;
}

.status-display-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
  margin-bottom: 12px;
}
.status-display-badge {
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.current-status-display.running .status-display-badge { color: #16a34a; }
.current-status-display.idle .status-display-badge { color: #2563eb; }
.current-status-display.fault .status-display-badge { color: #dc2626; }
.current-status-display.maintenance .status-display-badge { color: #d97706; }
.current-status-display.scrapped .status-display-badge { color: #64748b; }

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
  animation: pulse-dot 2s infinite ease-in-out;
}
.current-status-display.running .status-dot { background-color: #16a34a; }
.current-status-display.idle .status-dot { background-color: #2563eb; }
.current-status-display.fault .status-dot { background-color: #dc2626; }
.current-status-display.maintenance .status-dot { background-color: #d97706; }
.current-status-display.scrapped .status-dot { background-color: #64748b; }

@keyframes pulse-dot {
  0% { transform: scale(0.95); opacity: 0.5; }
  50% { transform: scale(1.1); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.5; }
}

.status-display-subtext {
  font-size: 11px;
  color: #94a3b8;
}

.premium-status-form {
  padding-top: 8px;
}
.status-submit-btn {
  width: 100%;
  border-radius: 20px !important;
  font-weight: 600 !important;
  height: 38px !important;
  background-color: #4f46e5 !important;
  border-color: #4f46e5 !important;
}
.status-submit-btn:hover {
  background-color: #4338ca !important;
  border-color: #4338ca !important;
}

/* Unified tabs card container styling */
.detail-tabs-card {
  border-radius: 12px !important;
  border: 1px solid #f1f5f9 !important;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03) !important;
  background: #fff !important;
  margin-top: 0px !important;
}

/* Set a stable height for tab content to prevent dialog height jumping/shaking on tab switch */
:deep(.el-tabs__content) {
  min-height: 240px;
}

/* Fixed-height scrollable dialog container rules for prop mode to completely resolve jitter */
.is-prop-mode .detail-tabs-card {
  height: 440px !important;
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

/* Premium Capsule Inputs */
:deep(.el-input__wrapper) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: all 0.3s ease !important;
  padding: 4px 16px !important;
}
:deep(.el-textarea__inner) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  border: none !important;
  transition: all 0.3s ease !important;
  padding: 10px 16px !important;
}
:deep(.el-input__wrapper.is-focus),
:deep(.el-textarea__inner:focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}
:deep(.el-input__wrapper:focus),
:deep(.el-input__inner:focus),
:deep(.el-input__wrapper.is-focus:focus) {
  outline: none !important;
}

/* Premium Tabs Style */
.detail-tabs-card :deep(.el-tabs__item) {
  font-weight: 600 !important;
  font-size: 14px !important;
  color: #64748b !important;
}
.detail-tabs-card :deep(.el-tabs__item.is-active) {
  color: #4f46e5 !important;
}
.detail-tabs-card :deep(.el-tabs__active-bar) {
  background-color: #4f46e5 !important;
  height: 3px !important;
  border-radius: 2px !important;
}

/* Consistent center alignment */
:deep(.el-input__inner) {
  text-align: center !important;
}
</style>
