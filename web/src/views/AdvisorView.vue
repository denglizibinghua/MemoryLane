<template>
  <div class="advisor-page">
    <el-container class="page-container">
      <el-header class="page-header">
        <el-button text @click="handleBack">
          <el-icon><ArrowLeft /></el-icon> 返回首页
        </el-button>
        <h1>🧠 军师模式</h1>
        <span style="width:100px" />
      </el-header>

      <el-main class="advisor-main">
        <div class="simulator-wrapper">
          <!-- Contact selector -->
          <div class="contact-bar">
            <el-select
              v-model="selectedContact"
              filterable
              clearable
              placeholder="选择联系人"
              value-key="id"
              style="width:240px"
              @change="handleContactChange"
            >
              <el-option
                v-for="c in contactOptions"
                :key="c.id"
                :label="c.name"
                :value="c"
              >
                <span>{{ c.name }}</span>
                <span class="contact-platform-tag">
                  <el-tag size="small" type="info">{{ platformLabel(c.platform) }}</el-tag>
                </span>
              </el-option>
            </el-select>
          </div>

          <!-- Chat area -->
          <div ref="chatArea" class="chat-area">
            <!-- Empty state -->
            <div v-if="messages.length === 0" class="chat-empty">
              <el-empty description="选择联系人，开始模拟对话" :image-size="100" />
            </div>

            <!-- Messages -->
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="message-row"
              :class="{ 'is-self': msg.speaker === 'self' }"
            >
              <!-- Contact bubble -->
              <template v-if="msg.speaker !== 'self'">
                <div class="avatar-placeholder contact-avatar" :style="contactAvatarStyle">
                  {{ avatarEmoji(selectedContact?.name) }}
                </div>
                <div class="bubble contact-bubble">
                  <p class="bubble-content">{{ msg.content }}</p>
                  <span class="bubble-time">{{ formatTime(msg.rawTime) }}</span>
                </div>
              </template>

              <!-- Self bubble -->
              <template v-else>
                <div class="bubble self-bubble">
                  <p class="bubble-content">{{ msg.content }}</p>
                  <span class="bubble-time">{{ formatTime(msg.rawTime) }}</span>
                </div>
                <div class="avatar-placeholder self-avatar">{{ selfEmoji }}</div>
              </template>
            </div>

            <!-- Loading indicator -->
            <div v-if="loading" class="message-row">
              <div class="avatar-placeholder contact-avatar" :style="contactAvatarStyle">
                {{ avatarEmoji(selectedContact?.name) }}
              </div>
              <div class="bubble contact-bubble loading-bubble">
                <span class="typing-dots"><span>.</span><span>.</span><span>.</span></span>
              </div>
            </div>

            <!-- Suggestion chips below last contact message -->
            <div v-if="showSuggestions" class="suggestions-row">
              <p class="suggestions-label">💡 AI 回复建议 — 点击选择：</p>
              <div class="suggestion-chips">
                <div
                  v-for="(s, idx) in currentSuggestions"
                  :key="idx"
                  class="suggestion-card"
                  :style="{ borderLeftColor: getStyleTagColor(s.style) }"
                  @click="handlePickSuggestion(s)"
                >
                  <p class="suggestion-text">{{ s.content }}</p>
                  <div class="suggestion-meta">
                    <el-tag :color="getStyleTagColor(s.style)" effect="dark" size="small">
                      {{ s.style }}
                    </el-tag>
                    <span class="suggestion-reason">{{ s.reason }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Error toast -->
            <el-alert
              v-if="error"
              :title="error"
              type="error"
              show-icon
              closable
              class="chat-error"
              @close="error = ''"
            />
          </div>

          <!-- Input area -->
          <div class="input-area">
            <el-input
              v-model="inputText"
              placeholder="输入对方说的话..."
              :disabled="!selectedContact"
              :rows="2"
              type="textarea"
              resize="none"
              class="chat-input"
              @keydown.enter.exact.prevent="handleSend"
            />
            <div class="input-actions">
              <el-button
                v-if="messages.length > 0"
                :loading="saving"
                :disabled="saving"
                @click="handleSave"
              >
                💾 保存对话
              </el-button>
              <el-button
                type="primary"
                :loading="loading"
                :disabled="!canSend"
                @click="handleSend"
              >
                发送
              </el-button>
            </div>
          </div>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'
import { suggestAdvisor, saveConversation, type AdvisorReply, type ConversationMessage } from '@/api/advisor'

// ── Types ──────────────────────────────────────────────────────────

interface ContactOption {
  id: number
  name: string
  platform: string
}

interface ChatMessage {
  id: string
  speaker: string   // "self" | contact name
  content: string
  rawTime: Date
}

interface Suggestion {
  style: string
  content: string
  reason: string
}

// ── State ──────────────────────────────────────────────────────────

const router = useRouter()
const contactOptions = ref<ContactOption[]>([])
const selectedContact = ref<ContactOption | null>(null)
let previousContact: ContactOption | null = null
const messages = ref<ChatMessage[]>([])
const currentSuggestions = ref<Suggestion[]>([])
const inputText = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const chatArea = ref<HTMLElement | null>(null)

const lastContactMessage = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].speaker !== 'self') return messages.value[i]
  }
  return null
})

