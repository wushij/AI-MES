<template>
  <div class="view-page">
    <PageHeader title="AI 智能排产" subtitle="基于 MES 实时态势生成可解释、可确认的排产决策方案。" />

    <!-- KPI 概览 -->
    <div v-if="kpiCards.length" class="kpi-row">
      <div v-for="card in kpiCards" :key="card.label" class="kpi-card" :class="`kpi-card--${card.tone}`">
        <div class="kpi-card__value">{{ card.value }}</div>
        <div class="kpi-card__label">{{ card.label }}</div>
      </div>
    </div>

    <div class="schedule-layout">
      <!-- 左侧：参数 -->
      <el-card shadow="hover" class="params-card">
        <template #header>
          <div class="params-card__header">
            <div class="params-card__header-icon"><el-icon><Setting /></el-icon></div>
            <div>
              <div class="card-header-title">排产参数</div>
              <div class="params-card__subtitle">选择一个工单，配置约束后获取 AI 建议</div>
            </div>
          </div>
        </template>

        <div class="params-panel">
          <section class="param-section">
            <div class="param-section__title">
              <el-icon><MagicStick /></el-icon>
              <span>场景预设</span>
            </div>
            <div class="preset-grid">
              <button
                v-for="preset in SCENARIO_PRESETS"
                :key="preset.id"
                type="button"
                class="preset-card"
                :class="{ 'preset-card--active': activePreset === preset.id }"
                @click="applyPreset(preset)"
              >
                <span class="preset-card__icon">{{ presetIcon(preset.id) }}</span>
                <span class="preset-card__label">{{ preset.label }}</span>
                <span class="preset-card__desc">{{ preset.desc }}</span>
              </button>
            </div>
          </section>

          <section class="param-section">
            <div class="param-section__title">
              <el-icon><Calendar /></el-icon>
              <span>计划日期</span>
            </div>
            <el-date-picker
              v-model="form.planDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="full-width custom-datepicker"
              placeholder="选择排产日期"
            />
          </section>

          <section class="param-section">
            <div class="param-section__title param-section__title--row">
              <div class="param-section__title-left">
                <el-icon><List /></el-icon>
                <span>选择工单</span>
                <em v-if="selectedWorkOrderCode" class="param-section__count">
                  {{ selectedWorkOrderCode }}
                </em>
              </div>
              <div class="work-order-toolbar">
                <button type="button" class="work-order-action-btn" @click="clearWorkOrderSelection">取消选择</button>
                <button type="button" class="work-order-action-btn" @click="loadWorkOrders">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </button>
              </div>
            </div>
            <div class="work-order-hint">
              <el-icon><InfoFilled /></el-icon>
              <span>每次仅排一个工单，点选卡片切换后重新获取建议</span>
            </div>
            <div v-if="!workOrderOptions.length" class="empty-work-orders">
              <span>暂无待排产工单</span>
            </div>
            <div v-else class="work-order-list-container">
              <div class="work-order-picker">
                <button
                  v-for="item in workOrderOptions"
                  :key="item.id"
                  type="button"
                  class="work-order-card"
                  :class="{ 'work-order-card--active': form.selectedWorkOrderId === item.id }"
                  @click="selectWorkOrder(item.id)"
                >
                  <span class="work-order-card__indicator" aria-hidden="true">
                    <span class="work-order-card__dot" />
                  </span>
                  <span class="work-order-card__main">
                    <span class="work-order-card__code">{{ item.code }}</span>
                    <span class="work-order-card__product">{{ item.productName || '未填写产品名' }}</span>
                  </span>
                  <el-icon v-if="form.selectedWorkOrderId === item.id" class="work-order-card__check">
                    <CircleCheckFilled />
                  </el-icon>
                </button>
              </div>
            </div>
          </section>

          <section v-if="selectedSituations.length" class="param-section">
            <div class="param-section__title">
              <el-icon><Timer /></el-icon>
              <span>工单态势</span>
            </div>
            <div class="situation-list">
              <div v-for="item in selectedSituations" :key="item.orderNo" class="situation-card">
                <div class="situation-card__head">
                  <strong>{{ item.orderNo }}</strong>
                  <el-tag size="small" :type="item.overdue ? 'danger' : 'info'" effect="plain" round>
                    {{ item.deadlineLabel }}
                  </el-tag>
                </div>
                <div class="situation-card__meta">
                  <span>{{ statusLabel(item.status) }}</span>
                  <span>进度 {{ item.progress ?? 0 }}%</span>
                  <span>{{ item.teamName || '未派班组' }}</span>
                </div>
                <div v-if="item.exceptionCount || item.materialRisk === 'warning'" class="situation-card__tags">
                  <el-tag v-if="item.exceptionCount" size="small" type="warning" effect="plain" round>
                    异常 {{ item.exceptionCount }}
                  </el-tag>
                  <el-tag v-if="item.materialRisk === 'warning'" size="small" type="danger" effect="plain" round>
                    物料风险
                  </el-tag>
                </div>
              </div>
            </div>
          </section>

          <el-alert
            v-if="contextExceptions.length"
            type="warning"
            :closable="false"
            show-icon
            class="inline-alert"
            :title="`存在 ${contextExceptions.length} 条未处理异常，建议人工复核`"
          />

          <section class="param-section">
            <div class="param-section__title">
              <el-icon><Filter /></el-icon>
              <span>约束条件</span>
            </div>
            <div class="constraint-grid">
              <label
                class="constraint-pill"
                :class="{ 'constraint-pill--on': form.materialConstraint }"
              >
                <el-checkbox v-model="form.materialConstraint" @change="activePreset = ''" />
                <span>物料可用性</span>
              </label>
              <label
                class="constraint-pill"
                :class="{ 'constraint-pill--on': form.deviceConstraint }"
              >
                <el-checkbox v-model="form.deviceConstraint" @change="activePreset = ''" />
                <span>设备负荷</span>
              </label>
              <label
                class="constraint-pill"
                :class="{ 'constraint-pill--on': form.teamConstraint }"
              >
                <el-checkbox v-model="form.teamConstraint" @change="activePreset = ''" />
                <span>班组工时</span>
              </label>
            </div>
          </section>

          <div class="submit-action">
            <el-button type="primary" :loading="loading" class="submit-btn" @click="generateSuggestions">
              <el-icon v-if="!loading" class="submit-btn__icon"><MagicStick /></el-icon>
              获取 AI 建议
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 右侧：结果 -->
      <el-card shadow="hover" class="result-card">
        <template #header>
          <div class="result-card__header">
            <div class="result-card__title-wrap">
              <span class="card-header-title">排产结果</span>
              <span v-if="result && generatedAtLabel" class="result-card__timestamp">上次生成 {{ generatedAtLabel }}</span>
            </div>
            <div v-if="result" class="result-card__actions">
              <el-tag v-if="resultMode" size="small" :type="resultMode === 'live' ? 'success' : 'info'" effect="plain">
                {{ resultMode === 'live' ? 'Coze 工作流' : '演示数据' }}
              </el-tag>
              <el-button type="primary" class="apply-btn" :loading="applying" @click="openApplyPreview">
                应用建议
              </el-button>
            </div>
          </div>
        </template>

        <div class="result-body">
        <div v-if="loading" class="loading-state">
          <el-skeleton animated :rows="8" />
          <div class="loading-text">AI 正在调用 Coze 工作流分析排产方案，约需 30～90 秒，请勿关闭页面…</div>
          <el-progress :percentage="85" status="success" :indeterminate="true" class="custom-progress" />
        </div>

        <el-empty
          v-else-if="!result"
          description="在左侧选择一个待排产工单并配置约束后，点击「获取 AI 建议」"
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

          <div class="constraint-summary">
            <span class="constraint-summary__label">本次约束：</span>
            <el-tag size="small" :type="appliedConstraints.materialAvailability ? 'success' : 'info'" effect="plain">
              物料可用性 {{ appliedConstraints.materialAvailability ? '已启用' : '已忽略' }}
            </el-tag>
            <el-tag size="small" :type="appliedConstraints.deviceLoad ? 'success' : 'info'" effect="plain">
              设备负荷 {{ appliedConstraints.deviceLoad ? '已启用' : '已忽略' }}
            </el-tag>
            <el-tag size="small" :type="appliedConstraints.teamHours ? 'success' : 'info'" effect="plain">
              班组工时 {{ appliedConstraints.teamHours ? '已启用' : '已忽略' }}
            </el-tag>
          </div>

          <!-- 8. AI 排产解读 -->
          <el-alert
            v-if="resultSummary"
            type="info"
            :closable="false"
            show-icon
            class="summary-alert"
            :title="resultSummary"
          />

          <!-- 7. 异常影响（结果区） -->
          <el-alert
            v-if="contextExceptions.length"
            type="warning"
            :closable="false"
            show-icon
            class="result-hint"
          >
            <template #title>异常影响提示</template>
            <ul class="impact-list">
              <li v-for="exc in contextExceptions.slice(0, 3)" :key="exc.id">
                {{ exc.workOrderNo }} · {{ exceptionTypeLabel(exc.eventType) }}
                <template v-if="exc.deviceName"> · {{ exc.deviceName }}</template>
                ：{{ exc.description }}
              </li>
            </ul>
          </el-alert>

          <!-- 设备负荷（上下文） -->
          <el-card v-if="appliedConstraints.deviceLoad && contextDevices.length" shadow="never" class="result-section">
            <template #header>
              <div class="section-title-wrapper">
                <div class="section-indicator section-indicator--red" />
                <span class="section-title">设备负荷概览</span>
                <el-button class="header-pill-btn btn-device" @click="router.push('/devices')">查看设备</el-button>
              </div>
            </template>
            <div class="team-load-grid">
              <div v-for="dev in contextDevices" :key="dev.id" class="team-load-card">
                <div class="team-load-card__head">
                  <strong>{{ dev.deviceName }}</strong>
                  <span>{{ dev.statusLabel ?? dev.status }}</span>
                </div>
                <el-progress
                  :percentage="dev.loadRate ?? 0"
                  :stroke-width="10"
                  :color="(dev.loadRate ?? 0) >= 85 ? '#ef4444' : '#4f46e5'"
                />
                <div class="team-load-card__meta">
                  <span>{{ dev.deviceCode }}</span>
                  <span>{{ dev.lineName || '未分配产线' }}</span>
                  <span v-if="dev.openExceptionCount">异常 {{ dev.openExceptionCount }}</span>
                </div>
              </div>
            </div>
          </el-card>

          <!-- 2. 甘特图 -->
          <el-card shadow="never" class="result-section">
            <template #header>
              <div class="section-title-wrapper">
                <div class="section-indicator" />
                <span class="section-title">排产时间轴</span>
              </div>
            </template>
            <SchedulingGantt :items="ganttItems" />
          </el-card>

          <!-- 5. 班组负荷 -->
          <el-card shadow="never" class="result-section">
            <template #header>
              <div class="section-title-wrapper">
                <div class="section-indicator section-indicator--green" />
                <span class="section-title">班组负荷概览</span>
              </div>
            </template>
            <div class="team-load-grid">
              <div v-for="team in teamLoads" :key="team.teamName" class="team-load-card">
                <div class="team-load-card__head">
                  <strong>{{ team.teamName }}</strong>
                  <span>{{ team.activeTaskCount ?? 0 }} 在制</span>
                </div>
                <el-progress
                  :percentage="team.loadRate ?? 0"
                  :stroke-width="10"
                  :color="(team.loadRate ?? 0) >= 85 ? '#ef4444' : '#4f46e5'"
                />
                <div class="team-load-card__meta">
                  <span>待启动 {{ team.pendingCount ?? 0 }}</span>
                  <span>生产中 {{ team.producingCount ?? 0 }}</span>
                  <span v-if="team.proposedHours">+{{ team.proposedHours }}h 建议</span>
                </div>
              </div>
            </div>
          </el-card>

          <!-- 6. 物料影响链 -->
          <el-card v-if="appliedConstraints.materialAvailability && materialAlerts.length" shadow="never" class="result-section">
            <template #header>
              <div class="section-title-wrapper">
                <div class="section-indicator section-indicator--amber" />
                <span class="section-title">物料影响链</span>
                <el-button class="header-pill-btn btn-material" @click="router.push('/materials')">查看物料</el-button>
              </div>
            </template>
            <div class="material-chain">
              <div v-for="mat in materialAlerts.slice(0, 6)" :key="mat.id" class="material-chain__item">
                <div class="material-chain__name">{{ mat.materialName }}</div>
                <div class="material-chain__stock">
                  库存 {{ mat.stockQty }}{{ mat.unit }} / 安全 {{ mat.safetyStock }}{{ mat.unit }}
                </div>
                <div class="material-chain__hint">缺料可能影响备料与装配工序排产，建议优先补料</div>
              </div>
            </div>
          </el-card>

          <!-- 优先级 -->
          <el-card shadow="never" class="result-section">
            <template #header>
              <div class="section-title-wrapper">
                <div class="section-indicator" />
                <span class="section-title">排产优先级建议</span>
              </div>
            </template>
            <el-table :data="result.priorities" stripe border :header-cell-style="tableHeaderStyle">
              <el-table-column prop="workOrderCode" label="工单号" min-width="140" align="center" />
              <el-table-column prop="priorityLabel" label="建议优先级" min-width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.priorityLabel.includes('高') ? 'danger' : row.priorityLabel.includes('中') ? 'warning' : 'info'">
                    {{ row.priorityLabel }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="排产排序理由" min-width="260" show-overflow-tooltip align="center" />
            </el-table>
          </el-card>

          <!-- 瓶颈 -->
          <el-card shadow="never" class="result-section">
            <template #header>
              <div class="section-title-wrapper">
                <div class="section-indicator" />
                <span class="section-title">工序负荷与瓶颈分析</span>
              </div>
            </template>
            <div v-if="result.bottlenecks.length" class="bottleneck-list">
              <div
                v-for="item in result.bottlenecks"
                :key="item.processName + item.suggestion"
                class="bottleneck-item"
                :class="{ 'bottleneck-item--high': item.loadRate >= 90 }"
              >
                <div class="bottleneck-item__head">
                  <div class="bottleneck-item__title">
                    <span class="bottleneck-item__name">{{ item.processName }}</span>
                    <span v-if="item.loadRate >= 90" class="bottleneck-item__tag">瓶颈</span>
                    <span v-else-if="item.loadRate >= 70" class="bottleneck-item__tag bottleneck-item__tag--warn">偏高</span>
                  </div>
                  <div class="bottleneck-item__rate" :class="{ 'is-high': item.loadRate >= 90 }">
                    <span class="bottleneck-item__rate-value">{{ item.loadRate }}</span>
                    <span class="bottleneck-item__rate-unit">%</span>
                  </div>
                </div>
                <div class="bottleneck-item__bar-wrap">
                  <el-progress
                    :percentage="item.loadRate"
                    :stroke-width="10"
                    :show-text="false"
                    :color="item.loadRate >= 90 ? '#ef4444' : item.loadRate >= 70 ? '#f59e0b' : '#4f46e5'"
                    class="bottleneck-progress"
                  />
                  <div class="bottleneck-item__bar-meta">
                    <span>工序负荷</span>
                    <span>{{ bottleneckLoadLabel(item.loadRate) }}</span>
                  </div>
                </div>
                <div v-if="item.suggestion" class="bottleneck-item__suggestion">
                  <el-icon><InfoFilled /></el-icon>
                  <p>{{ item.suggestion }}</p>
                </div>
              </div>
            </div>
            <el-empty v-else description="基于当前排产，各工位负荷良好，未发现明显瓶颈" class="small-empty" />
          </el-card>

          <!-- 派工 -->
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
        </div>
      </el-card>
    </div>

    <!-- 4. 应用前差异预览 -->
    <el-dialog v-model="applyDialogVisible" title="应用排产建议" width="920px" destroy-on-close class="apply-dialog">
      <div class="apply-dialog__meta">
        <div class="apply-dialog__meta-row">
          <span class="apply-dialog__label">计划日期</span>
          <strong>{{ form.planDate }}</strong>
        </div>
        <div class="apply-dialog__meta-row">
          <span class="apply-dialog__label">本次约束</span>
          <div class="apply-dialog__tags">
            <el-tag size="small" :type="appliedConstraints.materialAvailability ? 'success' : 'info'" effect="plain">
              物料 {{ appliedConstraints.materialAvailability ? '已启用' : '已忽略' }}
            </el-tag>
            <el-tag size="small" :type="appliedConstraints.deviceLoad ? 'success' : 'info'" effect="plain">
              设备 {{ appliedConstraints.deviceLoad ? '已启用' : '已忽略' }}
            </el-tag>
            <el-tag size="small" :type="appliedConstraints.teamHours ? 'success' : 'info'" effect="plain">
              班组 {{ appliedConstraints.teamHours ? '已启用' : '已忽略' }}
            </el-tag>
          </div>
        </div>
      </div>

      <div v-if="applyOverview" class="apply-dialog__overview">
        <div class="apply-dialog__overview-title">排产结论</div>
        <p v-if="applyOverview.summary" class="apply-dialog__overview-summary">{{ applyOverview.summary }}</p>
        <template v-if="applyOverview.bottlenecks.length">
          <div class="apply-dialog__overview-label">瓶颈与风险</div>
          <ul class="apply-dialog__overview-list">
            <li
              v-for="item in applyOverview.bottlenecks"
              :key="`${item.processName}-${item.loadRate}`"
              class="apply-dialog__overview-item"
            >
              <strong>{{ item.processName }}</strong>
              <el-tag size="small" type="warning" effect="plain">{{ item.loadRate }}%</el-tag>
              <span>{{ item.suggestion }}</span>
            </li>
          </ul>
        </template>
        <template v-if="applyOverview.priorities.length">
          <div class="apply-dialog__overview-label">工单依据</div>
          <ul class="apply-dialog__overview-list">
            <li
              v-for="item in applyOverview.priorities"
              :key="item.workOrderCode"
              class="apply-dialog__overview-item"
            >
              <strong>{{ item.workOrderCode }}</strong>
              <span>{{ item.reason }}</span>
            </li>
          </ul>
        </template>
      </div>

      <p class="diff-intro">
        下方表格仅展示将要写入工单的字段变更；完整排产说明见上方，并会一并保存到工单。
        <template v-if="applyChangeCount">
          共 {{ applyDiffRows.length }} 条派工，其中 {{ applyChangeCount }} 条班组/优先级有变更。
        </template>
      </p>

      <el-table :data="applyDiffRows" stripe border size="small" :header-cell-style="tableHeaderStyle" max-height="360">
        <el-table-column prop="workOrderCode" label="工单号" width="130" />
        <el-table-column label="班组变更" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'diff-changed': row.teamChanged }">
              {{ row.currentTeam }} → {{ row.suggestedTeam }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" min-width="110">
          <template #default="{ row }">
            <span :class="{ 'diff-changed': row.priorityChanged }">
              {{ row.currentPriority }} → {{ row.suggestedPriority }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="suggestedStart" label="建议开工" min-width="150" />
        <el-table-column prop="suggestedHours" label="工时" width="72" />
      </el-table>

      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="confirmApply">确认应用到工单</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="applySuccessDialogVisible"
      width="520px"
      class="apply-success-dialog"
      destroy-on-close
    >
      <template #header>
        <div class="apply-success-dialog__header">
          <div class="apply-success-dialog__icon">
            <el-icon><CircleCheckFilled /></el-icon>
          </div>
          <div>
            <div class="apply-success-dialog__title">排产建议已应用</div>
            <div class="apply-success-dialog__subtitle">已更新 {{ appliedResultRows.length }} 个工单的排产字段</div>
          </div>
        </div>
      </template>

      <div class="apply-success-list">
        <div v-for="row in appliedResultRows" :key="row.code" class="apply-success-card">
          <div class="apply-success-card__code">{{ row.code }}</div>
          <div class="apply-success-card__grid">
            <div class="apply-success-card__item">
              <span>班组</span>
              <strong>{{ row.team }}</strong>
            </div>
            <div class="apply-success-card__item">
              <span>优先级</span>
              <strong>{{ row.priority }}</strong>
            </div>
            <div v-if="row.startTime" class="apply-success-card__item">
              <span>建议开工</span>
              <strong>{{ row.startTime }}</strong>
            </div>
            <div v-if="row.hours" class="apply-success-card__item">
              <span>预计工时</span>
              <strong>{{ row.hours }} 小时</strong>
            </div>
          </div>
        </div>
      </div>

      <p class="apply-success-dialog__hint">班组、优先级、建议开工等信息已写入工单专用字段，备注保持不变。</p>

      <template #footer>
        <el-button @click="applySuccessDialogVisible = false">留在此页</el-button>
        <el-button type="primary" @click="goToWorkOrdersAfterApply">前往工单管理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Calendar, CircleCheckFilled, Filter, InfoFilled, List, MagicStick, Refresh, Setting, Timer } from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import SchedulingGantt from '@/components/scheduling/SchedulingGantt.vue'
import { useSchedulingStore } from '@/stores/scheduling'
import {
  applySchedulingSuggestions,
  getSchedulingContext,
  getSchedulingSuggestions
} from '@/api/coze'
import { getWorkOrders } from '@/api/workOrders'
import { priorityLabel } from '@/utils/labels'
import {
  SCENARIO_PRESETS,
  buildApplyDiffRows,
  buildGanttItems,
  enrichTeamLoads,
  exceptionTypeLabel,
  statusLabel,
  type ScenarioPreset
} from '@/utils/schedulingHelpers'

interface WorkOrderOption {
  id: string | number
  code: string
  productName?: string
}

interface AppliedResultRow {
  code: string
  team: string
  priority: string
  startTime: string
  hours: string
}

const tableHeaderStyle = { background: '#F8FAFC', fontWeight: '600', color: '#475569' }
const router = useRouter()
const schedulingStore = useSchedulingStore()
const form = schedulingStore.form
const {
  activePreset,
  result,
  resultMode,
  resultHint,
  resultSummary,
  appliedConstraints,
  schedulingContext,
  generatedAtLabel
} = storeToRefs(schedulingStore)

const loading = ref(false)
const applying = ref(false)
const contextLoading = ref(false)
const applyDialogVisible = ref(false)
const applySuccessDialogVisible = ref(false)
const appliedResultRows = ref<AppliedResultRow[]>([])
const workOrderOptions = ref<WorkOrderOption[]>([])

const selectedWorkOrderCode = computed(() => {
  if (form.selectedWorkOrderId == null) return ''
  const found = workOrderOptions.value.find((item) => item.id === form.selectedWorkOrderId)
  return found?.code ?? ''
})

const selectedSituations = computed(() => {
  if (form.selectedWorkOrderId == null) return []
  return (schedulingContext.value?.workOrders ?? []).filter(
    (item) => String(item.id) === String(form.selectedWorkOrderId)
  )
})

const contextExceptions = computed(() => schedulingContext.value?.exceptions ?? [])
const contextDevices = computed(() => schedulingContext.value?.devices ?? [])
const materialAlerts = computed(() => schedulingContext.value?.materialAlerts ?? [])

const kpiCards = computed(() => {
  const kpi = schedulingContext.value?.kpi
  if (!kpi && !result.value) return []
  const base = kpi ?? {}
  return [
    { label: '当前工单', value: base.selectedCount ?? (form.selectedWorkOrderId != null ? 1 : 0), tone: 'primary' },
    { label: '交期风险', value: base.overdueCount ?? 0, tone: 'danger' },
    { label: '未处理异常', value: base.exceptionCount ?? 0, tone: 'warning' },
    { label: '缺料预警', value: base.materialWarningCount ?? 0, tone: 'amber' },
    { label: '设备故障', value: base.deviceFaultCount ?? 0, tone: 'danger' }
  ]
})

const ganttItems = computed(() =>
  result.value ? buildGanttItems(result.value.dispatches, form.planDate) : []
)

const teamLoads = computed(() => {
  const base = schedulingContext.value?.teams ?? []
  if (!result.value?.dispatches?.length) return base
  return enrichTeamLoads(base, result.value.dispatches)
})

const applyDiffRows = computed(() => {
  if (!result.value) return []
  return buildApplyDiffRows(
    schedulingContext.value?.workOrders ?? [],
    result.value.priorities,
    result.value.dispatches
  )
})

const applyOverview = computed(() => {
  if (!result.value) return null
  const summary = (resultSummary.value || result.value.summary || '').trim()
  const bottlenecks = appliedConstraints.value.deviceLoad ? (result.value.bottlenecks ?? []) : []
  const priorityItems = (result.value.priorities ?? [])
    .filter((item) => {
      const reason = String(item.reason ?? '').trim()
      return reason && reason !== '--'
    })
    .map((item) => ({
      workOrderCode: item.workOrderCode,
      reason: String(item.reason ?? '').trim()
    }))
  const priorities = priorityItems.length > 1 || !summary ? priorityItems : []
  if (!summary && !bottlenecks.length && !priorities.length) return null
  return { summary, bottlenecks, priorities }
})

const applyChangeCount = computed(() => applyDiffRows.value.filter((row) => row.hasChanges).length)

onMounted(() => {
  schedulingStore.hydrate()
  void loadWorkOrders()
})

watch(
  () => form.selectedWorkOrderId,
  (id) => {
    void loadSchedulingContext(id != null ? [id] : [])
  }
)

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
    if (form.selectedWorkOrderId != null) {
      const stillExists = workOrderOptions.value.some((item) => item.id === form.selectedWorkOrderId)
      if (!stillExists) {
        form.selectedWorkOrderId = null
        schedulingStore.clearSchedulingResult()
      } else {
        void loadSchedulingContext([form.selectedWorkOrderId])
      }
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('加载工单列表失败')
  }
}

