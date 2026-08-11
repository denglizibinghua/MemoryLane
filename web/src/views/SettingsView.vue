<template>
  <div class="settings-page">
    <el-container class="settings-container">
      <el-header class="settings-header">
        <div class="header-left">
          <el-button circle @click="$router.push('/')" title="返回首页">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <div class="logo">
            <el-icon :size="28" color="#6366f1"><Timer /></el-icon>
            <span class="logo-text">MemoryLane</span>
          </div>
        </div>
      </el-header>

      <el-main class="settings-main">
        <div class="page-title">
          <h1>AI 设置</h1>
          <p>选择 AI 服务商并配置连接参数</p>
        </div>

        <el-card v-loading="loading" class="settings-card" shadow="hover">
          <el-form label-position="top" :model="form">
            <!-- Provider -->
            <el-form-item label="AI 服务商">
              <el-select v-model="form.provider" placeholder="选择服务商" style="width: 100%" @change="onProviderChange">
                <el-option
                  v-for="p in providers"
                  :key="p.key"
                  :label="p.name"
                  :value="p.key"
                />
              </el-select>
            </el-form-item>

            <!-- API Key -->
            <el-form-item :label="currentProvider.needsApiKey ? 'API Key *' : 'API Key (可选)'">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                placeholder="输入 API Key"
                @input="onApiKeyInput"
              />
              <div class="input-hint" v-if="!apiKeyModified && form.apiKey">
                已保存的密钥已脱敏显示，留空则保持不变
              </div>
            </el-form-item>

            <!-- API Base URL -->
            <el-form-item label="API Base URL">
              <el-input v-model="form.apiBase" placeholder="https://api.openai.com" />
              <div class="input-hint" v-if="currentProvider.defaultBaseUrl">
                {{ currentProvider.name }} 默认: {{ currentProvider.defaultBaseUrl }}
              </div>
            </el-form-item>

            <!-- Model -->
            <el-form-item label="模型">
              <el-input v-model="form.model" placeholder="gpt-4o-mini" />
              <div class="input-hint">按 Enter 确认</div>
            </el-form-item>

            <!-- Temperature -->
            <el-form-item label="Temperature (创造性)">
              <el-slider
                v-model="form.temperature"
                :min="0"
                :max="2"
                :step="0.1"
                show-input
                :show-input-controls="false"
              />
            </el-form-item>

            <!-- Advisor Style -->
            <el-form-item label="AI 回复风格">
              <el-select v-model="form.advisorStyle" placeholder="选择风格" style="width: 100%">
                <el-option
                  v-for="s in styleOptions"
                  :key="s.value"
                  :label="s.label"
                  :value="s.value"
                />
              </el-select>
              <div class="input-hint">{{ currentStyleDesc }}</div>
            </el-form-item>
          </el-form>

          <!-- Test Result -->
          <el-alert
            v-if="testResult"
            :title="testResult.message"
            :type="testResult.success ? 'success' : 'error'"
            show-icon
            closable
            class="test-result"
            @close="testResult = null"
          />

          <!-- Actions -->
          <div class="action-row">
            <el-button type="success" plain :loading="testing" @click="handleTest">
              测试连接
            </el-button>
            <el-button type="primary" :loading="saving" @click="handleSave">
              保存设置
            </el-button>
          </div>
        </el-card>

        <!-- Semantic Search (Embedding) Settings -->
        <el-card v-loading="loading" class="settings-card embedding-card" shadow="hover">
          <template #header>
            <div class="embedding-card-header">
              <span class="embedding-card-title">语义搜索</span>
              <el-tag
                :type="embeddingActive ? 'success' : 'info'"
                size="small"
                effect="plain"
              >
                {{ embeddingActive ? '已激活' : '未激活' }}
              </el-tag>
            </div>
          </template>

          <el-form label-position="top" :model="form">
            <!-- Enable Toggle -->
            <el-form-item label="启用语义搜索">
              <el-switch v-model="form.embeddingEnabled" />
            </el-form-item>

            <!-- Embedding Provider -->
            <el-form-item v-if="form.embeddingEnabled" label="Embedding 服务商">
              <el-select
                v-model="form.embeddingProvider"
                placeholder="选择 Embedding 服务商"
                style="width: 100%"
                @change="onEmbeddingProviderChange"
              >
                <el-option
                  v-for="p in embeddingProviderOptions"
                  :key="p.key"
                  :label="p.name"
                  :value="p.key"
                />
              </el-select>
            </el-form-item>

            <!-- Embedding Model -->
            <el-form-item v-if="form.embeddingEnabled" label="Embedding 模型">
              <el-input v-model="form.embeddingModel" placeholder="text-embedding-3-small" />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- Advanced: Prompt Templates & OCR -->
        <el-collapse class="advanced-collapse">
          <el-collapse-item title="高级设置" name="advanced">

        <!-- Prompt Templates -->
        <el-card v-loading="templateLoading" class="settings-card prompt-card" shadow="hover">
          <template #header>
            <div class="embedding-card-header">
              <span class="embedding-card-title">Prompt 模板</span>
              <el-tag :type="editedCount > 0 ? 'warning' : 'info'" size="small" effect="plain">
                {{ editedCount > 0 ? `已编辑 ${editedCount} 个` : '默认' }}
              </el-tag>
            </div>
          </template>

          <el-collapse v-model="activeTemplates" class="template-list">
            <el-collapse-item
              v-for="tmpl in templates"
              :key="tmpl.key"
              :name="tmpl.key"
            >
              <template #title>
                <span class="template-item-name">{{ tmpl.name }}</span>
                <el-tag
                  v-if="isTemplateEdited(tmpl.key)"
                  size="small"
                  type="warning"
                  effect="plain"
                  class="template-edited-tag"
                >
                  已修改
                </el-tag>
              </template>
              <p class="template-desc" v-if="tmpl.description">{{ tmpl.description }}</p>
              <el-input
                v-model="templateEdits[tmpl.key]"
                type="textarea"
                :rows="10"
                class="template-textarea"
              />
              <div class="template-actions">
                <el-button
                  size="small"
                  text
                  type="danger"
                  :disabled="!isTemplateEdited(tmpl.key)"
                  @click="resetTemplate(tmpl.key)"
                >
                  恢复默认
                </el-button>
              </div>
            </el-collapse-item>
          </el-collapse>

          <div class="action-row" style="margin-top: 20px">
            <el-button type="primary" :loading="savingTemplates" @click="handleSaveTemplates">
              保存模板
            </el-button>
          </div>
        </el-card>

        <!-- OCR Fallback Status -->
        <el-card class="settings-card ocr-card" shadow="hover">
          <template #header>
            <div class="embedding-card-header">
              <span class="embedding-card-title">截图 OCR</span>
              <el-tag
                :type="ocrStatus.tesseractAvailable ? 'success' : 'danger'"
                size="small"
                effect="plain"
              >
                {{ ocrStatus.tesseractAvailable ? '离线可用' : '离线不可用' }}
              </el-tag>
            </div>
          </template>
          <p class="ocr-desc">
            截图导入优先使用 AI 视觉模型识别文字。如果 AI 失败（超时/无视觉能力/未配置），
            自动切换到 Tesseract 离线 OCR 作为备用。
          </p>
          <div class="ocr-info">
            <el-tag :type="ocrStatus.fallbackEnabled ? 'success' : 'info'" size="small">
              自动回退：{{ ocrStatus.fallbackEnabled ? '已启用' : '已禁用' }}
            </el-tag>
            <span v-if="!ocrStatus.tesseractAvailable" class="ocr-hint">
              需在 server/tessdata/tessdata/ 下放置 chi_sim.traineddata
            </span>
          </div>
        </el-card>

          </el-collapse-item>
        </el-collapse>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ArrowLeft, Timer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getAiSettings,
  updateAiSettings,
  testAiConnection,
  getPromptTemplates,
  updatePromptTemplates,
  getOcrStatus,
  type AiSettings,
  type ProviderInfo,
  type PromptTemplateMeta,
  type OcrStatus,
} from '@/api/settings'

