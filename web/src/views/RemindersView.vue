<template>
  <div class="reminders-page">
    <el-container class="page-container">
      <el-header class="page-header">
        <el-button text @click="$router.push('/')">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h1>提醒</h1>
        <div style="display: flex; gap: 8px">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 120px" @change="onFilterChange">
            <el-option label="待确认" value="pending" />
            <el-option label="已确认" value="confirmed" />
            <el-option label="已触发" value="triggered" />
            <el-option label="已忽略" value="dismissed" />
          </el-select>
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon> 添加
          </el-button>
        </div>
      </el-header>

      <el-main>
        <div v-if="loading" style="text-align: center; padding: 40px">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p style="color: #9ca3af; margin-top: 12px">加载中...</p>
        </div>

        <el-empty v-else-if="reminders.length === 0" description="暂无提醒" />

        <div v-else class="reminder-list">
          <el-card
            v-for="r in reminders"
            :key="r.id"
            :class="['reminder-card', `status-${r.status}`]"
            shadow="hover"
          >
            <div class="reminder-body">
              <div class="reminder-header">
                <el-tag :type="statusType(r.status)" size="small">{{ statusLabel(r.status) }}</el-tag>
                <span class="reminder-contact" v-if="r.contact">
                  <el-icon><User /></el-icon>
                  {{ r.contact.name }}
                </span>
              </div>

              <h3 class="reminder-title">{{ r.title }}</h3>

              <div class="reminder-times">
                <div class="time-row" v-if="r.eventTime">
                  <el-icon><Timer /></el-icon>
                  <span>约定时间：{{ formatTime(r.eventTime) }}</span>
                </div>
                <div class="time-row">
                  <el-icon><AlarmClock /></el-icon>
                  <span>提醒时间：{{ formatTime(r.remindAt) }}</span>
                </div>
              </div>

              <p class="reminder-source" v-if="r.sourceText">
                「{{ r.sourceText }}」
              </p>
            </div>

            <div class="reminder-actions">
              <el-button size="small" text @click="openEdit(r)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <template v-if="r.status === 'confirmed'">
                <el-button size="small" @click="handleDismiss(r.id)">
                  <el-icon><Close /></el-icon> 忽略
                </el-button>
              </template>
            </div>
          </el-card>
        </div>
      </el-main>
    </el-container>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑提醒' : '添加提醒'"
      width="460px"
      destroy-on-close
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="联系人" required>
          <el-select
            v-model="form.contactId"
            filterable
            remote
            reserve-keyword
            clearable
            placeholder="输入姓名搜索"
            :remote-method="handleContactSearch"
            :loading="contactSearching"
            value-key="id"
            style="width: 100%"
          >
            <el-option
              v-for="c in contactSearchResults"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="如：一起吃饭" maxlength="100" />
        </el-form-item>
        <el-form-item label="约定时间" required>
          <el-date-picker
            v-model="form.eventTime"
            type="datetime"
            placeholder="选择日期时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!formValid" @click="handleSubmit">
          {{ editingId ? '保存' : '添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ArrowLeft, User, Timer, AlarmClock, Close, Loading, Plus, Edit } from '@element-plus/icons-vue'
import { useReminderStore } from '@/stores/reminders'
import { useContactStore } from '@/stores/contacts'
import type { Reminder, CreateReminderRequest } from '@/api/reminders'

const store = useReminderStore()
const contactStore = useContactStore()
const statusFilter = ref('')

const reminders = computed(() => store.reminders)
const loading = computed(() => store.loading)

// ── Dialog form ──
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const contactSearchResults = ref<{ id: number; name: string }[]>([])
const contactSearching = ref(false)

const form = ref({
  contactId: null as number | null,
  title: '',
  eventTime: '',
})

const formValid = computed(() =>
  form.value.contactId != null && form.value.title.trim() && form.value.eventTime
)

onMounted(() => {
  store.load()
  store.startPolling()
  contactStore.fetchAll()
})

onUnmounted(() => {
  store.stopPolling()
})

function onFilterChange() {
  store.load({ status: statusFilter.value || undefined })
}

function handleDismiss(id: number) {
  store.dismiss(id)
}

// ── Remote contact search ──
let searchTimer: ReturnType<typeof setTimeout> | null = null

function handleContactSearch(query: string) {
  if (!query.trim()) {
    contactSearchResults.value = []
    return
  }
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    contactSearching.value = true
    try {
      contactSearchResults.value = await contactStore.search(query)
    } finally {
      contactSearching.value = false
    }
  }, 300)
}

// ── Create / Edit ──
function openCreate() {
  editingId.value = null
  form.value = { contactId: null, title: '', eventTime: '' }
  dialogVisible.value = true
}

function openEdit(r: Reminder) {
  editingId.value = r.id
  form.value = {
    contactId: r.contact?.id ?? null,
    title: r.title,
    eventTime: r.eventTime ? toLocalDatetime(r.eventTime) : '',
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formValid.value) return
  const req: CreateReminderRequest = {
    contactId: form.value.contactId!,
    title: form.value.title.trim(),
    eventTime: new Date(form.value.eventTime).toISOString(),
  }
  if (editingId.value) {
    await store.update(editingId.value, req)
  } else {
    await store.create(req)
  }
  dialogVisible.value = false
}

// ── Helpers ──
function toLocalDatetime(iso: string) {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function statusType(status: string) {
  const map: Record<string, string> = { pending: 'warning', confirmed: 'success', triggered: 'danger', dismissed: 'info' }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = { pending: '待确认', confirmed: '已确认', triggered: '已触发', dismissed: '已忽略' }
  return map[status] || status
}

function formatTime(iso: string) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.reminders-page { min-height: 100vh; background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 50%, #fae8ff 100%); }
.page-container { max-width: 800px; margin: 0 auto; padding: 0 24px; }
.page-header { display: flex; align-items: center; justify-content: space-between; padding: 20px 0; height: auto; }
.page-header h1 { font-size: 24px; font-weight: 700; color: #1e1b4b; margin: 0; }
.reminder-list { display: flex; flex-direction: column; gap: 12px; }
.reminder-card { border-radius: 12px; transition: transform 0.2s; }
.reminder-card:hover { transform: translateY(-2px); }
.reminder-card.status-triggered { border-left: 4px solid #ef4444; }
.reminder-card.status-confirmed { border-left: 4px solid #22c55e; }
.reminder-body { display: flex; flex-direction: column; gap: 8px; }
.reminder-header { display: flex; align-items: center; gap: 8px; }
.reminder-contact { font-size: 13px; color: #6b7280; display: flex; align-items: center; gap: 4px; }
.reminder-title { font-size: 18px; font-weight: 600; color: #1f2937; margin: 0; }
.reminder-times { display: flex; flex-direction: column; gap: 4px; }
.time-row { font-size: 13px; color: #6b7280; display: flex; align-items: center; gap: 6px; }
.reminder-source { font-size: 13px; color: #9ca3af; font-style: italic; margin: 0; }
.reminder-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; padding-top: 12px; border-top: 1px solid #f3f4f6; }
</style>
