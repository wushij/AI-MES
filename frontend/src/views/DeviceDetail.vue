<template>
  <div :class="['view-page', { 'is-prop-mode': isPropMode }]" v-loading="loading">
    <PageHeader v-if="!isPropMode" :title="detail?.deviceName ?? '设备详情'" :subtitle="detail?.deviceCode ?? ''">
      <el-button @click="router.back()">返回</el-button>
      <el-button v-if="detail" type="primary" @click="router.push('/devices')">设备管理</el-button>
    </PageHeader>

    <template v-if="detail">
      <div class="summary-row">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__label">当前状态</div>
          <el-tag :type="deviceStatusTagType(detail.status)" effect="dark" round size="small">
            {{ detail.statusLabel ?? deviceStatusLabel(detail.status) }}
          </el-tag>
        </el-card>
        <el-card shadow="hover" class="stat-card stat-card--run">
          <div class="stat-card__label">今日运行</div>
          <div class="stat-card__value stat-card__value--duration" :title="detail.runtime?.todayRunLabel">
            {{ detail.runtime?.todayRunLabel || formatDurationMinutes(detail.runtime?.todayRunMinutes) }}
          </div>
        </el-card>
        <el-card shadow="hover" class="stat-card stat-card--stop">
          <div class="stat-card__label">今日停机</div>
          <div class="stat-card__value stat-card__value--duration" :title="detail.runtime?.todayStopLabel">
            {{ detail.runtime?.todayStopLabel || formatDurationMinutes(detail.runtime?.todayStopMinutes) }}
          </div>
        </el-card>
        <el-card shadow="hover" class="stat-card stat-card--rate">
          <div class="stat-card__label">稼动率</div>
          <div class="stat-card__value">{{ detail.runtime?.utilizationRate ?? 0 }}%</div>
        </el-card>
        <el-card shadow="hover" class="stat-card stat-card--alert" @click="activeTab = 'status'">
          <div class="stat-card__label">今日报警</div>
          <div class="stat-card__value">{{ detail.runtime?.todayAlertCount ?? 0 }}</div>
        </el-card>
      </div>

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
              <div class="runtime-overview">
                <div class="runtime-metric runtime-metric--run">
                  <div class="runtime-metric__label">今日运行时长</div>
                  <div class="runtime-metric__value">{{ detail.runtime?.todayRunLabel || formatDurationMinutes(detail.runtime?.todayRunMinutes) }}</div>
                  <div class="runtime-metric__hint">来自报工记录</div>
                </div>
                <div class="runtime-metric runtime-metric--stop">
                  <div class="runtime-metric__label">今日停机时长</div>
                  <div class="runtime-metric__value">{{ detail.runtime?.todayStopLabel || formatDurationMinutes(detail.runtime?.todayStopMinutes) }}</div>
                  <div class="runtime-metric__hint">
                    暂停 {{ formatDurationMinutes(detail.runtime?.todayPauseMinutes) }} ·
                    维修 {{ formatDurationMinutes(detail.runtime?.todayRepairStopMinutes) }} ·
                    状态 {{ formatDurationMinutes(detail.runtime?.todayStatusStopMinutes) }}
                  </div>
                </div>
                <div class="runtime-metric runtime-metric--rate">
                  <div class="runtime-metric__label">今日稼动率</div>
                  <div class="runtime-metric__value">{{ detail.runtime?.utilizationRate ?? 0 }}%</div>
                  <div class="runtime-metric__hint">运行 / (运行+停机)</div>
                </div>
              </div>

              <div class="status-section-title">今日报警</div>
              <DeviceAlertPanel
                :alerts="todayAlerts"
                empty-text="今日暂无设备报警"
                @view="handleAlertView"
              />

              <div class="status-section-title">状态变更</div>
              <el-row :gutter="32">
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

          <el-tab-pane :label="`点检记录 (${inspectionRecords.length})`" name="inspections">
            <div class="tab-content-box">
              <div class="tab-toolbar">
                <el-button type="primary" class="tab-action-btn" @click="inspectionDialogVisible = true">执行点检</el-button>
              </div>
              <el-table v-if="inspectionRecords.length" :data="inspectionRecords" stripe border class="detail-table" :header-cell-style="tableHeaderStyle">
                <el-table-column prop="recordNo" label="点检单号" min-width="140" align="center" />
                <el-table-column prop="planName" label="点检计划" min-width="140" align="center" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.planName || '--' }}</template>
                </el-table-column>
                <el-table-column prop="inspectorName" label="点检人员" width="100" align="center">
                  <template #default="{ row }">{{ row.inspectorName || '--' }}</template>
                </el-table-column>
                <el-table-column prop="inspectTime" label="点检时间" min-width="160" align="center">
                  <template #default="{ row }">{{ formatTime(row.inspectTime) }}</template>
                </el-table-column>
                <el-table-column label="点检项目" min-width="220" align="center">
                  <template #default="{ row }">
                    <div class="item-tags">
                      <el-tooltip
                        v-for="(item, idx) in row.checkItems"
                        :key="idx"
                        :content="item.remark"
                        placement="top"
                        :show-after="200"
                        :disabled="!item.remark"
                      >
                        <el-tag size="small" :type="item.isNormal ? 'success' : 'danger'" class="item-tag">
                          {{ item.itemName }}{{ item.isNormal ? '' : '（异常）' }}
                        </el-tag>
                      </el-tooltip>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="isNormal" label="结果" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.isNormal ? 'success' : 'danger'">{{ row.isNormalLabel || (row.isNormal ? '正常' : '异常') }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="备注" min-width="120" align="center">
                  <template #default="{ row }">
                    <el-tooltip v-if="row.remark" :content="row.remark" placement="top" :show-after="200">
                      <span class="table-ellipsis">{{ row.remark }}</span>
                    </el-tooltip>
                    <span v-else class="table-placeholder">--</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="88" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      size="small"
                      class="record-pill-btn record-pill-btn--delete"
                      :loading="deletingInspectionId === row.id"
                      @click="removeInspectionRecord(row)"
                    >
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无点检记录" />
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`保养记录 (${maintenanceRecords.length})`" name="maintenances">
            <div class="tab-content-box">
              <div class="tab-toolbar">
                <el-button type="primary" @click="maintenanceDialogVisible = true">执行保养</el-button>
              </div>
              <el-table v-if="maintenanceRecords.length" :data="maintenanceRecords" stripe border class="detail-table" :header-cell-style="tableHeaderStyle">
                <el-table-column prop="recordNo" label="保养单号" min-width="140" align="center" />
                <el-table-column prop="planName" label="保养计划" min-width="140" align="center" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.planName || '--' }}</template>
                </el-table-column>
                <el-table-column prop="maintainerName" label="保养人员" width="100" align="center">
                  <template #default="{ row }">{{ row.maintainerName || '--' }}</template>
                </el-table-column>
                <el-table-column prop="maintenanceTime" label="保养时间" min-width="160" align="center">
                  <template #default="{ row }">{{ formatTime(row.maintenanceTime) }}</template>
                </el-table-column>
                <el-table-column label="保养项目" min-width="220" align="center">
                  <template #default="{ row }">
                    <div class="item-tags">
                      <el-tooltip
                        v-for="(item, idx) in row.maintenanceItems"
                        :key="idx"
                        :content="item.remark"
                        placement="top"
                        :show-after="200"
                        :disabled="!item.remark"
                      >
                        <el-tag size="small" :type="item.done ? 'success' : 'warning'" class="item-tag">
                          {{ item.itemName }}{{ item.done ? '' : '（未完成）' }}
                        </el-tag>
                      </el-tooltip>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="isCompleted" label="结果" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.isCompleted ? 'success' : 'warning'">{{ row.isCompletedLabel || (row.isCompleted ? '已完成' : '未完成') }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="备注" min-width="120" align="center">
                  <template #default="{ row }">
                    <el-tooltip v-if="row.remark" :content="row.remark" placement="top" :show-after="200">
                      <span class="table-ellipsis">{{ row.remark }}</span>
                    </el-tooltip>
                    <span v-else class="table-placeholder">--</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="88" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      size="small"
                      class="record-pill-btn record-pill-btn--delete"
                      :loading="deletingMaintenanceId === row.id"
                      @click="removeMaintenanceRecord(row)"
                    >
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无保养记录" />
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`维修记录 (${repairOrders.length})`" name="repairs">
            <div class="tab-content-box">
              <div class="tab-toolbar">
                <el-button type="danger" plain @click="repairDialogVisible = true">提交报修</el-button>
              </div>
              <el-table v-if="repairOrders.length" :data="repairOrders" stripe border :header-cell-style="tableHeaderStyle">
                <el-table-column prop="repairNo" label="维修单号" min-width="140" />
                <el-table-column prop="faultReason" label="故障原因" min-width="140">
                  <template #default="{ row }">
                    <el-tooltip v-if="row.faultReason" :content="row.faultReason" placement="top" :show-after="200">
                      <span class="table-ellipsis">{{ row.faultReason }}</span>
                    </el-tooltip>
                    <span v-else class="table-placeholder">--</span>
                  </template>
                </el-table-column>
                <el-table-column prop="faultCode" label="故障代码" width="110" align="center">
                  <template #default="{ row }">{{ row.faultCode || '--' }}</template>
                </el-table-column>
                <el-table-column prop="reporterName" label="报修人" width="90" align="center" />
                <el-table-column prop="repairerName" label="维修人" width="90" align="center">
                  <template #default="{ row }">{{ row.repairerName || '--' }}</template>
                </el-table-column>
                <el-table-column prop="reportTime" label="报修时间" min-width="150">
                  <template #default="{ row }">{{ formatTime(row.reportTime) }}</template>
                </el-table-column>
                <el-table-column prop="repairMinutes" label="耗时(分)" width="90" align="center">
                  <template #default="{ row }">{{ row.repairMinutes ?? '--' }}</template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="repairStatusTag(row.status)">{{ row.statusLabel || row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="repairResult" label="维修结果" min-width="120">
                  <template #default="{ row }">
                    <el-tooltip v-if="row.repairResult" :content="row.repairResult" placement="top" :show-after="200">
                      <span class="table-ellipsis">{{ row.repairResult }}</span>
                    </el-tooltip>
                    <span v-else class="table-placeholder">--</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="148" align="center" fixed="right">
                  <template #default="{ row }">
                    <div class="record-actions">
                      <el-button
                        v-if="row.status === 'open' || row.status === 'processing'"
                        size="small"
                        class="action-btn action-btn--process"
                        @click="openRepairComplete(row)"
                      >
                        处理
                      </el-button>
                      <el-button
                        size="small"
                        class="record-pill-btn record-pill-btn--delete"
                        :loading="deletingRepairId === row.id"
                        @click="removeRepairOrder(row)"
                      >
                        删除
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无维修记录" />
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`关联异常 (${exceptions.length})`" name="exceptions">
            <div class="tab-content-box">
              <el-table v-if="exceptions.length" :data="exceptions" stripe border :header-cell-style="tableHeaderStyle">
              <el-table-column prop="eventNo" label="异常编号" min-width="120" />
              <el-table-column prop="description" label="故障描述" min-width="220">
                <template #default="{ row }">
                  <el-tooltip v-if="row.description" :content="row.description" placement="top" :show-after="200">
                    <span class="table-ellipsis">{{ row.description }}</span>
                  </el-tooltip>
                  <span v-else class="table-placeholder">--</span>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }"><StatusTag :status="row.status" /></template>
              </el-table-column>
              <el-table-column prop="occurTime" label="发生时间" min-width="160" />
              <el-table-column prop="handleResult" label="处理结果" min-width="120" />
              <el-table-column label="操作" width="88" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button
                    size="small"
                    class="record-pill-btn record-pill-btn--delete"
                    :loading="deletingExceptionId === row.id"
                    @click="removeDeviceException(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
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

    <DeviceInspectionDialog
      v-if="detail"
      v-model:visible="inspectionDialogVisible"
      :device-id="detail.id"
      :device-name="detail.deviceName"
      @success="onInspectionSuccess"
    />
    <DeviceMaintenanceDialog
      v-if="detail"
      v-model:visible="maintenanceDialogVisible"
      :device-id="detail.id"
      :device-name="detail.deviceName"
      @success="onMaintenanceSuccess"
    />
    <DeviceRepairDialog
      v-if="detail"
      v-model:visible="repairDialogVisible"
      :device-id="detail.id"
      :device-name="detail.deviceName"
      @success="onRepairSuccess"
    />
    <DeviceRepairCompleteDialog
      v-model:visible="repairCompleteVisible"
      :repair="activeRepair"
      @success="onRepairSuccess"
      @started="onRepairStarted"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { getDeviceFullDetail, getDeviceProcessRecords, updateDeviceStatus, type DeviceFullDetail, type DeviceHistoryItem, type DeviceProcessRecordItem, type DeviceTodayAlert } from '@/api/devices'
