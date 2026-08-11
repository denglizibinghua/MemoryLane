<template>
  <div class="home">
    <el-container class="hero-container">
      <el-header class="hero-header">
        <div class="logo">
          <el-icon :size="28" color="#6366f1"><Timer /></el-icon>
          <span class="logo-text">MemoryLane</span>
        </div>
        <div class="header-actions">
          <el-button circle @click="$router.push('/settings')" title="AI 设置">
            <el-icon><Setting /></el-icon>
          </el-button>
          <el-button type="primary" @click="$router.push('/contacts')">
            <el-icon><List /></el-icon>
            联系人
          </el-button>
          <el-button @click="$router.push('/memories')">
            <el-icon><Collection /></el-icon>
            记忆库
          </el-button>
        </div>
      </el-header>

      <el-main class="hero-main">
        <h1 class="hero-title">回忆你的每一次对话</h1>
        <p class="hero-subtitle">
          粘贴聊天记录或截图，AI 自动识别对话对象、提取关键记忆，在你需要时给出回复建议。
        </p>

        <div class="import-area">
          <el-card class="import-card" shadow="hover">
            <template #header>
              <span><el-icon><EditPen /></el-icon> 粘贴文本</span>
            </template>

            <el-input
              v-model="pasteText"
              type="textarea"
              :rows="6"
              placeholder="在此粘贴聊天记录...&#10;&#10;支持微信、QQ、抖音等常见格式"
              :disabled="previewLoading || importing"
            />

            <!-- Step 1: Preview -->
            <div class="card-footer" v-if="!previewResult">
              <div style="display: flex; gap: 8px">
                <el-select v-model="platform" placeholder="自动识别" style="width: 140px">
                  <el-option label="自动识别" value="auto" />
                  <el-option label="微信" value="wechat" />
                  <el-option label="QQ" value="qq" />
                  <el-option label="抖音" value="douyin" />
                  <el-option label="通用文本" value="generic" />
                </el-select>
              </div>
              <el-button
                type="primary"
                @click="handlePreview"
                :loading="previewLoading"
                :disabled="!pasteText.trim()"
              >
                解析
              </el-button>
            </div>

            <!-- Step 2: Select self → Import -->
            <div class="card-footer" v-if="previewResult && !importing">
              <div style="display: flex; gap: 8px; align-items: center; flex: 1">
                <span class="preview-info">
                  识别到 {{ previewResult.speakers.length }} 位说话人，{{ previewResult.messageCount }} 条消息
                </span>
                <span style="color: #6b7280; font-size: 13px; white-space: nowrap">我是谁：</span>
                <el-select v-model="selfName" placeholder="选择你自己" style="width: 160px">
                  <el-option
                    v-for="s in previewResult.speakers"
                    :key="s"
                    :label="s"
                    :value="s"
                  />
                </el-select>
              </div>
              <div style="display: flex; gap: 8px">
                <el-button @click="resetImport">重新粘贴</el-button>
                <el-button
                  type="primary"
                  @click="handleImport"
                  :disabled="!selfName"
                >
                  开始分析
                </el-button>
              </div>
            </div>
          </el-card>

          <el-card class="import-card screenshot-card" shadow="hover">
            <template #header>
              <span><el-icon><Camera /></el-icon> 截图导入</span>
            </template>

            <!-- Vision support warning -->
            <el-alert
              v-if="currentChatProvider"
              :title="`当前 AI 模型：${providerLabel(currentChatProvider)} / ${currentChatModel}。截图识别需要模型具备视觉能力，如识别失败请切换到 OpenAI、Claude 或通义千问。`"
              type="warning"
              :closable="false"
              show-icon
              class="vision-warning"
            />

            <!-- File selection -->
            <div v-if="!screenshotFile" class="screenshot-upload-zone" @click="screenshotFileInput?.click()">
              <el-icon :size="32" color="#9ca3af"><Camera /></el-icon>
              <span class="upload-hint">点击选择聊天截图</span>
              <span class="upload-sub">支持 PNG / JPG / WEBP</span>
              <input
                ref="screenshotFileInput"
                type="file"
                accept="image/*"
                style="display: none"
                @change="handleScreenshotSelect"
              />
            </div>

            <!-- Step 1: Show thumbnail → 开始识别 -->
            <div v-else-if="!ocrPreviewResult">
              <div class="screenshot-preview">
                <img :src="screenshotPreview" alt="截图预览" />
              </div>
              <div class="card-footer" style="justify-content: flex-end">
                <el-button @click="resetScreenshot">重新选择</el-button>
                <el-button type="primary" @click="handleScreenshotPreview" :loading="screenshotLoading">
                  开始识别
                </el-button>
              </div>
            </div>

            <!-- Step 2: Show OCR text → edit → 确认导入 -->
            <div v-else>
              <el-input
                v-model="ocrEditText"
                type="textarea"
                :rows="8"
                placeholder="OCR 识别结果，可手动修改..."
              />
              <div class="card-footer">
                <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
                  <el-select v-model="screenshotPlatform" placeholder="自动识别" style="width: 120px">
                    <el-option label="自动识别" value="auto" />
                    <el-option label="微信" value="wechat" />
                    <el-option label="QQ" value="qq" />
                    <el-option label="抖音" value="douyin" />
                    <el-option label="通用文本" value="generic" />
                  </el-select>
                  <el-input
                    v-model="screenshotSelfName"
                    placeholder="我是谁 (选填)"
                    style="width: 140px"
                    clearable
                  />
                </div>
                <div style="display: flex; gap: 8px">
                  <el-button @click="resetScreenshot">重新选择</el-button>
                  <el-button type="primary" @click="handleScreenshotConfirm" :loading="screenshotLoading">
                    确认导入
                  </el-button>
                </div>
              </div>
            </div>
          </el-card>
        </div>

        <!-- Error -->
        <el-alert
          v-if="importError"
          :title="importError"
          type="error"
          show-icon
          closable
          style="max-width: 640px; margin: 0 auto 16px"
        />

        <!-- Import results -->
        <div v-if="importResult && importResult.contacts.length > 0" class="import-results">
          <el-alert type="success" show-icon closable style="max-width: 640px; margin: 0 auto 16px">
            <template #title>
              导入完成：{{ importResult.stats.newMessages }} 条新消息，{{ importResult.stats.duplicates }} 条重复
            </template>
          </el-alert>
          <div class="contact-results">
            <el-card
              v-for="cr in importResult.contacts"
              :key="cr.contactId"
              shadow="never"
              class="contact-result-card"
              @click="$router.push(`/contacts/${cr.contactId}`)"
            >
              <div class="cr-name">{{ cr.contactName }}</div>
              <div class="cr-count">{{ cr.messageCount }} 条消息</div>
              <div v-if="extractingContacts[cr.contactId]" class="cr-extracting">
                <el-icon class="is-loading"><Loading /></el-icon> 分析中...
              </div>
              <div v-else-if="contactMemories[cr.contactId]" class="cr-memories">
                {{ contactMemories[cr.contactId] }} 条记忆
              </div>
            </el-card>
          </div>
        </div>

        <div class="features">
          <div class="feature-grid">
            <el-card
              v-for="f in features"
              :key="f.title"
              shadow="never"
              class="feature-card"
              :class="{ clickable: f.route }"
              @click="f.route && $router.push(f.route)"
            >
              <el-icon :size="32" :color="f.color"><component :is="f.icon" /></el-icon>
              <h3>{{ f.title }}</h3>
              <p>{{ f.desc }}</p>
            </el-card>
          </div>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Timer, List, Collection, EditPen, Camera, Loading, ChatDotRound, DataAnalysis, MagicStick, Setting, Bell, TrendCharts, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useMemoryStore } from '@/stores/memories'
