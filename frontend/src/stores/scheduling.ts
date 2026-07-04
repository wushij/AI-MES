import { computed, reactive, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { USER_STORAGE_KEY } from '@/api/request'
import type { TeamLoadRow } from '@/utils/schedulingHelpers'

export interface SchedulingForm {
  planDate: string
  selectedWorkOrderId: string | number | null
  materialConstraint: boolean
  deviceConstraint: boolean
  teamConstraint: boolean
}

export interface SchedulingResult {
  summary?: string
  priorities: Array<{ rank: number; workOrderCode: string; priorityLabel: string; reason: string }>
  bottlenecks: Array<{ processName: string; loadRate: number; suggestion: string }>
  dispatches: Array<{ workOrderCode: string; teamName: string; startTime: string; hours: string }>
}

export interface SchedulingContextWorkOrder {
  id: number | string
  orderNo: string
  productName?: string
  processName?: string
  status?: string
  progress?: number
  teamName?: string | null
  priorityLabel?: string
  overdue?: boolean
  deadlineLabel?: string
  exceptionCount?: number
  materialRisk?: string
}

export interface SchedulingContextException {
  id: number | string
  workOrderNo?: string
  eventType?: string
  description?: string
  deviceId?: number | string
  deviceCode?: string
  deviceName?: string
}

export interface SchedulingContextDevice {
  id: number | string
  deviceCode: string
  deviceName: string
  lineName?: string
  status?: string
  statusLabel?: string
  loadRate?: number
  openExceptionCount?: number
  available?: boolean
}

export interface SchedulingMaterialAlert {
  id: number | string
  materialName: string
  stockQty: number
  safetyStock: number
  unit?: string
}

export interface SchedulingContext {
  workOrders: SchedulingContextWorkOrder[]
  materialAlerts: SchedulingMaterialAlert[]
  exceptions: SchedulingContextException[]
  teams: TeamLoadRow[]
  devices?: SchedulingContextDevice[]
  kpi: Record<string, number>
}

interface SchedulingSnapshot {
  form: SchedulingForm
  activePreset: string
  result: SchedulingResult | null
  resultMode: 'live' | 'mock' | ''
  resultHint: string
  resultSummary: string
  appliedConstraints: {
    materialAvailability: boolean
    deviceLoad: boolean
    teamHours: boolean
  }
  schedulingContext: SchedulingContext | null
  generatedAt: string | null
}

const STORAGE_PREFIX = 'ai_mes_scheduling'

function currentDate() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function readCurrentUserId(): string {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY)
    if (!raw) return 'guest'
    const profile = JSON.parse(raw) as { id?: string | number }
    return String(profile?.id ?? 'guest')
  } catch {
    return 'guest'
  }
}

function storageKey() {
  return `${STORAGE_PREFIX}_${readCurrentUserId()}`
}

function createDefaultForm(): SchedulingForm {
  return {
    planDate: currentDate(),
    selectedWorkOrderId: null,
    materialConstraint: true,
    deviceConstraint: true,
    teamConstraint: true
  }
}

function resolveSelectedWorkOrderId(formSnapshot?: Partial<SchedulingForm> & { selectedWorkOrderIds?: Array<string | number> }) {
  if (formSnapshot?.selectedWorkOrderId != null && formSnapshot.selectedWorkOrderId !== '') {
    return formSnapshot.selectedWorkOrderId
  }
  const legacy = formSnapshot?.selectedWorkOrderIds
  if (Array.isArray(legacy) && legacy.length) {
    return legacy[0]
  }
  return null
}

