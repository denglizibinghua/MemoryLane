<template>
  <div class="page advisor-page">
    <header class="advisor-head">
      <div class="head-copy">
        <h1 class="head-title">军师模式</h1>
        <p class="head-sub">把对方刚说的话放进来，结合记忆给回复</p>
      </div>
      <div class="head-actions">
        <span class="style-chip" :title="`当前回复风格：${styleLabel}，可在 AI 设置中调整`">
          <span class="style-chip-key">风格</span>
          {{ styleLabel }}
        </span>
      </div>
    </header>

    <div class="advisor-layout">
      <!-- Contact rail -->
      <aside class="contact-rail" aria-label="联系人">
        <div class="rail-head">
          <span>联系人</span>
          <span class="rail-count tabular">{{ contactOptions.length }}</span>
        </div>

        <div v-if="contactOptions.length > 8" class="rail-search">
          <el-input
            v-model="contactQuery"
            size="small"
            placeholder="筛选联系人"
            :prefix-icon="Search"
            clearable
          />
        </div>

        <ul v-if="filteredContacts.length" class="rail-list">
          <li v-for="c in filteredContacts" :key="c.id">
            <button
              type="button"
              class="rail-item"
              :class="{ 'is-active': selectedContact?.id === c.id }"
              @click="handleContactChange(c)"
            >
              <MonogramAvatar :name="c.name" :size="30" />
              <span class="rail-copy">
                <span class="rail-name">{{ c.name }}</span>
                <span class="rail-meta">{{ platformLabel(c.platform) }}</span>
              </span>
              <span v-if="selectedContact?.id === c.id" class="rail-dot"></span>
            </button>
          </li>
        </ul>
        <p v-else class="rail-empty">
          {{ contactOptions.length ? '没有匹配的联系人' : '还没有联系人，先在首页导入一段聊天。' }}
        </p>
      </aside>

      <!-- Chat panel -->
      <section class="chat-panel">
        <header class="panel-head">
          <template v-if="selectedContact">
            <MonogramAvatar :name="selectedContact.name" :size="32" />
            <div class="panel-id">
              <p class="panel-name">{{ selectedContact.name }}</p>
              <p class="panel-meta">
                {{ platformLabel(selectedContact.platform) }} · 记忆会参与建议
              </p>
            </div>
            <div class="panel-actions">
              <el-button
                v-if="messages.length > 0"
                size="small"
                :loading="saving"
                :disabled="saving"
                @click="handleSave"
              >
                保存对话
              </el-button>
              <button
                type="button"
                class="icon-btn"
                title="清空当前对话"
                aria-label="清空当前对话"
                :disabled="isEmptyConversation"
                @click="handleClearChat"
              >
                <el-icon :size="15"><RefreshLeft /></el-icon>
              </button>
            </div>
          </template>
          <template v-else>
            <div class="panel-id">
              <p class="panel-name">未选择联系人</p>
              <p class="panel-meta">从联系人里挑一位，开始推演</p>
            </div>
          </template>
        </header>

        <!-- Chat canvas -->
        <div ref="chatArea" class="chat-canvas">
          <div v-if="messages.length === 0 && !loading" class="chat-empty">
            <svg class="empty-lane" viewBox="0 0 132 60" fill="none" aria-hidden="true">
              <path
                d="M8 46c24 0 24-32 54-32s30 32 62 32"
                stroke="var(--line-strong)"
                stroke-width="1.2"
                stroke-dasharray="3 5"
                stroke-linecap="round"
              />
              <circle cx="8" cy="46" r="2.6" fill="var(--accent)" />
              <circle cx="62" cy="14" r="2.6" fill="var(--seal)" />
              <circle cx="124" cy="46" r="2.6" fill="var(--faint)" />
            </svg>
            <h2 class="empty-title">{{ emptyTitle }}</h2>
            <p class="empty-desc">{{ emptyDesc }}</p>
            <div v-if="selectedContact" class="empty-examples">
              <span class="empty-examples-label">试 着 说</span>
              <div class="example-chips">
                <button
                  v-for="line in EXAMPLE_LINES"
                  :key="line"
                  type="button"
                  class="example-chip"
                  @click="applyDraft(line)"
                >
                  {{ line }}
                </button>
              </div>
            </div>
          </div>

          <div
            v-for="msg in messages"
            :key="msg.id"
            class="msg-row"
            :class="{ 'is-self': msg.speaker === 'self' }"
          >
            <MonogramAvatar v-if="msg.speaker !== 'self'" :name="selectedContact?.name" :size="30" />
            <MonogramAvatar v-else name="我" :size="30" />
            <div class="msg-stack">
              <div
                class="bubble"
                :class="msg.speaker === 'self' ? 'self-bubble' : 'contact-bubble'"
              >
                <p class="bubble-content">{{ msg.content }}</p>
              </div>
              <span class="msg-time tabular">{{ formatTime(msg.rawTime) }}</span>
            </div>
          </div>

          <div v-if="loading" class="msg-row">
            <MonogramAvatar :name="selectedContact?.name" :size="30" />
            <div class="bubble contact-bubble typing-bubble">
              <span class="typing-dots"><span></span><span></span><span></span></span>
            </div>
          </div>
        </div>

        <!-- Inline error -->
        <div v-if="error" class="inline-error" role="alert">
          <el-icon :size="14"><WarningFilled /></el-icon>
          <span class="inline-error-text">{{ error }}</span>
          <button
            type="button"
            class="retry-btn"
            :disabled="!lastContactMessage || loading"
            @click="retrySuggest"
          >
            重试
          </button>
        </div>

        <!-- Suggestions + new topics -->
        <section v-if="showSuggestionArea" class="suggest-panel" aria-label="军师建议">
          <template v-if="showSuggestions">
            <div class="suggest-head">
              <span class="suggest-title">军师建议</span>
              <span class="suggest-hint">点击直接采用</span>
            </div>
            <div class="suggest-list">
              <button
                v-for="(s, idx) in visibleSuggestions"
                :key="`${idx}-${s.content}`"
                type="button"
                class="suggest-card"
                @click="handlePickSuggestion(s)"
              >
                <span class="suggest-index tabular">{{ idx + 1 }}</span>
                <span class="suggest-body">
                  <span class="suggest-content">{{ s.content }}</span>
                  <span class="suggest-meta">
                    <span class="style-pill" :style="styleTone(s.style)">{{ s.style }}</span>
                    <span class="suggest-reason">{{ s.reason }}</span>
                  </span>
                </span>
              </button>
            </div>
          </template>

          <div v-if="showNewTopics" class="topics">
            <div class="topics-head">
              <span>换一个话题</span>
              <span class="topics-hint">只填入输入框</span>
            </div>
            <button
              v-for="(t, idx) in newTopics"
              :key="`${idx}-${t.content}`"
              type="button"
              class="topic-row"
              @click="applyTopic(t)"
            >
              <span class="topic-text">{{ t.content }}</span>
              <span class="topic-reason">{{ t.reason }}</span>
              <el-icon class="topic-icon" :size="12"><EditPen /></el-icon>
            </button>
          </div>
        </section>

        <!-- Composer -->
        <footer class="composer">
          <div class="composer-shell" :class="{ 'is-focused': composerFocused }">
            <textarea
              ref="composerRef"
              v-model="inputText"
              class="composer-input"
              rows="2"
              placeholder="输入对方说的话…"
              spellcheck="false"
              :disabled="!selectedContact"
              aria-label="输入对方说的话"
              @focus="composerFocused = true"
              @blur="composerFocused = false"
              @keydown.enter.exact.prevent="handleSend"
            ></textarea>
            <div class="composer-bar">
              <span class="composer-hint">Enter 发送 · Shift+Enter 换行</span>
              <el-button type="primary" :loading="loading" :disabled="!canSend" @click="handleSend">
                发送
              </el-button>
            </div>
          </div>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { EditPen, RefreshLeft, Search, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'
import {
  saveConversation,
  suggestAdvisor,
  type AdvisorReply,
  type ConversationMessage,
  type NewTopic,
} from '@/api/advisor'
import { getAiSettings } from '@/api/settings'
import { platformLabel } from '@/utils/presentation'
import MonogramAvatar from '@/components/MonogramAvatar.vue'

// ── Types ──────────────────────────────────────────────────────────

interface ContactOption {
  id: number
  name: string
  platform: string
}

interface ChatMessage {
  id: string
  speaker: string // "self" | contact name
  content: string
  rawTime: Date
}

// ── Constants ──────────────────────────────────────────────────────

const EXAMPLE_LINES = ['周末有空吗？', '我最近有点累', '你还记得上次说的那件事吗？']

const STYLE_LABELS: Record<string, string> = {
  default: '默认',
  humorous: '幽默',
  cute: '可爱',
  gentle: '温柔',
  cool: '高冷',
  tsundere: '傲娇',
}

// Restrained style tones — tints only, never solid dark pills.
const STYLE_TONES: Record<string, { background: string; color: string }> = {
  轻松随性: { background: 'var(--cat-event-tint)', color: 'var(--cat-event)' },
  引用约定: { background: 'var(--accent-tint)', color: 'var(--accent)' },
  关心体贴: { background: 'var(--cat-promise-tint)', color: 'var(--cat-promise)' },
  直接坦率: { background: 'var(--cat-persona-tint)', color: 'var(--cat-persona)' },
  幽默调侃: { background: 'var(--cat-preference-tint)', color: 'var(--cat-preference)' },
}

// ── State ──────────────────────────────────────────────────────────

const route = useRoute()
const contactOptions = ref<ContactOption[]>([])
const selectedContact = ref<ContactOption | null>(null)
const contactQuery = ref('')
const messages = ref<ChatMessage[]>([])
const currentSuggestions = ref<AdvisorReply[]>([])
const newTopics = ref<NewTopic[]>([])
const inputText = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const chatArea = ref<HTMLElement | null>(null)
const composerRef = ref<HTMLTextAreaElement | null>(null)
const composerFocused = ref(false)
const advisorStyle = ref('default')
let suggestToken = 0

// ── Computed ───────────────────────────────────────────────────────

const filteredContacts = computed(() => {
  const q = contactQuery.value.trim().toLowerCase()
  if (!q) return contactOptions.value
  return contactOptions.value.filter(
    (c) => c.name.toLowerCase().includes(q) || platformLabel(c.platform).includes(q)
  )
})

const styleLabel = computed(() => STYLE_LABELS[advisorStyle.value] || '默认')

const lastContactMessage = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i]!.speaker !== 'self') return messages.value[i]!
  }
  return null
})

