<template>
  <div class="home">
    <div class="page home-inner">
      <!-- Hero: thesis + action -->
      <section class="hero">
        <div class="hero-copy">
          <h1 class="hero-title">
            把聊过的，<br />
            <span class="hero-accent">都记住。</span>
          </h1>
          <p class="hero-desc">
            粘贴一段聊天记录。忆路会认出对话里的人，把约定、偏好和细节收进记忆库——下次开口前，先问它一句。
          </p>

          <dl class="hero-stats">
            <div class="hero-stat">
              <dt>联系人</dt>
              <dd class="tabular">{{ contacts.length }}</dd>
            </div>
            <div class="hero-stat">
              <dt>消息</dt>
              <dd class="tabular">{{ totalMessages === null ? '—' : totalMessages }}</dd>
            </div>
            <div class="hero-stat">
              <dt>待确认约定</dt>
              <dd class="tabular">{{ pendingCount === null ? '—' : pendingCount }}</dd>
            </div>
          </dl>

          <p class="hero-note">
            <el-icon :size="13"><Lock /></el-icon>
            <span>聊天原文留在你的数据库；AI 提炼时，只把必要片段发给你配置的模型服务。</span>
          </p>
        </div>

        <!-- Composer -->
        <section class="composer" :class="{ 'is-focused': composerFocused }">
          <header class="composer-top">
            <div class="mode-tabs" role="tablist" aria-label="导入方式">
              <button
                type="button"
                role="tab"
                class="mode-tab"
                :class="{ 'is-active': mode === 'text' }"
                :aria-selected="mode === 'text'"
                @click="mode = 'text'"
              >
                <el-icon :size="14"><EditPen /></el-icon>
                粘贴文本
              </button>
              <button
                type="button"
                role="tab"
                class="mode-tab"
                :class="{ 'is-active': mode === 'image' }"
                :aria-selected="mode === 'image'"
                @click="mode = 'image'"
              >
                <el-icon :size="14"><Camera /></el-icon>
                截图识别
              </button>
            </div>
            <span class="composer-formats">微信 · QQ · 抖音 · 短信</span>
          </header>

          <!-- Text import -->
          <template v-if="mode === 'text'">
            <div class="composer-body">
              <textarea
                v-model="pasteText"
                class="paste-input"
                :disabled="previewLoading || importing || !!previewResult"
                placeholder="把聊天记录粘在这里。例如：&#10;&#10;张三  下午 2:30&#10;周末去不去爬山？&#10;&#10;我  下午 2:31&#10;行啊，几点集合？"
                spellcheck="false"
                aria-label="粘贴聊天记录"
                @focus="composerFocused = true"
                @blur="composerFocused = false"
              ></textarea>

              <div class="composer-meta">
                <el-select v-model="platform" size="small" class="format-select" aria-label="聊天格式">
                  <el-option label="自动识别格式" value="auto" />
                  <el-option label="微信" value="wechat" />
                  <el-option label="QQ" value="qq" />
                  <el-option label="抖音" value="douyin" />
                  <el-option label="通用文本" value="generic" />
                </el-select>
                <span class="char-count tabular">{{ pasteText.length }} 字</span>
              </div>

              <!-- Preview summary -->
              <div v-if="previewResult" class="preview-strip">
                <div class="preview-top">
                  <div>
                    <p class="preview-title">已识别 {{ previewResult.messageCount }} 条消息</p>
                    <p class="preview-sub">
                      对话格式：{{ platformLabel(previewResult.platform) }} · 先告诉我哪一位是你
                    </p>
                  </div>
                  <span class="preview-speakers">{{ previewResult.speakers.length }} 位说话人</span>
                </div>

                <div class="speaker-select">
                  <button
                    v-if="pasteHasSelf"
                    type="button"
                    class="speaker-chip"
                    :class="{ 'is-active': selfName === '我' }"
                    @click="selfName = '我'"
                  >
                    <span class="speaker-dot"></span>
                    我（自己）
                    <el-icon v-if="selfName === '我'" :size="12"><Check /></el-icon>
                  </button>
                  <button
                    v-for="s in previewResult.speakers"
                    :key="s"
                    type="button"
                    class="speaker-chip"
                    :class="{ 'is-active': selfName === s }"
                    @click="selfName = s"
                  >
                    <span class="speaker-dot"></span>
                    {{ s }}
                    <el-icon v-if="selfName === s" :size="12"><Check /></el-icon>
                  </button>
                </div>
              </div>

              <p v-if="importError" class="composer-error">
                <el-icon :size="14"><WarningFilled /></el-icon>
                <span>{{ importError }}</span>
              </p>

              <div v-if="importing" class="composer-progress">
                <el-icon class="is-loading" :size="15"><Loading /></el-icon>
                <span>正在写入消息、分配联系人，稍后会在后台提炼记忆…</span>
              </div>
            </div>

            <footer class="composer-foot">
              <template v-if="!previewResult">
                <span class="foot-note">导入后自动去重，并在后台提炼记忆</span>
                <el-button type="primary" :loading="previewLoading" :disabled="!pasteText.trim()" @click="handlePreview">
                  解析这段对话
                </el-button>
              </template>
              <template v-else>
                <div class="foot-left">
                  <el-button text :disabled="importing" @click="resetImport">重新粘贴</el-button>
                </div>
                <el-button type="primary" :loading="importing" :disabled="!selfName" @click="handleImport">
                  导入并分析
                </el-button>
              </template>
            </footer>
          </template>

          <!-- Screenshot import -->
          <template v-else>
            <div class="composer-body">
              <input
                ref="screenshotFileInput"
                type="file"
                accept="image/*"
                class="sr-only"
                @change="handleScreenshotSelect"
              />

              <div
                v-if="!screenshotFile"
                class="drop-zone"
                :class="{ 'is-dragging': dragging }"
                role="button"
                tabindex="0"
                @click="screenshotFileInput?.click()"
                @keydown.enter.prevent="screenshotFileInput?.click()"
                @dragover.prevent="dragging = true"
                @dragleave.prevent="dragging = false"
                @drop.prevent="handleDrop"
              >
                <span class="drop-icon"><el-icon :size="22"><Picture /></el-icon></span>
                <p class="drop-title">把聊天截图拖到这里</p>
                <p class="drop-sub">或点击选择图片 · PNG / JPG / WEBP</p>
              </div>

              <template v-else>
                <div class="shot-preview">
                  <img :src="screenshotPreview" alt="待识别的聊天截图" />
                  <button type="button" class="shot-remove" aria-label="重新选择截图" @click="resetScreenshot">
                    <el-icon :size="14"><Close /></el-icon>
                  </button>
                </div>
                <div class="shot-meta">
                  <span class="shot-name">{{ screenshotFile.name }}</span>
                  <span class="shot-size tabular">{{ formatFileSize(screenshotFile.size) }}</span>
                </div>
              </template>

              <p v-if="visionHint" class="vision-hint">
                <el-icon :size="13"><WarningFilled /></el-icon>
                <span>{{ visionHint }}</span>
              </p>

              <p v-if="importError" class="composer-error">
                <el-icon :size="14"><WarningFilled /></el-icon>
                <span>{{ importError }}</span>
              </p>

              <div v-if="screenshotLoading" class="composer-progress">
                <el-icon class="is-loading" :size="15"><Loading /></el-icon>
                <span>正在识别截图里的文字和说话人…</span>
              </div>
            </div>

            <footer class="composer-foot">
              <span class="foot-note">识别后会回到文本预览，确认「我是谁」再导入</span>
              <el-button
                v-if="screenshotFile"
                type="primary"
                :loading="screenshotLoading"
                @click="handleScreenshotPreview"
              >
                识别这张截图
              </el-button>
              <el-button v-else type="primary" @click="screenshotFileInput?.click()">选择截图</el-button>
            </footer>
          </template>
        </section>
      </section>

      <!-- Import receipt -->
      <section v-if="importResult && importResult.contacts.length > 0" ref="receiptRef" class="receipt">
        <header class="receipt-head">
          <span class="receipt-check"><el-icon :size="16"><Check /></el-icon></span>
          <div class="receipt-copy">
            <h2>已导入 {{ importResult.stats.newMessages }} 条消息</h2>
            <p>
              {{ importResult.stats.duplicates }} 条重复已跳过 · 记忆正在后台提炼，这个页面会自动更新
            </p>
          </div>
          <el-button text @click="resetImport">再导入一段</el-button>
        </header>
        <ul class="receipt-list">
          <li v-for="cr in importResult.contacts" :key="cr.contactId" class="receipt-item">
            <MonogramAvatar :name="cr.contactName" :size="38" />
            <div class="receipt-contact">
              <RouterLink :to="`/contacts/${cr.contactId}`" class="receipt-name">
                {{ cr.contactName }}
              </RouterLink>
              <span class="receipt-meta">{{ cr.messageCount }} 条消息</span>
            </div>
            <span v-if="extractingContacts[cr.contactId] !== undefined" class="receipt-state is-working">
              <el-icon class="is-loading" :size="12"><Loading /></el-icon>
              提炼中
            </span>
            <span v-else class="receipt-state is-done">
              <el-icon :size="12"><Check /></el-icon>
              {{ contactMemories[cr.contactId] || 0 }} 条记忆
            </span>
            <RouterLink :to="`/contacts/${cr.contactId}`" class="receipt-link">
              查看
              <el-icon :size="11"><ArrowRight /></el-icon>
            </RouterLink>
          </li>
        </ul>
      </section>

      <!-- Pipeline -->
      <section class="pipeline-section">
        <div class="section-head">
          <h2>一段聊天，会走完这五步</h2>
          <p>从原始对话到一句能直接发出去的回复，中间每一步都可以回看。</p>
        </div>
        <ol class="pipeline">
          <li v-for="step in pipeline" :key="step.no" class="pipeline-step">
            <span class="step-no tabular">{{ step.no }}</span>
            <span class="step-icon"><el-icon :size="17"><component :is="step.icon" /></el-icon></span>
            <h3>{{ step.title }}</h3>
            <p>{{ step.desc }}</p>
          </li>
        </ol>
      </section>

      <!-- Lower: recent + entries -->
      <section class="home-lower">
        <div class="recent-panel">
          <header class="recent-head">
            <h2>最近联系</h2>
            <RouterLink to="/contacts" class="quiet-link">
              全部
              <el-icon :size="11"><ArrowRight /></el-icon>
            </RouterLink>
          </header>

          <ul v-if="recentContacts.length" class="recent-list">
            <li v-for="c in recentContacts" :key="c.id">
              <RouterLink :to="`/contacts/${c.id}`" class="recent-item">
                <MonogramAvatar :name="c.name" :size="36" />
                <span class="recent-copy">
                  <span class="recent-name">{{ c.name }}</span>
                  <span class="recent-meta">
                    {{ platformLabel(c.platform) }}
                    <template v-if="messageCounts[c.id] !== undefined">
                      · {{ messageCounts[c.id] }} 条消息
                    </template>
                  </span>
                </span>
                <el-icon class="recent-arrow" :size="13"><ArrowRight /></el-icon>
              </RouterLink>
            </li>
          </ul>

          <div v-else class="recent-empty">
            <p class="recent-empty-title">还没有归档的联系人</p>
            <p class="recent-empty-desc">粘一段聊天，识别出的说话人会出现在这里。</p>
          </div>
        </div>

        <nav class="entry-list" aria-label="功能入口">
          <RouterLink v-for="entry in entries" :key="entry.to" :to="entry.to" class="entry-row">
            <span class="entry-icon"><el-icon :size="17"><component :is="entry.icon" /></el-icon></span>
            <span class="entry-copy">
              <span class="entry-title">{{ entry.title }}</span>
              <span class="entry-desc">{{ entry.desc }}</span>
            </span>
            <el-icon class="entry-arrow" :size="13"><ArrowRight /></el-icon>
          </RouterLink>
        </nav>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import {
  ArrowRight,
  Camera,
  ChatLineRound,
  Check,
  Close,
  EditPen,
  Loading,
  Lock,
  Memo,
  Picture,
  Search,
  TrendCharts,
  User,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { Component } from 'vue'
import { useMemoryStore } from '@/stores/memories'
import { useContactStore } from '@/stores/contacts'
import { getAiSettings } from '@/api/settings'
import { fetchDueReminders } from '@/api/reminders'
import { fetchOverview } from '@/api/analytics'
import type { ImportResult, PreviewResult } from '@/stores/memories'
import { platformLabel } from '@/utils/presentation'
import MonogramAvatar from '@/components/MonogramAvatar.vue'
import api from '@/api'

interface PipelineStep {
  no: string
  title: string
  desc: string
  icon: Component
}

const memoryStore = useMemoryStore()
const contactStore = useContactStore()
const contacts = computed(() => contactStore.contacts)

const pasteText = ref('')
const platform = ref('auto')
const selfName = ref('')
const pasteHasSelf = ref(false)
const previewResult = ref<PreviewResult | null>(null)
const previewLoading = ref(false)
const importing = ref(false)
const importResult = ref<ImportResult | null>(null)
const importError = ref('')
const composerFocused = ref(false)
const receiptRef = ref<HTMLElement | null>(null)

const totalMessages = ref<number | null>(null)
const pendingCount = ref<number | null>(null)
const messageCounts = ref<Record<number, number>>({})
const lastMessageAt = ref<Record<number, number>>({})

const extractingContacts = ref<Record<number, string>>({})
const contactMemories = ref<Record<number, number>>({})
let memoryPollTimer: ReturnType<typeof setInterval> | null = null

const mode = ref<'text' | 'image'>('text')
const screenshotFile = ref<File | null>(null)
const screenshotPreview = ref('')
const screenshotLoading = ref(false)
const screenshotFileInput = ref<HTMLInputElement | null>(null)
const dragging = ref(false)

const currentChatProvider = ref('')
const currentChatModel = ref('')

const recentContacts = computed(() =>
  [...contacts.value]
    .sort((a, b) => {
      const byMessage = (lastMessageAt.value[b.id] || 0) - (lastMessageAt.value[a.id] || 0)
      if (byMessage !== 0) return byMessage
      return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
    })
    .slice(0, 5)
)

const visionHint = computed(() => {
  if (!currentChatProvider.value) return ''
  const visionProviders = ['openai', 'anthropic', 'dashscope', 'custom']
  if (visionProviders.includes(currentChatProvider.value)) return ''
  return `当前模型 ${currentChatModel.value || providerLabel(currentChatProvider.value)} 可能不支持读图。识别失败时会尝试降级到 Tesseract 离线 OCR。`
})

const pipeline: PipelineStep[] = [
  { no: '01', title: '导入', desc: '粘贴文本或截图，自动识别微信、QQ、抖音、短信等常见格式。', icon: EditPen },
  { no: '02', title: '识人', desc: '分辨每一句是谁说的，把消息归到同一个人名下。', icon: User },
  { no: '03', title: '记忆', desc: '提炼约定、偏好、个人信息、事件等 6 类结构化记忆。', icon: Memo },
  { no: '04', title: '检索', desc: '全文检索每条记忆；语义检索可在设置里开启。', icon: Search },
  { no: '05', title: '军师', desc: '结合历史记忆与你的语气，给出能直接用的回复。', icon: ChatLineRound },
]

const entries = [
  { to: '/memories', title: '记忆库', desc: '每条记忆都能翻到原话', icon: Memo },
  { to: '/advisor', title: '军师模式', desc: '把对方的话，接得更自然', icon: ChatLineRound },
  { to: '/analytics', title: '关系动态', desc: '看见消息背后的节奏', icon: TrendCharts },
]

onMounted(async () => {
  try {
    const settings = await getAiSettings()
    currentChatProvider.value = settings.provider || ''
    currentChatModel.value = settings.model || ''
  } catch {
    // AI settings are optional for the home page.
  }

  await Promise.allSettled([
    loadContacts(),
    loadOverview(),
    loadPending(),
  ])
})

onUnmounted(() => {
  if (memoryPollTimer) clearInterval(memoryPollTimer)
  if (screenshotPreview.value) URL.revokeObjectURL(screenshotPreview.value)
})

async function loadContacts() {
  try {
    await contactStore.fetchAll()
  } catch {
    // Backend may be offline; the composer remains usable.
  }
}

async function loadOverview() {
  try {
    const overview = await fetchOverview()
    totalMessages.value = overview.reduce((sum, c) => sum + (c.totalMessages || 0), 0)
    const counts: Record<number, number> = {}
    for (const c of overview) {
      counts[c.contactId] = c.totalMessages
      lastMessageAt.value[c.contactId] = c.lastMsgAt ? new Date(c.lastMsgAt).getTime() : 0
    }
    messageCounts.value = counts
  } catch {
    totalMessages.value = null
  }
}

async function loadPending() {
  try {
    const due = await fetchDueReminders()
    pendingCount.value = due.pendingCount || 0
  } catch {
    pendingCount.value = null
  }
}

function providerLabel(key: string): string {
  const map: Record<string, string> = {
    openai: 'OpenAI',
    deepseek: 'DeepSeek',
    ollama: 'Ollama',
    anthropic: 'Anthropic',
    dashscope: '通义千问',
    zhipuai: '智谱 GLM',
    moonshot: 'Kimi',
    custom: '自定义',
  }
  return map[key] || key
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function resetImport() {
  previewResult.value = null
  selfName.value = ''
  pasteHasSelf.value = false
  importResult.value = null
  importError.value = ''
  if (memoryPollTimer) {
    clearInterval(memoryPollTimer)
    memoryPollTimer = null
  }
  extractingContacts.value = {}
  contactMemories.value = {}
}

async function handlePreview() {
  if (!pasteText.value.trim()) {
    ElMessage.warning('请先粘贴聊天记录')
    return
  }
  previewLoading.value = true
  previewResult.value = null
  importError.value = ''
  try {
    previewResult.value = await memoryStore.previewImport(pasteText.value, platform.value)
    pasteHasSelf.value = /^我[：:]\s*\S/m.test(pasteText.value)
    selfName.value = pasteHasSelf.value ? '我' : ''
  } catch (e: any) {
    importError.value = e?.response?.data?.message || e?.message || '解析失败，请检查聊天记录格式'
  } finally {
    previewLoading.value = false
  }
}

async function handleImport() {
  if (!selfName.value) {
    ElMessage.warning('请先选择「我是谁」')
    return
  }
  importing.value = true
  importResult.value = null
  importError.value = ''
  try {
    importResult.value = await memoryStore.importText(selfName.value, platform.value, pasteText.value)
    pasteText.value = ''
    previewResult.value = null
    if (importResult.value.contacts.length === 0) {
      ElMessage.success(`已导入 ${importResult.value.stats.newMessages} 条消息`)
    }
    await nextTick()
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    receiptRef.value?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'center' })
    startMemoryPolling(importResult.value)
    loadContacts()
    loadOverview()
    loadPending()
  } catch (e: any) {
    importError.value = e?.response?.data?.message || e?.message || '导入失败，请重试'
  } finally {
    importing.value = false
  }
}

function handleScreenshotSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  acceptScreenshot(file)
  input.value = ''
}

function handleDrop(event: DragEvent) {
  dragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  acceptScreenshot(file)
}

function acceptScreenshot(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (screenshotPreview.value) URL.revokeObjectURL(screenshotPreview.value)
  screenshotFile.value = file
  screenshotPreview.value = URL.createObjectURL(file)
  importError.value = ''
}

function resetScreenshot() {
  if (screenshotPreview.value) URL.revokeObjectURL(screenshotPreview.value)
  screenshotFile.value = null
  screenshotPreview.value = ''
  screenshotLoading.value = false
  dragging.value = false
}

async function handleScreenshotPreview() {
  if (!screenshotFile.value) {
    ElMessage.warning('请先选择截图')
    return
  }
  screenshotLoading.value = true
  importError.value = ''
  try {
    const result = await memoryStore.previewScreenshot(screenshotFile.value)
    pasteText.value = result.ocrText
    platform.value = result.platform || 'auto'
    resetScreenshot()
    mode.value = 'text'
    await handlePreview()
  } catch (e: any) {
    importError.value = e?.response?.data?.message || e?.message || '截图识别失败，请重试'
  } finally {
    screenshotLoading.value = false
  }
}

function startMemoryPolling(result: ImportResult) {
  if (memoryPollTimer) clearInterval(memoryPollTimer)
  extractingContacts.value = {}
  contactMemories.value = {}

  for (const cr of result.contacts) {
    extractingContacts.value[cr.contactId] = cr.contactName
    contactMemories.value[cr.contactId] = 0
  }

  let attempts = 0
  const maxAttempts = 30

  memoryPollTimer = setInterval(async () => {
    attempts++
    let allDone = true

    for (const contactId of Object.keys(extractingContacts.value).map(Number)) {
      try {
        const res = await api.get(`/memories/contact/${contactId}`)
        const count = Array.isArray(res.data) ? res.data.length : 0
        if (count > 0) {
          contactMemories.value[contactId] = count
          delete extractingContacts.value[contactId]
        } else {
          allDone = false
        }
      } catch {
        allDone = false
      }
    }

    if (allDone || attempts >= maxAttempts) {
      if (memoryPollTimer) clearInterval(memoryPollTimer)
      memoryPollTimer = null
      const total = Object.values(contactMemories.value).reduce((a, b) => a + b, 0)
      if (total > 0) ElMessage.success(`记忆分析完成，共提取 ${total} 条记忆`)
    }
  }, 2000)
}
</script>