import { getAiSettings, type AiSettingsResponse } from '@/api/settings'
import type { ImportResult, PreviewResult, ScreenshotPreview } from '@/stores/memories'
import api from '@/api'

const memoryStore = useMemoryStore()

const pasteText = ref('')
const platform = ref('auto')
const selfName = ref('')
const previewResult = ref<PreviewResult | null>(null)
const previewLoading = ref(false)
const importing = ref(false)
const importResult = ref<ImportResult | null>(null)
const importError = ref('')

// Memory extraction polling state
const extractingContacts = ref<Record<number, string>>({})  // contactId → name
const contactMemories = ref<Record<number, number>>({})      // contactId → memory count
let memoryPollTimer: ReturnType<typeof setInterval> | null = null

// Screenshot import state
const screenshotFile = ref<File | null>(null)
const screenshotPreview = ref('')
const screenshotLoading = ref(false)
const screenshotPlatform = ref('auto')
const screenshotSelfName = ref('')
const screenshotFileInput = ref<HTMLInputElement | null>(null)
const ocrPreviewResult = ref<ScreenshotPreview | null>(null)
const ocrEditText = ref('')

// Current AI model info (for vision support warning)
const currentChatProvider = ref('')
const currentChatModel = ref('')

onMounted(async () => {
  try {
    const settings: AiSettingsResponse = await getAiSettings()
    currentChatProvider.value = settings.provider || ''
    currentChatModel.value = settings.model || ''
  } catch {
    // Settings not critical for screenshot functionality
  }
})

