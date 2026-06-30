import type { Router } from 'vue-router'
import { TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from '@/api/request'

let router: Router | null = null
let handlingUnauthorized = false

export function bindAuthRouter(instance: Router) {
  router = instance
}

export function clearAuthStorage() {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
  localStorage.removeItem(USER_STORAGE_KEY)
}

export async function handleUnauthorized(redirect = true) {
  if (handlingUnauthorized) return
  handlingUnauthorized = true
  try {
    clearAuthStorage()
    const { useUserStore } = await import('@/stores/user')
    const userStore = useUserStore()
    userStore.clearSession()
    if (!redirect || !router) return
    if (router.currentRoute.value.path === '/login') return
    const redirectTo = encodeURIComponent(router.currentRoute.value.fullPath)
    await router.replace(`/login?redirect=${redirectTo}`)
  } finally {
    handlingUnauthorized = false
  }
}