const showSuggestions = computed(() => currentSuggestions.value.length > 0 && !loading.value)
const visibleSuggestions = computed(() => currentSuggestions.value.slice(0, 3))
const showNewTopics = computed(() => newTopics.value.length > 0 && !loading.value)
const showSuggestionArea = computed(() => showSuggestions.value || showNewTopics.value)

const canSend = computed(
  () => selectedContact.value !== null && inputText.value.trim().length > 0 && !loading.value
)

const isEmptyConversation = computed(
  () =>
    messages.value.length === 0 &&
    currentSuggestions.value.length === 0 &&
    newTopics.value.length === 0 &&
    !inputText.value.trim()
)

const emptyTitle = computed(() => {
  if (contactOptions.value.length === 0) return '还没有可以推演的联系人'
  if (!selectedContact.value) return '先选一位联系人'
  return '把对方刚说的话放进来'
})

const emptyDesc = computed(() => {
  if (contactOptions.value.length === 0) {
    return '先在首页导入一段聊天。联系人和记忆会出现在这里，军师才有话可接。'
  }
  if (!selectedContact.value) {
    return '从联系人里挑一位，军师会带着你和 TA 的历史记忆一起想，再接下一句。'
  }
  return `军师会翻出你和「${selectedContact.value.name}」的记忆，给几条能直接发出去的回复。`
})