interface ProviderDefault {
  name: string
  defaultModel: string
  defaultBaseUrl: string
  needsApiKey: boolean
}

const providerDefaults: Record<string, ProviderDefault> = {
  openai:     { name: 'OpenAI',         defaultModel: 'gpt-4o-mini',                 defaultBaseUrl: 'https://api.openai.com',     needsApiKey: true },
  deepseek:   { name: 'DeepSeek',       defaultModel: 'deepseek-chat',                 defaultBaseUrl: 'https://api.deepseek.com',    needsApiKey: true },
  ollama:     { name: 'Ollama (本地)',   defaultModel: 'qwen2.5:7b',                  defaultBaseUrl: 'http://localhost:11434',     needsApiKey: false },
  anthropic:  { name: 'Anthropic',       defaultModel: 'claude-3-5-sonnet-latest',    defaultBaseUrl: 'https://api.anthropic.com', needsApiKey: true },
  dashscope:  { name: '通义千问',       defaultModel: 'qwen-plus',                    defaultBaseUrl: '',                           needsApiKey: true },
  zhipuai:    { name: '智谱 GLM',       defaultModel: 'glm-4-flash',                  defaultBaseUrl: '',                           needsApiKey: true },
  moonshot:   { name: 'Kimi (月之暗面)', defaultModel: 'moonshot-v1-8k',              defaultBaseUrl: 'https://api.moonshot.cn',   needsApiKey: true },
  custom:     { name: '自定义 (OpenAI 兼容)', defaultModel: 'gpt-4o-mini',           defaultBaseUrl: '',                           needsApiKey: true },
}

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const apiKeyModified = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)

