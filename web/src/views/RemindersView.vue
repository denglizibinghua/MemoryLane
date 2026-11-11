<template>
  <div class="page reminders-page">
    <PageHeader
      title="提醒"
      description="从聊天里的约定自动浮出来，也可以自己加一条。"
    >
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建提醒
        </el-button>
      </template>
    </PageHeader>

    <!-- Status filter -->
    <div class="filter-row" role="group" aria-label="按状态筛选提醒">
      <button
        v-for="f in filters"
        :key="f.value || 'all'"
        type="button"
        class="filter-chip"
        :class="{ 'is-active': statusFilter === f.value }"
        @click="statusFilter = f.value"
      >
        {{ f.label }}
        <span class="filter-count tabular">{{ counts[f.value] }}</span>
      </button>
    </div>

    <!-- Error -->
    <div v-if="loadError" class="error-band" role="alert">
      <span>{{ loadError }}</span>
      <el-button size="small" @click="loadReminders">重试</el-button>
    </div>

    <!-- Loading -->
    <div
      v-if="!initialLoaded || loading"
      class="reminder-surface"
      aria-busy="true"
      aria-label="正在加载提醒"
    >
      <div v-for="n in 4" :key="n" class="skeleton-row">
        <el-skeleton-item variant="rect" class="sk-date" />
        <div class="sk-copy">
          <el-skeleton-item variant="text" class="sk-title" />
          <el-skeleton-item variant="text" class="sk-meta" />
          <el-skeleton-item variant="text" class="sk-quote" />
        </div>
      </div>
    </div>

    <!-- Empty -->
    <EmptyState
      v-else-if="!filteredReminders.length && !loadError"
      :title="statusFilter ? '这个状态里还没有提醒' : '还没有提醒'"
      :description="
        statusFilter
          ? '换一个状态看看，或者回到全部提醒。'
          : '导入聊天后，检测到的约定会出现在这里；也可以手动添加。'
      "
    >
      <el-button v-if="statusFilter" @click="statusFilter = ''">查看全部</el-button>
      <el-button v-else type="primary" @click="openCreate">新建提醒</el-button>
    </EmptyState>

    <!-- List -->
    <section v-else-if="filteredReminders.length" class="reminder-surface">
      <ul class="reminder-list">
        <li v-for="r in filteredReminders" :key="r.id" class="reminder-row">
          <div class="date-block" aria-hidden="true">
            <span class="date-month tabular">{{ dateParts(r.eventTime || r.remindAt).month }}</span>
            <span class="date-day tabular">{{ dateParts(r.eventTime || r.remindAt).day }}</span>
          </div>

          <div class="reminder-main">
            <h3 class="reminder-title">{{ r.title }}</h3>
            <div class="reminder-meta">
              <span v-if="r.contact" class="contact-chip">
                <el-icon :size="12"><User /></el-icon>
                {{ r.contact.name }}
              </span>
              <span v-if="r.eventTime" class="time-line">约定时间 {{ formatTime(r.eventTime) }}</span>
              <span class="time-line">提醒时间 {{ formatTime(r.remindAt) }}</span>
            </div>
            <p v-if="r.sourceText" class="reminder-source">「{{ r.sourceText }}」</p>
          </div>

          <div class="reminder-side">
            <span class="status" :class="`is-${r.status}`">
              <i class="status-dot" aria-hidden="true"></i>
              {{ statusLabel(r.status) }}
            </span>
            <div class="row-actions">
              <el-button text size="small" :disabled="busyId === r.id" @click="openEdit(r)">
                编辑
              </el-button>
              <el-button
                v-if="r.status === 'pending'"
                text
                size="small"
                :disabled="busyId === r.id"
                @click="handleConfirm(r.id)"
              >
                确认
              </el-button>
              <el-button
                v-if="r.status === 'pending' || r.status === 'confirmed'"
                text
                size="small"
                :disabled="busyId === r.id"
                @click="handleDismiss(r.id)"
              >
                忽略
              </el-button>
            </div>
          </div>
        </li>
      </ul>
    </section>

    <!-- Create / edit dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑提醒' : '新建提醒'"
      width="min(460px, 92vw)"
      destroy-on-close
    >
      <el-form :model="form" label-position="top" class="reminder-form">
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
            class="field-full"
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
          <el-input v-model="form.title" placeholder="如：一起吃饭" maxlength="100" class="field-full" />
        </el-form-item>

        <el-form-item label="约定时间" required>
          <el-date-picker
            v-model="form.eventTime"
            type="datetime"
            placeholder="选择日期时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            class="field-full"
          />
        </el-form-item>
      </el-form>

      <p class="form-hint">保存后会在约定时间前 30 分钟提醒你。</p>

      <template #footer>
        <div class="dialog-foot">
          <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="!formValid || submitting"
            @click="handleSubmit"
          >
            {{ editingId ? '保存' : '创建' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Plus, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useReminderStore } from '@/stores/reminders'
import { useContactStore } from '@/stores/contacts'
import type { CreateReminderRequest, Reminder } from '@/api/reminders'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

type ReminderStatus = Reminder['status']
type StatusFilter = '' | ReminderStatus

const store = useReminderStore()
const contactStore = useContactStore()

const statusFilter = ref<StatusFilter>('')
const initialLoaded = ref(false)
const loadError = ref('')
const loading = computed(() => store.loading)

const filters: { label: string; value: StatusFilter }[] = [
  { label: '全部', value: '' },
  { label: '待确认', value: 'pending' },
  { label: '已确认', value: 'confirmed' },
  { label: '已触发', value: 'triggered' },
  { label: '已忽略', value: 'dismissed' },
]

const STATUS_ORDER: Record<ReminderStatus, number> = {
  pending: 0,
  confirmed: 1,
  triggered: 2,
  dismissed: 3,
}

const counts = computed<Record<StatusFilter, number>>(() => {
  const result: Record<StatusFilter, number> = {
    '': store.reminders.length,
    pending: 0,
    confirmed: 0,
    triggered: 0,
    dismissed: 0,
  }
  for (const r of store.reminders) result[r.status] += 1
  return result
})

function timeValue(iso?: string | null): number {
  if (!iso) return Number.POSITIVE_INFINITY
  const value = new Date(iso).getTime()
  return Number.isNaN(value) ? Number.POSITIVE_INFINITY : value
}

const filteredReminders = computed(() => {
  const base = statusFilter.value
    ? store.reminders.filter((r) => r.status === statusFilter.value)
    : [...store.reminders]

  return base.sort((a, b) => {
    const byStatus = STATUS_ORDER[a.status] - STATUS_ORDER[b.status]
    if (byStatus !== 0) return byStatus
    const byTime = timeValue(a.remindAt) - timeValue(b.remindAt)
    return byTime !== 0 ? byTime : a.id - b.id
  })
})

// ── Row actions ──
const busyId = ref<number | null>(null)

async function handleConfirm(id: number) {
  busyId.value = id
  try {
    await store.confirm(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '确认失败，请重试')
  } finally {
    busyId.value = null
  }
}

async function handleDismiss(id: number) {
  busyId.value = id
  try {
    await store.dismiss(id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '操作失败，请重试')
  } finally {
    busyId.value = null
  }
}

// ── Loading ──
async function loadReminders() {
  loadError.value = ''
  try {
    await store.load()
  } catch (e: any) {
    loadError.value = e?.response?.data?.message || e?.message || '提醒加载失败，请重试'
  } finally {
    initialLoaded.value = true
  }
}

// ── Dialog form ──
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const contactSearchResults = ref<{ id: number; name: string }[]>([])
const contactSearching = ref(false)

const form = ref({
  contactId: null as number | null,
  title: '',
  eventTime: '',
})

const formValid = computed(
  () =>
    form.value.contactId !== null &&
    !!form.value.title.trim() &&
    !!form.value.eventTime
)

onMounted(() => {
  loadReminders()
  store.startPolling()
  contactStore.fetchAll().catch(() => {
    // Contact store may be unavailable; remote search still retries on input.
  })
})

onUnmounted(() => {
  store.stopPolling()
  if (searchTimer) clearTimeout(searchTimer)
})

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

// ── Create / edit ──
function openCreate() {
  editingId.value = null
  form.value = { contactId: null, title: '', eventTime: '' }
  contactSearchResults.value = []
  dialogVisible.value = true
}

function openEdit(r: Reminder) {
  editingId.value = r.id
  form.value = {
    contactId: r.contact?.id ?? null,
    title: r.title,
    eventTime: r.eventTime ? toLocalDatetime(r.eventTime) : '',
  }
  contactSearchResults.value = r.contact ? [{ id: r.contact.id, name: r.contact.name }] : []
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formValid.value || submitting.value) return
  submitting.value = true
  const req: CreateReminderRequest = {
    contactId: form.value.contactId!,
    title: form.value.title.trim(),
    eventTime: new Date(form.value.eventTime).toISOString(),
  }
  try {
    if (editingId.value) {
      await store.update(editingId.value, req)
    } else {
      await store.create(req)
    }
    dialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败，请重试')
  } finally {
    submitting.value = false
  }
}

