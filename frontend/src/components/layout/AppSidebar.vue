<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  HomeFilled,
  Calendar,
  List,
  Operation,
  UserFilled,
  Warning,
  Box,
  ChatDotRound,
  TrendCharts,
  Setting
} from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import WorkshopSummaryCard from './WorkshopSummaryCard.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import type { NavItem } from '@/types'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const navItems: NavItem[] = [
  { path: '/dashboard', label: '首页驾驶舱', icon: HomeFilled },
  { path: '/plans', label: '生产计划', icon: Calendar, permission: '生产计划' },
  { path: '/work-orders', label: '工单管理', icon: List, permission: ['工单管理', '工单反馈'] },
  { path: '/process', label: '工序进度', icon: Operation, permission: '工序进度' },
  { path: '/teams', label: '班组管理', icon: UserFilled, permission: '班组' },
  { path: '/exceptions', label: '异常管理', icon: Warning, permission: '异常上报' },
  { path: '/materials', label: '物料预警', icon: Box, permission: '物料' },
  { path: '/ai-chat', label: '智能客服', icon: ChatDotRound, permission: 'AI 客服' },
  { path: '/ai-scheduling', label: '智能排产', icon: TrendCharts, permission: '排产' },
  {
    path: '/admin/users',
    label: '系统管理',
    icon: Setting,
    permission: ['用户管理', '角色管理', 'Coze 配置', '系统配置']
  }
]

const visibleNavItems = computed(() =>
  navItems.filter((item) => userStore.canAccessPermission(item.permission))
)

const activeMenu = computed(() => {
  if (route.path.startsWith('/admin')) return '/admin/users'
  if (route.path.startsWith('/work-orders/')) return '/work-orders'
  return route.path
})
</script>

<template>
  <aside class="layout-sidebar" :class="{ 'is-collapse': appStore.sidebarCollapsed }">
    <div class="cd-sidebar-logo">
      <BrandMark :size="28" class="cd-logo-icon" />
      <span v-show="!appStore.sidebarCollapsed" class="cd-logo-text">AI-MES</span>
    </div>

    <div class="menu-wrapper">
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="true"
        router
      >
        <el-menu-item
          v-for="item in visibleNavItems"
          :key="item.path"
          :index="item.path"
          class="menu-item"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title><span>{{ item.label }}</span></template>
        </el-menu-item>
      </el-menu>
    </div>

    <div class="cd-sidebar-footer">
      <WorkshopSummaryCard />
    </div>
  </aside>
</template>
