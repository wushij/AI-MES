<template>
  <div class="view-page">
    <PageHeader title="AI 智能排产" subtitle="利用 AI 对工单排序、识别瓶颈并给出派工建议。" />
    
    <div class="schedule-layout">
      <!-- Left Panel: Parameters -->
      <el-card shadow="hover" class="params-card">
        <template #header>
          <div class="card-header-title">排产参数</div>
        </template>
        <el-form label-width="80px" class="params-form" label-position="left">
          <el-form-item label="计划日期" class="params-form-item">
            <el-date-picker
              v-model="form.planDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="full-width custom-datepicker"
              placeholder="选择排产日期"
            />
          </el-form-item>
          
          <el-form-item label="选择工单" class="params-form-item">
            <div v-if="!workOrderOptions.length" class="empty-work-orders">
              <span>暂无待排产工单</span>
              <el-button link type="primary" @click="loadWorkOrders">刷新</el-button>
            </div>
            <div v-else class="work-order-list-container">
              <el-checkbox-group v-model="form.selectedWorkOrderIds" class="work-order-checkboxes">
                <div v-for="item in workOrderOptions" :key="item.id" class="checkbox-row">
                  <el-checkbox :label="item.id">
                    <span class="work-order-code">{{ item.code }}</span>
                    <span class="work-order-desc">{{ item.productName ? ` (${item.productName})` : '' }}</span>
                  </el-checkbox>
                </div>
              </el-checkbox-group>
            </div>
          </el-form-item>
          
          <el-form-item label="约束条件" class="params-form-item">
            <div class="constraint-group">
              <el-checkbox v-model="form.materialConstraint">物料可用性</el-checkbox>
              <el-checkbox v-model="form.deviceConstraint">设备负荷</el-checkbox>
              <el-checkbox v-model="form.teamConstraint">班组工时</el-checkbox>
            </div>
          </el-form-item>
          
          <div class="submit-action">
            <el-button
              type="primary"
              :loading="loading"
              class="submit-btn"
              @click="generateSuggestions"
            >
              获取 AI 建议
            </el-button>
          </div>
        </el-form>
      </el-card>

      <!-- Right Panel: Results -->
      <el-card shadow="hover" class="result-card">
        <template #header>
          <div class="result-card__header">
            <span class="card-header-title">排产结果</span>
            <div v-if="result" class="result-card__actions">
              <el-tag v-if="resultMode" size="small" :type="resultMode === 'live' ? 'success' : 'info'" effect="plain">
                {{ resultMode === 'live' ? 'Coze 工作流' : '演示数据' }}
              </el-tag>
              <el-button
                type="primary"
                class="apply-btn"
                :loading="applying"
                @click="applySuggestions"
              >
                应用建议
              </el-button>
            </div>
          </div>
        </template>
        
        <div v-if="loading" class="loading-state">
          <el-skeleton animated :rows="8" />
          <div class="loading-text">AI 正在调用 Coze 工作流分析排产方案，约需 30～90 秒，请勿关闭页面…</div>
          <el-progress :percentage="85" status="success" :indeterminate="true" class="custom-progress" />
        </div>
        
        <el-empty
          v-else-if="!result"
          description="在左侧选择待排产的工单并配置约束后，点击“获取 AI 建议”"
          class="custom-empty"
        />
        
        <template v-else>
          <el-alert
            v-if="resultMode === 'mock' && resultHint"
            type="warning"
            :title="resultHint"
            :closable="false"
            show-icon
            class="result-hint"
          />
          <!-- Priority Recommendations -->
          <el-card shadow="never" class="result-section">
            <template #header>
              <div class="section-title-wrapper">
                <div class="section-indicator" />
                <span class="section-title">优先级排序建议</span>
              </div>
            </template>
            <el-table :data="result.priorities" stripe border :header-cell-style="tableHeaderStyle">
              <el-table-column prop="rank" label="建议次序" width="90" align="center">
                <template #default="{ row }">
                  <span :class="['rank-badge', `rank-badge--${row.rank}`]">{{ row.rank }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="workOrderCode" label="工单号" min-width="140" align="center" />
              <el-table-column prop="priorityLabel" label="判定等级" min-width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.priorityLabel.includes('高') ? 'danger' : row.priorityLabel.includes('中') ? 'warning' : 'info'">
                    {{ row.priorityLabel }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="排产排序理由" min-width="260" show-overflow-tooltip align="center" />
            </el-table>
          </el-card>

          <!-- Bottleneck Analysis -->
          <el-card shadow="never" class="result-section">
            <template #header>
              <div class="section-title-wrapper">
                <div class="section-indicator" />
                <span class="section-title">工序负荷与瓶颈分析</span>
              </div>
            </template>
            <div v-if="result.bottlenecks.length" class="bottleneck-list">
              <div v-for="item in result.bottlenecks" :key="item.processName" class="bottleneck-item">
                <div class="bottleneck-item__top">
                  <span class="bottleneck-name">{{ item.processName }}</span>
                  <strong :class="['bottleneck-rate', item.loadRate >= 90 ? 'bottleneck-rate--high' : '']">
                    预计负荷率: {{ item.loadRate }}%
                  </strong>
                </div>
                <el-progress
                  :percentage="item.loadRate"
                  :stroke-width="8"
                  :color="item.loadRate >= 90 ? '#ef4444' : '#4f46e5'"
                  class="bottleneck-progress"
                />
                <div class="bottleneck-item__hint">{{ item.suggestion }}</div>
              </div>
            </div>
            <el-empty v-else description="基于当前排产，各工位负荷良好，未发现明显瓶颈" class="small-empty" />
          </el-card>

          <!-- Dispatch Suggestions -->
          <el-card shadow="never" class="result-section">
            <template #header>
              <div class="section-title-wrapper">
                <div class="section-indicator" />
                <span class="section-title">派工班组与时间规划</span>
              </div>
            </template>
            <el-table :data="result.dispatches" stripe border :header-cell-style="tableHeaderStyle">
              <el-table-column prop="workOrderCode" label="工单号" min-width="140" align="center" />
              <el-table-column prop="teamName" label="建议承接班组" min-width="130" align="center">
                <template #default="{ row }">
                  <span class="team-badge">{{ row.teamName }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="startTime" label="建议启动时间" min-width="180" align="center" />
              <el-table-column prop="hours" label="预计耗时" min-width="120" align="center">
                <template #default="{ row }">
                  <span class="time-hours">{{ row.hours }} 小时</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </template>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import { getSchedulingSuggestions, applySchedulingSuggestions } from '@/api/coze'
import { getWorkOrders } from '@/api/workOrders'
import { priorityLabel } from '@/utils/labels'

interface WorkOrderOption { id: string | number; code: string; productName?: string }
interface SchedulingResult {
  priorities: Array<{ rank: number; workOrderCode: string; priorityLabel: string; reason: string }>
  bottlenecks: Array<{ processName: string; loadRate: number; suggestion: string }>
  dispatches: Array<{ workOrderCode: string; teamName: string; startTime: string; hours: string }>
}

const tableHeaderStyle = { background: '#F8FAFC', fontWeight: '600', color: '#475569' }
const router = useRouter()
const loading = ref(false)
const applying = ref(false)
const workOrderOptions = ref<WorkOrderOption[]>([])
const result = ref<SchedulingResult | null>(null)
const resultMode = ref<'live' | 'mock' | ''>('')
const resultHint = ref('')
const form = reactive({
  planDate: currentDate(),
  selectedWorkOrderIds: [] as Array<string | number>,
  materialConstraint: true,
  deviceConstraint: true,
  teamConstraint: true
})

onMounted(loadWorkOrders)

async function loadWorkOrders() {
  try {
    const [pendingResp, assignedResp] = await Promise.all([
      getWorkOrders({ status: 'pending', page: 1, size: 200 }),
      getWorkOrders({ status: 'assigned', page: 1, size: 200 })
    ])
    const merged = [...normalizeList(pendingResp), ...normalizeList(assignedResp)]
    const unique = new Map<string | number, WorkOrderOption>()
    merged.forEach((item: any) => {
      const id = item.id ?? item.workOrderId
      if (id == null || unique.has(id)) return
      unique.set(id, {
        id,
        code: String(item.orderNo ?? item.code ?? item.workOrderCode ?? '--'),
        productName: item.productName
      })
    })
    workOrderOptions.value = Array.from(unique.values())
  } catch (error) {
    console.error(error)
    ElMessage.error('加载工单列表失败')
  }
}

async function generateSuggestions() {
  if (!form.selectedWorkOrderIds.length) {
    ElMessage.warning('请至少选择一个工单进行排产')
    return
  }
  loading.value = true
  resultMode.value = ''
  resultHint.value = ''
  try {
    const response = await getSchedulingSuggestions({
      planDate: form.planDate,
      workOrderIds: form.selectedWorkOrderIds.map((id) => Number(id))
    })
    const payload = (response as Record<string, unknown>) ?? {}
    resultMode.value = payload.mode === 'live' ? 'live' : 'mock'
    const dataObj = (payload.result as Record<string, unknown>) ?? payload
    result.value = mapSchedulingResult(dataObj)
    resultHint.value = String(payload.message ?? '')
    if (resultMode.value === 'live') {
      ElMessage.success('AI 排产建议已生成')
    } else {
      ElMessage.warning(resultHint.value || '当前为演示排产结果')
    }
  } catch (error) {
    console.error('[AiScheduling] 获取排产建议失败', error)
    const message = error instanceof Error ? error.message : '获取排产建议失败'
    ElMessage.error(message.includes('timeout') ? 'AI 排产请求超时（工作流约需 30～90 秒），请稍后重试' : message)
  } finally {
    loading.value = false
  }
}

function mapSchedulingResult(dataObj: Record<string, unknown>): SchedulingResult {
  return {
    priorities: normalizeList(dataObj.priorities ?? dataObj.prioritySuggestions).map((item: any, index: number) => ({
      rank: Number(item.rank ?? index + 1),
      workOrderCode: String(item.workOrderCode ?? item.workOrderNo ?? item.orderNo ?? item.code ?? '--'),
      priorityLabel: String(item.priorityLabel ?? priorityLabel(item.priority) ?? '--'),
      reason: String(item.reason ?? item.comment ?? item.rationale ?? '--')
    })),
    bottlenecks: normalizeList(dataObj.bottlenecks ?? dataObj.bottleneckWarnings).map((item: any) => ({
      processName: String(item.processName ?? item.process ?? item.name ?? '--'),
      loadRate: parseLoadRate(item.loadRate ?? item.rate),
      suggestion: String(item.suggestion ?? item.advice ?? item.reason ?? '--')
    })),
    dispatches: normalizeList(dataObj.dispatches ?? dataObj.dispatchSuggestions).map((item: any) => ({
      workOrderCode: String(item.workOrderCode ?? item.workOrderNo ?? item.orderNo ?? item.code ?? '--'),
      teamName: String(item.teamName ?? item.team ?? item.suggestedTeam ?? '--'),
      startTime: String(item.startTime ?? item.suggestedStart ?? item.suggestedStartTime ?? '--'),
      hours: String(item.hours ?? item.estimatedHours ?? '--')
    }))
  }
}

function parseLoadRate(value: unknown): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return Math.min(100, Math.max(0, value))
  }
  const text = String(value ?? '').replace('%', '').trim()
  const num = Number(text)
  return Number.isFinite(num) ? Math.min(100, Math.max(0, num)) : 0
}

async function applySuggestions() {
  if (!result.value?.dispatches?.length) {
    ElMessage.warning('当前没有可应用的派工建议')
    return
  }
  applying.value = true
  try {
    const response = await applySchedulingSuggestions({
      planDate: form.planDate,
      dispatches: result.value.dispatches
    })
    const appliedCount = Number(response.appliedCount ?? 0)
    const skippedCount = Number(response.skippedCount ?? 0)
    const applied = normalizeList(response.applied)
    if (appliedCount > 0) {
      const lines = applied.map((item: any) => {
        const code = String(item.orderNo ?? item.workOrderCode ?? item.code ?? '--')
        const team = String(item.teamName ?? '未分配')
        return `· ${code} → 班组「${team}」，状态「已派工」`
      })
      try {
        await ElMessageBox.confirm(
          `${lines.join('\n')}\n\n变更已写入工单（班组、状态、备注），不会修改生产计划。\n是否前往「工单管理」查看？`,
          '排产建议已应用',
          {
            confirmButtonText: '前往工单管理',
            cancelButtonText: '留在此页',
            type: 'success'
          }
        )
        const firstCode = String(applied[0]?.orderNo ?? applied[0]?.workOrderCode ?? applied[0]?.code ?? '')
        await router.push({
          path: '/work-orders',
          query: firstCode ? { keyword: firstCode } : undefined
        })
      } catch {
        ElMessage.success(`已派工 ${appliedCount} 个工单，请到左侧菜单「工单管理」查看`)
      }
      await loadWorkOrders()
    } else {
      const reason = response.skipped?.[0]?.reason ?? '请检查班组名称是否为甲班/乙班'
      ElMessage.warning(`没有工单被更新：${reason}`)
    }
    if (skippedCount > 0 && appliedCount > 0) {
      ElMessage.warning(`${skippedCount} 条建议未能应用，请查看控制台详情`)
      console.warn('[AiScheduling] skipped dispatches', response.skipped)
    }
  } catch (error) {
    console.error('[AiScheduling] 应用建议失败', error)
    ElMessage.error('应用建议失败')
  } finally {
    applying.value = false
  }
}

function normalizeList(value: any) {
  const payload = value?.data ?? value
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.records)) return payload.records
  if (Array.isArray(payload?.list)) return payload.list
  if (Array.isArray(payload?.items)) return payload.items
  return []
}

