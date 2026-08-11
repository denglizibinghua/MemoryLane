import api from './index'

export interface Reminder {
  id: number
  title: string
  remindAt: string
  eventTime: string | null
  status: 'pending' | 'confirmed' | 'triggered' | 'dismissed'
  sourceText: string | null
  createdAt: string
  contact: {
    id: number
    name: string
  }
  memory: {
    id: number
  } | null
}

export interface DueResponse {
  triggered: Reminder[]
  pendingCount: number
  confirmedCount: number
}

export interface CreateReminderRequest {
  contactId: number
  title: string
  eventTime: string   // ISO 8601
  remindAt?: string   // optional, defaults to eventTime - 30min
}

export async function fetchReminders(params?: {
  contactId?: number
  status?: string
}): Promise<Reminder[]> {
  const res = await api.get('/reminders', { params })
  return res.data
}

export async function fetchDueReminders(): Promise<DueResponse> {
  const res = await api.get('/reminders/due')
  return res.data
}

export async function confirmReminder(id: number): Promise<Reminder> {
  const res = await api.post(`/reminders/${id}/confirm`)
  return res.data
}

export async function dismissReminder(id: number): Promise<Reminder> {
  const res = await api.post(`/reminders/${id}/dismiss`)
  return res.data
}

export async function createReminder(req: CreateReminderRequest): Promise<Reminder> {
  const res = await api.post('/reminders', req)
  return res.data
}

export async function updateReminder(id: number, req: CreateReminderRequest): Promise<Reminder> {
  const res = await api.put(`/reminders/${id}`, req)
  return res.data
}
