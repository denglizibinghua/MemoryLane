import api from './index'

export interface TrendPoint {
  bucket: string
  msgCount: number
  selfCount: number
}

export interface ContactStats {
  contactId: number
  contactName: string
  totalMessages: number
  selfCount: number
  selfRatio: number
  firstMsgAt: string | null
  lastMsgAt: string | null
}

export async function fetchTrends(contactId: number, granularity: string = 'week'): Promise<TrendPoint[]> {
  const res = await api.get(`/analytics/contact/${contactId}/trends`, { params: { granularity } })
  return res.data
}

export async function fetchOverview(): Promise<ContactStats[]> {
  const res = await api.get('/analytics/overview')
  return res.data
}