function currentDate() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.schedule-layout {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 20px;
  align-items: start;
}

.params-card, .result-card {
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03) !important;
}

.card-header-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.params-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.params-form-item {
  margin-bottom: 0;
}

/* Custom capsule input borders */
.custom-datepicker :deep(.el-input__wrapper) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 4px 16px !important;
}

.custom-datepicker :deep(.el-input__wrapper.is-focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

/* Beautiful Work Order checkboxes scroll list */
.work-order-list-container {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background-color: #f8fafc;
  padding: 8px 12px;
  max-height: 240px;
  overflow-y: auto;
  transition: all 0.3s ease;
}
.work-order-list-container:hover {
  border-color: #cbd5e1;
}

.work-order-checkboxes {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.checkbox-row {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  border-radius: 8px;
  transition: background 0.2s ease;
}
.checkbox-row:hover {
  background: #f1f5f9;
}

.work-order-code {
  font-family: monospace;
  font-weight: 600;
  color: #334155;
  font-size: 13px;
}

.work-order-desc {
  font-size: 12px;
  color: #64748b;
}

.empty-work-orders {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  color: #94a3b8;
  padding: 20px 16px;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 12px;
}

/* Constraint Layout Group */
.constraint-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #f8fafc;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  width: 100%;
}

.constraint-group :deep(.el-checkbox) {
  margin-right: 0;
  height: auto;
}

.submit-action {
  margin-top: 8px;
}

.submit-btn {
  width: 100%;
  border-radius: 20px !important;
  padding: 12px 20px !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%) !important;
  border: none !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
  font-weight: 600;
  transition: all 0.2s ease !important;
}
.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.35) !important;
}

