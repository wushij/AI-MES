import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { handleUnauthorized } from '@/utils/session'

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipAuth?: boolean
    skipErrorHandler?: boolean
    skipUnauthorizedRedirect?: boolean
  }
}

export const TOKEN_STORAGE_KEY = 'ai_mes_access_token'
export const USER_STORAGE_KEY = 'ai_mes_user'

export interface ExtendedRequestConfig extends AxiosRequestConfig {
  skipAuth?: boolean
  skipErrorHandler?: boolean
  skipUnauthorizedRedirect?: boolean
}

export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

export interface ApiRequestError extends Error {
  apiCode?: number
  httpStatus?: number
  canceled?: boolean
}

function logApiIssue(level: 'error' | 'warn', message: string, detail?: unknown) {
  const logger = level === 'error' ? console.error : console.warn
  if (detail !== undefined) {
    logger(`[API] ${message}`, detail)
    return
  }
  logger(`[API] ${message}`)
}

export function createApiError(message: string, apiCode?: number, httpStatus?: number) {
  const error = new Error(message) as ApiRequestError
  error.apiCode = apiCode
  error.httpStatus = httpStatus
  return error
}

export function createAbortError() {
  const error = createApiError('已取消请求')
  error.name = 'CanceledError'
  error.canceled = true
  return error
}

/** 从 ApiRequestError 或 AxiosError 中解析业务/HTTP 状态码 */
export function resolveErrorStatus(error: unknown): number | undefined {
  if (!error || typeof error !== 'object') return undefined
  const err = error as ApiRequestError & AxiosError<ApiResult>
  if (typeof err.apiCode === 'number') return err.apiCode
  if (typeof err.httpStatus === 'number') return err.httpStatus
  if (typeof err.response?.status === 'number') return err.response.status
  if (typeof err.response?.data?.code === 'number') return err.response.data.code
  return undefined
}

export function isNetworkFailure(error: unknown) {
  if (!error || typeof error !== 'object') return false
  const err = error as AxiosError
  if (!err.response) return true
  return err.code === 'ECONNABORTED' || err.code === 'ERR_NETWORK'
}

export function isAbortError(error: unknown) {
  if (!error || typeof error !== 'object') return false
  const err = error as ApiRequestError & { code?: string }
  if (err.canceled) return true
  return err.name === 'CanceledError' || err.name === 'AbortError' || err.code === 'ERR_CANCELED'
}

export function normalizeRequestError(error: AxiosError<ApiResult>): ApiRequestError {
  const apiCode = error.response?.data?.code
  const httpStatus = error.response?.status
  const message = error.response?.data?.message || error.message || '网络异常，请重试'
  return createApiError(message, apiCode, httpStatus)
}

function isUnauthorizedStatus(status?: number) {
  return status === 401 || status === 403
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000
})

request.interceptors.request.use((config) => {
  const nextConfig = config as ExtendedRequestConfig
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)

  if (token && !nextConfig.skipAuth) {
    config.headers = (config.headers ?? {}) as typeof config.headers
    ;(config.headers as Record<string, string>).Authorization = `Bearer ${token}`
  }

  return config
})

request.interceptors.response.use(
  (response) => {
    const cfg = response.config as ExtendedRequestConfig
    const body = response.data as ApiResult | unknown
    if (body && typeof body === 'object' && 'code' in body && 'data' in body) {
      const result = body as ApiResult
      if (result.code !== 200) {
        if (isUnauthorizedStatus(result.code) && !cfg.skipUnauthorizedRedirect) {
          logApiIssue('warn', `未授权 (${result.code}): ${result.message}`)
          void handleUnauthorized()
        } else if (cfg.skipErrorHandler) {
          logApiIssue('warn', `请求失败 (${result.code}): ${result.message}`)
        } else {
          logApiIssue('error', `请求失败 (${result.code}): ${result.message}`)
        }
        return Promise.reject(createApiError(result.message || '请求失败', result.code, response.status))
      }
      response.data = result.data
    }
    if (typeof body === 'string' && body.trimStart().startsWith('<')) {
      return Promise.reject(createApiError('服务返回异常页面，请确认后端已启动', undefined, response.status))
    }
    return response
  },
  (error: AxiosError<ApiResult>) => {
    const cfg = error.config as ExtendedRequestConfig | undefined

    if (isAbortError(error)) {
      return Promise.reject(createAbortError())
    }

    const normalized = normalizeRequestError(error)
    const status = resolveErrorStatus(normalized)

    if (isUnauthorizedStatus(status) && !cfg?.skipUnauthorizedRedirect) {
      logApiIssue('warn', `未授权 (${status}): ${normalized.message}`, error)
      void handleUnauthorized()
    } else if (!cfg?.skipErrorHandler) {
      logApiIssue('error', normalized.message, error)
      ElMessage.error(normalized.message)
    } else {
      logApiIssue('warn', normalized.message, error)
    }

    return Promise.reject(normalized)
  }
)

export default request
