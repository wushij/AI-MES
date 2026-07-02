<template>
  <div class="view-page">
    <PageHeader title="系统管理" subtitle="角色管理 — 查看并配置各角色可访问的功能范围。" />
    <AdminSubNav />

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="rolesList"
        stripe
        border
        highlight-current-row
        :header-cell-style="tableHeaderStyle"
      >
        <el-table-column prop="roleName" label="角色" min-width="120" align="center">
          <template #default="{ row }">
            <span class="role-name-text">{{ row.roleName }}</span>
          </template>
        </el-table-column>
        
        <el-table-column label="权限范围" min-width="480" align="center">
          <template #default="{ row }">
            <div class="tags-container">
              <el-tag v-if="row.fullAccess" size="small" effect="dark" class="perm-tag perm-tag--full">
                全部权限
              </el-tag>
              <template v-else>
                <el-tag
                  v-for="perm in row.permissions"
                  :key="perm"
                  size="small"
                  effect="light"
                  class="perm-tag"
                >
                  {{ perm }}
                </el-tag>
                <span v-if="!row.permissions.length" class="empty-text">暂无分配权限</span>
              </template>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button
              size="small"
              class="action-btn action-btn--edit"
              @click="handleEdit(row)"
            >
              编辑权限
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑权限对话框 -->
    <el-dialog
      v-model="dialogVisible"
      width="560px"
      class="custom-edit-dialog"
      :show-close="true"
    >
      <template #header>
        <div class="dialog-header-custom">
          <el-icon class="header-icon"><Setting /></el-icon>
          <span class="header-text">配置角色权限 - {{ currentRole?.roleName }}</span>
        </div>
      </template>

      <div v-if="currentRole" class="custom-dialog-body">
        <div class="dialog-tip-box">
          <p v-if="isAdminRole">管理员默认拥有全部功能权限，无需单独配置。</p>
          <p v-else>请勾选该角色允许访问的菜单模块和功能。点击确认后将立刻生效。</p>
        </div>

        <el-form label-position="top" class="edit-permissions-form">
          <el-form-item label="可访问功能模块">
            <el-checkbox-group
              v-model="selectedPermissions"
              class="permissions-checkbox-grid"
              :disabled="isAdminRole"
            >
              <el-checkbox
                v-for="perm in allAvailablePermissions"
                :key="perm"
                :label="perm"
                class="perm-checkbox-item"
              >
                {{ perm }}
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer-custom">
          <el-button class="btn-cancel" @click="dialogVisible = false">{{ isAdminRole ? '关闭' : '取消' }}</el-button>
          <el-button v-if="!isAdminRole" type="primary" class="btn-submit" @click="savePermissions">
            确认保存
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import AdminSubNav from '@/components/admin/AdminSubNav.vue'
import { getRoles, updateRolePermissions, type RoleItem } from '@/api/roles'

const tableHeaderStyle = { background: '#F5F7FA', fontWeight: '600' }

const allAvailablePermissions = [
  '生产计划', '工单管理', '班组', '物料', '排产',
  '工序进度', '工单反馈', '异常上报', 'AI 客服',
  '用户管理', '角色管理', 'Coze 配置', '系统配置'
]

const rolesList = ref<RoleItem[]>([])
const dialogVisible = ref(false)
const currentRole = ref<RoleItem | null>(null)
const selectedPermissions = ref<string[]>([])
const loading = ref(false)

const isAdminRole = computed(() => currentRole.value?.id === 'admin' || Boolean(currentRole.value?.fullAccess))

async function loadRoles() {
  loading.value = true
  try {
    rolesList.value = await getRoles()
  } catch (error) {
    console.error(error)
    ElMessage.error('加载角色权限数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadRoles()
})

function handleEdit(row: RoleItem) {
  currentRole.value = row
  selectedPermissions.value = row.fullAccess ? [...allAvailablePermissions] : [...row.permissions]
  dialogVisible.value = true
}

async function savePermissions() {
  if (!currentRole.value) return
  
  try {
    await updateRolePermissions(currentRole.value.id, selectedPermissions.value)
    ElMessage.success('权限保存成功，已登录用户刷新页面后生效')
    dialogVisible.value = false
    await loadRoles()
  } catch (error) {
    console.error(error)
    ElMessage.error('权限保存失败')
  }
}
</script>

<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-card {
  border-radius: 16px;
}

.role-name-text {
  font-weight: 600;
  color: #1e293b;
  font-size: 14px;
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  padding: 4px 0;
}

.perm-tag {
  border-radius: 12px !important;
  font-weight: 500 !important;
  padding: 4px 10px !important;
  background-color: rgba(79, 70, 229, 0.05) !important;
  border-color: rgba(79, 70, 229, 0.15) !important;
  color: #4f46e5 !important;
}

.perm-tag--full {
  background-color: #4f46e5 !important;
  border-color: #4f46e5 !important;
  color: #fff !important;
}

.empty-text {
  color: #94a3b8;
  font-size: 13px;
  font-style: italic;
}

/* Action Button */
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

/* Custom dialog styling */
.custom-edit-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08) !important;
}

.custom-edit-dialog :deep(.el-dialog__header) {
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

.custom-dialog-body {
  padding: 8px 4px;
}

.dialog-tip-box {
  background-color: rgba(79, 70, 229, 0.03);
  border: 1px solid rgba(79, 70, 229, 0.08);
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 20px;
}

.dialog-tip-box p {
  margin: 0;
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
}

.edit-permissions-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #1e293b;
  padding-bottom: 12px !important;
  font-size: 14px;
}

/* Checkbox Grid layout */
.permissions-checkbox-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  width: 100%;
}

.perm-checkbox-item {
  margin-right: 0 !important;
  height: auto !important;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  transition: all 0.2s ease;
}

.perm-checkbox-item:hover {
  background: #fff;
  border-color: #cbd5e1;
}

.perm-checkbox-item.is-checked {
  border-color: #4f46e5 !important;
  background: rgba(79, 70, 229, 0.03) !important;
}

.perm-checkbox-item :deep(.el-checkbox__label) {
  font-size: 13px;
  color: #334155;
}

.perm-checkbox-item.is-checked :deep(.el-checkbox__label) {
  color: #4f46e5;
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
</style>
