/**
 * Unified el-select / el-tree-select toggle:
 * click once to expand, click again on the same control to collapse.
 */
function getSelectWrapper(target: EventTarget | null): HTMLElement | null {
  if (!(target instanceof HTMLElement)) return null
  return target.closest('.el-select__wrapper') as HTMLElement | null
}

function isSelectOpen(wrapper: HTMLElement): boolean {
  if (wrapper.getAttribute('aria-expanded') === 'true') return true
  return wrapper.classList.contains('is-focused')
}

function closeSelect(wrapper: HTMLElement) {
  const input = wrapper.querySelector('input') as HTMLInputElement | null
  input?.blur()

  const root = wrapper.closest('.el-select') as HTMLElement | null
  const vm = (root as any)?.__vueParentComponent
  const select = vm?.exposed ?? vm?.proxy
  if (typeof select?.blur === 'function') {
    select.blur()
  }

  ;(document.activeElement as HTMLElement | null)?.blur()
}

export function installSelectToggle() {
  if (typeof document === 'undefined') return

  let pendingCloseWrapper: HTMLElement | null = null

  document.addEventListener(
    'mousedown',
    (event: MouseEvent) => {
      const target = event.target as HTMLElement | null
      if (!target) return

      // Option clicks are handled by Element Plus — do not intercept.
      if (target.closest('.el-select-dropdown, .el-tree, .el-cascader-panel')) return

      const wrapper = getSelectWrapper(target)
      if (!wrapper || !isSelectOpen(wrapper)) return

      pendingCloseWrapper = wrapper
      event.preventDefault()
      event.stopImmediatePropagation()
      closeSelect(wrapper)
    },
    true
  )

  // Filterable selects reopen on the trailing click — suppress it after manual close.
  document.addEventListener(
    'click',
    (event: MouseEvent) => {
      if (!pendingCloseWrapper) return

      const wrapper = getSelectWrapper(event.target)
      if (wrapper === pendingCloseWrapper) {
        event.preventDefault()
        event.stopImmediatePropagation()
      }
      pendingCloseWrapper = null
    },
    true
  )
}