const showSuggestions = computed(() =>
  currentSuggestions.value.length > 0 && !loading.value
)

const canSend = computed(() =>
  selectedContact.value !== null && inputText.value.trim().length > 0 && !loading.value
)

// Pick a fun self-avatar — one per session
const selfEmoji = (() => {
  const pool = ['🐱', '🐶', '🐰', '🦊', '🐼', '🐨', '🐙', '🦄', '🐣', '🐳']
  return pool[Math.floor(Math.random() * pool.length)]
})()

const CONTACT_EMOJI_POOL = [
  '🐻', '🐸', '🐵', '🦁', '🐮', '🐷', '🐭', '🐹', '🐯', '🐺',
  '🐗', '🐴', '🦉', '🐌', '🐞', '🐝', '🦋', '🐠', '🐡', '🐬',
  '🦀', '🦑', '🐊', '🐢', '🦎', '🐍', '🦕', '🦖', '🐲', '🌵',
]

const CONTACT_COLOR_POOL = [
  ['#e17055', '#d63031'], ['#00b894', '#00cec9'], ['#0984e3', '#6c5ce7'],
  ['#fdcb6e', '#e17055'], ['#a29bfe', '#6c5ce7'], ['#fd79a8', '#e84393'],
  ['#fab1a0', '#e17055'], ['#81ecec', '#00b894'], ['#ff7675', '#d63031'],
  ['#74b9ff', '#0984e3'],
]

function avatarEmoji(name: string | null | undefined): string {
  if (!name) return '👤'
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return CONTACT_EMOJI_POOL[Math.abs(hash) % CONTACT_EMOJI_POOL.length]
}

const contactAvatarStyle = computed(() => {
  const name = selectedContact.value?.name
  if (!name) return { background: '#c0c0c0' }
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  const colors = CONTACT_COLOR_POOL[Math.abs(hash) % CONTACT_COLOR_POOL.length]
  return { background: `linear-gradient(135deg, ${colors[0]}, ${colors[1]})` }
})

// ── Lifecycle ──────────────────────────────────────────────────────