async function loadSchedulingContext(ids: Array<string | number>) {
  if (!ids.length) {
    schedulingContext.value = null
    return
  }
  contextLoading.value = true
  try {
    schedulingContext.value = (await getSchedulingContext(ids)) as typeof schedulingContext.value
  } catch (error) {
    console.error('[AiScheduling] load context failed', error)
  } finally {
    contextLoading.value = false
  }
}

function applyPreset(preset: ScenarioPreset) {
  activePreset.value = preset.id
  form.materialConstraint = preset.materialConstraint
  form.deviceConstraint = preset.deviceConstraint
  form.teamConstraint = preset.teamConstraint
}

function presetIcon(id: string) {
  const icons: Record<string, string> = {
    deadline: '⏰',
    material: '📦',
    balance: '⚖️'
  }
  return icons[id] ?? '✨'
}

function bottleneckLoadLabel(rate: number) {
  if (rate >= 90) return '瓶颈风险'
  if (rate >= 70) return '负荷偏高'
  return '负荷正常'
}

function clearWorkOrderSelection() {
  form.selectedWorkOrderId = null
  schedulingStore.clearSchedulingResult()
}

function selectWorkOrder(id: string | number) {
  if (form.selectedWorkOrderId === id) return
  form.selectedWorkOrderId = id
  handleWorkOrderChange()
}