// ── Lifecycle ──────────────────────────────────────────────────────

onMounted(async () => {
  try {
    const res = await api.get<ContactOption[]>('/contacts')
    contactOptions.value = res.data || []
  } catch {
    // contacts API unavailable — leave options empty
  }

  selectContactFromQuery()
  if (!selectedContact.value && contactOptions.value.length > 0) {
    switchTo(contactOptions.value[0]!)
  }

  try {
    const settings = await getAiSettings()
    advisorStyle.value = settings.advisorStyle || 'default'
  } catch {
    advisorStyle.value = 'default'
  }
})

onBeforeRouteLeave(async () => {
  if (messages.value.length === 0) return true
  try {
    await ElMessageBox.confirm(
      '当前对话未保存，离开将清空聊天记录。确定离开？',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    return true
  } catch {
    return false
  }
})

// ── Helpers ────────────────────────────────────────────────────────

function uid(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

function formatTime(d: Date): string {
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  return `${h}:${m}`
}

async function scrollToBottom() {
  await nextTick()
  if (chatArea.value) {
    chatArea.value.scrollTop = chatArea.value.scrollHeight
  }
}

function buildRecentContext(): string[] {
  return messages.value.map(
    (m) => `${m.speaker === 'self' ? '我' : selectedContact.value!.name}: ${m.content}`
  )
}

function styleTone(style: string) {
  return STYLE_TONES[style] || { background: 'var(--wash)', color: 'var(--muted)' }
}

// Deep link from ContactDetailView: /advisor?contactId=123
function selectContactFromQuery() {
  const raw = route.query.contactId
  const value = Array.isArray(raw) ? raw[0] : raw
  if (!value) return
  const id = Number(value)
  if (!Number.isFinite(id)) return
  const found = contactOptions.value.find((c) => c.id === id)
  if (found) selectedContact.value = found
}

function resetConversation() {
  suggestToken++
  loading.value = false
  messages.value = []
  currentSuggestions.value = []
  newTopics.value = []
  inputText.value = ''
  error.value = ''
}

function switchTo(contact: ContactOption) {
  selectedContact.value = contact
  resetConversation()
  scrollToBottom()
}

// ── Actions ────────────────────────────────────────────────────────

// Contact switching keeps the unsaved-conversation confirmation. The
// selection is only applied after confirmation, so cancelling naturally
// leaves the previous contact in place.
function handleContactChange(next: ContactOption) {
  if (next.id === selectedContact.value?.id) return
  if (messages.value.length > 0) {
    ElMessageBox.confirm(
      '当前对话未保存，切换联系人将清空聊天记录。确定切换？',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
      .then(() => switchTo(next))
      .catch(() => {
        // user cancelled — keep the current contact selected
      })
  } else {
    switchTo(next)
  }
}

async function fetchSuggestions(lastMessage: string) {
  if (!selectedContact.value) return
  const contact = selectedContact.value
  const token = ++suggestToken
  loading.value = true
  error.value = ''
  try {
    const result = await suggestAdvisor({
      contactId: contact.id,
      lastMessage,
      recentContext: buildRecentContext(),
    })
    if (token !== suggestToken) return
    currentSuggestions.value = result.replies || []
    newTopics.value = result.newTopics || []
    if (currentSuggestions.value.length === 0 && newTopics.value.length === 0) {
      ElMessage.info('没有生成回复建议')
    }
  } catch (e: any) {
    if (token !== suggestToken) return
    error.value = e?.response?.data?.message || e?.message || '请求失败'
    currentSuggestions.value = []
    newTopics.value = []
  } finally {
    if (token === suggestToken) {
      loading.value = false
      await scrollToBottom()
    }
  }
}

async function handleSend() {
  if (!canSend.value || !selectedContact.value) return

  const content = inputText.value.trim()
  inputText.value = ''
  currentSuggestions.value = []
  newTopics.value = []
  error.value = ''

  messages.value.push({
    id: uid(),
    speaker: selectedContact.value.name,
    content,
    rawTime: new Date(),
  })
  await scrollToBottom()
  await fetchSuggestions(content)
}

// Retry requests suggestions for the last contact message again, without
// appending a duplicate chat message.
async function retrySuggest() {
  const last = lastContactMessage.value
  if (!last || !selectedContact.value || loading.value) return
  await fetchSuggestions(last.content)
}

function handlePickSuggestion(s: AdvisorReply) {
  messages.value.push({
    id: uid(),
    speaker: 'self',
    content: s.content,
    rawTime: new Date(),
  })
  currentSuggestions.value = []
  newTopics.value = []
  error.value = ''
  scrollToBottom()
}

function applyDraft(text: string) {
  inputText.value = text
  nextTick(() => composerRef.value?.focus())
}

// New topics only fill the composer — they never send.
function applyTopic(topic: NewTopic) {
  applyDraft(topic.content)
}

async function handleClearChat() {
  if (isEmptyConversation.value) return
  try {
    await ElMessageBox.confirm('清空当前对话？已输入的内容不会保存。', '清空对话', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  resetConversation()
}

async function handleSave() {
  if (!selectedContact.value || messages.value.length === 0) return

  saving.value = true
  try {
    const convMessages: ConversationMessage[] = messages.value.map((m) => ({
      speaker: m.speaker === 'self' ? 'self' : selectedContact.value!.name,
      content: m.content,
      rawTime: m.rawTime.toISOString(),
    }))

    const result = await saveConversation({
      contactId: selectedContact.value.id,
      messages: convMessages,
    })

    const saved = result.stats.newMessages
    const dup = result.stats.duplicates
    ElMessage.success(`已保存 ${saved} 条消息${dup > 0 ? `（${dup} 条重复已跳过）` : ''}`)

    await ElMessageBox.confirm(
      `已保存 ${saved} 条消息到记忆库。是否清空当前聊天？`,
      '保存成功',
      { confirmButtonText: '清空继续', cancelButtonText: '保留', type: 'success' }
    )
    resetConversation()
    nextTick(() => composerRef.value?.focus())
  } catch (e: any) {
    // "cancel" / "close" come from the post-save clear dialog, not a failed save.
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
/* ── Frame: a full-height work surface, not a scrolling page ── */

.advisor-page {
  height: 100dvh;
  padding: 24px 44px 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.advisor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 52px;
  flex-shrink: 0;
  margin-bottom: 14px;
}

.head-copy {
  min-width: 0;
}

.head-title {
  font-size: 22px;
  font-weight: 650;
  line-height: 1.15;
  letter-spacing: -0.03em;
  color: var(--ink);
}

.head-sub {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.style-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--line-strong);
  border-radius: 99px;
  color: var(--ink-2);
  font-size: 11px;
  font-weight: 550;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.style-chip-key {
  font-size: 9.5px;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--faint);
}

.advisor-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
}

/* ── Contact rail ──────────────────────────────────────────── */

.contact-rail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding-top: 2px;
}

.rail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 9px;
  border-bottom: 1px solid var(--line-strong);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--muted);
}

.rail-count {
  color: var(--faint);
  font-weight: 500;
}

.rail-search {
  margin-top: 8px;
}

.rail-search :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  padding-left: 0;
}

.rail-search :deep(.el-input__wrapper:hover),
.rail-search :deep(.el-input__wrapper.is-focus) {
  background: transparent;
  box-shadow: none;
}

.rail-search :deep(.el-input__inner) {
  font-size: 12px;
}

.rail-list {
  flex: 1;
  min-height: 0;
  margin: 8px 0 0;
  padding: 0 0 6px;
  list-style: none;
  overflow-y: auto;
}

.rail-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 8px;
  margin-bottom: 2px;
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-2);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.rail-item:hover {
  background: var(--wash);
}