<style scoped>
.home {
  min-height: 100%;
}

.home-inner {
  padding-top: 44px;
}

/* ── Hero ──────────────────────────────────────────────────── */

.hero {
  display: grid;
  grid-template-columns: minmax(300px, 0.82fr) minmax(520px, 1.18fr);
  gap: 54px;
  align-items: start;
}

.hero-copy {
  padding-top: 14px;
}

.hero-title {
  font-size: 50px;
  line-height: 1.08;
  letter-spacing: -0.045em;
  color: var(--ink);
}

.hero-accent {
  color: var(--accent);
  background: linear-gradient(transparent 66%, var(--seal-tint) 66%, var(--seal-tint) 92%, transparent 92%);
  padding: 0 0.04em;
}

.hero-desc {
  max-width: 38ch;
  margin-top: 20px;
  font-size: 14.5px;
  line-height: 1.85;
  color: var(--muted);
}

.hero-stats {
  display: flex;
  gap: 28px;
  margin: 30px 0 0;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.hero-stat dt {
  font-size: 10.5px;
  letter-spacing: 0.06em;
  color: var(--faint);
}

.hero-stat dd {
  margin: 4px 0 0;
  font-size: 20px;
  font-weight: 650;
  line-height: 1;
  color: var(--ink);
}

.hero-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 26px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--faint);
}