function handleWorkOrderChange() {
  schedulingStore.clearSchedulingResult()
}

async function generateSuggestions() {
  if (form.selectedWorkOrderId == null) {
    ElMessage.warning('请先选择一个工单进行排产')
    return
  }
  loading.value = true
  try {
    const workOrderIds = [form.selectedWorkOrderId]
    await loadSchedulingContext(workOrderIds)
    const response = await getSchedulingSuggestions({
      planDate: form.planDate,
      workOrderIds: workOrderIds.map((id) => Number(id)),
      materialConstraint: form.materialConstraint,
      deviceConstraint: form.deviceConstraint,
      teamConstraint: form.teamConstraint
    })
    const payload = (response as unknown as Record<string, unknown>) ?? {}
    const mode = payload.mode === 'live' ? 'live' : 'mock'
    const constraintPayload = (payload.constraints as Record<string, boolean> | undefined) ?? {}
    const dataObj = (payload.result as Record<string, unknown>) ?? payload
    schedulingStore.setSchedulingResult({
      result: mapSchedulingResult(dataObj),
      resultMode: mode,
      resultSummary: String(dataObj.summary ?? ''),
      resultHint: String(payload.message ?? ''),
      appliedConstraints: {
        materialAvailability: constraintPayload.materialAvailability !== false,
        deviceLoad: constraintPayload.deviceLoad !== false,
        teamHours: constraintPayload.teamHours !== false
      }
    })
    if (mode === 'live') {
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

function mapSchedulingResult(dataObj: Record<string, unknown>) {
  return {
    summary: String(dataObj.summary ?? ''),
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

function openApplyPreview() {
  if (!result.value?.dispatches?.length) {
    ElMessage.warning('当前没有可应用的派工建议')
    return
  }
  applyDialogVisible.value = true
}

async function confirmApply() {
  applyDialogVisible.value = false
  await applySuggestions()
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
      summary: resultSummary.value || result.value.summary,
      priorities: result.value.priorities,
      bottlenecks: result.value.bottlenecks,
      dispatches: result.value.dispatches
    })
    const appliedCount = Number(response.appliedCount ?? 0)
    const skippedCount = Number(response.skippedCount ?? 0)
    const applied = normalizeList(response.applied)
    if (appliedCount > 0) {
      appliedResultRows.value = mapAppliedResultRows(applied)
      applySuccessDialogVisible.value = true
      await loadWorkOrders()
      if (form.selectedWorkOrderId != null) {
        await loadSchedulingContext([form.selectedWorkOrderId])
      }
    } else {
      const reason = response.skipped?.[0]?.reason ?? '请检查班组名称是否为甲班/乙班/丙班'
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

function formatAppliedDateTime(value: unknown) {
  const text = String(value ?? '').trim()
  if (!text) return ''
  return text.replace('T', ' ').substring(0, 16)
}

function mapAppliedResultRows(applied: any[]): AppliedResultRow[] {
  return applied.map((item) => {
    const hoursRaw = item.estimatedHours ?? item.suggestedHours
    const hoursText = hoursRaw != null ? String(hoursRaw).replace(/小时/g, '').trim() : ''
    return {
      code: String(item.orderNo ?? item.workOrderCode ?? item.code ?? '--'),
      team: String(item.teamName ?? '未分配'),
      priority: priorityLabel(item.priority),
      startTime: formatAppliedDateTime(item.scheduledStartTime ?? item.suggestedStartTime),
      hours: hoursText
    }
  })
}

function goToWorkOrdersAfterApply() {
  applySuccessDialogVisible.value = false
  const firstCode = appliedResultRows.value[0]?.code
  void router.push({
    path: '/work-orders',
    query: firstCode && firstCode !== '--' ? { keyword: firstCode } : undefined
  })
}
</script>

<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.kpi-card {
  padding: 16px 18px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.kpi-card__value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  color: #0f172a;
}

.kpi-card__label {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
}

.kpi-card--danger .kpi-card__value { color: #ef4444; }
.kpi-card--warning .kpi-card__value { color: #d97706; }
.kpi-card--amber .kpi-card__value { color: #f59e0b; }
.kpi-card--primary .kpi-card__value { color: #4f46e5; }

.schedule-layout {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 20px;
  align-items: start;
}

.params-card {
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03) !important;
  align-self: start;
}

.result-card {
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03) !important;
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.params-card :deep(.el-card__body) {
  padding: 0;
}

.result-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 20px;
}

.params-card :deep(.el-card__header),
.result-card :deep(.el-card__header) {
  flex-shrink: 0;
}

.params-card :deep(.el-card__header) {
  padding: 16px 18px 12px;
  border-bottom: 1px solid #f1f5f9;
}

.result-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.params-card__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.params-card__header-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(79, 70, 229, 0.12), rgba(22, 119, 255, 0.1));
  color: #4f46e5;
  font-size: 18px;
}

.params-card__subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
  font-weight: 400;
}

.params-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 4px 18px 18px;
}

.param-section {
  padding: 14px 0;
  border-bottom: 1px solid #f1f5f9;
}

.param-section:last-of-type {
  border-bottom: none;
}

.param-section__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.param-section__title--row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.param-section__title-left {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.param-section__count {
  font-style: normal;
  font-size: 11px;
  font-weight: 600;
  color: #4f46e5;
  background: rgba(79, 70, 229, 0.08);
  padding: 2px 8px;
  border-radius: 999px;
}

.preset-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.preset-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: center;
  min-height: 88px;
}

.preset-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.08);
  transform: translateY(-1px);
}

.preset-card--active {
  border-color: #4f46e5;
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.08), rgba(99, 102, 241, 0.02));
  box-shadow: 0 0 0 1px rgba(79, 70, 229, 0.15);
}

