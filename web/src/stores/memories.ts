import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api'

export interface Memory {
  id: number
  category: string
  content: string
  confidence: number
  sourceMsgIds: number[]
  validFrom: string
  validUntil: string | null
  createdAt: string
}

export interface SearchResult {
  id: number
  type: string
  content: string
  score: number
  category: string
  contactId: number
  contactName: string
}

export interface ContactResult {
  contactId: number
  contactName: string
  messageCount: number
}

export interface ImportResult {
  taskId: string
  stats: {
    newMessages: number
    duplicates: number
    memoriesExtracted: number
  }
  contacts: ContactResult[]
}

export interface PreviewResult {
  platform: string
  speakers: string[]
  messageCount: number
}

export interface ScreenshotPreview {
  ocrText: string
  platform: string
  speakers: string[]
  messageCount: number
}

export const useMemoryStore = defineStore('memories', () => {
  const memories = ref<Memory[]>([])
  const searchResults = ref<SearchResult[]>([])
  const loading = ref(false)
  const searching = ref(false)
  const lastImport = ref<ImportResult | null>(null)

  async function fetchByContact(contactId: number) {
    loading.value = true
    try {
      const res = await api.get<Memory[]>(`/memories/contact/${contactId}`)
      memories.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function fetchByCategory(contactId: number, category: string) {
    loading.value = true
    try {
      const res = await api.get<Memory[]>(`/memories/contact/${contactId}/category/${category}`)
      memories.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function searchMemories(q: string, contactId?: number) {
    searching.value = true
    try {
      const res = await api.get<SearchResult[]>('/memories/search', {
        params: { q, contactId },
      })
      searchResults.value = res.data
    } finally {
      searching.value = false
    }
  }

  async function previewImport(content: string, platform: string): Promise<PreviewResult> {
    const res = await api.post<PreviewResult>('/import/preview', { content, platform })
    return res.data
  }

  async function importText(selfName: string, platform: string, content: string) {
    const res = await api.post<ImportResult>('/import/text', {
      selfName: selfName || undefined,
      platform,
      content,
    })
    lastImport.value = res.data
    return res.data
  }

  async function importScreenshot(file: File, selfName: string, platform: string) {
    const form = new FormData()
    form.append('image', file)
    if (selfName) form.append('selfName', selfName)
    if (platform && platform !== 'auto') form.append('platform', platform)
    const res = await api.post<ImportResult>('/import/screenshot', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    lastImport.value = res.data
    return res.data
  }

  async function previewScreenshot(file: File): Promise<ScreenshotPreview> {
    const form = new FormData()
    form.append('image', file)
    const res = await api.post<ScreenshotPreview>('/import/screenshot/preview', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return res.data
  }

  return {
    memories, searchResults, loading, searching, lastImport,
    fetchByContact, fetchByCategory, searchMemories, previewImport, importText, importScreenshot, previewScreenshot,
  }
})