.hero-note :deep(.el-icon) {
  margin-top: 1px;
  flex-shrink: 0;
}

/* ── Composer ──────────────────────────────────────────────── */

.composer {
  background: var(--paper);
  border: 1px solid var(--line-strong);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.composer.is-focused {
  border-color: #b5bddf;
  box-shadow: var(--shadow-sm), 0 0 0 4px var(--accent-tint);
}

.composer-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 20px 0;
}

.mode-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
}

.mode-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 2px 10px;
  margin-right: 16px;
  border: 0;
  background: transparent;
  color: var(--faint);
  font-size: 13px;
  font-weight: 550;
  cursor: pointer;
  transition: color 0.15s ease;
}

.mode-tab::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  border-radius: 2px;
  background: transparent;
  transition: background-color 0.15s ease;
}

.mode-tab:hover {
  color: var(--ink-2);
}

.mode-tab.is-active {
  color: var(--ink);
}

.mode-tab.is-active::after {
  background: var(--ink);
}

.composer-formats {
  font-size: 10.5px;
  color: var(--faint);
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.composer-body {
  padding: 8px 20px 0;
}

.paste-input {
  display: block;
  width: 100%;
  min-height: 236px;
  padding: 12px 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-family: var(--font-sans);
  font-size: 14.5px;
  line-height: 1.85;
  resize: vertical;
  outline: none;
}

.paste-input::placeholder {
  color: var(--faint);
}

.paste-input:disabled {
  color: var(--muted);
  cursor: default;
}

.composer-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0 14px;
  border-top: 1px solid var(--line);
}

