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

  return { contacts, loading, fetchAll, search, create, remove }
})