.preset-card__icon {
  font-size: 18px;
  line-height: 1;
}

.preset-card__label {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.preset-card__desc {
  font-size: 10px;
  line-height: 1.35;
  color: #94a3b8;
  padding: 0 2px;
}

.full-width {
  width: 100%;
}

.text-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #4f46e5;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}

.text-link:hover {
  color: #3730a3;
}

.work-order-toolbar {
  display: flex;
  gap: 8px;
}

.work-order-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border: 1px solid #dbe3ef;
  border-radius: 999px;
  background: #fff;
  color: #4f46e5;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, color 0.15s ease;
}

.work-order-action-btn:hover {
  border-color: #c7d2fe;
  background: #f8fafc;
  color: #3730a3;
}

.custom-datepicker :deep(.el-input__wrapper) {
  border-radius: 12px !important;
  background-color: #f8fafc !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  padding: 6px 14px !important;
}

.custom-datepicker :deep(.el-input__wrapper.is-focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.12) !important;
}

.work-order-list-container {
  border: 1px solid #e8edf4;
  border-radius: 14px;
  background: linear-gradient(180deg, #fafbfd 0%, #f8fafc 100%);
  padding: 8px;
  max-height: 220px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

.work-order-list-container::-webkit-scrollbar {
  width: 6px;
}

.work-order-list-container::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #cbd5e1;
}

.work-order-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0 0 10px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
}

