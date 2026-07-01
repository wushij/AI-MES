import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth'
import { TOKEN_STORAGE_KEY, USER_STORAGE_KEY, type ApiRequestError } from '@/api/request'
import { clearAuthStorage } from '@/utils/session'
import type { LoginPayload, LoginResponse, UserProfile, UserRole } from '@/types'
import { useAiChatStore } from './aiChat'
import { useChatStore } from './chat'
import { useNotificationStore } from './notifications'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_STORAGE_KEY) || '')
  const profile = ref<UserProfile | null>(readStoredProfile())
  const initialized = ref(false)
  const backendUnavailable = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value))
  const role = computed<UserRole | ''>(() => profile.value?.role ?? '')
  const isAdmin = computed(() => role.value === 'admin')
  const isSupervisor = computed(() => role.value === 'supervisor')
  const isWorker = computed(() => role.value === 'worker')
  const displayName = computed(() => profile.value?.realName || profile.value?.nickname || profile.value?.username || '未登录')

  function persistSession(payload: LoginResponse) {
    token.value = payload.token
    profile.value = payload.user
    localStorage.setItem(TOKEN_STORAGE_KEY, payload.token)
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(payload.user))
  }

  function resetChatStores() {
    useAiChatStore().reset()
    useChatStore().reset()
    useNotificationStore().reset()
  }

  function clearSession() {
    token.value = ''
    profile.value = null
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
      initialized.value = true
      return
    }
    try {
      profile.value = await getCurrentUser()
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(profile.value))
    } catch (error) {
      const status = (error as ApiRequestError)?.apiCode ?? (error as ApiRequestError)?.httpStatus
      if (status === 401 || status === 403) {
        console.warn('[Auth] 登录已失效，将跳转登录页', error)
        clearSession()
        return
      }
      console.error('[Auth] 校验登录状态失败，后端可能未就绪', error)
      profile.value = profile.value ?? readStoredProfile()
      backendUnavailable.value = true
    } finally {
      initialized.value = true
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
    return roles.includes(role.value as UserRole)
  }

  function updateProfile(newProfile: UserProfile) {
    profile.value = newProfile
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(newProfile))
  }

  return {
    token,
    profile,
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
    clearSession,
    updateProfile
  }
})

function readStoredProfile() {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY)
    return raw ? (JSON.parse(raw) as UserProfile) : null
  } catch {
    return null
  }
}
