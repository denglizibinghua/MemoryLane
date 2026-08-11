import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchReminders,
  fetchDueReminders,
  confirmReminder,
  dismissReminder,
  createReminder,
  updateReminder,
  type Reminder,
  type DueResponse,
  type CreateReminderRequest,
} from '@/api/reminders'

export const useReminderStore = defineStore('reminders', () => {
  const reminders = ref<Reminder[]>([])
  const loading = ref(false)
  const lastDuePoll = ref<DueResponse | null>(null)

  let pollTimer: ReturnType<typeof setInterval> | null = null

  async function load(params?: { contactId?: number; status?: string }) {
    loading.value = true
    try {
      reminders.value = await fetchReminders(params)
    } finally {
      loading.value = false
    }
  }

  /**
   * Poll the due endpoint every 30s. When triggered reminders exist,
   * fire browser notifications for any we haven't seen yet.
   */
  function startPolling() {
    if (pollTimer) return
    const seenIds = new Set<number>()

    pollDue()
    pollTimer = setInterval(pollDue, 30_000)

    async function pollDue() {
      try {
        const res = await fetchDueReminders()
        lastDuePoll.value = res

        for (const r of res.triggered) {
          if (!seenIds.has(r.id)) {
            seenIds.add(r.id)
            doNotify(r)
          }
        }
      } catch {
        // silent — polling failures shouldn't surface to user
      }
    }
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  async function confirm(id: number) {
    const updated = await confirmReminder(id)
    const i = reminders.value.findIndex((r) => r.id === id)
    if (i >= 0) reminders.value[i] = updated
    ElMessage.success('已确认提醒')
  }

  async function dismiss(id: number) {
    const updated = await dismissReminder(id)
    const i = reminders.value.findIndex((r) => r.id === id)
    if (i >= 0) reminders.value[i] = updated
    ElMessage.info('已忽略提醒')
  }

  async function create(req: CreateReminderRequest) {
    const r = await createReminder(req)
    reminders.value.unshift(r)
    ElMessage.success('提醒已添加')
  }

  async function update(id: number, req: CreateReminderRequest) {
    const updated = await updateReminder(id, req)
    const i = reminders.value.findIndex((r) => r.id === id)
    if (i >= 0) reminders.value[i] = updated
    ElMessage.success('提醒已更新')
  }

  return { reminders, loading, lastDuePoll, load, startPolling, stopPolling, confirm, dismiss, create, update }
})

/**
 * Fire a browser notification via the Notification API.
 * Requests permission if not yet granted.
 */
function doNotify(reminder: Reminder) {
  if (!('Notification' in window)) return

  if (Notification.permission === 'granted') {
    new Notification('MemoryLane 提醒', {
      body: reminder.title,
      icon: '/favicon.ico',
      tag: `reminder-${reminder.id}`,
    })
  } else if (Notification.permission !== 'denied') {
    Notification.requestPermission().then((perm) => {
      if (perm === 'granted') {
        doNotify(reminder)
      }
    })
  }
}