.rail-item.is-active {
  background: var(--paper);
  border-color: rgba(45, 63, 158, 0.38);
}

.rail-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.rail-name {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-meta {
  margin-top: 1px;
  font-size: 10px;
  color: var(--faint);
}

.rail-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  flex-shrink: 0;
}

.rail-empty {
  padding: 14px 8px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--faint);
}

/* ── Chat panel ────────────────────────────────────────────── */

.chat-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 54px;
  flex-shrink: 0;
  padding: 0 14px 0 16px;
  background: var(--paper);
  border-bottom: 1px solid var(--line);
}

.panel-id {
  flex: 1;
  min-width: 0;
}

.panel-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-meta {
  margin-top: 1px;
  font-size: 10.5px;
  color: var(--faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.icon-btn:hover:not(:disabled) {
  background: var(--wash);
  color: var(--ink);
}

.icon-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

/* ── Chat canvas ───────────────────────────────────────────── */

.chat-canvas {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  background: var(--bg);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chat-canvas::-webkit-scrollbar {
  width: 8px;
}

.chat-canvas::-webkit-scrollbar-thumb {
  background: rgba(27, 26, 22, 0.14);
  border: 2px solid var(--bg);
  border-radius: 99px;
}

/* Empty */

.chat-empty {
  margin: auto;
  max-width: 400px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.empty-lane {
  width: 132px;
  height: 60px;
  margin-bottom: 12px;
}

.empty-title {
  font-size: 16px;
  font-weight: 620;
  letter-spacing: -0.015em;
  color: var(--ink);
}

.empty-desc {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--muted);
  text-wrap: pretty;
}

.empty-examples {
  width: 100%;
  margin-top: 18px;
}

.empty-examples-label {
  display: block;
  margin-bottom: 7px;
  font-size: 9.5px;
  font-weight: 600;
  letter-spacing: 0.16em;
  color: var(--faint);
}

.example-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}

.example-chip {
  min-height: 32px;
  padding: 5px 11px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  background: var(--paper);
  color: var(--ink-2);
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, background-color 0.15s ease;
}

.example-chip:hover {
  border-color: var(--accent);
  background: var(--accent-tint);
  color: var(--accent);
}

/* Messages */

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.msg-row.is-self {
  flex-direction: row-reverse;
}

.msg-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  max-width: 72%;
}

.msg-row.is-self .msg-stack {
  align-items: flex-end;
}

.bubble {
  min-width: 0;
  padding: 9px 13px;
  border-radius: 12px;
}

.contact-bubble {
  background: var(--paper);
  border: 1px solid var(--line);
  border-bottom-left-radius: 4px;
  color: var(--ink);
}

.self-bubble {
  background: var(--accent);
  border: 1px solid var(--accent);
  border-bottom-right-radius: 4px;
  color: var(--paper);
}

.bubble-content {
  margin: 0;
  font-size: 14.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.msg-time {
  display: block;
  margin-top: 4px;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  color: var(--faint);
}

/* Typing */

.typing-bubble {
  display: flex;
  align-items: center;
  min-height: 34px;
  padding: 8px 14px;
}

.typing-dots {
  display: inline-flex;
  gap: 4px;
}

.typing-dots span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--faint);
  animation: typing 1.3s infinite ease-in-out;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.18s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.36s;
}

