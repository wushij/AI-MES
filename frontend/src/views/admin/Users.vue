<template>
  <div v-if="isAdmin" class="view-page">
    <PageHeader title="系统管理" subtitle="用户管理 — 管理账号、角色、班组归属及启用/禁用访问权限。" />
    <AdminSubNav />

    <!-- 过滤器和操作栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索姓名或用户名"
            clearable
            class="toolbar__input"
            @keyup.enter="loadUsers"
          />
          <el-select v-model="filters.role" placeholder="角色" clearable class="toolbar__select">
            <el-option label="管理员" value="admin" />
            <el-option label="车间主管" value="supervisor" />
            <el-option label="计划与物控" value="planner" />
            <el-option label="设备与品质工程师" value="engineer" />
            <el-option label="普通员工" value="worker" />
          </el-select>
          <el-select v-model="filters.enabled" placeholder="状态" clearable class="toolbar__select">
            <el-option label="已启用" :value="true" />
            <el-option label="已禁用" :value="false" />
          </el-select>
        </div>
        <div class="toolbar__actions">
          <el-button class="btn-reset" @click="resetFilters">重置</el-button>
          <el-button class="btn-create" type="primary" @click="openCreate">新建用户</el-button>
        </div>
      </div>
    </el-card>

    <!-- 用户列表表格 -->
    <el-card shadow="hover">
      <el-table
        v-loading="loading"
        :data="pagedUsers"
        stripe
        border
        highlight-current-row
        :header-cell-style="tableHeaderStyle"
      >
        <el-table-column prop="name" label="姓名" min-width="120" align="center" />
        <el-table-column prop="username" label="用户名" min-width="140" align="center" />
        <el-table-column label="角色" min-width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : row.role === 'supervisor' ? 'warning' : row.role === 'planner' ? 'success' : row.role === 'engineer' ? '' : 'info'">
              {{ roleText(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teamName" label="班组" min-width="140" align="center" />
        <el-table-column label="状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" align="center" />
        <el-table-column label="操作" fixed="right" width="280" align="center">
          <template #default="{ row }">
            <el-button size="small" class="action-btn action-btn--edit" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" class="action-btn action-btn--reset" @click="resetPassword(row)">重置密码</el-button>
            <el-button size="small" :class="['action-btn', row.enabled ? 'action-btn--disable' : 'action-btn--enable']" @click="toggleStatus(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户" />
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

    <!-- 编辑/新建弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建用户' : '编辑用户'"
      width="560px"
      class="admin-form-dialog"
    >
      <el-form ref="formRef" :model="formModel" :rules="rules" label-width="96px" class="admin-form-dialog__form">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="formModel.name" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formModel.username" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="formModel.role" class="full-width">
            <el-option label="管理员" value="admin" />
            <el-option label="车间主管" value="supervisor" />
            <el-option label="计划与物控" value="planner" />
            <el-option label="设备与品质工程师" value="engineer" />
            <el-option label="普通员工" value="worker" />
          </el-select>
        </el-form-item>
        <el-form-item label="班组">
          <el-select v-model="formModel.teamId" clearable class="full-width">
            <el-option v-for="team in teams" :key="team.id" :label="team.name" :value="team.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="formModel.enabled" inline-prompt active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialogVisible" title="重置密码" width="480px" class="admin-form-dialog">
      <p class="reset-tip">为用户 <strong>{{ resettingUser?.username }}</strong> 设置新密码</p>
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="96px" class="admin-form-dialog__form">
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="resetForm.password"
            type="password"
            show-password
            placeholder="请输入新密码"
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="resetForm.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
            autocomplete="new-password"
            @keyup.enter="submitResetPassword"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="submitResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
  <Forbidden v-else />
</template>



<script setup lang="ts">

import { ElMessage, ElMessageBox } from 'element-plus'

import type { FormInstance, FormRules } from 'element-plus'

import { computed, onMounted, reactive, ref, watch } from 'vue'

import Forbidden from '../Forbidden.vue'

import PageHeader from '@/components/common/PageHeader.vue'

import AdminSubNav from '@/components/admin/AdminSubNav.vue'

import { createUser, getUsers, resetUserPassword, toggleUserStatus, updateUser } from '@/api/users'

import { getTeams } from '@/api/teams'

import { useUserStore } from '@/stores/user'

import { roleLabel } from '@/utils/labels'

interface TeamOption { id: number; name: string }

interface UserRow { id: string | number; name: string; username: string; role: string; teamId?: string | number; teamName: string; enabled: boolean; createdAt: string }

const userStore = useUserStore()

const loading = ref(false)

const saving = ref(false)

const dialogVisible = ref(false)

const dialogMode = ref<'create' | 'edit'>('create')

const editingId = ref<string | number | null>(null)

const users = ref<UserRow[]>([])

const teams = ref<TeamOption[]>([])

const pagination = reactive({ page: 1, size: 10, total: 0 })

const filteredUsers = computed(() => {
  return users.value.filter((row) => {
    const keyword = filters.keyword.trim().toLowerCase()
    if (keyword && !row.name.toLowerCase().includes(keyword) && !row.username.toLowerCase().includes(keyword)) {
      return false
    }
    if (filters.role && row.role !== filters.role) return false
    if (filters.enabled !== undefined && row.enabled !== filters.enabled) return false
    return true
  })
})

const pagedUsers = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredUsers.value.slice(start, start + pagination.size)
})

watch(
  () => filteredUsers.value.length,
  (count) => {
    pagination.total = count
    if ((pagination.page - 1) * pagination.size >= count && pagination.page > 1) {
      pagination.page = 1
    }
  },
  { immediate: true }
)

const formRef = ref<FormInstance>()
const resetFormRef = ref<FormInstance>()

const resetDialogVisible = ref(false)
const resetting = ref(false)
const resettingUser = ref<UserRow | null>(null)
const resetForm = reactive({ password: '', confirmPassword: '' })

const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }

const isAdmin = computed(() => userStore.isAdmin)

const filters = reactive({ keyword: '', role: '', enabled: undefined as boolean | undefined })

const formModel = reactive<{ name: string; username: string; role: string; teamId: number | undefined; enabled: boolean }>({
  name: '',
  username: '',
  role: 'worker',
  teamId: undefined,
  enabled: true
})

const rules: FormRules = { name: [{ required: true, message: '请输入姓名', trigger: 'blur' }], username: [{ required: true, message: '请输入用户名', trigger: 'blur' }], role: [{ required: true, message: '请选择角色', trigger: 'change' }] }

const resetRules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度为 6-64 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== resetForm.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

onMounted(() => { if (isAdmin.value) { loadUsers(); loadTeams() } })

async function loadUsers() {
  loading.value = true
  try {
    const list = await getUsers()
    users.value = list.map((item) => ({
      id: item.id,
      name: String(item.realName ?? '--'),
      username: String(item.username ?? '--'),
      role: String(item.role ?? 'worker'),
      teamId: item.teamId,
      teamName: String(item.teamName ?? '--'),
      enabled: item.status === 1,
      createdAt: String(item.createTime ?? '--')
    }))
  } catch (error) {
    console.error(error)
    ElMessage.error('加载用户失败')
  } finally {
    loading.value = false
  }
}

async function loadTeams() {
  try {
    const response = await getTeams()
    teams.value = response.map((item) => ({
      id: Number(item.id),
      name: String(item.teamName ?? '--')
    }))
  } catch (error) {
    console.error(error)
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  Object.assign(formModel, { name: '', username: '', role: 'worker', teamId: undefined, enabled: true })
  dialogVisible.value = true
}

function openEdit(row: UserRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(formModel, {
    name: row.name,
    username: row.username,
    role: row.role,
    teamId: row.teamId != null && row.teamId !== '' ? Number(row.teamId) : undefined,
    enabled: row.enabled
  })
  dialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      username: formModel.username,
      realName: formModel.name,
      role: formModel.role,
      teamId: formModel.teamId ?? null,
      status: formModel.enabled ? 1 : 0
    }
    if (dialogMode.value === 'create') {
      await createUser(payload)
      ElMessage.success('用户已创建')
    } else if (editingId.value != null) {
      await updateUser(editingId.value, payload)
      ElMessage.success('用户已更新')
    }
    dialogVisible.value = false
    loadUsers()
  } catch (error) {
    console.error(error)
    ElMessage.error('保存用户失败')
  } finally {
    saving.value = false
  }
}

