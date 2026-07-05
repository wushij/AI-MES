<template>
  <div class="view-page">
    <PageHeader title="首页驾驶舱" subtitle="生产计划、工单、异常与缺料预警一屏总览" />

    <div class="action-bar">
      <div class="action-bar__meta">
        <el-tag effect="plain" type="info">今日 {{ todayLabel }}</el-tag>
        <span class="action-bar__time">最后刷新：{{ lastRefresh || '--' }}</span>
      </div>
      <div class="action-bar__buttons">
        <el-button v-if="userStore.isSupervisor" @click="router.push('/plans')">新建计划</el-button>
        <el-button @click="router.push('/exceptions')">上报异常</el-button>
        <el-button @click="router.push('/ai-chat')">AI 客服</el-button>
        <el-button type="primary" :loading="loading" @click="loadDashboard()">刷新</el-button>
      </div>
    </div>

    <el-skeleton :loading="loading" animated :rows="8">
      <template #template>
        <el-row :gutter="16">
          <el-col v-for="item in 4" :key="item" :xs="24" :sm="12" :lg="6">
            <el-skeleton-item variant="rect" style="height: 88px; border-radius: 20px" />
          </el-col>
        </el-row>
      </template>

      <div class="dashboard-metrics-panel">
        <div class="dashboard-metrics">
          <div
            v-for="card in kpiCards"
            :key="card.title"
            class="dashboard-metric"
            :style="{ borderColor: card.color }"
          >
            <div class="dashboard-metric__label">{{ card.title }}</div>
            <div class="dashboard-metric__value-row">
              <span class="dashboard-metric__value" :style="{ color: card.color }">{{ card.value }}</span>
              <span class="dashboard-metric__unit">{{ card.unit }}</span>
            </div>
            <div v-if="card.trendValue" class="dashboard-metric__trend" :class="`dashboard-metric__trend--${card.trend}`">
              {{ card.trendText }}
            </div>
          </div>
        </div>
      </div>

      <el-row :gutter="16" class="section-gap">
        <el-col :xs="24" :lg="12">
          <el-card class="panel-card" shadow="hover">
            <template #header>
              <div class="panel-card__header">
                <span>班组进度</span>
                <el-tag effect="plain" type="primary">今日</el-tag>
              </div>
            </template>
            <div v-if="teamProgress.length" ref="teamChartRef" class="chart-box"></div>
            <el-empty v-else description="暂无班组进度数据" />
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="12">
          <el-card class="panel-card" shadow="hover">
            <template #header>
              <div class="panel-card__header">
                <span>近7日产出</span>
                <el-tag effect="plain">单</el-tag>
              </div>
            </template>
            <div v-if="outputTrend.length" ref="trendChartRef" class="chart-box"></div>
            <el-empty v-else description="暂无产出趋势数据" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="section-gap">
        <el-col :span="24">
          <el-card class="panel-card" shadow="hover">
            <template #header>
              <div class="panel-card__header">
                <span>设备状态</span>
                <el-button
                  v-if="userStore.canAccessPermission('设备')"
                  size="small"
                  class="panel-card__more-btn"
                  @click="router.push('/devices')"
                >
                  查看全部
                </el-button>
              </div>
            </template>
            <div class="device-kpi-row">
              <div class="device-kpi"><span>总数</span><strong>{{ summary.deviceTotalCount ?? 0 }}</strong></div>
              <div class="device-kpi device-kpi--ok"><span>运行</span><strong>{{ summary.deviceRunningCount ?? 0 }}</strong></div>
              <div class="device-kpi device-kpi--danger"><span>故障</span><strong>{{ summary.deviceFaultCount ?? 0 }}</strong></div>
              <div class="device-kpi device-kpi--warn"><span>今日报警</span><strong>{{ summary.deviceTodayAlertCount ?? 0 }}</strong></div>
            </div>
            <el-table v-if="deviceList.length" :data="deviceList" stripe border size="small" :header-cell-style="tableHeaderStyle" class="panel-table">
              <el-table-column prop="deviceCode" label="编号" min-width="90" align="center" />
              <el-table-column prop="deviceName" label="名称" min-width="120" align="center" />
              <el-table-column prop="lineName" label="产线" min-width="80" align="center" />
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="deviceStatusTagType(row.status)">{{ row.statusLabel ?? deviceStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无异常设备" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="section-gap">
        <el-col :span="24">
          <el-card class="panel-card" shadow="hover">
            <template #header>
              <div class="panel-card__header">
                <span>最新异常</span>
                <el-button size="small" class="panel-card__more-btn" @click="router.push('/exceptions')">查看全部</el-button>
              </div>
            </template>
            <el-table v-if="exceptionList.length" :data="exceptionList" stripe border highlight-current-row :header-cell-style="tableHeaderStyle" class="panel-table">
              <el-table-column prop="code" label="异常单号" min-width="130" align="center" />
              <el-table-column prop="typeLabel" label="类型" min-width="100" align="center" />
              <el-table-column prop="workOrderCode" label="工单号" min-width="110" align="center" />
              <el-table-column prop="deviceLabel" label="设备" min-width="120" align="center" />
              <el-table-column prop="reportedAt" label="上报时间" min-width="150" align="center" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }"><StatusTag :status="row.status" /></template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无异常记录" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="section-gap">
        <el-col :span="24">
          <el-card class="panel-card" shadow="hover">
            <template #header>
              <div class="panel-card__header">
                <span>缺料预警</span>
                <el-button size="small" class="panel-card__more-btn" @click="router.push('/materials')">查看全部</el-button>
              </div>
            </template>
            <el-table v-if="materialAlerts.length" :data="materialAlerts" stripe border highlight-current-row :header-cell-style="tableHeaderStyle" class="panel-table">
              <el-table-column prop="name" label="物料" min-width="160" align="center" />
              <el-table-column prop="stockQty" label="库存" min-width="100" align="center" />
              <el-table-column prop="safetyStock" label="安全库存" min-width="110" align="center" />
              <el-table-column prop="gap" label="缺口" min-width="90" align="center" />
              <el-table-column label="状态" width="110" align="center">
                <template #default><StatusTag status="warning" /></template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="物料库存正常" />
          </el-card>
        </el-col>
      </el-row>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { getDashboardData } from '@/api/dashboard'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { deviceStatusLabel, deviceStatusTagType } from '@/utils/deviceLabels'

interface DashboardSummary {
  planCount: number
  planTrend: string
  inProgressCount: number
  inProgressTrend: string
  openExceptionCount: number
  newExceptionCount: number
  materialAlertCount: number
  newMaterialAlertCount: number
  deviceTotalCount?: number
  deviceRunningCount?: number
  deviceFaultCount?: number
  deviceTodayAlertCount?: number
  todayOutput?: number
}
interface TeamProgressItem { teamName: string; completedQty: number; totalQty: number; progress: number }
interface OutputTrendItem { date: string; outputQty: number }
interface ExceptionItem { id: string | number; code: string; typeLabel: string; workOrderCode: string; deviceLabel?: string; reportedAt: string; status: string }
interface DeviceBriefItem { id: string | number; deviceCode: string; deviceName: string; lineName?: string; status?: string; statusLabel?: string }
interface MaterialAlertItem { id: string | number; name: string; stockQty: number; safetyStock: number; gap: number }

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const loading = ref(false)
const lastRefresh = ref('')
const teamChartRef = ref<HTMLDivElement>()
const trendChartRef = ref<HTMLDivElement>()
const teamChart = shallowRef<echarts.ECharts>()
const trendChart = shallowRef<echarts.ECharts>()
const teamProgress = ref<TeamProgressItem[]>([])
const outputTrend = ref<OutputTrendItem[]>([])
const exceptionList = ref<ExceptionItem[]>([])
const deviceList = ref<DeviceBriefItem[]>([])
const materialAlerts = ref<MaterialAlertItem[]>([])
const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600', textAlign: 'center' as const }
const summary = reactive<DashboardSummary>({ planCount: 0, planTrend: '0%', inProgressCount: 0, inProgressTrend: '0%', openExceptionCount: 0, newExceptionCount: 0, materialAlertCount: 0, newMaterialAlertCount: 0, deviceTotalCount: 0, deviceRunningCount: 0, deviceFaultCount: 0, deviceTodayAlertCount: 0 })
let refreshTimer: ReturnType<typeof setInterval> | null = null
const role = computed(() => userStore.role)
const isSupervisor = computed(() => userStore.isSupervisor || userStore.isAdmin)
const todayLabel = computed(() => formatDate(new Date()))
const kpiCards = computed(() => [
  {
    title: '今日计划',
    value: summary.planCount,
    unit: '条',
    color: '#4f46e5',
    trend: inferTrend(summary.planTrend),
    trendValue: summary.planTrend,
    trendText: summary.planTrend
  },
  {
    title: '在制工单',
    value: summary.inProgressCount,
    unit: '单',
    color: '#0d9488',
    trend: inferTrend(summary.inProgressTrend),
    trendValue: summary.inProgressTrend,
    trendText: summary.inProgressTrend
  },
  {
    title: '未处理异常',
    value: summary.openExceptionCount,
    unit: '项',
    color: summary.openExceptionCount > 0 ? '#e11d48' : '#64748b',
    trend: summary.newExceptionCount > 0 ? 'up' as const : 'flat' as const,
    trendValue: String(summary.newExceptionCount),
    trendText: `今日新增 ${summary.newExceptionCount}`
  },
  {
    title: '缺料预警',
    value: summary.materialAlertCount,
    unit: '项',
    color: summary.materialAlertCount > 0 ? '#a855f7' : '#64748b',
    trend: summary.newMaterialAlertCount > 0 ? 'up' as const : 'flat' as const,
    trendValue: String(summary.newMaterialAlertCount),
    trendText: `今日新增 ${summary.newMaterialAlertCount}`
  }
])

onMounted(() => {
  void loadDashboard()
  window.addEventListener('resize', resizeCharts)
  refreshTimer = setInterval(() => { void loadDashboard(true) }, 30000)
})
watch(
  () => appStore.workshopSummary.todayOutput,
  (val) => {
    syncTrendToday(val)
  }
)
onActivated(() => { void loadDashboard(true) })
onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})