onMounted(async () => {
  try {
    const res = await api.get('/contacts')
    contactOptions.value = (res.data as ContactOption[]) || []
  } catch {
    // contacts API unavailable — leave options empty
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
  return messages.value.map(m =>
    `${m.speaker === 'self' ? '我' : selectedContact.value!.name}: ${m.content}`
  )
}

function platformLabel(p: string) {
  const map: Record<string, string> = {
    wechat: '微信', qq: 'QQ',
    douyin: '抖音', sms: '短信', other: '其他',
  }
  return map[p] || p
}

function getStyleTagColor(style: string): string {
  const map: Record<string, string> = {
    轻松随性: '#67c23a',
    引用约定: '#409eff',
    关心体贴: '#e6a23c',
    直接坦率: '#f56c6c',
    幽默调侃: '#909399',
  }
  return map[style] || '#909399'
}

// ── Actions ────────────────────────────────────────────────────────

async function handleSend() {
  if (!canSend.value) return

  const content = inputText.value.trim()
  inputText.value = ''
  currentSuggestions.value = []

  // Push contact message
  messages.value.push({
    id: uid(),
    speaker: selectedContact.value!.name,
    content,
    rawTime: new Date(),
  })
  await scrollToBottom()

  // Fetch AI suggestions
  loading.value = true
  error.value = ''
  try {
    const result = await suggestAdvisor({
      contactId: selectedContact.value!.id,
      lastMessage: content,
      recentContext: buildRecentContext(),
    })
    currentSuggestions.value = result.replies || []
    if (currentSuggestions.value.length === 0) {
      ElMessage.info('没有生成回复建议')
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || '请求失败'
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

function handlePickSuggestion(s: Suggestion) {
  messages.value.push({
    id: uid(),
    speaker: 'self',
    content: s.content,
    rawTime: new Date(),
  })
  currentSuggestions.value = []
  scrollToBottom()
}

async function handleSave() {
  if (!selectedContact.value || messages.value.length === 0) return

  saving.value = true
  try {
    const convMessages: ConversationMessage[] = messages.value.map(m => ({
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
    messages.value = []
    currentSuggestions.value = []
    inputText.value = ''
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

function handleContactChange() {
  if (messages.value.length > 0) {
    ElMessageBox.confirm(
      '当前对话未保存，切换联系人将清空聊天记录。确定切换？',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    ).then(() => {
      previousContact = selectedContact.value
      messages.value = []
      currentSuggestions.value = []
      inputText.value = ''
      error.value = ''
    }).catch(() => {
      // Revert selection
      selectedContact.value = previousContact
    })
  } else {
    previousContact = selectedContact.value
  }
}

function handleBack() {
  if (messages.value.length > 0) {
    ElMessageBox.confirm(
      '当前对话未保存，返回将清空聊天记录。确定返回？',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    ).then(() => {
      messages.value = []
      currentSuggestions.value = []
      inputText.value = ''
      router.push('/')
    }).catch(() => {
      // user cancelled — stay on page
    })
  } else {
    router.push('/')
  }
}
</script>

<style scoped>
.advisor-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.page-container {
  max-width: 720px;
  margin: 0 auto;
  padding: 0 16px;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  height: auto;
  flex-shrink: 0;
}

.page-header h1 {
  font-size: 18px;
  font-weight: 700;
  margin: 0;
  color: #1e1b4b;
}

.advisor-main {
  padding: 0;
  flex: 1;
  overflow: hidden;
  display: flex;
}

.simulator-wrapper {
  width: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px 12px 0 0;
  overflow: hidden;
}

/* ── Contact bar ─────────────────────────────────── */

.contact-bar {
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  background: #fafafa;
  flex-shrink: 0;
}

.contact-platform-tag {
  float: right;
  margin-left: 8px;
}

/* ── Chat area ───────────────────────────────────── */

.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-area::-webkit-scrollbar {
  width: 4px;
}

.chat-area::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 2px;
}

.chat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── Message rows ────────────────────────────────── */

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.message-row.is-self {
  flex-direction: row-reverse;
}

/* ── Avatars ─────────────────────────────────────── */

.avatar-placeholder {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  color: #fff;
}

.contact-avatar {
  background: #c0c0c0;
}

.self-avatar {
  background: linear-gradient(135deg, #7c55d8, #5b3cc4);
}

/* ── Bubbles ─────────────────────────────────────── */

.bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 8px;
  position: relative;
}

.contact-bubble {
  background: #e8e8e8;
  color: #1f2937;
}

.self-bubble {
  background: linear-gradient(135deg, #7c55d8, #5b3cc4);
  color: #fff;
}

.bubble-content {
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.bubble-time {
  display: block;
  font-size: 11px;
  margin-top: 4px;
  opacity: 0.7;
  text-align: right;
}

/* ── Loading bubble ──────────────────────────────── */

.loading-bubble {
  min-width: 60px;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.typing-dots span {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #999;
  margin: 0 2px;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

/* ── Suggestions ─────────────────────────────────── */

.suggestions-row {
  padding-left: 44px;  /* align with bubble after avatar */
}

.suggestions-label {
  font-size: 12px;
  color: #9ca3af;
  margin: 0 0 8px 0;
}

.suggestion-chips {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.suggestion-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-left: 4px solid #409eff;
  border-radius: 8px;
  padding: 12px 14px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  max-width: 100%;
}

.suggestion-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.suggestion-text {
  margin: 0 0 8px 0;
  font-size: 15px;
  line-height: 1.7;
  color: #1f2937;
  word-break: break-word;
  white-space: pre-wrap;
}

.suggestion-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.suggestion-reason {
  font-size: 12px;
  color: #9ca3af;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Chat error ──────────────────────────────────── */

.chat-error {
  margin: 8px 0;
}

/* ── Input area ──────────────────────────────────── */

.input-area {
  padding: 12px 16px;
  border-top: 1px solid #eee;
  background: #fafafa;
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.chat-input {
  flex: 1;
}

.chat-input :deep(.el-textarea__inner) {
  border-radius: 8px;
}

.input-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ── Responsive ──────────────────────────────────── */

@media (max-width: 600px) {
  .page-container {
    padding: 0 8px;
  }

  .bubble {
    max-width: 85%;
  }

  .input-area {
    flex-direction: column;
    align-items: stretch;
  }

  .input-actions {
    justify-content: flex-end;
  }
}
</style>
