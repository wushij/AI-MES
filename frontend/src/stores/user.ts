import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth'
import { isNetworkFailure, resolveErrorStatus, TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from '@/api/request'
import { clearAuthStorage } from '@/utils/session'
import { matchPermission } from '@/utils/permissions'
import type { LoginPayload, LoginResponse, UserProfile, UserRole } from '@/types'
import { useAiChatStore } from './aiChat'
import { useChatStore } from './chat'
import { useNotificationStore } from './notifications'

const HYDRATE_MAX_ATTEMPTS = 3
const HYDRATE_RETRY_DELAY_MS = 1000

function delay(ms: number) {
  return new Promise<void>((resolve) => {
    setTimeout(resolve, ms)
  })
}

function isUnauthorizedStatus(status?: number) {
  return status === 401 || status === 403
}

function hasValidProfile(profile: UserProfile | null | undefined): profile is UserProfile {
  return Boolean(profile?.role)
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_STORAGE_KEY) || '')
  const profile = ref<UserProfile | null>(readStoredProfile())
  const initialized = ref(false)
  const backendUnavailable = ref(false)
  const permissions = ref<string[]>(readStoredProfile()?.permissions ?? [])
  const fullAccess = ref(Boolean(readStoredProfile()?.fullAccess))

  const isAuthenticated = computed(() => Boolean(token.value && hasValidProfile(profile.value)))
  const role = computed<UserRole | ''>(() => profile.value?.role ?? '')
  const isAdmin = computed(() => role.value === 'admin' || fullAccess.value)
  const isSupervisor = computed(() => role.value === 'supervisor')
  const isWorker = computed(() => role.value === 'worker')
  const displayName = computed(() => profile.value?.realName || profile.value?.nickname || profile.value?.username || '未登录')

  function applyProfile(nextProfile: UserProfile) {
    profile.value = nextProfile
    permissions.value = nextProfile.permissions ?? []
    fullAccess.value = Boolean(nextProfile.fullAccess)
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(nextProfile))
  }

  function persistSession(payload: LoginResponse) {
    token.value = payload.token
    localStorage.setItem(TOKEN_STORAGE_KEY, payload.token)
    applyProfile(payload.user)
  }

  function resetChatStores() {
    useAiChatStore().reset()
    useChatStore().reset()
    useNotificationStore().reset()
  }

  function clearSession() {
    token.value = ''
    profile.value = null
    permissions.value = []
    fullAccess.value = false
    backendUnavailable.value = false
    clearAuthStorage()
    resetChatStores()
    initialized.value = true
  }

  async function login(payload: LoginPayload) {
    resetChatStores()
    const response = await loginApi(payload)
    persistSession(response)
    backendUnavailable.value = false
    initialized.value = true
    return response
  }

  async function hydrate() {
    if (initialized.value) return
    backendUnavailable.value = false

    if (!token.value) {
      profile.value = null
      initialized.value = true
      return
    }

    if (!hasValidProfile(profile.value)) {
      profile.value = readStoredProfile()
    }

    for (let attempt = 1; attempt <= HYDRATE_MAX_ATTEMPTS; attempt++) {
      try {
        const currentUser = await getCurrentUser()
        if (!hasValidProfile(currentUser)) {
          console.warn('[Auth] 用户信息不完整，将清除登录态')
          clearSession()
          return
        }
        applyProfile(currentUser)
        backendUnavailable.value = false
        initialized.value = true
        return
      } catch (error) {
        const status = resolveErrorStatus(error)

        if (isUnauthorizedStatus(status)) {
          console.warn('[Auth] 登录已失效，将跳转登录页', error)
          clearSession()
          return
        }

        const canRetry = isNetworkFailure(error) && attempt < HYDRATE_MAX_ATTEMPTS
        if (canRetry) {
          console.warn(`[Auth] 后端未就绪，${HYDRATE_RETRY_DELAY_MS}ms 后重试 (${attempt}/${HYDRATE_MAX_ATTEMPTS})`)
          await delay(HYDRATE_RETRY_DELAY_MS)
          continue
        }

        if (isNetworkFailure(error) && hasValidProfile(profile.value)) {
          console.error('[Auth] 无法校验登录状态，使用本地缓存并进入离线提示模式', error)
          permissions.value = profile.value.permissions ?? []
          fullAccess.value = Boolean(profile.value.fullAccess)
          backendUnavailable.value = true
          initialized.value = true
          return
        }

        console.warn('[Auth] 登录态无效，将清除并跳转登录页', error)
        clearSession()
        return
      }
    }
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      /* ignore logout network errors */
    } finally {
      clearSession()
      initialized.value = true
    }
  }

  function canAccess(roles?: UserRole[]) {
    if (!roles?.length) return true
    if (!hasValidProfile(profile.value)) return false
    return roles.includes(profile.value.role)
  }

  function canAccessPermission(required?: string | string[]) {
    return matchPermission(permissions.value, required, fullAccess.value)
  }

  function updateProfile(newProfile: UserProfile) {
    applyProfile(newProfile)
  }

  return {
    token,
    profile,
    permissions,
    fullAccess,
    initialized,
    backendUnavailable,
    isAuthenticated,
    role,
    isAdmin,
    isSupervisor,
    isWorker,
    displayName,
    login,
    hydrate,
    logout,
    canAccess,
    canAccessPermission,
    clearSession,
    updateProfile
  }
})

function readStoredProfile() {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY)
    const parsed = raw ? (JSON.parse(raw) as UserProfile) : null
    return hasValidProfile(parsed) ? parsed : null
  } catch {
    return null
  }
}