function resetPassword(row: UserRow) {
  resettingUser.value = row
  resetForm.password = ''
  resetForm.confirmPassword = ''
  resetDialogVisible.value = true
  resetFormRef.value?.clearValidate()
}

async function submitResetPassword() {
  const valid = await resetFormRef.value?.validate().catch(() => false)
  if (!valid || !resettingUser.value) return
  resetting.value = true
  try {
    await resetUserPassword(resettingUser.value.id, resetForm.password)
    ElMessage.success(`用户 ${resettingUser.value.username} 的密码已重置`)
    resetDialogVisible.value = false
  } catch (error) {
    console.error(error)
    ElMessage.error('重置密码失败')
  } finally {
    resetting.value = false
  }
}

async function toggleStatus(row: UserRow) {
  if (row.enabled) {
    try {
      await ElMessageBox.confirm(
        `确认禁用用户「${row.username}」？禁用后该账号将无法登录系统。`,
        '禁用确认',
        {
          type: 'warning',
          confirmButtonText: '确认禁用',
          cancelButtonText: '取消',
          distinguishCancelAndClose: true
        }
      )
    } catch {
      return
    }
  }

  try {
    await toggleUserStatus(row.id)
    ElMessage.success(`用户已${row.enabled ? '禁用' : '启用'}`)
    loadUsers()
  } catch (error) {
    console.error(error)
    ElMessage.error('更新状态失败')
  }
}

function roleText(value: string) { return roleLabel(value) }

function resetFilters() {
  filters.keyword = ''
  filters.role = ''
  filters.enabled = undefined
  pagination.page = 1
  loadUsers()
}

</script>


<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Toolbar Card Styling */
.toolbar-card {
  border-radius: 16px;
  border: 1px solid #f1f5f9;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
}

.toolbar-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar__filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

/* Rounded Capsule Inputs */
.toolbar__input {
  width: 240px;
}

.toolbar__input :deep(.el-input__wrapper) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  border: 1px solid #e2e8f0 !important;
  box-shadow: none !important;
  padding: 0 16px !important;
  height: 38px;
  transition: all 0.2s ease;
}

.toolbar__input :deep(.el-input__wrapper):hover {
  border-color: #cbd5e1 !important;
}

.toolbar__input :deep(.el-input__wrapper.is-focus) {
  border-color: #4f46e5 !important;
  background-color: #fff !important;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12) !important;
}

.toolbar__select {
  width: 140px;
}

.toolbar__select :deep(.el-select__wrapper) {
  border-radius: 20px !important;
  background-color: #f8fafc !important;
  border: 1px solid #e2e8f0 !important;
  box-shadow: none !important;
  padding: 0 16px !important;
  height: 38px;
  transition: all 0.2s ease;
}

.toolbar__select :deep(.el-select__wrapper):hover {
  border-color: #cbd5e1 !important;
}

.toolbar__select :deep(.el-select__wrapper.is-focus) {
  border-color: #4f46e5 !important;
  background-color: #fff !important;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12) !important;
}

/* Buttons style */
.btn-reset {
  border-radius: 20px !important;
  padding: 0 20px !important;
  height: 38px !important;
  font-weight: 500 !important;
  border: 1px solid #e2e8f0 !important;
  color: #64748b !important;
  background: #fff !important;
  transition: all 0.2s ease !important;
}

.btn-reset:hover {
  border-color: #cbd5e1 !important;
  color: #334155 !important;
  background: #f8fafc !important;
}

.btn-create {
  border-radius: 20px !important;
  padding: 0 20px !important;
  height: 38px !important;
  font-weight: 500 !important;
  background: linear-gradient(135deg, #4f46e5, #1677ff) !important;
  border: none !important;
  color: #fff !important;
  transition: all 0.2s ease !important;
}

.btn-create:hover {
  opacity: 0.95 !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.24) !important;
}

.btn-create:active {
  transform: scale(0.98) !important;
}

/* Premium Card and Table styling */
.el-card {
  border-radius: 16px !important;
  border: 1px solid #f1f5f9 !important;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03) !important;
}