// ── Helpers ──
function dateParts(iso?: string | null): { month: string; day: string } {
  const d = iso ? new Date(iso) : null
  if (!d || Number.isNaN(d.getTime())) return { month: '—', day: '—' }
  return {
    month: `${d.getMonth() + 1}月`,
    day: String(d.getDate()).padStart(2, '0'),
  }
}

function toLocalDatetime(iso: string) {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    pending: '待确认',
    confirmed: '已确认',
    triggered: '已触发',
    dismissed: '已忽略',
  }
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
.reminders-page {
  min-height: 100%;
}

/* ── Filters ───────────────────────────────────────────────── */

.filter-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 20px;
  padding-bottom: 2px;
  overflow-x: auto;
  scrollbar-width: none;
}

.filter-row::-webkit-scrollbar {
  display: none;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 11px;
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.filter-chip:hover {
  background: var(--wash);
  color: var(--ink-2);
}

.filter-chip.is-active {
  background: var(--paper);
  border-color: var(--line-strong);
  color: var(--ink);
  font-weight: 600;
}

.filter-count {
  font-size: 10px;
  font-weight: 500;
  color: var(--faint);
}

.filter-chip.is-active .filter-count {
  color: var(--muted);
}

/* ── List surface ──────────────────────────────────────────── */

.reminder-surface {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.reminder-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.reminder-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 18px;
  align-items: start;
  padding: 18px 20px;
  transition: background-color 0.15s ease;
}

.reminder-row + .reminder-row {
  border-top: 1px solid var(--line);
}

.reminder-row:hover {
  background: #faf9f4;
}

.date-block {
  width: 42px;
  padding-top: 1px;
  text-align: center;
}

.date-month {
  display: block;
  font-size: 10px;
  letter-spacing: 0.03em;
  color: var(--faint);
}

.date-day {
  display: block;
  margin-top: 1px;
  font-size: 22px;
  font-weight: 650;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.reminder-main {
  min-width: 0;
}

.reminder-title {
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--ink);
  word-break: break-word;
}

.reminder-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px 12px;
  margin-top: 5px;
}