const features = [
  {
    icon: ChatDotRound,
    color: '#6366f1',
    title: '多平台支持',
    desc: '自动识别微信、QQ、抖音、短信等聊天格式',
    route: '/contacts',
  },
  {
    icon: DataAnalysis,
    color: '#a855f7',
    title: '记忆提炼',
    desc: 'AI 自动提取约定、偏好、个人信息等结构化记忆',
    route: '/memories',
  },
  {
    icon: MagicStick,
    color: '#ec4899',
    title: '军师模式',
    desc: '基于历史聊天记忆，智能生成回复建议',
    route: '/advisor',
  },
  {
    icon: Bell,
    color: '#f59e0b',
    title: '约定提醒',
    desc: '自动检测聊天中的约定，到时间浏览器通知',
    route: '/reminders',
  },
  {
    icon: TrendCharts,
    color: '#10b981',
    title: '关系面板',
    desc: '消息趋势、发言比例、谁更主动，一目了然',
    route: '/analytics',
  },
  {
    icon: User,
    color: '#6366f1',
    title: '我的形象',
    desc: '告诉军师你是谁、怎么说话，AI 回复更懂你',
    route: '/profile',
  },
]

function resetImport() {
  previewResult.value = null
  selfName.value = ''
  importResult.value = null
  importError.value = ''
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
    // Auto-select if only one non-self speaker
    if (previewResult.value.speakers.length === 1) {
      selfName.value = previewResult.value.speakers[0]
    } else {
      selfName.value = ''
    }
  } catch (e: any) {
    importError.value = e?.response?.data?.message || e?.message || '解析失败，请检查聊天记录格式'
  } finally {
    previewLoading.value = false
  }
}

async function handleImport() {
  if (!selfName.value) {
    ElMessage.warning('请先选择"我是谁"')
    return
  }
  importing.value = true
  importResult.value = null
  importError.value = ''
  try {
    importResult.value = await memoryStore.importText(selfName.value, platform.value, pasteText.value)
    pasteText.value = ''
    previewResult.value = null
    startMemoryPolling(importResult.value)
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
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  screenshotFile.value = file
  screenshotPreview.value = URL.createObjectURL(file)
  importError.value = ''
  importResult.value = null
}

function resetScreenshot() {
  if (screenshotPreview.value) {
    URL.revokeObjectURL(screenshotPreview.value)
  }
  screenshotFile.value = null
  screenshotPreview.value = ''
  screenshotSelfName.value = ''
  screenshotPlatform.value = 'auto'
  ocrPreviewResult.value = null
  ocrEditText.value = ''
  importResult.value = null
  importError.value = ''
  if (screenshotFileInput.value) {
    screenshotFileInput.value.value = ''
  }
}

async function handleScreenshotPreview() {
  if (!screenshotFile.value) {
    ElMessage.warning('请先选择截图')
    return
  }
  screenshotLoading.value = true
  importError.value = ''
  ocrPreviewResult.value = null
  try {
    ocrPreviewResult.value = await memoryStore.previewScreenshot(screenshotFile.value)
    ocrEditText.value = ocrPreviewResult.value.ocrText
  } catch (e: any) {
    importError.value = e?.response?.data?.message || e?.message || '截图识别失败，请重试'
  } finally {
    screenshotLoading.value = false
  }
}

async function handleScreenshotConfirm() {
  if (!ocrEditText.value.trim()) {
    ElMessage.warning('OCR 识别内容为空')
    return
  }
  screenshotLoading.value = true
  importResult.value = null
  importError.value = ''
  try {
    importResult.value = await memoryStore.importText(
      screenshotSelfName.value,
      screenshotPlatform.value || ocrPreviewResult.value?.platform || 'auto',
      ocrEditText.value,
    )
    startMemoryPolling(importResult.value)
    resetScreenshot()
  } catch (e: any) {
    importError.value = e?.response?.data?.message || e?.message || '导入失败，请重试'
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
      clearInterval(memoryPollTimer!)
      memoryPollTimer = null
      const total = Object.values(contactMemories.value).reduce((a, b) => a + b, 0)
      if (total > 0) {
        ElMessage.success(`记忆分析完成，共提取 ${total} 条记忆`)
      }
    }
  }, 2000)
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
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 50%, #fae8ff 100%);
}