.work-order-hint .el-icon {
  margin-top: 2px;
  font-size: 14px;
  color: #94a3b8;
  flex-shrink: 0;
}

.work-order-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.work-order-card {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #e8edf4;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.work-order-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.06);
}

.work-order-card--active {
  border-color: #818cf8;
  background: linear-gradient(90deg, rgba(99, 102, 241, 0.1), rgba(99, 102, 241, 0.02));
  box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.18);
}

.work-order-card__indicator {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid #cbd5e1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: border-color 0.15s ease;
}

.work-order-card--active .work-order-card__indicator {
  border-color: #4f46e5;
}

.work-order-card__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: transparent;
  transition: background 0.15s ease;
}

.work-order-card--active .work-order-card__dot {
  background: #4f46e5;
}

.work-order-card__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.work-order-card__code {
  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: #0f172a;
}

.work-order-card__product {
  font-size: 12px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-order-card__check {
  flex-shrink: 0;
  font-size: 18px;
  color: #4f46e5;
}

.constraint-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.constraint-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  cursor: pointer;
  transition: all 0.15s ease;
  font-size: 13px;
  color: #475569;
}

.constraint-pill:hover {
  border-color: #cbd5e1;
}

.constraint-pill--on {
  border-color: rgba(79, 70, 229, 0.35);
  background: rgba(99, 102, 241, 0.06);
  color: #312e81;
}