.format-select {
  width: 146px;
}

.format-select :deep(.el-select__wrapper) {
  background: transparent;
  box-shadow: none;
  padding-left: 0;
  min-height: 28px;
}

.format-select :deep(.el-select__wrapper:hover) {
  box-shadow: none;
}

.format-select :deep(.el-select__wrapper.is-focused) {
  box-shadow: none;
}

.format-select :deep(.el-select__selected-item) {
  font-size: 12px;
  color: var(--muted);
}

.char-count {
  font-size: 10.5px;
  color: var(--faint);
}

.preview-strip {
  margin-bottom: 14px;
  padding: 14px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}

.preview-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.preview-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}

.preview-sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--muted);
}

.preview-speakers {
  flex-shrink: 0;
  font-size: 10.5px;
  color: var(--faint);
  white-space: nowrap;
}

.speaker-select {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.speaker-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  background: var(--paper);
  color: var(--ink-2);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease, color 0.15s ease;
}

.speaker-chip:hover {
  border-color: #aab2d6;
}

.speaker-chip.is-active {
  border-color: var(--accent);
  background: var(--accent-tint);
  color: var(--accent);
  font-weight: 600;
}

.speaker-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.45;
}

.speaker-chip.is-active .speaker-dot {
  opacity: 1;
}