.contact-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 7px;
  border-radius: var(--r-xs);
  background: var(--wash);
  color: var(--ink-2);
  font-size: 11px;
}

.contact-chip :deep(.el-icon) {
  color: var(--muted);
}

.time-line {
  font-size: 11.5px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.reminder-source {
  display: inline-block;
  max-width: 100%;
  margin-top: 7px;
  padding: 3px 8px;
  border-radius: var(--r-xs);
  background: var(--wash);
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.6;
  word-break: break-word;
}

.reminder-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 9px;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status.is-pending {
  color: var(--warning);
}

.status.is-confirmed {
  color: var(--success);
}

.status.is-triggered {
  color: var(--seal);
}

.status.is-dismissed {
  color: var(--faint);
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-right: -8px;
}

.row-actions :deep(.el-button.is-text) {
  padding: 4px 8px;
  font-size: 12px;
}

/* ── Loading ───────────────────────────────────────────────── */

.skeleton-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 18px;
  padding: 18px 20px;
}

.skeleton-row + .skeleton-row {
  border-top: 1px solid var(--line);
}

.sk-date {
  width: 42px;
  height: 42px;
  border-radius: var(--r-sm);
}

.sk-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.sk-title {
  width: 38%;
  height: 12px;
}

.sk-meta {
  width: 58%;
  height: 9px;
}

.sk-quote {
  width: 72%;
  height: 9px;
}

/* ── Error ─────────────────────────────────────────────────── */

.error-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--danger-tint);
  color: #8f2f25;
  font-size: 12.5px;
}

/* ── Dialog ────────────────────────────────────────────────── */

.field-full {
  width: 100%;
}

.reminder-form :deep(.el-select),
.reminder-form :deep(.el-date-editor) {
  width: 100%;
}

.form-hint {
  margin: -6px 0 0;
  font-size: 11px;
  color: var(--faint);
}

.dialog-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

/* ── Responsive ────────────────────────────────────────────── */

@media (max-width: 700px) {
  .filter-row {
    margin-bottom: 16px;
  }

  .reminder-row {
    grid-template-columns: 42px minmax(0, 1fr);
    gap: 12px 14px;
    padding: 16px;
  }

  .date-block {
    grid-row: 1 / span 2;
  }

  .reminder-side {
    grid-column: 2;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    gap: 8px 12px;
    flex-wrap: wrap;
  }

  .row-actions {
    margin-right: -8px;
  }

  .skeleton-row {
    padding: 16px;
  }

  .status {
    font-size: 10.5px;
  }
}

@media (max-width: 380px) {
  .reminder-meta {
    gap: 4px 9px;
  }

  .contact-chip {
    padding: 2px 6px;
  }
}
</style>