.constraint-pill :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
}

.constraint-pill :deep(.el-checkbox__label) {
  display: none;
}

.submit-action {
  padding-top: 14px;
}

.submit-btn {
  width: 100%;
  height: 44px;
  border-radius: 14px !important;
  background: linear-gradient(135deg, #4f46e5 0%, #2563eb 100%) !important;
  border: none !important;
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.28) !important;
  font-weight: 700;
  font-size: 14px;
}

.submit-btn__icon {
  margin-right: 6px;
}

.empty-work-orders {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #94a3b8;
  padding: 24px 16px;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 14px;
}

.situation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.situation-card {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #fff, #f8fafc);
}

.situation-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.situation-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
  font-size: 11px;
  color: #64748b;
}

.situation-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.inline-alert {
  margin: 10px 0 0;
  border-radius: 12px;
}

.card-header-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.result-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.result-card__title-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-card__timestamp {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
}

.result-card__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.apply-btn {
  border-radius: 20px !important;
  background: #10b981 !important;
  border: none !important;
}

.constraint-summary,
.summary-alert,
.result-hint {
  margin-bottom: 16px;
}

.constraint-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.constraint-summary__label {
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
}

.impact-list {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 13px;
  color: #92400e;
}

.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  gap: 20px;
  min-height: 320px;
}