import { getDeviceInspectionRecords, deleteDeviceInspectionRecord, type DeviceInspectionRecord } from '@/api/deviceInspections'
import { getDeviceMaintenanceRecords, deleteDeviceMaintenanceRecord, type DeviceMaintenanceRecord } from '@/api/deviceMaintenances'
import { getDeviceRepairs, deleteDeviceRepair, type DeviceRepairOrder } from '@/api/deviceRepairs'
import { deleteException } from '@/api/exceptions'
import DeviceInspectionDialog from '@/components/device/DeviceInspectionDialog.vue'
import DeviceMaintenanceDialog from '@/components/device/DeviceMaintenanceDialog.vue'
import DeviceRepairDialog from '@/components/device/DeviceRepairDialog.vue'
import DeviceRepairCompleteDialog from '@/components/device/DeviceRepairCompleteDialog.vue'
import DeviceAlertPanel from '@/components/device/DeviceAlertPanel.vue'
import { formatDurationMinutes } from '@/utils/duration'
import { DEVICE_STATUSES, deviceActionLabel, deviceStatusLabel, deviceStatusTagType } from '@/utils/deviceLabels'
import { useNotificationStore } from '@/stores/notifications'
import { confirmDelete } from '@/utils/confirmDelete'

const notificationStore = useNotificationStore()
const { deviceAlertVersion, lastDeviceAlert } = storeToRefs(notificationStore)