const form = ref<AiSettings>({
  provider: 'openai',
  apiKey: '',
  apiBase: '',
  model: '',
  temperature: 0.3,
  embeddingEnabled: false,
  embeddingProvider: 'openai',
  embeddingModel: '',
  advisorStyle: 'default',
})

const serverProviders = ref<ProviderInfo[]>([])
const embeddingActive = ref(false)
const embeddingProviders = ref<ProviderInfo[]>([])

const providers = computed(() => {
  if (serverProviders.value.length > 0) {
    return serverProviders.value.map((p) => ({
      key: p.key,
      name: p.name,
    }))
  }
  return Object.entries(providerDefaults).map(([key, v]) => ({
    key,
    name: v.name,
  }))
})

const currentProvider = computed(() => {
  const key = form.value.provider
  const server = serverProviders.value.find((p) => p.key === key)
  if (server) {
    return {
      name: server.name,
      defaultModel: server.defaultModel,
      defaultBaseUrl: server.defaultBaseUrl,
      needsApiKey: server.needsApiKey,
    }
  }
  const local = providerDefaults[key]
  return local || providerDefaults.openai
})

const embeddingProviderOptions = computed(() => {
  if (embeddingProviders.value.length > 0) {
    return embeddingProviders.value.map((p) => ({
      key: p.key,
      name: p.name,
    }))
  }
  return Object.entries(embeddingModelDefaults).map(([key]) => ({
    key,
    name: providerDefaults[key]?.name || key,
  }))
})

function isMaskedApiKey(key: string): boolean {
  if (!key) return false
  return key.includes('***') || (key.includes('...') && key.length < 20)
}

function onApiKeyInput() {
  apiKeyModified.value = true
}

function onProviderChange(newProvider: string) {
  const def = providerDefaults[newProvider] || currentProvider.value
  const modelDefault = (def as ProviderDefault).defaultModel || ''
  const baseDefault = (def as ProviderDefault).defaultBaseUrl || ''

  // Only auto-fill if current values are empty or look like defaults
  if (!form.value.model || Object.values(providerDefaults).some((d) => d.defaultModel === form.value.model)) {
    form.value.model = modelDefault
  }
  if (!form.value.apiBase || Object.values(providerDefaults).some((d) => d.defaultBaseUrl === form.value.apiBase)) {
    form.value.apiBase = baseDefault
  }
}

const embeddingModelDefaults: Record<string, string> = {
  openai: 'text-embedding-3-small',
  zhipuai: 'embedding-2',
  ollama: 'nomic-embed-text',
}