.custom-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  margin: 0;
  padding: 32px 24px;
  border-radius: 14px;
  background:
    radial-gradient(circle at 50% 0%, rgba(79, 70, 229, 0.06), transparent 55%),
    linear-gradient(180deg, #fafbfd 0%, #f8fafc 100%);
  border: 1px dashed #e2e8f0;
}

.custom-empty :deep(.el-empty__description) {
  max-width: 280px;
  line-height: 1.6;
  color: #94a3b8;
}

.loading-text {
  font-size: 14px;
  color: #64748b;
}

.custom-progress {
  width: 80%;
}

.result-section {
  border-radius: 12px;
  border: 1px solid #f1f5f9 !important;
}

.result-section + .result-section {
  margin-top: 16px;
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

.section-indicator--green { background: #10b981; }
.section-indicator--amber { background: #f59e0b; }

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.header-pill-btn.el-button {
  border-radius: 20px !important;
  padding: 4px 10px !important;
  font-size: 11px !important;
  font-weight: 600 !important;
  height: 22px !important;
  background: #fff !important;
  border: 1.5px solid #e2e8f0 !important;
  transition: all 0.2s ease !important;
  margin: 0 0 0 8px !important;
  cursor: pointer !important;
}

.header-pill-btn.el-button.btn-device {
  color: #4f46e5 !important;
  border-color: #e0e7ff !important;
}
.header-pill-btn.el-button.btn-device:hover {
  background: #e0e7ff !important;
  border-color: #c7d2fe !important;
}

.header-pill-btn.el-button.btn-material {
  color: #f59e0b !important;
  border-color: #fef3c7 !important;
}
.header-pill-btn.el-button.btn-material:hover {
  background: #fef3c7 !important;
  border-color: #fde68a !important;
}

.team-load-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.team-load-card {
  padding: 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.team-load-card__head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
}

.team-load-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  font-size: 11px;
  color: #64748b;
}

.material-chain {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}

.material-chain__item {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid #fde68a;
  background: #fffbeb;
}

.material-chain__name {
  font-weight: 700;
  color: #92400e;
}

.material-chain__stock {
  margin-top: 4px;
  font-size: 12px;
  color: #b45309;
}

.material-chain__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #78716c;
}

.bottleneck-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
}

