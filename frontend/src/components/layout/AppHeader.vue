<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Expand, Fold, Bell, ArrowDown, UserFilled } from '@element-plus/icons-vue'
import AppThemePicker from './AppThemePicker.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notifications'
import { roleLabel as getRoleLabel } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const { unreadItems, readItems, unreadCount } = storeToRefs(notificationStore)

const currentTitle = computed(() => {
  const title = route.meta?.title
  return typeof title === 'string' ? title : 'AI-MES'
})

const roleLabel = computed(() => {
  return getRoleLabel(userStore.role)
})

const activeTab = ref('unread')

async function handleCommand(command: string | number | object) {
  if (command === 'logout') {
    await userStore.logout()
    await router.push('/login')
  } else if (command === 'profile') {
    await router.push('/profile')
  }
}

function handleNotificationPopoverShow() {
  void notificationStore.fetchAll()
}

async function markAllAsRead() {
  try {
    await notificationStore.markAllAsRead()
  } catch (error) {
    console.error('标记全部已读失败:', error)
  }
}

async function clearRead() {
  try {
    await ElMessageBox.confirm(
      '将删除所有已读通知，此操作不可撤销。是否继续？',
      '确认清除已读通知',
      {
        confirmButtonText: '确认清空',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await notificationStore.clearRead()
    ElMessage.success('已清除全部已读通知')
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      ElMessage.info('已取消清除操作')
      return
    }
    console.error('清除已读通知失败:', error)
    ElMessage.error('清除已读通知失败，请稍后重试')
  }
}

async function handleNotificationClick(item: { id: number; target: string }) {
  try {
    await notificationStore.markAsRead(item.id)
    await router.push(item.target)
  } catch (error) {
    console.error('标记单条已读失败:', error)
  }
}

function handleReadNotificationClick(item: { target: string }) {
  void router.push(item.target)
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__left">
      <el-icon class="app-header__collapse" @click="appStore.toggleSidebar()">
        <Fold v-if="!appStore.sidebarCollapsed" />
        <Expand v-else />
      </el-icon>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="currentTitle && currentTitle !== 'AI-MES'">
          {{ currentTitle }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="app-header__right">
      <el-tag round effect="plain" class="app-header__role">{{ roleLabel }}</el-tag>
      <AppThemePicker
        :current-color="appStore.themeColor"
        :active-preset-id="appStore.activeThemePresetId"
        @preset-select="appStore.selectThemePreset"
        @color-change="appStore.changeThemeColor"
      />
      <el-popover
        placement="bottom-end"
        :width="320"
        trigger="click"
        popper-class="notification-popover"
        @show="handleNotificationPopoverShow"
      >
        <template #reference>
          <div class="header-icon-wrapper">
            <el-badge :value="unreadCount" :max="9" :hidden="unreadCount === 0" class="notification-badge">
              <el-icon class="header-icon-btn"><Bell /></el-icon>
            </el-badge>
          </div>
        </template>

        <div class="notification-panel">
          <div class="panel-title">
            <span>通知中心</span>
            <el-button
              v-if="activeTab === 'unread' && unreadItems.length > 0"
              link
              type="primary"
              size="small"
              @click="markAllAsRead"
            >
              全部已读
            </el-button>
            <el-button
              v-else-if="activeTab === 'read' && readItems.length > 0"
              link
              type="danger"
              size="small"
              @click="clearRead"
            >
              清除已读
            </el-button>
          </div>

          <el-tabs v-model="activeTab" class="notification-tabs">
            <el-tab-pane name="unread">
              <template #label>
                <span>未读 ({{ unreadItems.length }})</span>
              </template>
              <div v-if="unreadItems.length > 0" class="notification-list">
                <div v-for="item in unreadItems" :key="item.id" class="notification-item" @click="handleNotificationClick(item)">
                  <div class="item-icon" :class="`item-icon--${item.type}`">
                    <el-icon><component :is="item.icon" /></el-icon>
                  </div>
                  <div class="item-content">
                    <div class="item-text">{{ item.text }}</div>
                    <div class="item-time">{{ item.time }}</div>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无未读消息" :image-size="60" />
            </el-tab-pane>

            <el-tab-pane name="read">
              <template #label>
                <span>已读 ({{ readItems.length }})</span>
              </template>
              <div v-if="readItems.length > 0" class="notification-list">
                <div v-for="item in readItems" :key="item.id" class="notification-item" @click="handleReadNotificationClick(item)">
                  <div class="item-icon is-read-icon" :class="`item-icon--${item.type}`">
                    <el-icon><component :is="item.icon" /></el-icon>
                  </div>
                  <div class="item-content">
                    <div class="item-text is-read-text">{{ item.text }}</div>
                    <div class="item-time">{{ item.time }}</div>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无已读消息" :image-size="60" />
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-popover>
      <el-dropdown @command="handleCommand">
        <span class="app-header__user">
          <el-avatar :size="32" :src="userStore.profile?.avatar">
            <el-icon v-if="!userStore.profile?.avatar"><UserFilled /></el-icon>
          </el-avatar>
          <span class="app-header__username">{{ userStore.displayName }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人中心</el-dropdown-item>
            <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
.header-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-right: 4px;
}

.header-icon-wrapper:hover {
  background-color: #f1f5f9;
}

.header-icon-btn {
  font-size: 20px;
  color: #64748b;
  transition: color 0.3s ease;
}

.header-icon-wrapper:hover .header-icon-btn {
  color: #4f46e5;
}

/* Notification Panel */
.notification-panel {
  padding: 4px;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 14px;
  color: #0f172a;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
  margin-bottom: 8px;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 280px;
  overflow-y: auto;
}

.notification-item {
  display: flex;
  gap: 12px;
  padding: 10px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.notification-item:hover {
  background-color: #f8fafc;
}

.item-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-size: 16px;
  flex-shrink: 0;
}

.item-icon--warning {
  background-color: #fffbeb;
  color: #f59e0b;
}

.item-icon--danger {
  background-color: #fef2f2;
  color: #ef4444;
}

.item-icon--info {
  background-color: #eff6ff;
  color: #3b82f6;
}

.item-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.item-text {
  font-size: 13px;
  color: #334155;
  line-height: 1.4;
  font-weight: 500;
  text-align: left;
}

.item-time {
  font-size: 11px;
  color: #94a3b8;
  text-align: left;
}

.is-read-text {
  color: #94a3b8 !important;
  font-weight: 400 !important;
}

.is-read-icon {
  opacity: 0.6;
}

:deep(.notification-tabs .el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: #f1f5f9;
}

:deep(.notification-tabs .el-tabs__item) {
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  padding: 0 16px;
}

:deep(.notification-tabs .el-tabs__item.is-active) {
  color: #4f46e5;
  font-weight: 600;
}

:deep(.notification-tabs .el-tabs__active-bar) {
  background-color: #4f46e5;
  height: 2px;
}
</style>

<style>
.notification-popover {
  padding: 12px !important;
  border-radius: 12px !important;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08) !important;
  border: 1px solid #e2e8f0 !important;
}
</style>