function onEmbeddingProviderChange(newProvider: string) {
  const defaultModel = embeddingModelDefaults[newProvider] || ''
  if (defaultModel) {
    form.value.embeddingModel = defaultModel
  }
}

const styleOptions = [
  { value: 'default', label: '默认', desc: '自然得体，不添加额外风格' },
  { value: 'humorous', label: '😄 幽默', desc: '风趣俏皮，用梗和调侃让对话轻松有趣' },
  { value: 'cute', label: '🐱 可爱', desc: '软萌治愈，带撒娇语气和温暖的回应' },
  { value: 'gentle', label: '🌸 温柔', desc: '细腻体贴，让对方感到被理解和关心' },
  { value: 'cool', label: '🧊 高冷', desc: '话少但到位，简洁有力不啰嗦' },
  { value: 'tsundere', label: '😤 傲娇', desc: '嘴上嫌弃行动关心，傲娇属性拉满' },
]

const currentStyleDesc = computed(() => {
  const found = styleOptions.find(s => s.value === form.value.advisorStyle)
  return found ? found.desc : ''
})

async function loadSettings() {
  loading.value = true
  try {
    const data = await getAiSettings()
    if (data.providers && data.providers.length > 0) {
      serverProviders.value = data.providers
    }
    form.value.provider = data.provider || 'openai'
    form.value.apiBase = data.apiBase || ''
    form.value.model = data.model || ''
    form.value.temperature = data.temperature ?? 0.3
    form.value.embeddingEnabled = data.embeddingEnabled ?? false
    form.value.embeddingProvider = data.embeddingProvider || 'openai'
    form.value.embeddingModel = data.embeddingModel || ''
    form.value.advisorStyle = data.advisorStyle || 'default'
    embeddingActive.value = data.embeddingActive ?? false
    if (data.embeddingProviders && data.embeddingProviders.length > 0) {
      embeddingProviders.value = data.embeddingProviders
    }

    if (data.apiKey && isMaskedApiKey(data.apiKey)) {
      form.value.apiKey = data.apiKey
      apiKeyModified.value = false
    } else if (data.apiKey) {
      form.value.apiKey = data.apiKey
      apiKeyModified.value = false
    } else {
      form.value.apiKey = ''
      apiKeyModified.value = false
    }
  } catch (e: any) {
    ElMessage.error('加载设置失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function handleTest() {
  testing.value = true
  testResult.value = null
  try {
    const payload = buildPayload()
    const result = await testAiConnection(payload)
    testResult.value = result
  } catch (e: any) {
    testResult.value = {
      success: false,
      message: e?.response?.data?.message || e?.message || '连接测试失败',
    }
  } finally {
    testing.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const payload = buildPayload()
    const data = await updateAiSettings(payload)
    ElMessage.success('设置已保存')
    // After save, if apiKey was sent, server might return masked version
    if (data.apiKey && isMaskedApiKey(data.apiKey)) {
      form.value.apiKey = data.apiKey
      apiKeyModified.value = false
    }
    embeddingActive.value = data.embeddingActive ?? embeddingActive.value
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

function buildPayload(): AiSettings {
  return {
    provider: form.value.provider,
    apiKey: apiKeyModified.value ? form.value.apiKey : '',
    apiBase: form.value.apiBase,
    model: form.value.model,
    temperature: form.value.temperature,
    embeddingEnabled: form.value.embeddingEnabled,
    embeddingProvider: form.value.embeddingProvider,
    embeddingModel: form.value.embeddingModel,
    advisorStyle: form.value.advisorStyle,
  }
}

// --- Prompt Templates ---

const templates = ref<PromptTemplateMeta[]>([])
const templateEdits = ref<Record<string, string>>({})
const templateOriginals = ref<Record<string, string>>({})
const activeTemplates = ref<string[]>([])
const templateLoading = ref(false)
const savingTemplates = ref(false)

const editedCount = computed(() => {
  return templates.value.filter((t) => isTemplateEdited(t.key)).length
})

function isTemplateEdited(key: string): boolean {
  const original = templateOriginals.value[key]
  const current = templateEdits.value[key]
  return original !== undefined && current !== undefined && original !== current
}

async function loadTemplates() {
  templateLoading.value = true
  try {
    const data = await getPromptTemplates()
    const list = Object.values(data) as PromptTemplateMeta[]
    templates.value = list
    const edits: Record<string, string> = {}
    const originals: Record<string, string> = {}
    for (const t of list) {
      edits[t.key] = t.content
      originals[t.key] = t.content
    }
    templateEdits.value = edits
    templateOriginals.value = originals
  } catch (e: any) {
    ElMessage.error('加载 Prompt 模板失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    templateLoading.value = false
  }
}

function resetTemplate(key: string) {
  templateEdits.value[key] = templateOriginals.value[key]
}

async function handleSaveTemplates() {
  savingTemplates.value = true
  try {
    // Build diff: only send changed templates
    const updates: Record<string, string> = {}
    for (const t of templates.value) {
      if (isTemplateEdited(t.key)) {
        updates[t.key] = templateEdits.value[t.key]
      }
    }
    if (Object.keys(updates).length === 0) {
      ElMessage.info('没有修改，无需保存')
      return
    }
    await updatePromptTemplates(updates)
    // Update originals to reflect saved state
    for (const key of Object.keys(updates)) {
      templateOriginals.value[key] = templateEdits.value[key]
    }
    ElMessage.success('Prompt 模板已保存')
  } catch (e: any) {
    ElMessage.error('保存模板失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    savingTemplates.value = false
  }
}

onMounted(() => {
  loadSettings()
  loadTemplates()
  loadOcrStatus()
})

// --- OCR Status ---

const ocrStatus = ref<OcrStatus>({ tesseractAvailable: false, fallbackEnabled: true })

async function loadOcrStatus() {
  try {
    const data = await getOcrStatus()
    ocrStatus.value = data
  } catch {
    // OCR status is informational — fail silently
  }
}
</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 50%, #fae8ff 100%);
}

.settings-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 24px;
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0;
  height: auto;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
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

.settings-main {
  padding: 20px 0 60px;
}

.page-title {
  text-align: center;
  margin-bottom: 32px;
}

.page-title h1 {
  font-size: 28px;
  font-weight: 800;
  color: #1e1b4b;
  margin: 0 0 8px;
}

.page-title p {
  font-size: 15px;
  color: #6b7280;
  margin: 0;
}

.settings-card {
  max-width: 640px;
  margin: 0 auto;
  border-radius: 12px;
}

.settings-card :deep(.el-form-item__label) {
  font-weight: 600;
  color: #374151;
  padding-bottom: 4px;
}

.input-hint {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
  line-height: 1.4;
}

.test-result {
  margin-top: 16px;
}

.action-row {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  justify-content: flex-end;
}

.embedding-card {
  max-width: 640px;
  margin: 24px auto 0;
  border-radius: 12px;
}

.embedding-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.embedding-card-title {
  font-size: 17px;
  font-weight: 700;
  color: #1e1b4b;
}

.prompt-card {
  max-width: 640px;
  margin: 24px auto 0;
  border-radius: 12px;
}

.template-list :deep(.el-collapse-item__header) {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  padding: 12px 0;
}

.template-item-name {
  flex: 1;
}

.template-edited-tag {
  margin-left: 8px;
}

.template-desc {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 12px;
  line-height: 1.5;
}

.template-textarea :deep(.el-textarea__inner) {
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.template-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.ocr-card {
  max-width: 640px;
  margin: 24px auto 0;
  border-radius: 12px;
}

.ocr-desc {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 12px;
  line-height: 1.6;
}

.ocr-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.ocr-hint {
  font-size: 12px;
  color: #9ca3af;
}

.advanced-collapse {
  max-width: 640px;
  margin: 24px auto 0;
  border-radius: 12px;
  overflow: hidden;
}

.advanced-collapse :deep(.el-collapse-item__header) {
  font-size: 15px;
  font-weight: 600;
  color: #6b7280;
  padding: 14px 20px;
  background: #f9fafb;
  border-radius: 12px;
}

.advanced-collapse :deep(.el-collapse-item__wrap) {
  border: none;
}
</style>
