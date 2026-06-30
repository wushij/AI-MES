import request from './request'
import { normalizeList } from '@/utils/normalizeList'
import type { Team } from '@/types'

export interface TeamSavePayload {
  teamCode?: string
  teamName: string
  leaderId?: number | null
  memberCount?: number
  lineName?: string
}

export function getTeams() {
  return request.get<Team[]>('/teams').then((res) => normalizeList<Team>(res.data))
}

export function getTeamDetail(id: number | string) {
  return request.get<Team>(`/teams/${id}`).then((res) => res.data)
}

export function createTeam(payload: TeamSavePayload) {
  return request.post<Team>('/teams', payload).then((res) => res.data)
}

export function updateTeam(id: number | string, payload: TeamSavePayload) {
  return request.put<Team>(`/teams/${id}`, payload).then((res) => res.data)
}

export function deleteTeam(id: number | string) {
  return request.delete(`/teams/${id}`).then((res) => res.data)
}
