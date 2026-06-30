import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/components/layout/AppLayout.vue'
import { useUserStore } from '@/stores/user'
import { bindAuthRouter } from '@/utils/session'
import type { AppRouteMeta, UserRole } from '@/types'

declare module 'vue-router' {
  interface RouteMeta extends AppRouteMeta { }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true, title: 'AI-MES' },
      children: [
        {
          path: '',
          redirect: '/dashboard'
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/Dashboard.vue'),
          meta: { title: '首页驾驶舱', roles: ['admin', 'supervisor', 'worker'] }
        },
        {
          path: 'plans',
          name: 'plans',
          component: () => import('@/views/Plans.vue'),
          meta: { title: '生产计划', roles: ['admin', 'supervisor'] }
        },
        {
          path: 'work-orders',
          name: 'work-orders',
          component: () => import('@/views/WorkOrders.vue'),
          meta: { title: '工单管理', roles: ['admin', 'supervisor', 'worker'] }
        },
        {
          path: 'work-orders/:id',
          name: 'work-order-detail',
          component: () => import('@/views/WorkOrderDetail.vue'),
          meta: { title: '工单详情', roles: ['admin', 'supervisor', 'worker'] }
        },
        {
          path: 'process',
          name: 'process',
          component: () => import('@/views/Process.vue'),
          meta: { title: '工序进度', roles: ['admin', 'worker'] }
        },
        {
          path: 'teams',
          name: 'teams',
          component: () => import('@/views/Teams.vue'),
          meta: { title: '班组管理', roles: ['admin', 'supervisor'] }
        },
        {
          path: 'exceptions',
          name: 'exceptions',
          component: () => import('@/views/Exceptions.vue'),
          meta: { title: '异常管理', roles: ['admin', 'supervisor', 'worker'] }
        },
        {
          path: 'materials',
          name: 'materials',
          component: () => import('@/views/Materials.vue'),
          meta: { title: '物料预警', roles: ['admin', 'supervisor'] }
        },
        {
          path: 'ai-chat',
          name: 'ai-chat',
          component: () => import('@/views/AiChat.vue'),
          meta: { title: 'AI 智能客服', roles: ['admin', 'supervisor', 'worker'] }
        },
        {
          path: 'ai-scheduling',
          name: 'ai-scheduling',
          component: () => import('@/views/AiScheduling.vue'),
          meta: { title: 'AI 智能排产', roles: ['admin', 'supervisor'] }
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('@/views/admin/Users.vue'),
          meta: { title: '系统管理', roles: ['admin'] }
        },
        {
          path: 'admin/roles',
          name: 'admin-roles',
          component: () => import('@/views/admin/Roles.vue'),
          meta: { title: '系统管理', roles: ['admin'] }
        },
        {
          path: 'admin/coze',
          name: 'admin-coze',
          component: () => import('@/views/admin/Coze.vue'),
          meta: { title: '系统管理', roles: ['admin'] }
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/Profile.vue'),
          meta: { title: '个人中心', roles: ['admin', 'supervisor', 'worker'] }
        }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  await userStore.hydrate()

  const title = to.meta.title ? `${to.meta.title} - AI-MES` : 'AI-MES'
  document.title = title

  if (to.path === '/login' && userStore.isAuthenticated) {
    return getHomePath(userStore.role)
  }

  if (to.meta.requiresAuth && !userStore.isAuthenticated) {
    const redirect = encodeURIComponent(to.fullPath)
    return `/login?redirect=${redirect}`
  }

  if (to.meta.roles?.length && !userStore.canAccess(to.meta.roles)) {
    return getHomePath(userStore.role)
  }

  return true
})

function getHomePath(role: UserRole | '') {
  return '/dashboard'
}

bindAuthRouter(router)

export default router
