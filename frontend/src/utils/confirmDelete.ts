import { ElMessageBox } from 'element-plus'

export type ConfirmDeleteOptions = {
  message: string
  title?: string
  confirmButtonText?: string
  cancelButtonText?: string
}

/** Returns true when user confirms; false when cancelled or closed. */
export async function confirmDelete(messageOrOptions: string | ConfirmDeleteOptions): Promise<boolean> {
  const options: ConfirmDeleteOptions =
    typeof messageOrOptions === 'string' ? { message: messageOrOptions } : messageOrOptions

  try {
    await ElMessageBox.confirm(options.message, options.title ?? '删除确认', {
      type: 'warning',
      confirmButtonText: options.confirmButtonText ?? '删除',
      cancelButtonText: options.cancelButtonText ?? '取消',
      distinguishCancelAndClose: true
    })
    return true
  } catch {
    return false
  }
}