.el-card :deep(.el-card__header) {
  border-bottom: 1px solid #f1f5f9;
  padding: 16px 20px;
  font-weight: 600;
  color: #0f172a;
}

:deep(.el-table) {
  border-radius: 12px;
  overflow: hidden;
  --el-table-border-color: #f1f5f9;
  --el-table-header-bg-color: #f8fafc;
}

:deep(.el-table__header-wrapper th) {
  background-color: #f8fafc !important;
  color: #475569 !important;
  font-weight: 600 !important;
  font-size: 13px;
  padding: 12px 0 !important;
}

:deep(.el-table__row td) {
  padding: 12px 0 !important;
  font-size: 13px;
  color: #334155;
}

:deep(.el-table .el-button--link) {
  font-size: 13px;
  font-weight: 500;
  padding: 0 4px;
}

/* Custom Translucent Badges / Tags */
:deep(.el-tag) {
  border-radius: 8px !important;
  font-weight: 500 !important;
  padding: 2px 10px !important;
  border: none !important;
}

:deep(.el-tag--success) {
  background-color: #f0fdf4 !important;
  color: #16a34a !important;
}

:deep(.el-tag--danger) {
  background-color: #fef2f2 !important;
  color: #dc2626 !important;
}

:deep(.el-tag--warning) {
  background-color: #fffbeb !important;
  color: #d97706 !important;
}

:deep(.el-tag--info) {
  background-color: #f8fafc !important;
  color: #64748b !important;
}

/* Dialog Styles */
:deep(.el-dialog) {
  border-radius: 20px !important;
  overflow: hidden;
  padding: 24px;
}

:deep(.el-dialog__header) {
  margin-right: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}

:deep(.el-dialog__title) {
  font-weight: 700;
  color: #0f172a;
}

:deep(.el-dialog__body) {
  padding: 16px 0 8px !important;
}

.admin-form-dialog__form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.admin-form-dialog__form :deep(.el-form-item__label) {
  justify-content: flex-end;
  padding-right: 12px;
  color: #475569;
}

.admin-form-dialog__form :deep(.el-input__wrapper) {
  border-radius: 10px !important;
  min-height: 40px;
  padding: 0 12px !important;
  background: #fff !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}

.admin-form-dialog__form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset !important;
}

.admin-form-dialog__form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px #4f46e5 inset,
    0 0 0 3px rgba(79, 70, 229, 0.1) !important;
}

.admin-form-dialog__form :deep(.el-input__inner) {
  text-align: left;
}

.admin-form-dialog__form :deep(.el-select__wrapper) {
  border-radius: 10px !important;
  min-height: 40px;
  padding: 0 12px !important;
  background: #fff !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}

.admin-form-dialog__form :deep(.el-select .el-select__selection) {
  justify-content: flex-start;
}

.admin-form-dialog__form :deep(.el-select .el-select__selected-item),
.admin-form-dialog__form :deep(.el-select .el-select__placeholder) {
  text-align: left;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #475569;
}

:deep(.el-dialog__footer) {
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}

.full-width {
  width: 100%;
}

.reset-tip {
  margin: 0 0 18px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e8edf3;
  color: #64748b;
  font-size: 14px;
  line-height: 1.5;
}

.reset-tip strong {
  color: #0f172a;
}

/* Table Action Buttons with Borders */
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

.action-btn--reset {
  border: 1px solid #e2e8f0 !important;
  color: #64748b !important;
}
.action-btn--reset:hover {
  background: #f8fafc !important;
  border-color: #cbd5e1 !important;
  color: #334155 !important;
}

.action-btn--disable {
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #ef4444 !important;
}
.action-btn--disable:hover {
  background: rgba(239, 68, 68, 0.05) !important;
  border-color: #ef4444 !important;
}

.action-btn--enable {
  border: 1px solid rgba(16, 185, 129, 0.3) !important;
  color: #10b981 !important;
}
.action-btn--enable:hover {
  background: rgba(16, 185, 129, 0.05) !important;
  border-color: #10b981 !important;
}

.table-pagination {
  display: flex;
  justify-content: flex-start;
  margin-top: 12px;
}
</style>