.bottleneck-item {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.04);
}

.bottleneck-item--high {
  border-color: #fecaca;
  background: linear-gradient(180deg, #fff 0%, #fff8f8 100%);
}

.bottleneck-item__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.bottleneck-item__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.bottleneck-item__name {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.bottleneck-item__tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: #fef2f2;
  color: #dc2626;
}

.bottleneck-item__tag--warn {
  background: #fffbeb;
  color: #d97706;
}

.bottleneck-item__rate {
  display: flex;
  align-items: baseline;
  gap: 2px;
  flex-shrink: 0;
}

.bottleneck-item__rate-value {
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
  color: #4f46e5;
  font-variant-numeric: tabular-nums;
}

.bottleneck-item__rate.is-high .bottleneck-item__rate-value {
  color: #ef4444;
}

.bottleneck-item__rate-unit {
  font-size: 14px;
  font-weight: 700;
  color: #94a3b8;
}

.bottleneck-item__bar-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bottleneck-item__bar-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #94a3b8;
}

.bottleneck-progress :deep(.el-progress-bar__outer) {
  border-radius: 999px;
  background: #e8edf4;
}

.bottleneck-progress :deep(.el-progress-bar__inner) {
  border-radius: 999px;
}

.bottleneck-item__suggestion {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border-left: 3px solid #4f46e5;
  font-size: 13px;
  line-height: 1.65;
  color: #475569;
}

.bottleneck-item--high .bottleneck-item__suggestion {
  background: #fffbeb;
  border-left-color: #f59e0b;
}

.bottleneck-item__suggestion p {
  margin: 0;
  flex: 1;
}

.bottleneck-item__suggestion .el-icon {
  flex-shrink: 0;
  margin-top: 3px;
  font-size: 15px;
  color: #4f46e5;
}

.bottleneck-item--high .bottleneck-item__suggestion .el-icon {
  color: #d97706;
}

.team-badge {
  background: rgba(79, 70, 229, 0.08);
  color: #4f46e5;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
}

.diff-intro {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.apply-dialog__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.apply-dialog__meta-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.apply-dialog__label {
  font-size: 12px;
  color: #94a3b8;
}

.apply-dialog__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.apply-dialog__overview {
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.apply-dialog__overview-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 700;
  color: #334155;
}

.apply-dialog__overview-summary {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
}

.apply-dialog__overview-label {
  margin: 10px 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.apply-dialog__overview-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.apply-dialog__overview-item {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  line-height: 1.65;
  color: #475569;
}

.apply-dialog__overview-item + .apply-dialog__overview-item {
  margin-top: 8px;
}

.apply-dialog__overview-item strong {
  color: #334155;
  flex-shrink: 0;
}

.apply-success-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding-bottom: 0;
}

.apply-success-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}

.apply-success-dialog__header {
  display: flex;
  align-items: center;
  gap: 14px;
}

.apply-success-dialog__icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #ecfdf5;
  color: #10b981;
  font-size: 22px;
}

.apply-success-dialog__title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.apply-success-dialog__subtitle {
  margin-top: 2px;
  font-size: 13px;
  color: #64748b;
}

.apply-success-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.apply-success-card {
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #fff, #f8fafc);
}

.apply-success-card__code {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 10px;
}

.apply-success-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}

.apply-success-card__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.apply-success-card__item span {
  font-size: 12px;
  color: #94a3b8;
}

.apply-success-card__item strong {
  font-size: 14px;
  color: #334155;
  font-weight: 600;
}

.apply-success-dialog__hint {
  margin: 14px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #94a3b8;
}

.diff-changed {
  color: #dc2626;
  font-weight: 600;
}

@media (max-width: 1100px) {
  .schedule-layout,
  .kpi-row {
    grid-template-columns: 1fr;
  }
}
</style>
