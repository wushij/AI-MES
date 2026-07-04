import request from './request'

export interface InspectionPlan {
  id: number | string
  operationId: number | string
  itemName: string
  standard?: string
  minValue?: string
  maxValue?: string
  unit?: string
  sortNo?: number
}

export interface InspectionItemPayload {
  planId?: number | string
  itemName: string
  measuredValue?: string
  result?: string
  remark?: string
}

export function getInspectionPlansByProcess(workOrderId: number | string, processName: string) {
  return request
    .get<InspectionPlan[]>('/quality/inspection-plans/by-process', {
      params: { workOrderId, processName }
    })
    .then((res) => res.data)
}

export function submitInspectionRecords(payload: {
  workOrderId: number | string
  processName: string
  items: InspectionItemPayload[]
}) {
  return request
    .post<{ records: unknown[]; hasFailure: boolean; exceptionId?: number | string }>(
      '/quality/inspection-records',
      payload
    )
    .then((res) => res.data)
}
