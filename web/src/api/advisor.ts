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

// ── Conversation save ────────────────────────────────────────────

export interface ConversationMessage {
  speaker: string   // "self" | contact name
  content: string
  rawTime?: string  // ISO 8601, defaults to now server-side
}

export interface SaveConversationRequest {
  contactId: number
  messages: ConversationMessage[]
}

export interface ConversationSaveResult {
  contactId: number
  contactName: string
  messageCount: number
  messageIds: number[]
}

export interface ConversationSaveResponse {
  taskId: string
  stats: {
    newMessages: number
    duplicates: number
    memoriesExtracted: number
  }
  contacts: ConversationSaveResult[]
}

export async function saveConversation(payload: SaveConversationRequest): Promise<ConversationSaveResponse> {
  const res = await api.post('/import/conversation', payload)
  return res.data
}