const props = defineProps<{
  id?: string | number
  initialTab?: string
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
const activeTab = ref(props.initialTab || 'basic')
const detail = ref<DeviceFullDetail | null>(null)
const processRecords = ref<DeviceProcessRecordItem[]>([])
const inspectionRecords = ref<DeviceInspectionRecord[]>([])
const maintenanceRecords = ref<DeviceMaintenanceRecord[]>([])
const repairOrders = ref<DeviceRepairOrder[]>([])
const inspectionDialogVisible = ref(false)
const maintenanceDialogVisible = ref(false)
const repairDialogVisible = ref(false)
const repairCompleteVisible = ref(false)
const activeRepair = ref<DeviceRepairOrder | null>(null)
const deletingInspectionId = ref<string | number | null>(null)
const deletingMaintenanceId = ref<string | number | null>(null)
const deletingRepairId = ref<string | number | null>(null)
const deletingExceptionId = ref<string | number | null>(null)
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
const todayAlerts = computed(() => detail.value?.runtime?.todayAlerts ?? [])

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
    case 'inspection': return 'action-inspection'
    case 'maintenance': return 'action-maintenance'
    case 'repair': return 'action-repair'
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

function repairStatusTag(status?: string) {
  switch (status) {
    case 'completed': return 'success'
    case 'processing': return 'warning'
    case 'open': return 'danger'
    default: return 'info'
  }
}

async function loadDetail() {
  const id = deviceId.value
  if (!id) return
  loading.value = true
  try {
    const [detailData, records, inspections, maintenances, repairs] = await Promise.all([
      getDeviceFullDetail(id),
      getDeviceProcessRecords(id),
      getDeviceInspectionRecords(id),
      getDeviceMaintenanceRecords(id),
      getDeviceRepairs({ deviceId: id })
    ])
    detail.value = detailData
    processRecords.value = records
    inspectionRecords.value = inspections
    maintenanceRecords.value = maintenances
    repairOrders.value = repairs
    statusForm.status = detail.value.status ?? 'idle'
  } catch (error) {
    console.error(error)
    ElMessage.error('加载设备详情失败')
  } finally {
    loading.value = false
  }
}

async function onInspectionSuccess() {
  await loadDetail()
  activeTab.value = 'inspections'
  ElMessage.success('点检已提交，请在下方点检记录中查看')
  emit('save-success')
}

async function onMaintenanceSuccess() {
  await loadDetail()
  activeTab.value = 'maintenances'
  ElMessage.success('保养已提交，请在下方保养记录中查看')
  emit('save-success')
}

async function removeInspectionRecord(row: DeviceInspectionRecord) {
  const ok = await confirmDelete({
    title: '删除点检记录',
    message: `确定删除点检单「${row.recordNo}」？删除后不可恢复。`
  })
  if (!ok) return
  deletingInspectionId.value = row.id
  try {
    await deleteDeviceInspectionRecord(row.id)
    ElMessage.success('点检记录已删除')
    await loadDetail()
    emit('save-success')
  } catch (error) {
    console.error(error)
    ElMessage.error('删除失败')
  } finally {
    deletingInspectionId.value = null
  }
}

async function removeMaintenanceRecord(row: DeviceMaintenanceRecord) {
  const ok = await confirmDelete({
    title: '删除保养记录',
    message: `确定删除保养单「${row.recordNo}」？删除后不可恢复。`
  })
  if (!ok) return
  deletingMaintenanceId.value = row.id
  try {
    await deleteDeviceMaintenanceRecord(row.id)
    ElMessage.success('保养记录已删除')
    await loadDetail()
    emit('save-success')
  } catch (error) {
    console.error(error)
    ElMessage.error('删除失败')
  } finally {
    deletingMaintenanceId.value = null
  }
}

async function removeRepairOrder(row: DeviceRepairOrder) {
  const ok = await confirmDelete({
    title: '删除维修记录',
    message: `确定删除维修单「${row.repairNo}」？删除后不可恢复。`
  })
  if (!ok) return
  deletingRepairId.value = row.id
  try {
    await deleteDeviceRepair(row.id)
    ElMessage.success('维修记录已删除')
    await loadDetail()
    emit('save-success')
  } catch (error) {
    console.error(error)
    ElMessage.error('删除失败')
  } finally {
    deletingRepairId.value = null
  }
}

type DeviceExceptionItem = NonNullable<DeviceFullDetail['exceptions']>[number]

async function removeDeviceException(row: DeviceExceptionItem) {
  const ok = await confirmDelete({
    title: '删除关联异常',
    message: `确定删除异常「${row.eventNo}」？删除后不可恢复。`
  })
  if (!ok) return
  deletingExceptionId.value = row.id
  try {
    await deleteException(row.id)
    ElMessage.success('异常记录已删除')
    await loadDetail()
    emit('save-success')
  } catch (error) {
    console.error(error)
    ElMessage.error('删除失败')
  } finally {
    deletingExceptionId.value = null
  }
}

async function onRepairSuccess() {
  await loadDetail()
  activeTab.value = 'repairs'
  emit('save-success')
}

function openRepairComplete(row: DeviceRepairOrder) {
  activeRepair.value = row
  repairCompleteVisible.value = true
}

function onRepairStarted(repair: DeviceRepairOrder) {
  const index = repairOrders.value.findIndex((item) => item.id === repair.id)
  if (index >= 0) {
    repairOrders.value[index] = repair
  }
  activeRepair.value = repair
}

function handleAlertView(alert: DeviceTodayAlert) {
  if (alert.source === 'exc_event') {
    router.push('/exceptions')
    return
  }
  if (alert.source === 'dev_maintenance_plan') {
    maintenanceDialogVisible.value = true
    return
  }
  if (alert.source === 'dev_repair_order') {
    activeTab.value = 'repairs'
    const target = repairOrders.value.find((item) => String(item.id) === String(alert.sourceId))
    if (target && (target.status === 'open' || target.status === 'processing')) {
      openRepairComplete(target)
    }
    return
  }
  activeTab.value = 'exceptions'
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

watch(deviceAlertVersion, () => {
  const alertDeviceId = lastDeviceAlert.value?.deviceId
  if (alertDeviceId == null || String(alertDeviceId) !== deviceId.value) {
    return
  }
  void loadDetail()
})

onMounted(loadDetail)
</script>

<style scoped>
.summary-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.stat-card {
  flex: 1 1 0;
  min-width: 120px;
  text-align: center;
  border-radius: 12px !important;
  border: 1px solid #f1f5f9 !important;
  height: 92px !important;
  display: flex !important;
  flex-direction: column !important;
  justify-content: center !important;
  align-items: center !important;
  box-sizing: border-box !important;
  overflow: hidden;
}
.stat-card :deep(.el-card__body) {
  padding: 0 10px !important;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  width: 100%;
  overflow: hidden;
}
.stat-card__label { color: #64748b; font-size: 12px; margin-bottom: 6px; white-space: nowrap; }
.stat-card__value {
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
  max-width: 100%;
}
.stat-card__value--duration {
  font-size: 18px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 4px;
}
.stat-card__text { font-size: 15px; font-weight: 600; color: #334155; line-height: 1.2; }
.stat-card--run .stat-card__value { color: #16a34a; }
.stat-card--stop .stat-card__value { color: #d97706; }
.stat-card--rate .stat-card__value { color: #4f46e5; }
.stat-card--maint .stat-card__value { color: #ea580c; }
.stat-card--alert { cursor: pointer; }
.stat-card--alert:hover { border-color: #fecaca !important; }
@media (max-width: 768px) {
  .summary-row .stat-card {
    flex: 1 1 calc(50% - 8px);
    min-width: calc(50% - 8px);
  }
}
.status-tab-container {
  padding: 4px 0;
}
.runtime-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.runtime-metric {
  border-radius: 14px;
  padding: 16px 18px;
  border: 1px solid #e2e8f0;
  background: #fff;
}
.runtime-metric--run {
  background: linear-gradient(135deg, #f0fdf4 0%, #ecfdf5 100%);
  border-color: #bbf7d0;
}
.runtime-metric--stop {
  background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
  border-color: #fde68a;
}
.runtime-metric--rate {
  background: linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%);
  border-color: #c7d2fe;
}
.runtime-metric__label {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}
.runtime-metric__value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
}
.runtime-metric__hint {
  margin-top: 8px;
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.4;
}
.status-section-title {
  font-size: 14px;
  font-weight: 700;
  color: #334155;
  margin: 8px 0 12px;
  padding-left: 2px;
}
.tab-content-box {
  border: 1px solid var(--el-table-border-color, #ebeef5);
  border-radius: 8px;
  padding: 20px 24px;
  background: #fff;
}
.tab-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: flex-end;
}
.tab-action-btn {
  border-radius: 18px !important;
  font-weight: 600 !important;
  background: #0f172a !important;
  border: none !important;
}
.tab-action-btn:hover {
  background: #1e293b !important;
}
.detail-table :deep(.el-table__header .cell),
.detail-table :deep(.el-table__body .cell) {
  text-align: center;
}
.item-tags {
  display: inline-flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px;
  max-width: 100%;
}
.item-tag {
  margin: 0 !important;
  cursor: default;
}
.table-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  color: #334155;
}
.table-placeholder {
  color: #94a3b8;
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
.history-card__dot.action-inspection { color: #7c3aed; background: #ede9fe; }
.history-card__dot.action-maintenance { color: #d97706; background: #fef3c7; }
.history-card__dot.action-repair { color: #dc2626; background: #fee2e2; }
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
.history-badge.action-repair { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }
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

.action-btn {
  border-radius: 16px !important;
  font-weight: 600 !important;
  padding: 4px 12px !important;
  height: 24px !important;
}
.action-btn--process {
  color: #4f46e5 !important;
  border-color: #c7d2fe !important;
  background: #eef2ff !important;
}
.action-btn--process:hover {
  background: #e0e7ff !important;
  border-color: #a5b4fc !important;
  color: #4338ca !important;
}
.record-pill-btn {
  border-radius: 16px !important;
  padding: 4px 12px !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  height: 26px !important;
  background: #fff !important;
  transition: all 0.2s ease !important;
}
.record-pill-btn--delete {
  color: #ef4444 !important;
  border: 1px solid #fecaca !important;
}
.record-pill-btn--delete:hover:not(:disabled) {
  background: #fee2e2 !important;
  color: #b91c1c !important;
  border-color: #fca5a5 !important;
}
.record-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
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

@media (max-width: 900px) {
  .runtime-overview { grid-template-columns: 1fr; }
}

/* Fixed-height scrollable dialog container rules for prop mode to completely resolve jitter */
.is-prop-mode .detail-tabs-card {
  height: 560px !important;
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