export const useSchedulingStore = defineStore('scheduling', () => {
  const hydrated = ref(false)
  const activePreset = ref('')
  const form = reactive<SchedulingForm>(createDefaultForm())
  const result = ref<SchedulingResult | null>(null)
  const resultMode = ref<'live' | 'mock' | ''>('')
  const resultHint = ref('')
  const resultSummary = ref('')
  const appliedConstraints = ref({
    materialAvailability: true,
    deviceLoad: true,
    teamHours: true
  })
  const schedulingContext = ref<SchedulingContext | null>(null)
  const generatedAt = ref<string | null>(null)

  const hasResult = computed(() => result.value != null)

  const generatedAtLabel = computed(() => {
    if (!generatedAt.value) return ''
    const date = new Date(generatedAt.value)
    if (Number.isNaN(date.getTime())) return ''
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  })

  let persisting = false

  function buildSnapshot(): SchedulingSnapshot {
    return {
      form: {
        planDate: form.planDate,
        selectedWorkOrderId: form.selectedWorkOrderId,
        materialConstraint: form.materialConstraint,
        deviceConstraint: form.deviceConstraint,
        teamConstraint: form.teamConstraint
      },
      activePreset: activePreset.value,
      result: result.value,
      resultMode: resultMode.value,
      resultHint: resultHint.value,
      resultSummary: resultSummary.value,
      appliedConstraints: { ...appliedConstraints.value },
      schedulingContext: schedulingContext.value,
      generatedAt: generatedAt.value
    }
  }

  function applySnapshot(snapshot: SchedulingSnapshot) {
    form.planDate = snapshot.form?.planDate ?? currentDate()
    form.selectedWorkOrderId = resolveSelectedWorkOrderId(snapshot.form)
    form.materialConstraint = snapshot.form?.materialConstraint ?? true
    form.deviceConstraint = snapshot.form?.deviceConstraint ?? true
    form.teamConstraint = snapshot.form?.teamConstraint ?? true
    activePreset.value = snapshot.activePreset ?? ''
    result.value = snapshot.result ?? null
    resultMode.value = snapshot.resultMode ?? ''
    resultHint.value = snapshot.resultHint ?? ''
    resultSummary.value = snapshot.resultSummary ?? ''
    appliedConstraints.value = {
      materialAvailability: snapshot.appliedConstraints?.materialAvailability !== false,
      deviceLoad: snapshot.appliedConstraints?.deviceLoad !== false,
      teamHours: snapshot.appliedConstraints?.teamHours !== false
    }
    schedulingContext.value = snapshot.schedulingContext ?? null
    generatedAt.value = snapshot.generatedAt ?? null
  }

  function persistSnapshot() {
    if (!hydrated.value || persisting) return
    try {
      sessionStorage.setItem(storageKey(), JSON.stringify(buildSnapshot()))
    } catch (error) {
      console.warn('[scheduling] persist snapshot failed', error)
    }
  }

  function hydrate() {
    if (hydrated.value) return
    hydrated.value = true
    persisting = true
    try {
      const raw = sessionStorage.getItem(storageKey())
      if (!raw) return
      const snapshot = JSON.parse(raw) as SchedulingSnapshot
      applySnapshot(snapshot)
    } catch (error) {
      console.warn('[scheduling] hydrate snapshot failed', error)
    } finally {
      persisting = false
    }
  }

  function setSchedulingResult(payload: {
    result: SchedulingResult
    resultMode: 'live' | 'mock'
    resultHint?: string
    resultSummary?: string
    appliedConstraints?: {
      materialAvailability: boolean
      deviceLoad: boolean
      teamHours: boolean
    }
  }) {
    result.value = payload.result
    resultMode.value = payload.resultMode
    resultHint.value = payload.resultHint ?? ''
    resultSummary.value = payload.resultSummary ?? ''
    if (payload.appliedConstraints) {
      appliedConstraints.value = { ...payload.appliedConstraints }
    }
    generatedAt.value = new Date().toISOString()
    persistSnapshot()
  }

  function clearSchedulingResult() {
    result.value = null
    resultMode.value = ''
    resultHint.value = ''
    resultSummary.value = ''
    generatedAt.value = null
    persistSnapshot()
  }

  function resetForm() {
    Object.assign(form, createDefaultForm())
    activePreset.value = ''
    clearSchedulingResult()
    schedulingContext.value = null
    persistSnapshot()
  }

  watch(
    () => buildSnapshot(),
    () => persistSnapshot(),
    { deep: true }
  )

  return {
    hydrated,
    activePreset,
    form,
    result,
    resultMode,
    resultHint,
    resultSummary,
    appliedConstraints,
    schedulingContext,
    generatedAt,
    hasResult,
    generatedAtLabel,
    hydrate,
    persistSnapshot,
    setSchedulingResult,
    clearSchedulingResult,
    resetForm
  }
})
