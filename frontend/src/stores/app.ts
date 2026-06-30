import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getWorkshopSummary } from '@/api/dashboard'
import {
  applyCustomTheme,
  applyThemePreset,
  findPresetIdByPrimary,
  getThemePreset,
  initThemeFromStorage,
  saveThemeSelection
} from '@/utils/theme'
import type { ThemePresetId, WorkshopSummary } from '@/types'

const DEFAULT_WORKSHOP_SUMMARY: WorkshopSummary = {
  shiftName: '白班',
  activeLines: 0,
  onTimeRate: 0,
  todayOutput: 0
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const pageLoading = ref(false)
  const themePreset = ref<ThemePresetId>('slate')
  const themeColor = ref(initThemeFromStorage())
  const workshopSummary = ref<WorkshopSummary>(DEFAULT_WORKSHOP_SUMMARY)

  const activeThemePresetId = computed(() => findPresetIdByPrimary(themeColor.value) ?? 'custom')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setWorkshopSummary(summary: WorkshopSummary) {
    workshopSummary.value = summary
  }

  function selectThemePreset(id: ThemePresetId) {
    themePreset.value = id
    applyThemePreset(id)
    themeColor.value = getThemePreset(id).primary
    saveThemeSelection(id)
  }

  function changeThemeColor(color: string | null) {
    if (!color) return
    const presetId = findPresetIdByPrimary(color)
    if (presetId) {
      selectThemePreset(presetId)
      return
    }
    themeColor.value = applyCustomTheme(color)
    saveThemeSelection('custom', color)
  }

  async function loadWorkshopSummary() {
    try {
      const data = await getWorkshopSummary()
      setWorkshopSummary({
        shiftName: String(data.lineName ?? '装配产线'),
        activeLines: Number(data.activeLines ?? 0),
        onTimeRate: Number(data.onTimeRate ?? data.completionRate ?? 0),
        todayOutput: Number(data.todayOutput ?? 0)
      })
    } catch {
      /* keep defaults */
    }
  }

  return {
    sidebarCollapsed,
    pageLoading,
    themePreset,
    themeColor,
    activeThemePresetId,
    workshopSummary,
    toggleSidebar,
    selectThemePreset,
    changeThemeColor,
    setWorkshopSummary,
    loadWorkshopSummary
  }
})
