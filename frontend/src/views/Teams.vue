<template>
  <div v-if="isSupervisor" class="view-page">
    <PageHeader title="班组管理" subtitle="查看班组长、成员及当前任务分配情况。">
      <el-button type="primary" @click="openCreate">新建班组</el-button>
    </PageHeader>

    <el-card shadow="hover">
      <el-table
        v-loading="loading"
        :data="pagedTeams"
        stripe
        border
        highlight-current-row
        :header-cell-style="tableHeaderStyle"
      >
        <el-table-column prop="code" label="班组编号" min-width="100" align="center" />
        <el-table-column prop="name" label="班组名称" min-width="140" align="center" />
        <el-table-column prop="leaderName" label="班组长" min-width="120" align="center" />
        <el-table-column prop="memberCount" label="成员数" min-width="100" align="center" />
        <el-table-column prop="lineName" label="产线" min-width="140" align="center" />
        <el-table-column label="操作" fixed="right" width="260" align="center">
          <template #default="{ row }">
            <el-button size="small" class="action-btn action-btn--view" @click="openDetail(row)">查看</el-button>
            <el-button size="small" class="action-btn action-btn--edit" @click="openEdit(row)">编辑</el-button>
            <el-button
              size="small"
              class="action-btn action-btn--delete"
              :loading="actionLoading === `delete-${row.id}`"
              @click="removeTeam(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无班组数据">
            <el-button type="primary" class="action-btn-refresh" @click="openCreate">新建班组</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建班组' : '编辑班组'" width="520px">
      <el-form ref="formRef" :model="formModel" :rules="rules" label-width="96px">
        <el-form-item v-if="dialogMode === 'create'" label="班组编号">
          <el-input v-model="formModel.teamCode" placeholder="留空自动生成，如 T-001" />
        </el-form-item>
        <el-form-item label="班组名称" prop="teamName">
          <el-input v-model="formModel.teamName" maxlength="50" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'edit'" label="班组长">
          <el-select
            v-model="formModel.leaderId"
            clearable
            class="full-width"
            :placeholder="leaderOptions.length ? '从本班组成员中选择' : '请先在用户管理中将员工划入本班组'"
            :disabled="!leaderOptions.length"
          >
            <el-option v-for="user in leaderOptions" :key="user.id" :label="user.label" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="产线">
          <el-input v-model="formModel.lineName" placeholder="如 产线A" />
        </el-form-item>
        <el-form-item label="成员数">
          <el-input-number v-model="formModel.memberCount" :min="0" class="full-width" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="drawerVisible"
      width="680px"
      class="custom-team-dialog"
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom">
          <el-icon class="header-icon"><UserFilled /></el-icon>
          <span class="header-text">{{ detail?.name ? `${detail.name} 详情` : '班组详情' }}</span>
        </div>
      </template>

      <template v-if="detail">
        <!-- Metadata 2x2 Grid -->
        <div class="order-meta-grid">
          <div class="meta-item">
            <span class="meta-item__label">班组编号</span>
            <span class="meta-item__val code-highlight">{{ detail.code }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">班组长</span>
            <span class="meta-item__val text-highlight">{{ detail.leaderName || '未指定' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">成员数</span>
            <span class="meta-item__val">{{ detail.memberCount }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">产线</span>
            <span class="meta-item__val text-secondary">{{ detail.lineName || '--' }}</span>
          </div>
        </div>

        <!-- 3 KPI Cards -->
        <div class="kpi-row-custom">
          <div class="kpi-card-custom kpi-card-custom--pending">
            <div class="kpi-card-custom__val">{{ detail.summary.pending }}</div>
            <div class="kpi-card-custom__label">待派工工单</div>
          </div>
          <div class="kpi-card-custom kpi-card-custom--producing">
            <div class="kpi-card-custom__val">{{ detail.summary.producing }}</div>
            <div class="kpi-card-custom__label">进行中任务</div>
          </div>
          <div class="kpi-card-custom kpi-card-custom--done">
            <div class="kpi-card-custom__val">{{ detail.summary.done }}</div>
            <div class="kpi-card-custom__label">已完工工单</div>
          </div>
        </div>

        <!-- Tabs layout for sublists -->
        <el-tabs v-model="activeTab" class="custom-dialog-tabs">
          <el-tab-pane label="班组成员" name="members">
            <el-table :data="detail.members" stripe border :header-cell-style="tableHeaderStyle" class="custom-table-compact">
              <el-table-column prop="name" label="姓名" min-width="120" align="center" />
              <el-table-column prop="roleLabel" label="角色" min-width="100" align="center" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="关联工单" name="orders">
            <el-table :data="detail.tasks" stripe border :header-cell-style="tableHeaderStyle" class="custom-table-compact">
              <el-table-column prop="workOrderCode" label="工单号" min-width="130" align="center" />
              <el-table-column prop="productName" label="产品" min-width="150" show-overflow-tooltip align="center" />
              <el-table-column prop="processName" label="当前工序" min-width="110" align="center" />
              <el-table-column label="状态" width="110" align="center">
                <template #default="{ row }"><StatusTag :status="row.status" /></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else description="暂无班组详情" />

      <template #footer>
        <div class="dialog-footer-custom">
          <el-button class="btn-cancel" @click="drawerVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
  <Forbidden v-else />
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import Forbidden from './Forbidden.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { createTeam, deleteTeam, getTeamDetail, getTeams, updateTeam } from '@/api/teams'
import { useUserStore } from '@/stores/user'
import { roleLabel } from '@/utils/labels'

interface TeamRow {
  id: number | string
  code: string
  name: string
  leaderId?: number
  leaderName: string
  memberCount: number
  lineName: string
}

interface LeaderOption {
  id: number
  label: string
}

interface TeamDetail extends TeamRow {
  summary: { pending: number; producing: number; done: number }
  members: Array<{ id: number | string; name: string; roleLabel: string }>
  tasks: Array<{ id: number | string; workOrderCode: string; productName: string; processName: string; status: string }>
}

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const actionLoading = ref('')
const drawerVisible = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | string | null>(null)
const formRef = ref<FormInstance>()
const teams = ref<TeamRow[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })

const pagedTeams = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return teams.value.slice(start, start + pagination.size)
})

watch(
  () => teams.value.length,
  (count) => {
    pagination.total = count
    if ((pagination.page - 1) * pagination.size >= count && pagination.page > 1) {
      pagination.page = 1
    }
  },
  { immediate: true }
)
const leaderOptions = ref<LeaderOption[]>([])
const detail = ref<TeamDetail | null>(null)
const activeTab = ref('members')
const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }
const isSupervisor = computed(() => userStore.isAdmin || userStore.isSupervisor)

