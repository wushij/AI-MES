import type { Component } from 'vue'

export type UserRole = 'admin' | 'supervisor' | 'worker'
export type ThemePresetId = 'slate' | 'indigo' | 'teal' | 'emerald' | 'amber' | 'blush' | 'rose' | 'violet'
export type PlanStatus = 'draft' | 'released' | 'paused' | 'completed'
export type WorkOrderStatus = 'pending' | 'assigned' | 'producing' | 'exception' | 'done'
export type ExceptionStatus = 'open' | 'processing' | 'closed'
export type ExceptionSeverity = 'low' | 'medium' | 'high' | 'critical'
export type MaterialAlertLevel = 'normal' | 'warning' | 'critical'
export type ChatRole = 'user' | 'assistant' | 'system'

export interface ApiPage<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface ApiListParams {
  page?: number
  size?: number
  keyword?: string
  status?: string
  teamId?: number | string
}

export interface UserProfile {
  id: number | string
  username: string
  nickname?: string
  realName?: string
  role: UserRole
  avatar?: string
  teamId?: number | string
  teamName?: string
  status?: number
  createTime?: string
}

export interface LoginPayload {
  username: string
  password: string
  captchaId?: string
  captchaAnswer?: string
}

export interface LoginResponse {
  token: string
  refreshToken?: string
  expiresAt?: string
  user: UserProfile
}

export interface CaptchaState {
  id: string
  img: string
  required?: boolean
}

export interface DashboardStats {
  planCount: number
  activeWorkOrders: number
  exceptionCount: number
  materialAlerts: number
  completionRate: number
  todayOutput: number
}

export interface TeamProgress {
  teamId: number | string
  teamName: string
  progress: number
  completed: number
  pending: number
}

export interface AlertItem {
  id: number | string
  title: string
  source: string
  description?: string
  level: 'info' | 'warning' | 'danger' | 'success'
  createdAt: string
}

export interface DashboardTrendPoint {
  date: string
  output: number
  target: number
}

export interface WorkshopSummary {
  shiftName: string
  activeLines: number
  onTimeRate: number
  todayOutput: number
}

export interface Plan {
  id: number | string
  planNo: string
  productName: string
  planQty: number
  completedQty?: number
  planDate: string
  status: PlanStatus
  priority?: 'low' | 'medium' | 'high'
  lineName?: string
  remark?: string
}

export interface WorkOrder {
  id: number | string
  workOrderNo: string
  productName: string
  planId?: number | string
  teamId?: number | string
  teamName?: string
  processName: string
  progress: number
  status: WorkOrderStatus
  dueDate: string
  assigneeName?: string
  priority?: 'low' | 'medium' | 'high'
  lineName?: string
  exceptionCount?: number
}

export interface ProcessTask {
  id: number | string
  workOrderNo: string
  processName: string
  progress: number
  status: WorkOrderStatus
  dueDate: string
  priority: 'low' | 'medium' | 'high'
  teamName?: string
}

export interface Team {
  id: number | string
  teamCode: string
  teamName: string
  leaderId?: number | string
  leaderName?: string
  memberCount: number
  shiftName?: string
  activeWorkOrders?: number
  completionRate?: number
}

export interface ProductionException {
  id: number | string
  workOrderId?: number | string
  workOrderNo?: string
  type: string
  severity: ExceptionSeverity
  description: string
  status: ExceptionStatus
  processName?: string
  reporterName?: string
  handlerName?: string
  createdAt: string
  updatedAt?: string
}

export interface Material {
  id: number | string
  materialCode: string
  materialName: string
  stockQty: number
  safetyQty: number
  unit: string
  shortage: boolean
  alertLevel: MaterialAlertLevel
  supplierName?: string
  updatedAt?: string
}

export interface CozeChatMessage {
  id: string
  role: ChatRole
  content: string
  createdAt: string
  pending?: boolean
}

export interface CozeChatRequest {
  message: string
  sessionId?: string
  conversationId?: string
  context?: Record<string, unknown>
}

export interface CozeChatResponse {
  reply: string
  sessionId?: string
  conversationId?: string
  mode?: string
  contextOrders?: string[]
  suggestions?: string[]
}

export interface SchedulingSuggestion {
  workOrderId: number | string
  workOrderNo: string
  team: string
  process: string
  suggestedStart: string
  loadRate?: string
  rationale?: string
}

export interface CozeSchedulingResponse {
  summary: string
  bottlenecks: string[]
  suggestions: SchedulingSuggestion[]
}

export interface CozeConfig {
  botId: string
  apiUrl: string
  workflowId?: string
  welcomeMessage?: string
  knowledgeBase?: string
  enabled: boolean
  hasApiToken?: boolean
  apiTokenMasked?: string
  configured?: boolean
  tokenSource?: 'database' | 'env' | 'none' | string
  botSource?: 'database' | 'env' | 'none' | string
  updateTime?: string
}

export interface CozeConfigSavePayload {
  apiToken?: string
  botId: string
  apiUrl: string
  workflowId?: string
  welcomeMessage?: string
  knowledgeBase?: string
  enabled: boolean
}

export interface CozeHealthCheckItem {
  status: string
  message: string
  chatId?: string
  workflowId?: string | null
  mode?: string
  sampleWorkOrderNo?: string
  summary?: {
    priorities: number
    bottlenecks: number
    dispatches: number
  }
}

export interface CozeHealthResult {
  configured: boolean
  enabled: boolean
  apiUrl: string
  botId?: string | null
  tokenSource?: string
  status: string
  message: string
  chat?: CozeHealthCheckItem
  workflow?: CozeHealthCheckItem
}

export interface AppRouteMeta {
  title: string
  section?: string
  requiresAuth?: boolean
  roles?: UserRole[]
  breadcrumb?: string[]
}

export interface NavItem {
  path: string
  label: string
  icon: Component
  roles?: UserRole[]
}
