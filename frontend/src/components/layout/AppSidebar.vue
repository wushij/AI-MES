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
  { path: '/dashboard', label: '首页驾驶舱', icon: HomeFilled, roles: ['admin', 'supervisor', 'worker'] },
  { path: '/plans', label: '生产计划', icon: Calendar, roles: ['admin', 'supervisor'] },
  { path: '/work-orders', label: '工单管理', icon: List, roles: ['admin', 'supervisor', 'worker'] },
  { path: '/process', label: '工序进度', icon: Operation, roles: ['admin', 'worker'] },
  { path: '/teams', label: '班组管理', icon: UserFilled, roles: ['admin', 'supervisor'] },
  { path: '/exceptions', label: '异常管理', icon: Warning, roles: ['admin', 'supervisor', 'worker'] },
  { path: '/materials', label: '物料预警', icon: Box, roles: ['admin', 'supervisor'] },
  { path: '/ai-chat', label: '智能客服', icon: ChatDotRound, roles: ['admin', 'supervisor', 'worker'] },
  { path: '/ai-scheduling', label: '智能排产', icon: TrendCharts, roles: ['admin', 'supervisor'] },
  { path: '/admin/users', label: '系统管理', icon: Setting, roles: ['admin'] }
]

const visibleNavItems = computed(() => navItems.filter((item) => userStore.canAccess(item.roles)))

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
