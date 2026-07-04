import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/components/layout/AppLayout.vue'
import { useUserStore } from '@/stores/user'
import { bindAuthRouter } from '@/utils/session'
import type { AppRouteMeta } from '@/types'

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
          meta: { title: '首页驾驶舱' }
        },
        {
          path: 'plans',
          name: 'plans',
          component: () => import('@/views/Plans.vue'),
          meta: { title: '生产计划', permission: '生产计划' }
        },
        {
          path: 'work-orders',
          name: 'work-orders',
          component: () => import('@/views/WorkOrders.vue'),
          meta: { title: '工单管理', permission: ['工单管理', '工单反馈'] }
        },
        {
          path: 'work-orders/:id',
          name: 'work-order-detail',
          component: () => import('@/views/WorkOrderDetail.vue'),
          meta: { title: '工单详情', permission: ['工单管理', '工单反馈'] }
        },
        {
          path: 'process',
          name: 'process',
          component: () => import('@/views/Process.vue'),
          meta: { title: '工序进度', permission: '工序进度' }
        },
        {
          path: 'teams',
          name: 'teams',
          component: () => import('@/views/Teams.vue'),
          meta: { title: '班组管理', permission: '班组' }
        },
        {
          path: 'devices',
          name: 'devices',
          component: () => import('@/views/Devices.vue'),
          meta: { title: '设备管理', permission: '设备' }
        },
        {
          path: 'devices/:id',
          name: 'device-detail',
          component: () => import('@/views/DeviceDetail.vue'),
          meta: { title: '设备详情', permission: '设备' }
        },
        {
          path: 'exceptions',
          name: 'exceptions',
          component: () => import('@/views/Exceptions.vue'),
          meta: { title: '异常管理', permission: '异常上报' }
        },
        {
          path: 'materials',
          name: 'materials',
          component: () => import('@/views/Materials.vue'),
          meta: { title: '物料预警', permission: '物料' }
        },
        {
          path: 'ai-chat',
          name: 'ai-chat',
          component: () => import('@/views/AiChat.vue'),
          meta: { title: 'AI 智能客服', permission: 'AI 客服' }
        },
        {
          path: 'ai-scheduling',
          name: 'ai-scheduling',
          component: () => import('@/views/AiScheduling.vue'),
          meta: { title: 'AI 智能排产', permission: '排产' }
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('@/views/admin/Users.vue'),
          meta: { title: '系统管理', permission: '用户管理' }
        },
        {
          path: 'admin/roles',
          name: 'admin-roles',
          component: () => import('@/views/admin/Roles.vue'),
          meta: { title: '系统管理', permission: '角色管理' }
        },
        {
          path: 'admin/coze',
          name: 'admin-coze',
          component: () => import('@/views/admin/Coze.vue'),
          meta: { title: '系统管理', permission: 'Coze 配置' }
        },
        {
          path: 'process-management',
          name: 'process-management',
          component: () => import('@/views/ProcessManagement.vue'),
          meta: { title: '工艺管理', permission: '工艺管理' }
        },
        {
          path: 'process-management/:id',
          name: 'process-route-detail',
          component: () => import('@/views/ProcessRouteDetail.vue'),
          meta: { title: '工艺详情', permission: '工艺管理' }
        },
        {
          path: 'admin/routing',
          redirect: '/process-management'
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/Profile.vue'),
          meta: { title: '个人中心' }
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

  if (to.path === '/login') {
    if (userStore.isAuthenticated) {
      return '/dashboard'
    }
    return true
  }

  if (to.meta.requiresAuth && !userStore.isAuthenticated) {
    const redirect = encodeURIComponent(to.fullPath)
    return `/login?redirect=${redirect}`
  }

  if (to.meta.permission && !userStore.canAccessPermission(to.meta.permission)) {
    if (!userStore.isAuthenticated) {
      const redirect = encodeURIComponent(to.fullPath)
      return `/login?redirect=${redirect}`
    }
    return '/dashboard'
  }

  return true
})

bindAuthRouter(router)

export default router