.hero-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 24px;
}

.hero-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0;
  height: auto;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-text {
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.hero-main {
  padding: 40px 0 60px;
}

.hero-title {
  font-size: 36px;
  font-weight: 800;
  color: #1e1b4b;
  text-align: center;
  margin-bottom: 16px;
  letter-spacing: -0.5px;
}

.hero-subtitle {
  font-size: 16px;
  color: #6b7280;
  text-align: center;
  max-width: 560px;
  margin: 0 auto 40px;
  line-height: 1.7;
}

.import-card {
  max-width: 640px;
  margin: 0 auto 24px;
  border-radius: 12px;
  flex: 1;
  min-width: 0;
}

.import-area {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  max-width: 960px;
  margin: 0 auto 24px;
}

@media (max-width: 768px) {
  .import-area {
    flex-direction: column;
  }
  .import-card {
    max-width: 100%;
  }
}

.screenshot-upload-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 16px;
  border: 2px dashed #d1d5db;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.vision-warning {
  margin-bottom: 12px;
  font-size: 13px;
}

.screenshot-upload-zone:hover {
  border-color: #6366f1;
  background: rgba(99, 102, 241, 0.04);
}

.upload-hint {
  font-size: 14px;
  color: #6b7280;
  margin-top: 4px;
}

.upload-sub {
  font-size: 12px;
  color: #9ca3af;
}

.screenshot-preview {
  text-align: center;
  margin-bottom: 12px;
}

.screenshot-preview img {
  max-width: 100%;
  max-height: 200px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  object-fit: contain;
}

.import-card :deep(.el-card__header) {
  font-weight: 600;
  color: #374151;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  gap: 12px;
}

.preview-info {
  font-size: 13px;
  color: #6b7280;
  white-space: nowrap;
}

.import-results {
  max-width: 640px;
  margin: 0 auto 60px;
}

.contact-results {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.contact-result-card {
  cursor: pointer;
  text-align: center;
  border: none;
  border-radius: 12px;
  min-width: 140px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.contact-result-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.12);
}

.cr-name {
  font-size: 15px;
  font-weight: 600;
  color: #1e1b4b;
}

.cr-count {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
}

.cr-extracting {
  font-size: 12px;
  color: #6366f1;
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.cr-memories {
  font-size: 12px;
  color: #10b981;
  margin-top: 4px;
}

.features {
  max-width: 960px;
  margin: 0 auto;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.feature-card {
  text-align: center;
  border: none;
  border-radius: 12px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.feature-card.clickable {
  cursor: pointer;
}

.feature-card.clickable:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.15);
}

.feature-card:not(.clickable):hover {
  transform: translateY(-4px);
}

.feature-card h3 {
  margin: 16px 0 8px;
  font-size: 16px;
  color: #1f2937;
}

.feature-card p {
  font-size: 13px;
  color: #9ca3af;
  line-height: 1.6;
}
</style>
