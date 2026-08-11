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

export interface ImportResult {
  taskId: string
  stats: {
    newMessages: number
    duplicates: number
    memoriesExtracted: number
  }
  contactId: number
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

  async function importText(contactName: string, platform: string, content: string) {
    const res = await api.post<ImportResult>('/import/text', {
      contactName: contactName || undefined,
      platform,
      content,
    })
    lastImport.value = res.data
    return res.data
  }

  return { memories, searchResults, loading, searching, lastImport, fetchByContact, fetchByCategory, searchMemories, importText }
})