const formModel = reactive({
  teamCode: '',
  teamName: '',
  lineName: '',
  memberCount: 0,
  leaderId: undefined as number | undefined
})

const rules: FormRules = {
  teamName: [{ required: true, message: '请输入班组名称', trigger: 'blur' }]
}

onMounted(() => {
  if (isSupervisor.value) void loadTeams()
})

function mapTeamRow(item: Record<string, unknown>): TeamRow {
  const leaderId = item.leaderId != null ? Number(item.leaderId) : undefined
  return {
    id: (item.id as number | string) ?? '',
    code: String(item.teamCode ?? '--'),
    name: String(item.teamName ?? '--'),
    leaderId: Number.isFinite(leaderId) ? leaderId : undefined,
    leaderName: String(item.leaderName ?? '未指定'),
    memberCount: Number(item.memberCount ?? 0),
    lineName: String(item.lineName ?? '--')
  }
}

async function loadLeaderOptions(teamId: number | string) {
  try {
    const payload = (await getTeamDetail(teamId)) as Record<string, unknown>
    const members = Array.isArray(payload.members) ? payload.members : []
    leaderOptions.value = members.map((item: Record<string, unknown>) => ({
      id: Number(item.id),
      label: `${String(item.realName ?? item.username ?? '--')}（${roleLabel(String(item.role ?? ''))}）`
    }))
    const leaderId = payload.leaderId != null ? Number(payload.leaderId) : undefined
    if (
      leaderId != null &&
      Number.isFinite(leaderId) &&
      !leaderOptions.value.some((option) => option.id === leaderId)
    ) {
      leaderOptions.value.unshift({
        id: leaderId,
        label: String(payload.leaderName ?? leaderId)
      })
    }
  } catch (error) {
    console.error(error)
    leaderOptions.value = []
  }
}

async function loadTeams() {
  loading.value = true
  try {
    const list = await getTeams()
    teams.value = (Array.isArray(list) ? list : []).map((item) => mapTeamRow(item as Record<string, unknown>))
  } catch (error) {
    console.error(error)
    ElMessage.error('加载班组失败，请确认后端已启动且数据库已初始化')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  leaderOptions.value = []
  Object.assign(formModel, { teamCode: '', teamName: '', lineName: '', memberCount: 0, leaderId: undefined })
  dialogVisible.value = true
}

async function openEdit(row: TeamRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(formModel, {
    teamCode: row.code,
    teamName: row.name,
    lineName: row.lineName === '--' ? '' : row.lineName,
    memberCount: row.memberCount,
    leaderId: row.leaderId
  })
  await loadLeaderOptions(row.id)
  dialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      teamCode: formModel.teamCode || undefined,
      teamName: formModel.teamName,
      lineName: formModel.lineName || undefined,
      memberCount: formModel.memberCount,
      leaderId: dialogMode.value === 'edit' ? formModel.leaderId ?? null : undefined
    }
    if (dialogMode.value === 'create') {
      await createTeam(payload)
      ElMessage.success('班组已创建')
    } else if (editingId.value != null) {
      await updateTeam(editingId.value, payload)
      ElMessage.success('班组已更新')
    }
    dialogVisible.value = false
    await loadTeams()
  } catch (error) {
    console.error(error)
    ElMessage.error('保存班组失败')
  } finally {
    saving.value = false
  }
}