.composer-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0 0 12px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
  background: var(--danger-tint);
  color: #8f2f25;
  font-size: 12px;
  line-height: 1.6;
}

.composer-error :deep(.el-icon) {
  margin-top: 1px;
  flex-shrink: 0;
}

.composer-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
  background: var(--accent-tint);
  color: var(--accent);
  font-size: 12px;
}

.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 20px;
  background: #faf9f4;
  border-top: 1px solid var(--line);
}

.foot-note {
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--faint);
}

.foot-left {
  display: flex;
  align-items: center;
}

/* ── Screenshot ────────────────────────────────────────────── */

.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 250px;
  padding: 28px 20px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-md);
  background: #faf9f4;
  cursor: pointer;
  transition: border-color 0.16s ease, background-color 0.16s ease;
}

.drop-zone:hover,
.drop-zone.is-dragging {
  border-color: var(--accent);
  background: var(--accent-tint);
}

.drop-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: var(--r-md);
  background: var(--paper);
  border: 1px solid var(--line);
  color: var(--muted);
}

.drop-title {
  margin-top: 12px;
  font-size: 13.5px;
  font-weight: 550;
  color: var(--ink-2);
}

.drop-sub {
  margin-top: 3px;
  font-size: 11px;
  color: var(--faint);
}

.shot-preview {
  position: relative;
  display: grid;
  place-items: center;
  padding: 12px;
  background: #faf9f4;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}

.shot-preview img {
  max-width: 100%;
  max-height: 220px;
  border-radius: var(--r-sm);
  object-fit: contain;
}

.shot-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--line);
  border-radius: var(--r-xs);
  background: var(--paper);
  color: var(--muted);
  cursor: pointer;
}