function disposeCharts() {
  teamChart.value?.dispose()
  trendChart.value?.dispose()
  teamChart.value = undefined
  trendChart.value = undefined
}

function ensureChart(
  chartRef: { value: echarts.ECharts | undefined },
  el: HTMLDivElement | undefined
): echarts.ECharts | undefined {
  if (!el) return undefined
  if (chartRef.value && chartRef.value.getDom() !== el) {
    chartRef.value.dispose()
    chartRef.value = undefined
  }
  if (!chartRef.value) {
    chartRef.value = echarts.init(el)
  }
  return chartRef.value
}

async function loadDashboard(silent = false) {
  if (!silent) {
    loading.value = true
    disposeCharts()
  }
  try {
    const [payload] = await Promise.all([getDashboardData(), appStore.loadWorkshopSummary()])
    const overview = payload.overview ?? {}
    Object.assign(summary, overview)
    teamProgress.value = payload.teamProgress ?? []
    outputTrend.value = payload.outputTrend ?? []
    const todayOutput = appStore.workshopSummary.todayOutput ?? summary.todayOutput
    if (todayOutput != null) {
      syncTrendToday(todayOutput)
    }
    exceptionList.value = (payload.exceptionList ?? []) as ExceptionItem[]
    deviceList.value = ((payload as { deviceList?: Record<string, unknown>[] }).deviceList ?? []).map((item) => ({
      id: (item.id ?? '') as string | number,
      deviceCode: String(item.deviceCode ?? ''),
      deviceName: String(item.deviceName ?? ''),
      lineName: item.lineName ? String(item.lineName) : '',
      status: item.status ? String(item.status) : 'idle',
      statusLabel: item.statusLabel ? String(item.statusLabel) : undefined
    }))
    materialAlerts.value = (payload.materialAlerts ?? []).map((item) => ({
      id: item.id as string | number,
      name: String(item.name ?? ''),
      stockQty: Number(item.stockQty ?? 0),
      safetyStock: Number(item.safetyStock ?? 0),
      gap: Number(item.gap ?? 0)
    }))
    lastRefresh.value = formatDateTime(new Date())
  } catch (error) {
    console.error(error)
    if (!silent) ElMessage.error('加载驾驶舱数据失败')
  } finally {
    if (!silent) loading.value = false
    await nextTick()
    renderCharts()
  }
}

