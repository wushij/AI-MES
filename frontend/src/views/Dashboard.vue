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
            <el-skeleton-item variant="rect" style="height: 132px; border-radius: 16px" />
          </el-col>
        </el-row>
      </template>

      <el-row :gutter="16">
        <el-col v-for="card in kpiCards" :key="card.title" :xs="24" :sm="12" :lg="6">
          <KpiCard v-bind="card" />
        </el-col>
      </el-row>

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
                <el-tag effect="plain">件</el-tag>
              </div>
            </template>
            <div v-if="outputTrend.length" ref="trendChartRef" class="chart-box"></div>
            <el-empty v-else description="暂无产出趋势数据" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="section-gap">
        <el-col :xs="24" :lg="12">
          <el-card class="panel-card" shadow="hover">
            <template #header>
              <div class="panel-card__header">
                <span>最新异常</span>
                <el-button link type="primary" @click="router.push('/exceptions')">查看全部</el-button>
              </div>
            </template>
            <el-table v-if="exceptionList.length" :data="exceptionList" stripe border highlight-current-row :header-cell-style="tableHeaderStyle">
              <el-table-column prop="code" label="异常单号" min-width="130" />
              <el-table-column prop="typeLabel" label="类型" min-width="120" />
              <el-table-column prop="workOrderCode" label="工单号" min-width="120" />
              <el-table-column prop="reportedAt" label="上报时间" min-width="160" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }"><StatusTag :status="row.status" /></template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无异常记录" />
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="12">
          <el-card class="panel-card" shadow="hover">
            <template #header>
              <div class="panel-card__header">
                <span>缺料预警</span>
                <el-button link type="primary" @click="router.push('/materials')">查看全部</el-button>
              </div>
            </template>
            <el-table v-if="materialAlerts.length" :data="materialAlerts" stripe border highlight-current-row :header-cell-style="tableHeaderStyle">
              <el-table-column prop="name" label="物料" min-width="160" />
              <el-table-column prop="stockQty" label="库存" min-width="100" />
              <el-table-column prop="safetyStock" label="安全库存" min-width="110" />
              <el-table-column prop="gap" label="缺口" min-width="90" />
              <el-table-column label="状态" width="110">
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
import { Box, Document, List, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getDashboardData } from '@/api/dashboard'
import KpiCard from '@/components/common/KpiCard.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'

interface DashboardSummary {
  planCount: number
  planTrend: string
  inProgressCount: number
  inProgressTrend: string
  openExceptionCount: number
  newExceptionCount: number
  materialAlertCount: number
  newMaterialAlertCount: number
}
interface TeamProgressItem { teamName: string; completedQty: number; totalQty: number; progress: number }
interface OutputTrendItem { date: string; outputQty: number }
interface ExceptionItem { id: string | number; code: string; typeLabel: string; workOrderCode: string; reportedAt: string; status: string }
interface MaterialAlertItem { id: string | number; name: string; stockQty: number; safetyStock: number; gap: number }

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const lastRefresh = ref('')
const teamChartRef = ref<HTMLDivElement>()
const trendChartRef = ref<HTMLDivElement>()
const teamChart = shallowRef<echarts.ECharts>()
const trendChart = shallowRef<echarts.ECharts>()
const teamProgress = ref<TeamProgressItem[]>([])
const outputTrend = ref<OutputTrendItem[]>([])
const exceptionList = ref<ExceptionItem[]>([])
const materialAlerts = ref<MaterialAlertItem[]>([])
const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }
const summary = reactive<DashboardSummary>({ planCount: 0, planTrend: '0%', inProgressCount: 0, inProgressTrend: '0%', openExceptionCount: 0, newExceptionCount: 0, materialAlertCount: 0, newMaterialAlertCount: 0 })
let refreshTimer: ReturnType<typeof setInterval> | null = null
const role = computed(() => userStore.role)
const isSupervisor = computed(() => userStore.isSupervisor || userStore.isAdmin)
const todayLabel = computed(() => formatDate(new Date()))
const kpiCards = computed(() => [
  { title: '今日计划', value: summary.planCount, unit: '条', icon: Document, color: '#1677FF', trend: inferTrend(summary.planTrend), trendValue: summary.planTrend },
  { title: '在制工单', value: summary.inProgressCount, unit: '单', icon: List, color: '#13C2C2', trend: inferTrend(summary.inProgressTrend), trendValue: summary.inProgressTrend },
  { title: '未处理异常', value: summary.openExceptionCount, unit: '项', icon: Warning, color: summary.openExceptionCount > 0 ? '#F5222D' : '#64748B', trend: summary.newExceptionCount > 0 ? 'up' as const : 'flat' as const, trendValue: `今日新增 ${summary.newExceptionCount}` },
  { title: '缺料预警', value: summary.materialAlertCount, unit: '项', icon: Box, color: summary.materialAlertCount > 0 ? '#FA8C16' : '#64748B', trend: summary.newMaterialAlertCount > 0 ? 'up' as const : 'flat' as const, trendValue: `今日新增 ${summary.newMaterialAlertCount}` },
])

onMounted(() => {
  void loadDashboard()
  window.addEventListener('resize', resizeCharts)
  refreshTimer = setInterval(() => { void loadDashboard(true) }, 30000)
})
onActivated(() => { void loadDashboard(true) })
onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  window.removeEventListener('resize', resizeCharts)
  teamChart.value?.dispose()
  trendChart.value?.dispose()
})

async function loadDashboard(silent = false) {
  if (!silent) loading.value = true
  try {
    const payload = await getDashboardData()
    const overview = payload.overview ?? {}
    Object.assign(summary, overview)
    teamProgress.value = payload.teamProgress ?? []
    outputTrend.value = payload.outputTrend ?? []
    exceptionList.value = (payload.exceptionList ?? []) as ExceptionItem[]
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
  if (teamChartRef.value) {
    teamChart.value ??= echarts.init(teamChartRef.value)
    teamChart.value.setOption({
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

  if (trendChartRef.value) {
    trendChart.value ??= echarts.init(trendChartRef.value)
    trendChart.value.setOption({
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
                    <span style="font-weight: 600; color: #fff;">${item.value} <span style="font-weight: 400; font-size: 11px; color: #94a3b8;">件</span></span>
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
.action-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 20px; background: #fff; border: 1px solid var(--theme-border, #e2e8f0); border-radius: 16px; box-shadow: 0 2px 12px rgba(15, 23, 42, 0.05); }
.action-bar__meta, .action-bar__buttons { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.action-bar__time { color: var(--theme-text-secondary, #64748b); font-size: 13px; }
.section-gap { margin-top: 0; }
.panel-card { border-radius: 16px; }
.panel-card__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-weight: 600; }
.chart-box { height: 320px; }
:deep(.el-card__body) { min-height: 180px; }
@media (max-width: 900px) { .action-bar { align-items: flex-start; flex-direction: column; } .chart-box { height: 280px; } }
</style>