async function removeTeam(row: TeamRow) {
  await ElMessageBox.confirm(
    `确认删除班组「${row.name}」？若班组下仍有成员或关联工单，将无法删除。`,
    '删除班组',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  actionLoading.value = `delete-${row.id}`
  try {
    await deleteTeam(row.id)
    ElMessage.success('班组已删除')
    await loadTeams()
  } catch (error) {
    console.error(error)
    ElMessage.error('删除失败，请确认该班组下无成员和工单')
  } finally {
    actionLoading.value = ''
  }
}

async function openDetail(row: TeamRow) {
  activeTab.value = 'members'
  drawerVisible.value = true
  detail.value = null
  try {
    const payload = (await getTeamDetail(row.id)) as Record<string, unknown>
    const summary = (payload.taskSummary as Record<string, number>) ?? {}
    detail.value = {
      ...mapTeamRow(payload),
      summary: {
        pending: Number(summary.pending ?? 0),
        producing: Number(summary.producing ?? 0),
        done: Number(summary.done ?? 0)
      },
      members: Array.isArray(payload.members)
        ? payload.members.map((item: Record<string, unknown>) => ({
            id: item.id as number | string,
            name: String(item.realName ?? item.username ?? '--'),
            roleLabel: roleLabel(String(item.role ?? ''))
          }))
        : [],
      tasks: Array.isArray(payload.tasks)
        ? payload.tasks.map((item: Record<string, unknown>) => ({
            id: item.workOrderId as number | string,
            workOrderCode: String(item.orderNo ?? '--'),
            productName: String(item.productName ?? '--'),
            processName: String(item.processName ?? '--'),
            status: String(item.status ?? 'pending')
          }))
        : []
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('加载班组详情失败')
  }
}
</script>

<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-block {
  margin-bottom: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.full-width {
  width: 100%;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
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

/* Action Buttons with Outlines */
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

.action-btn--view {
  border: 1px solid #e2e8f0 !important;
  color: #64748b !important;
}
.action-btn--view:hover {
  background: #f8fafc !important;
  border-color: #cbd5e1 !important;
  color: #334155 !important;
}

.action-btn--delete {
  border: 1px solid rgba(245, 34, 45, 0.3) !important;
  color: #f5222d !important;
}
.action-btn--delete:hover {
  background: rgba(245, 34, 45, 0.05) !important;
  border-color: #f5222d !important;
}

/* Custom dialog & elements styles for Team Detail */
.custom-team-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08) !important;
}

.custom-team-dialog :deep(.el-dialog__header) {
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

.order-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1px;
  background: #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  margin-bottom: 20px;
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
  font-family: monospace;
  color: #4f46e5;
}

.text-highlight {
  color: #0f172a;
}

.text-secondary {
  color: #475569;
}

/* KPI Custom Cards */
.kpi-row-custom {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.kpi-card-custom {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.3s ease;
}

.kpi-card-custom__val {
  font-size: 24px;
  font-weight: 700;
}

.kpi-card-custom__label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.kpi-card-custom--pending {
  border-left: 4px solid #faad14;
}
.kpi-card-custom--pending .kpi-card-custom__val {
  color: #d97706;
}

.kpi-card-custom--producing {
  border-left: 4px solid #1677ff;
}
.kpi-card-custom--producing .kpi-card-custom__val {
  color: #1d4ed8;
}

.kpi-card-custom--done {
  border-left: 4px solid #52c41a;
}
.kpi-card-custom--done .kpi-card-custom__val {
  color: #15803d;
}

/* Tabs and Tables Inside Dialog */
.custom-dialog-tabs :deep(.el-tabs__nav-wrap::after) {
  background-color: #f1f5f9;
}

.custom-dialog-tabs :deep(.el-tabs__item) {
  font-weight: 600;
  color: #64748b;
  font-size: 14px;
  padding: 0 16px;
}

.custom-dialog-tabs :deep(.el-tabs__item.is-active) {
  color: #4f46e5;
}

.custom-dialog-tabs :deep(.el-tabs__active-bar) {
  background-color: #4f46e5;
}

.custom-table-compact {
  margin-top: 10px;
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

.table-pagination {
  display: flex;
  justify-content: flex-start;
  margin-top: 12px;
}
</style>