function renderCharts() {
  const teamInstance = ensureChart(teamChart, teamChartRef.value)
  if (teamInstance) {
    teamInstance.setOption({
      grid: { left: 80, right: 48, top: 16, bottom: 24 },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'none' },
        backgroundColor: '#0f172a',
        borderRadius: 8,
        padding: [10, 14],
        textStyle: { color: '#ffffff', fontFamily: 'Inter, sans-serif', fontSize: 13 },
        borderWidth: 0,
        shadowColor: 'rgba(15, 23, 42, 0.15)',
        shadowBlur: 10,
        formatter: function (params: any) {
          const item = params[0];
          return `<div style="font-weight: 500; color: #94a3b8; font-size: 11px; margin-bottom: 4px;">${item.name}</div>
                  <div style="display: flex; align-items: center; gap: 6px;">
                    <span style="display: inline-block; width: 8px; height: 8px; border-radius: 50%; background-color: #6366f1;"></span>
                    <span style="font-weight: 600; color: #fff;">已完成 ${item.value}%</span>
                  </div>`;
        }
      },
      xAxis: {
        type: 'value',
        max: 100,
        axisLabel: { formatter: '{value}%', color: '#94a3b8', fontFamily: 'Inter, sans-serif' },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } }
      },
      yAxis: {
        type: 'category',
        data: teamProgress.value.map((item) => item.teamName),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#64748b', fontWeight: 600, fontFamily: 'Inter, sans-serif', fontSize: 13 }
      },
      series: [
        {
          name: '进度',
          type: 'bar',
          barWidth: 16,
          showBackground: true,
          backgroundStyle: { color: '#f1f5f9', borderRadius: 8 },
          data: teamProgress.value.map((item) => item.progress),
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#4f46e5' },
              { offset: 1, color: '#6366f1' }
            ]),
            borderRadius: 8
          },
          label: {
            show: true,
            position: 'right',
            formatter: '{c}%',
            fontFamily: 'Inter, sans-serif',
            fontWeight: 700,
            color: '#475569',
            fontSize: 12
          }
        }
      ]
    })
  }

  const trendInstance = ensureChart(trendChart, trendChartRef.value)
  if (trendInstance) {
    trendInstance.setOption({
      grid: { left: 48, right: 16, top: 24, bottom: 28 },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#0f172a',
        borderRadius: 8,
        padding: [10, 14],
        textStyle: { color: '#ffffff', fontFamily: 'Inter, sans-serif', fontSize: 13 },
        borderWidth: 0,
        shadowColor: 'rgba(15, 23, 42, 0.15)',
        shadowBlur: 10,
        formatter: function (params: any) {
          const item = params[0];
          return `<div style="font-weight: 500; color: #94a3b8; font-size: 11px; margin-bottom: 4px;">${item.axisValue}</div>
                  <div style="display: flex; align-items: center; gap: 6px;">
                    <span style="display: inline-block; width: 8px; height: 8px; border-radius: 50%; background-color: #6366f1;"></span>
                    <span style="font-weight: 600; color: #fff;">${item.value} <span style="font-weight: 400; font-size: 11px; color: #94a3b8;">单</span></span>
                  </div>`;
        }
      },
      xAxis: {
        type: 'category',
        data: outputTrend.value.map((item) => item.date),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94a3b8', fontFamily: 'Inter, sans-serif' }
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94a3b8', fontFamily: 'Inter, sans-serif' },
        splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } }
      },
      series: [
        {
          type: 'line',
          smooth: true,
          data: outputTrend.value.map((item) => item.outputQty),
          symbol: 'circle',
          symbolSize: 8,
          showSymbol: false,
          lineStyle: { color: '#4f46e5', width: 3.5, shadowColor: 'rgba(79, 70, 229, 0.2)', shadowBlur: 10, shadowOffsetY: 4 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(79, 70, 229, 0.22)' },
              { offset: 1, color: 'rgba(79, 70, 229, 0.01)' }
            ])
          },
          itemStyle: { color: '#4f46e5', borderWidth: 2, borderColor: '#fff' },
          emphasis: {
            scale: true,
            itemStyle: { color: '#4f46e5', borderWidth: 3, borderColor: '#fff' }
          }
        }
      ]
    })
  }
}
function resizeCharts() { teamChart.value?.resize(); trendChart.value?.resize() }
function syncTrendToday(todayOutput: number) {
  if (!outputTrend.value.length) return
  const last = outputTrend.value[outputTrend.value.length - 1]
  if (last.outputQty === todayOutput) return
  last.outputQty = todayOutput
  if (trendChart.value) {
    trendChart.value.setOption({
      series: [{ data: outputTrend.value.map((item) => item.outputQty) }]
    })
  }
}
function normalizeList<T>(value: any): T[] { if (Array.isArray(value)) return value as T[]; if (Array.isArray(value?.records)) return value.records as T[]; if (Array.isArray(value?.list)) return value.list as T[]; if (Array.isArray(value?.items)) return value.items as T[]; if (Array.isArray(value?.data)) return value.data as T[]; return [] }
function calcPercent(doneValue: any, totalValue: any) { const done = Number(doneValue || 0); const total = Number(totalValue || 0); return total > 0 ? Math.round((done / total) * 100) : 0 }
function inferTrend(value: string): 'flat' | 'up' | 'down' {
  if (value.startsWith('-')) return 'down'
  if (value === '0%' || value === '0') return 'flat'
  return 'up'
}
function formatDate(date: Date) { return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` }
function formatDateTime(date: Date) { return `${formatDate(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}` }
function pad(value: number) { return String(value).padStart(2, '0') }
</script>

<style scoped>
.view-page { display: flex; flex-direction: column; gap: 16px; }
.dashboard-metrics-panel {
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e0e7ff;
  border-radius: 20px;
  box-shadow: 0 2px 12px rgba(79, 70, 229, 0.05);
}
.dashboard-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.dashboard-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 18px;
  border-radius: 20px;
  background: #f8fafc;
  border: 1.5px solid #e2e8f0;
  transition: all 0.25s ease;
  min-height: 88px;
}
.dashboard-metric:hover {
  background: #fff;
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
}
.dashboard-metric__label {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}
.dashboard-metric__value-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.dashboard-metric__value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}
.dashboard-metric__unit {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}
.dashboard-metric__trend {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  margin-top: 2px;
}
.dashboard-metric__trend--up { color: #0d9488; }
.dashboard-metric__trend--down { color: #e11d48; }
.action-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 20px; background: #fff; border: 1px solid var(--theme-border, #e2e8f0); border-radius: 16px; box-shadow: 0 2px 12px rgba(15, 23, 42, 0.05); }
.action-bar__meta, .action-bar__buttons { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.action-bar__time { color: var(--theme-text-secondary, #64748b); font-size: 13px; }
.section-gap { margin-top: 0; }
.panel-card { border-radius: 16px; margin-bottom: 16px; }
.panel-card__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-weight: 600; }
.panel-card__more-btn {
  border-radius: 20px !important;
  padding: 4px 14px !important;
  height: 28px !important;
  border: 1px solid #e2e8f0 !important;
  background: #fff !important;
  color: #475569 !important;
  font-weight: 500 !important;
  font-size: 13px !important;
  margin: 0 !important;
}
.panel-card__more-btn:hover,
.panel-card__more-btn:focus {
  border-color: #cbd5e1 !important;
  color: #0f172a !important;
  background: #f8fafc !important;
}
.chart-box { height: 320px; }
.panel-card :deep(.el-card__body) { min-height: 180px; }
.device-kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.device-kpi { background: #f8fafc; border-radius: 12px; padding: 12px; text-align: center; }
.device-kpi span { display: block; font-size: 12px; color: #64748b; }
.device-kpi strong { font-size: 22px; color: #0f172a; }
.device-kpi--ok strong { color: #16a34a; }
.device-kpi--danger strong { color: #dc2626; }
.device-kpi--warn strong { color: #d97706; }
.panel-table :deep(.el-table__header .cell),
.panel-table :deep(.el-table__body .cell) {
  text-align: center;
}
@media (max-width: 900px) {
  .action-bar { align-items: flex-start; flex-direction: column; }
  .chart-box { height: 280px; }
  .device-kpi-row { grid-template-columns: repeat(2, 1fr); }
  .dashboard-metrics { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 560px) {
  .dashboard-metrics { grid-template-columns: 1fr; }
}
</style>
