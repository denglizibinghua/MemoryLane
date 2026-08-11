import api from './index'

export interface AdvisorReply {
  style: string
  content: string
  reason: string
}

export interface NewTopic {
  content: string
  reason: string
}

export interface AdvisorSuggestResponse {
  replies: AdvisorReply[]
  newTopics: NewTopic[]
}

export interface AdvisorSuggestRequest {
  contactId: number
  lastMessage: string
  recentContext?: string[]
}

export async function suggestAdvisor(payload: AdvisorSuggestRequest): Promise<AdvisorSuggestResponse> {
  const res = await api.post('/advisor/suggest', payload)
  return res.data
}