.shot-remove:hover {
  color: var(--ink);
  background: var(--wash);
}

.shot-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--muted);
}

.shot-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shot-size {
  flex-shrink: 0;
  color: var(--faint);
}

.vision-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 12px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
  background: var(--warning-tint);
  color: #7d500f;
  font-size: 11px;
  line-height: 1.6;
}

.vision-hint :deep(.el-icon) {
  margin-top: 1px;
  flex-shrink: 0;
}

/* ── Receipt ───────────────────────────────────────────────── */

.receipt {
  margin-top: 34px;
  padding: 18px 20px;
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-xs);
}

.receipt-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.receipt-check {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--success-tint);
  color: var(--success);
  flex-shrink: 0;
}

.receipt-copy {
  flex: 1;
  min-width: 0;
}

.receipt-copy h2 {
  font-size: 14.5px;
  font-weight: 620;
}

.receipt-copy p {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--muted);
}

.receipt-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.receipt-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 0;
  border-top: 1px solid var(--line);
}

.receipt-contact {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.receipt-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}

.receipt-name:hover {
  color: var(--accent);
}

.receipt-meta {
  font-size: 10.5px;
  color: var(--faint);
}

.receipt-state {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  white-space: nowrap;
}

.receipt-state.is-working {
  color: var(--accent);
}

.receipt-state.is-done {
  color: var(--success);
}

.receipt-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 11.5px;
  color: var(--muted);
}

.receipt-link:hover {
  color: var(--accent);
}

/* ── Pipeline ──────────────────────────────────────────────── */

.pipeline-section {
  margin-top: 76px;
}

.section-head h2 {
  font-size: 21px;
  font-weight: 650;
  letter-spacing: -0.025em;
}

.section-head p {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--muted);
}

.pipeline {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 0;
  margin: 26px 0 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--line-strong);
}

.pipeline-step {
  position: relative;
  padding: 22px 22px 0 0;
}

.pipeline-step + .pipeline-step {
  padding-left: 22px;
  border-left: 1px solid var(--line);
}

.step-no {
  display: block;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--faint);
}

.step-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-top: 14px;
  border-radius: var(--r-sm);
  background: var(--paper);
  border: 1px solid var(--line);
  color: var(--ink-2);
}

.pipeline-step h3 {
  margin-top: 12px;
  font-size: 14px;
  font-weight: 620;
}

.pipeline-step p {
  margin-top: 5px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--muted);
}

/* ── Lower ─────────────────────────────────────────────────── */

.home-lower {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(360px, 1.1fr);
  gap: 44px;
  margin-top: 68px;
  padding-top: 30px;
  border-top: 1px solid var(--line);
}

.recent-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.recent-head h2 {
  font-size: 15px;
  font-weight: 620;
}

