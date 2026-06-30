import request from './request'
import type { ProcessTask } from '@/types'

export function getProcessTasks() {
  return request.get<{
    pending: ProcessTask[]
    producing: ProcessTask[]
    doneToday: ProcessTask[]
  }>('/work-orders/process-board').then((res) => res.data)
}