/* Results styles */
.result-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.result-card__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.apply-btn {
  border-radius: 20px !important;
  padding: 8px 18px !important;
  background: #10b981 !important;
  border: none !important;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.2) !important;
  font-weight: 500;
}
.apply-btn:hover {
  background: #059669 !important;
  box-shadow: 0 6px 16px rgba(16, 185, 129, 0.3) !important;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  text-align: center;
  gap: 20px;
}

.loading-text {
  font-size: 14px;
  color: #64748b;
  font-weight: 500;
}

.custom-progress {
  width: 80%;
}

.custom-empty {
  padding: 80px 0;
}

.result-section {
  border-radius: 12px;
  border: 1px solid #f1f5f9 !important;
}
.result-section + .result-section {
  margin-top: 20px;
}

.result-hint {
  margin-bottom: 16px;
}

.section-title-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-indicator {
  width: 4px;
  height: 16px;
  background: #4f46e5;
  border-radius: 2px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

/* Priority rank circles */
.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  background: #f1f5f9;
  color: #64748b;
}
.rank-badge--1 {
  background: #fef2f2;
  color: #ef4444;
}
.rank-badge--2 {
  background: #fffbeb;
  color: #d97706;
}
.rank-badge--3 {
  background: #f0fdf4;
  color: #10b981;
}

/* Bottlenecks lists */
.bottleneck-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.bottleneck-item {
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bottleneck-item__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.bottleneck-name {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.bottleneck-rate {
  font-size: 13px;
  color: #4f46e5;
}

.bottleneck-rate--high {
  color: #ef4444;
}

.bottleneck-progress {
  margin: 4px 0;
}

.bottleneck-item__hint {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  background: #fff;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid #f1f5f9;
}

.team-badge {
  background: rgba(79, 70, 229, 0.08);
  color: #4f46e5;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
}

.time-hours {
  font-weight: 600;
  color: #0f172a;
}

@media (max-width: 1100px) {
  .schedule-layout {
    grid-template-columns: 1fr;
  }
}
</style>