.quiet-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: var(--faint);
}

.quiet-link:hover {
  color: var(--accent);
}

.recent-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 0;
  border-top: 1px solid var(--line);
}

.recent-item:hover .recent-name {
  color: var(--accent);
}

.recent-item:hover .recent-arrow {
  color: var(--accent);
  transform: translateX(2px);
}

.recent-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.recent-name {
  font-size: 13px;
  font-weight: 550;
  color: var(--ink);
  transition: color 0.15s ease;
}

.recent-meta {
  margin-top: 1px;
  font-size: 10.5px;
  color: var(--faint);
}

.recent-arrow {
  color: var(--faint);
  transition: color 0.15s ease, transform 0.15s ease;
}

.recent-empty {
  padding: 22px 0 0;
  border-top: 1px solid var(--line);
}

.recent-empty-title {
  font-size: 13px;
  font-weight: 550;
  color: var(--ink-2);
}

.recent-empty-desc {
  margin-top: 4px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--faint);
}

.entry-list {
  display: flex;
  flex-direction: column;
}

.entry-row {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 14px 0;
  border-top: 1px solid var(--line);
}

.entry-row:hover .entry-title {
  color: var(--accent);
}

.entry-row:hover .entry-arrow {
  color: var(--accent);
  transform: translateX(2px);
}

.entry-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--r-sm);
  background: var(--paper);
  border: 1px solid var(--line);
  color: var(--ink-2);
  flex-shrink: 0;
}

.entry-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.entry-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
  transition: color 0.15s ease;
}

.entry-desc {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--faint);
}

.entry-arrow {
  color: var(--faint);
  transition: color 0.15s ease, transform 0.15s ease;
}

/* ── Responsive ────────────────────────────────────────────── */

@media (max-width: 1250px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 34px;
  }

  .hero-copy {
    padding-top: 0;
  }

  .hero-desc {
    max-width: 52ch;
  }
}

@media (max-width: 1100px) {
  .pipeline {
    grid-template-columns: repeat(2, 1fr);
    border-top: 0;
  }

  .pipeline-step {
    padding: 18px 0 18px 0;
    border-top: 1px solid var(--line);
  }

  .pipeline-step + .pipeline-step {
    padding-left: 0;
    border-left: 0;
  }

  .pipeline-step:nth-child(even) {
    padding-left: 18px;
    border-left: 1px solid var(--line);
  }

  .pipeline-step:last-child {
    grid-column: span 2;
    padding-left: 0;
    border-left: 0;
  }

  .home-lower {
    grid-template-columns: 1fr;
    gap: 34px;
  }
}

@media (max-width: 900px) {
  .hero-title {
    font-size: 42px;
  }
}

@media (max-width: 560px) {
  .home-inner {
    padding-top: 26px;
  }

  .hero-title {
    font-size: 36px;
    letter-spacing: -0.035em;
  }

  .hero-desc {
    margin-top: 14px;
    font-size: 13.5px;
  }

  .hero-stats {
    gap: 22px;
    margin-top: 24px;
  }

  .hero-note {
    margin-top: 18px;
  }

  .composer-top {
    padding: 14px 15px 0;
  }

  .composer-formats {
    display: none;
  }

  .composer-body {
    padding: 6px 15px 0;
  }

  .paste-input {
    min-height: 200px;
    font-size: 14px;
  }

  .composer-foot {
    padding: 12px 15px;
    flex-wrap: wrap;
  }

  .foot-note {
    flex-basis: 100%;
  }

  .composer-foot :deep(.el-button) {
    width: 100%;
  }

  .foot-left {
    width: 100%;
  }

  .foot-left :deep(.el-button) {
    width: auto;
  }

  .preview-top {
    flex-direction: column;
    gap: 4px;
  }

  .pipeline-section {
    margin-top: 52px;
  }

  .pipeline {
    grid-template-columns: 1fr;
  }

  .pipeline-step,
  .pipeline-step:nth-child(even),
  .pipeline-step:last-child {
    grid-column: auto;
    padding: 16px 0;
    border-left: 0;
  }

  .home-lower {
    margin-top: 48px;
  }

  .receipt-head {
    flex-wrap: wrap;
  }

  .receipt-head :deep(.el-button) {
    margin-left: 44px;
  }

  .receipt-item {
    flex-wrap: wrap;
  }

  .receipt-link {
    margin-left: auto;
  }
}
</style>
