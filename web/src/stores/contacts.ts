import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api'

export interface Contact {
  id: number
  name: string
  platform: string
  profile: string
  createdAt: string
}

export interface ContactCandidate {
  id: number
  name: string
  platform: string
  messageCount: number
}

export interface DuplicateGroup {
  candidates: ContactCandidate[]
  reason: string
  confidence: number
}

export const useContactStore = defineStore('contacts', () => {
  const contacts = ref<Contact[]>([])
  const loading = ref(false)

  async function fetchAll() {
    loading.value = true
    try {
      const res = await api.get<Contact[]>('/contacts')
      contacts.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function search(query: string): Promise<Contact[]> {
    const res = await api.get<Contact[]>('/contacts/search', { params: { q: query } })
    return res.data
  }

  async function create(name: string, platform: string): Promise<Contact> {
    const res = await api.post<Contact>('/contacts', { name, platform })
    await fetchAll()
    return res.data
  }

  async function remove(id: number) {
    await api.delete(`/contacts/${id}`)
    await fetchAll()
  }

  async function merge(targetId: number, sourceIds: number[]) {
    await api.post('/contacts/merge', { targetId, sourceIds })
    await fetchAll()
  }

  async function fetchDuplicates(): Promise<DuplicateGroup[]> {
    const res = await api.get<DuplicateGroup[]>('/contacts/duplicates')
    return res.data
  }

  return { contacts, loading, fetchAll, search, create, remove, merge, fetchDuplicates }
})
