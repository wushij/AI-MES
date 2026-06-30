import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { handleUnauthorized } from '@/utils/session'

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
}

function logApiIssue(level: 'error' | 'warn', message: string, detail?: unknown) {
  const logger = level === 'error' ? console.error : console.warn
  if (detail !== undefined) {
    logger(`[API] ${message}`, detail)
    return
  }
  logger(`[API] ${message}`)
}

function createApiError(message: string, apiCode?: number, httpStatus?: number) {
  const error = new Error(message) as ApiRequestError
  error.apiCode = apiCode
  error.httpStatus = httpStatus
  return error
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
        if ((result.code === 401 || result.code === 403) && !cfg.skipUnauthorizedRedirect) {
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
    const message = error.response?.data?.message || error.message || '网络异常，请重试'

    if (error.response?.status === 401 && !cfg?.skipUnauthorizedRedirect) {
      logApiIssue('warn', `未授权 (401): ${message}`, error)
      void handleUnauthorized()
    } else if (error.response?.status === 403 && !cfg?.skipUnauthorizedRedirect) {
      logApiIssue('warn', `无权限 (403): ${message}`, error)
      void handleUnauthorized()
    } else if (!cfg?.skipErrorHandler) {
      logApiIssue('error', message, error)
      ElMessage.error(message)
    } else {
      logApiIssue('warn', message, error)
    }

    return Promise.reject(error)
  }
)

export default request
