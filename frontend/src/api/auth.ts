import request, { type ExtendedRequestConfig } from './request'
import type { CaptchaState, LoginPayload, LoginResponse, UserProfile } from '@/types'

const publicConfig: ExtendedRequestConfig = {
  skipAuth: true,
  skipErrorHandler: true
}

interface AuthPayload {
  token: string
  id: number
  username: string
  realName?: string
  avatar?: string
  role: UserProfile['role']
  teamId?: number
  teamName?: string
}

function toLoginResponse(data: AuthPayload): LoginResponse {
  return {
    token: data.token,
    user: {
      id: data.id,
      username: data.username,
      realName: data.realName,
      avatar: data.avatar,
      role: data.role,
      teamId: data.teamId,
      teamName: data.teamName
    }
  }
}

function toUserProfile(data: AuthPayload): UserProfile {
  return {
    id: data.id,
    username: data.username,
    realName: data.realName,
    avatar: data.avatar,
    role: data.role,
    teamId: data.teamId,
    teamName: data.teamName
  }
}

export function login(payload: LoginPayload) {
  return request
    .post<AuthPayload>('/auth/login', payload, publicConfig)
    .then((res) => toLoginResponse(res.data))
}

export function getCaptchaRequired() {
  return request
    .get<{ required: boolean }>('/auth/captcha/required', publicConfig)
    .then((res) => res.data)
}

export function getCaptcha() {
  return request
    .get<CaptchaState>(`/auth/captcha?_=${Date.now()}`, publicConfig)
    .then((res) => res.data)
}

export function getCurrentUser() {
  return request
    .get<AuthPayload>('/auth/info', {
      timeout: 8000,
      skipErrorHandler: true,
      skipUnauthorizedRedirect: true
    } as ExtendedRequestConfig)
    .then((res: any) => toUserProfile(res.data as any))
}

export function logout() {
  return request.post<void>('/auth/logout').then((res) => res.data)
}

export function updateUserProfile(payload: { realName: string; avatar?: string }) {
  return request.put<AuthPayload>('/auth/profile', payload).then((res) => toUserProfile(res.data))
}

export function changePassword(payload: { oldPassword: string; newPassword: string }) {
  return request.put<string>('/auth/password', payload).then((res) => res.data)
}