@keyframes typing {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.35;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

/* ── Inline error ──────────────────────────────────────────── */

.inline-error {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding: 8px 14px;
  border-top: 1px solid rgba(178, 58, 46, 0.16);
  background: var(--danger-tint);
  color: #8f2f25;
  font-size: 12px;
}

.inline-error :deep(.el-icon) {
  flex-shrink: 0;
}

.inline-error-text {
  flex: 1;
  min-width: 0;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.retry-btn {
  flex-shrink: 0;
  min-height: 28px;
  padding: 3px 10px;
  border: 1px solid rgba(178, 58, 46, 0.35);
  border-radius: var(--r-xs);
  background: var(--paper);
  color: var(--danger);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.retry-btn:hover:not(:disabled) {
  border-color: var(--danger);
}

.retry-btn:disabled {
  opacity: 0.5;
  cursor: default;
}

/* ── Suggestion panel ──────────────────────────────────────── */

.suggest-panel {
  flex-shrink: 0;
  max-height: 52%;
  overflow-y: auto;
  padding: 12px 16px 14px;
  background: var(--paper);
  border-top: 1px solid var(--line-strong);
}

.suggest-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.suggest-title {
  font-size: 10.5px;
  font-weight: 650;
  letter-spacing: 0.1em;
  color: var(--muted);
}

.suggest-hint {
  font-size: 10px;
  color: var(--faint);
}

.suggest-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.suggest-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 9px 11px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease, transform 0.15s ease;
}

.suggest-card:hover {
  border-color: rgba(45, 63, 158, 0.4);
  background: var(--paper);
  transform: translateY(-1px);
}

.suggest-index {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  margin-top: 1px;
  border-radius: var(--r-xs);
  background: var(--accent-tint);
  color: var(--accent);
  font-size: 10.5px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.suggest-body {
  display: block;
  flex: 1;
  min-width: 0;
}

.suggest-content {
  display: block;
  font-size: 15px;
  line-height: 1.6;
  color: var(--ink);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.suggest-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
  min-width: 0;
}

.style-pill {
  flex-shrink: 0;
  padding: 1px 6px;
  border: 1px solid rgba(27, 26, 22, 0.08);
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.suggest-reason {
  flex: 1;
  min-width: 0;
  font-size: 11.5px;
  color: var(--faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* New topics */

.topics {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}

.topics:first-child {
  margin-top: 0;
  padding-top: 0;
  border-top: 0;
}

.topics-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 2px;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--muted);
}

.topics-hint {
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0;
  color: var(--faint);
}

.topic-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 30px;
  padding: 5px 4px;
  border: 0;
  border-radius: var(--r-xs);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.topic-row:hover {
  background: var(--wash);
}

.topic-row:hover .topic-text {
  color: var(--accent);
}

.topic-text {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s ease;
}

.topic-reason {
  flex-shrink: 0;
  max-width: 42%;
  font-size: 10.5px;
  color: var(--faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topic-icon {
  flex-shrink: 0;
  color: var(--faint);
}

/* ── Composer ──────────────────────────────────────────────── */

.composer {
  flex-shrink: 0;
  padding: 12px 16px 14px;
  background: var(--paper);
  border-top: 1px solid var(--line);
}

.composer-shell {
  border: 1px solid var(--line-strong);
  border-radius: var(--r-md);
  background: var(--surface);
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background-color 0.15s ease;
}

.composer-shell.is-focused {
  border-color: rgba(45, 63, 158, 0.55);
  background: var(--paper);
  box-shadow: 0 0 0 3px var(--accent-tint);
}

.composer-input {
  display: block;
  width: 100%;
  min-height: 46px;
  padding: 10px 12px 2px;
  border: 0;
  background: transparent;
  outline: none;
  resize: none;
  color: var(--ink);
  font-family: var(--font-sans);
  font-size: 14.5px;
  line-height: 1.6;
}

.composer-input::placeholder {
  color: var(--faint);
}

.composer-input:disabled {
  color: var(--faint);
  cursor: default;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 4px 8px 8px 12px;
}

.composer-hint {
  font-size: 10px;
  letter-spacing: 0.01em;
  color: var(--faint);
}

/* ── Responsive: mobile work surface ───────────────────────── */

@media (max-width: 960px) {
  .advisor-page {
    height: calc(
      100dvh - var(--mobile-bar-h) - var(--mobile-tabs-h) - env(safe-area-inset-bottom)
    );
    padding: 12px 14px 0;
  }

  .advisor-head {
    min-height: 40px;
    gap: 10px;
    margin-bottom: 10px;
  }

  .head-title {
    font-size: 18px;
  }

  .head-sub {
    display: none;
  }

  .head-actions {
    gap: 6px;
  }

  /* Save lives in the panel header on mobile. */
  .head-actions :deep(.el-button) {
    display: none;
  }

  .advisor-layout {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr);
    gap: 10px;
  }

  .contact-rail {
    min-height: 0;
    padding-top: 0;
  }

  .rail-head,
  .rail-search {
    display: none;
  }

  .rail-list {
    display: flex;
    flex: none;
    gap: 6px;
    margin: 0;
    padding: 0 0 2px;
    overflow-x: auto;
    overflow-y: hidden;
    scrollbar-width: none;
  }

  .rail-list::-webkit-scrollbar {
    display: none;
  }

  .rail-item {
    width: auto;
    flex-shrink: 0;
    min-height: 40px;
    margin-bottom: 0;
    padding: 5px 10px 5px 5px;
    background: var(--paper);
    border-color: var(--line);
  }

  .rail-item.is-active {
    background: var(--accent-tint);
    border-color: rgba(45, 63, 158, 0.4);
  }

  .rail-copy {
    flex-direction: row;
    align-items: center;
    gap: 6px;
  }

  .rail-name {
    font-size: 12px;
  }

  .rail-meta,
  .rail-dot {
    display: none;
  }

  .rail-empty {
    padding: 10px 0;
  }

  .panel-head {
    height: 50px;
    gap: 8px;
    padding: 0 10px 0 12px;
  }

  .panel-meta {
    font-size: 10px;
  }

  .panel-actions :deep(.el-button) {
    min-height: 40px;
    padding: 0 12px;
  }

  .icon-btn {
    width: 40px;
    height: 40px;
  }

  .chat-canvas {
    padding: 14px 12px;
    gap: 12px;
  }

  .msg-stack {
    max-width: 86%;
  }

  .bubble-content {
    font-size: 15px;
  }

  .example-chip {
    min-height: 40px;
  }

  .retry-btn {
    min-height: 40px;
  }

  .suggest-panel {
    max-height: 48%;
    padding: 10px 12px 12px;
  }

  .suggest-card {
    padding: 8px 10px;
  }

  .suggest-content {
    font-size: 14.5px;
  }

  .topic-row {
    min-height: 40px;
  }

  .composer {
    padding: 10px;
  }

  .composer-bar {
    padding: 2px 6px 6px 10px;
  }

  .composer-bar :deep(.el-button) {
    min-height: 40px;
    padding: 0 18px;
  }
}

@media (max-width: 380px) {
  .style-chip {
    padding: 0 8px;
  }

  .style-chip-key {
    display: none;
  }
}
</style>
