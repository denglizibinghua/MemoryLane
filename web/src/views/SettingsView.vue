<template>
  <div class="page">
    <PageHeader
      title="AI 设置"
      description="模型、检索与回复风格。改完保存即可生效，不用重启。"
    />

    <div v-if="loading" class="settings-loading" aria-label="正在加载设置">
      <div class="surface loading-panel">
        <el-skeleton :rows="7" animated />
      </div>
      <div class="surface loading-panel">
        <el-skeleton :rows="5" animated />
      </div>
    </div>

    <template v-else>
      <div v-if="loadError" class="load-error" role="alert">
        <el-icon :size="14"><WarningFilled /></el-icon>
        <span>{{ loadError }}</span>
        <el-button text size="small" @click="loadSettings">重试</el-button>
      </div>

      <div class="settings-layout">
        <aside class="settings-nav" aria-label="设置分区">
          <p class="nav-label">设置</p>
          <a
            v-for="s in settingsSections"
            :key="s.id"
            :href="`#${s.id}`"
            class="nav-link"
            :class="{ 'is-active': activeSection === s.id }"
            @click.prevent="scrollToSection(s.id)"
          >
            {{ s.label }}
          </a>
        </aside>

        <div class="settings-main">
          <!-- 1. 模型服务 -->
          <section id="ai-model" class="surface settings-section">
            <header class="section-head">
              <div class="section-head-copy">
                <h2 class="section-title">模型服务</h2>
                <p class="section-desc">
                  选择服务商并填入连接参数。测试连接只检查这一组 AI 配置，不会保存。
                </p>
              </div>
              <el-tag size="small" effect="plain" type="info">{{ currentProvider.name }}</el-tag>
            </header>

            <div class="provider-grid" role="radiogroup" aria-label="AI 服务商">
              <button
                v-for="p in providers"
                :key="p.key"
                type="button"
                class="provider-card"
                :class="{ 'is-selected': form.provider === p.key }"
                role="radio"
                :aria-checked="form.provider === p.key"
                @click="selectProvider(p.key)"
              >
                <span class="provider-body">
                  <span class="provider-name">{{ p.name }}</span>
                  <span v-if="p.description" class="provider-desc">{{ p.description }}</span>
                </span>
                <el-icon v-if="form.provider === p.key" class="provider-check" :size="13">
                  <Check />
                </el-icon>
              </button>
            </div>

            <el-form label-position="top" class="settings-form" @submit.prevent>
              <el-form-item :label="currentProvider.needsApiKey ? 'API Key' : 'API Key（可选）'">
                <el-input
                  v-model="form.apiKey"
                  type="password"
                  show-password
                  placeholder="输入 API Key"
                  @input="onApiKeyInput"
                />
                <p v-if="!apiKeyModified && form.apiKey" class="field-hint">
                  已保存的密钥已脱敏显示，留空则保持不变。
                </p>
              </el-form-item>

              <el-form-item label="API Base URL">
                <el-input v-model="form.apiBase" placeholder="https://api.openai.com" />
                <p v-if="currentProvider.defaultBaseUrl" class="field-hint">
                  {{ currentProvider.name }} 默认：{{ currentProvider.defaultBaseUrl }}
                </p>
              </el-form-item>

              <el-form-item label="模型">
                <el-input v-model="form.model" placeholder="gpt-4o-mini" />
                <p class="field-hint">
                  {{
                    currentProvider.defaultModel
                      ? `默认：${currentProvider.defaultModel}`
                      : '填写服务商支持的模型名'
                  }}
                </p>
              </el-form-item>

              <el-form-item label="Temperature（创造性）" class="temp-item">
                <el-slider
                  v-model="form.temperature"
                  :min="0"
                  :max="2"
                  :step="0.1"
                  show-input
                  :show-input-controls="false"
                  aria-label="Temperature"
                />
                <p class="field-hint">0 更稳，2 更发散；建议保持在 0.3 左右。</p>
              </el-form-item>
            </el-form>

            <div
              v-if="testResult"
              class="status-band"
              :class="testResult.success ? 'is-success' : 'is-danger'"
              role="status"
            >
              <el-icon :size="14">
                <component :is="testResult.success ? CircleCheck : WarningFilled" />
              </el-icon>
              <span class="status-text">{{ testResult.message }}</span>
              <button
                type="button"
                class="status-close"
                aria-label="关闭测试结果"
                @click="testResult = null"
              >
                <el-icon :size="12"><Close /></el-icon>
              </button>
            </div>

            <div class="section-actions">
              <el-button type="success" plain :loading="testing" @click="handleTest">
                测试连接
              </el-button>
              <el-button type="primary" :loading="saving" @click="handleSave">
                保存设置
              </el-button>
            </div>
          </section>

          <!-- 2. 语义搜索 -->
          <section id="semantic" class="surface settings-section">
            <header class="section-head">
              <div class="section-head-copy">
                <h2 class="section-title">语义搜索</h2>
                <p class="section-desc">
                  语义检索默认关闭；开启并配置 Embedding 服务后，记忆检索会同时使用全文与向量。
                </p>
              </div>
              <el-tag
                :type="embeddingActive ? 'success' : 'info'"
                size="small"
                effect="plain"
              >
                {{ embeddingActive ? '已激活' : '未激活' }}
              </el-tag>
            </header>

            <div class="switch-row">
              <div class="switch-copy">
                <p class="switch-title">启用语义搜索</p>
                <p class="switch-desc">关闭时只做全文检索，速度更快、结果更直接。</p>
              </div>
              <el-switch v-model="form.embeddingEnabled" aria-label="启用语义搜索" />
            </div>

            <el-form
              v-if="form.embeddingEnabled"
              label-position="top"
              class="settings-form"
              @submit.prevent
            >
              <el-form-item label="Embedding 服务商">
                <el-select
                  v-model="form.embeddingProvider"
                  placeholder="选择 Embedding 服务商"
                  aria-label="Embedding 服务商"
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

              <el-form-item label="Embedding 模型">
                <el-input v-model="form.embeddingModel" placeholder="text-embedding-3-small" />
              </el-form-item>
            </el-form>
          </section>

          <!-- 3. 回复风格 -->
          <section id="reply-style" class="surface settings-section">
            <header class="section-head">
              <div class="section-head-copy">
                <h2 class="section-title">回复风格</h2>
                <p class="section-desc">决定军师写建议时的语感。它只影响措辞，不改动你的记忆数据。</p>
              </div>
            </header>

            <div class="style-grid" role="radiogroup" aria-label="AI 回复风格">
              <button
                v-for="s in styleOptions"
                :key="s.value"
                type="button"
                class="style-card"
                :class="{ 'is-selected': form.advisorStyle === s.value }"
                role="radio"
                :aria-checked="form.advisorStyle === s.value"
                @click="form.advisorStyle = s.value"
              >
                <span class="style-radio" aria-hidden="true"></span>
                <span class="style-copy">
                  <span class="style-label">{{ s.label }}</span>
                  <span class="style-desc">{{ s.desc }}</span>
                </span>
              </button>
            </div>

            <p class="section-note">
              <el-icon :size="12"><InfoFilled /></el-icon>
              <span>当前：{{ currentStyleDesc }}。保存后，军师的建议会按这个风格来写。</span>
            </p>
          </section>

          <!-- 4. Prompt 模板 -->
          <section id="prompts" class="surface settings-section">
            <header class="section-head">
              <div class="section-head-copy">
                <h2 class="section-title">Prompt 模板</h2>
                <p class="section-desc">调整军师读取记忆、组织回复时使用的系统提示词。</p>
              </div>
              <el-tag
                :type="editedCount > 0 ? 'warning' : 'info'"
                size="small"
                effect="plain"
              >
                {{ editedCount > 0 ? `已编辑 ${editedCount} 个` : '默认' }}
              </el-tag>
            </header>

            <div v-if="templateError" class="inline-error" role="alert">
              <el-icon :size="14"><WarningFilled /></el-icon>
              <span>{{ templateError }}</span>
              <el-button text size="small" @click="loadTemplates">重试</el-button>
            </div>

            <div v-if="templateLoading" class="prompt-loading">
              <el-skeleton :rows="4" animated />
            </div>

            <p v-else-if="!templates.length" class="quiet-note">没有可编辑的模板。</p>

            <template v-else>
              <el-collapse v-model="activeTemplates" class="template-list">
                <el-collapse-item
                  v-for="tmpl in templates"
                  :key="tmpl.key"
                  :name="tmpl.key"
                >
                  <template #title>
                    <span class="template-name">{{ tmpl.name }}</span>
                    <el-tag
                      v-if="isTemplateEdited(tmpl.key)"
                      size="small"
                      type="warning"
                      effect="plain"
                      class="template-tag"
                    >
                      已修改
                    </el-tag>
                  </template>

                  <p v-if="tmpl.description" class="template-desc">{{ tmpl.description }}</p>

                  <el-input
                    v-model="templateEdits[tmpl.key]"
                    type="textarea"
                    :rows="10"
                    class="template-textarea"
                    spellcheck="false"
                  />

                  <div class="template-foot">
                    <span class="template-meta tabular">
                      {{ (templateEdits[tmpl.key] || '').length }} 字符
                    </span>
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

              <div class="template-actions">
                <el-button type="primary" :loading="savingTemplates" @click="handleSaveTemplates">
                  保存模板
                </el-button>
              </div>
            </template>
          </section>

          <!-- 5. 截图 OCR -->
          <section id="ocr" class="surface settings-section">
            <header class="section-head">
              <div class="section-head-copy">
                <h2 class="section-title">截图 OCR</h2>
                <p class="section-desc">
                  截图导入优先使用 AI 视觉模型识别文字。如果 AI 失败（超时 / 无视觉能力 / 未配置），
                  自动切换到 Tesseract 离线 OCR 作为备用。
                </p>
              </div>
              <el-tag
                :type="ocrStatus.tesseractAvailable ? 'success' : 'danger'"
                size="small"
                effect="plain"
              >
                {{ ocrStatus.tesseractAvailable ? '离线可用' : '离线不可用' }}
              </el-tag>
            </header>

            <div class="ocr-row">
              <el-tag
                :type="ocrStatus.fallbackEnabled ? 'success' : 'info'"
                size="small"
                effect="plain"
              >
                自动回退：{{ ocrStatus.fallbackEnabled ? '已启用' : '已禁用' }}
              </el-tag>
              <span v-if="!ocrStatus.tesseractAvailable" class="ocr-hint">
                需在 server/tessdata/tessdata/ 下放置 chi_sim.traineddata
              </span>
            </div>
          </section>

          <div class="save-dock">
            <span class="save-note">保存会同时写入模型、语义搜索与回复风格。</span>
            <el-button type="primary" :loading="saving" @click="handleSave">保存设置</el-button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Check, CircleCheck, Close, InfoFilled, WarningFilled } from '@element-plus/icons-vue'
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
import PageHeader from '@/components/PageHeader.vue'

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

const providerDescriptions: Record<string, string> = {
  openai: '通用能力强，图片与长文本都稳。',
  deepseek: '中文理解好，价格低，响应快。',
  ollama: '本机运行，不联网，需要先装模型。',
  anthropic: '长文与细腻语气见长。',
  dashscope: '阿里云通义千问，国内直连。',
  zhipuai: '智谱 GLM，中文场景友好。',
  moonshot: 'Kimi，长文本处理见长。',
  custom: '任何 OpenAI 兼容接口。',
}

const settingsSections = [
  { id: 'ai-model', label: '模型服务' },
  { id: 'semantic', label: '语义搜索' },
  { id: 'reply-style', label: '回复风格' },
  { id: 'prompts', label: 'Prompt 模板' },
  { id: 'ocr', label: '截图 OCR' },
]

const activeSection = ref('ai-model')

const loading = ref(false)
const loadError = ref('')
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
      description: p.description || providerDescriptions[p.key] || '',
    }))
  }
  return Object.entries(providerDefaults).map(([key, v]) => ({
    key,
    name: v.name,
    description: providerDescriptions[key] || '',
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

function selectProvider(key: string) {
  form.value.provider = key
  onProviderChange(key)
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
  { value: 'humorous', label: '幽默', desc: '风趣俏皮，用梗和调侃让对话轻松有趣' },
  { value: 'cute', label: '可爱', desc: '软萌治愈，带撒娇语气和温暖的回应' },
  { value: 'gentle', label: '温柔', desc: '细腻体贴，让对方感到被理解和关心' },
  { value: 'cool', label: '高冷', desc: '话少但到位，简洁有力不啰嗦' },
  { value: 'tsundere', label: '傲娇', desc: '嘴上嫌弃行动关心，傲娇属性拉满' },
]

const currentStyleDesc = computed(() => {
  const found = styleOptions.find(s => s.value === form.value.advisorStyle)
  return found ? found.desc : ''
})

function scrollToSection(id: string) {
  activeSection.value = id
  const el = document.getElementById(id)
  if (!el) return
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  el.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
}

async function loadSettings() {
  loading.value = true
  loadError.value = ''
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
    const message = e?.response?.data?.message || e?.message || '未知错误'
    loadError.value = '加载设置失败：' + message
    ElMessage.error('加载设置失败：' + message)
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
    loadError.value = ''
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
const templateError = ref('')
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
  templateError.value = ''
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
    const message = e?.response?.data?.message || e?.message || '未知错误'
    templateError.value = '加载 Prompt 模板失败：' + message
    ElMessage.error('加载 Prompt 模板失败：' + message)
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
.settings-layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 40px;
  align-items: start;
}

/* ── Section nav ───────────────────────────────────────────── */

.settings-nav {
  position: sticky;
  top: 28px;
  display: flex;
  flex-direction: column;
  padding-top: 2px;
}

.nav-label {
  padding: 0 10px 9px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--line-strong);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--faint);
}

.nav-link {
  position: relative;
  padding: 7px 10px 7px 22px;
  border-radius: var(--r-sm);
  font-size: 12.5px;
  line-height: 1.4;
  color: var(--muted);
  transition: color 0.15s ease, background-color 0.15s ease;
}

.nav-link::before {
  content: '';
  position: absolute;
  left: 9px;
  top: 50%;
  width: 4px;
  height: 4px;
  margin-top: -2px;
  border-radius: 50%;
  background: transparent;
  transition: background-color 0.15s ease;
}

.nav-link:hover {
  background: var(--wash);
  color: var(--ink);
}

.nav-link.is-active {
  background: var(--accent-tint);
  color: var(--accent);
  font-weight: 600;
}

.nav-link.is-active::before {
  background: var(--accent);
}

/* ── Column ────────────────────────────────────────────────── */

.settings-main {
  display: flex;
  flex-direction: column;
  gap: 22px;
  max-width: 760px;
  min-width: 0;
}

.settings-section {
  padding: 24px 26px;
  scroll-margin-top: 24px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-head-copy {
  min-width: 0;
}

.section-title {
  font-size: 16px;
  font-weight: 620;
  letter-spacing: -0.015em;
  color: var(--ink);
}

.section-desc {
  max-width: 56ch;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--muted);
}

/* ── Providers ─────────────────────────────────────────────── */

.provider-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 20px;
}

.provider-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  padding: 12px 13px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--paper);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.provider-card:hover {
  border-color: var(--line-strong);
  background: #faf9f4;
}

.provider-card.is-selected {
  border-color: var(--accent);
  background: var(--accent-tint);
}

.provider-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.provider-name {
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.provider-card.is-selected .provider-name {
  color: var(--accent-strong);
}

.provider-desc {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--faint);
  overflow-wrap: anywhere;
}

.provider-check {
  margin-top: 1px;
  color: var(--accent);
  flex-shrink: 0;
}

/* ── Form ──────────────────────────────────────────────────── */

.settings-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.settings-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.settings-form :deep(.el-form-item__label) {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--ink-2);
}

.settings-form :deep(.el-form-item__content) {
  flex-direction: column;
  align-items: stretch;
}

.field-hint {
  width: 100%;
  margin-top: 5px;
  font-size: 11px;
  line-height: 1.55;
  color: var(--faint);
  overflow-wrap: anywhere;
}

.temp-item :deep(.el-input__inner) {
  font-variant-numeric: tabular-nums;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 18px;
}

.switch-copy {
  min-width: 0;
}

.switch-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
}

.switch-desc {
  margin-top: 2px;
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--faint);
}

/* ── Status bands ──────────────────────────────────────────── */

.status-band {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 18px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  font-size: 12px;
  line-height: 1.6;
}

.status-band :deep(.el-icon) {
  margin-top: 2px;
  flex-shrink: 0;
}

.status-band.is-success {
  background: var(--success-tint);
  color: #245a43;
}

.status-band.is-danger {
  background: var(--danger-tint);
  color: #8f2f25;
}

.status-text {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}

.status-close {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  margin: -2px -4px 0 0;
  border: 0;
  border-radius: var(--r-xs);
  background: transparent;
  color: inherit;
  opacity: 0.65;
  cursor: pointer;
}

.status-close:hover {
  opacity: 1;
  background: rgba(27, 26, 22, 0.06);
}

.section-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.section-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* ── Reply styles ──────────────────────────────────────────── */

.style-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.style-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--paper);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.style-card:hover {
  border-color: var(--line-strong);
  background: #faf9f4;
}

.style-card.is-selected {
  border-color: var(--accent);
  background: var(--accent-tint);
}

.style-radio {
  display: grid;
  place-items: center;
  width: 14px;
  height: 14px;
  margin-top: 2px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: var(--paper);
  flex-shrink: 0;
}

.style-card.is-selected .style-radio {
  border-color: var(--accent);
}

.style-card.is-selected .style-radio::after {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
}

.style-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.style-label {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--ink);
}

.style-desc {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.55;
  color: var(--faint);
}

.section-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  font-size: 11px;
  line-height: 1.6;
  color: var(--faint);
}

.section-note :deep(.el-icon) {
  margin-top: 2px;
  flex-shrink: 0;
}

/* ── Prompt templates ──────────────────────────────────────── */

.prompt-loading {
  padding: 4px 0;
}

.quiet-note {
  padding: 14px 0;
  font-size: 11.5px;
  color: var(--faint);
}

.inline-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
  background: var(--danger-tint);
  color: #8f2f25;
  font-size: 11.5px;
  line-height: 1.6;
}

.inline-error span {
  flex: 1;
  min-width: 0;
}

.template-list {
  margin-top: -4px;
}

.template-list :deep(.el-collapse-item__header) {
  min-height: 46px;
  padding: 6px 0;
  gap: 8px;
  font-size: 13.5px;
  color: var(--ink);
}

.template-list :deep(.el-collapse-item__arrow) {
  margin-left: 8px;
  color: var(--faint);
}

.template-list :deep(.el-collapse-item__content) {
  padding-bottom: 16px;
}

.template-name {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.template-tag {
  flex-shrink: 0;
}

.template-desc {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--muted);
}

.template-textarea :deep(.el-textarea__inner) {
  min-height: 220px;
  padding: 12px 14px;
  background: #faf9f4;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.7;
}

.template-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.template-meta {
  font-size: 10.5px;
  color: var(--faint);
}

.template-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ── OCR ───────────────────────────────────────────────────── */

.ocr-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.ocr-hint {
  min-width: 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--faint);
  overflow-wrap: anywhere;
}

/* ── Save dock ─────────────────────────────────────────────── */

.save-dock {
  position: static;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}

.save-note {
  min-width: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--faint);
}

/* ── States ────────────────────────────────────────────────── */

.load-error {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 760px;
  margin-bottom: 18px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--danger-tint);
  color: #8f2f25;
  font-size: 12px;
  line-height: 1.6;
}

.load-error span {
  flex: 1;
  min-width: 0;
}

.settings-loading {
  display: flex;
  flex-direction: column;
  gap: 22px;
  max-width: 760px;
}

.loading-panel {
  padding: 24px 26px;
}

/* ── Responsive ────────────────────────────────────────────── */

@media (max-width: 1100px) {
  .settings-layout {
    grid-template-columns: 180px minmax(0, 1fr);
    gap: 28px;
  }
}

@media (max-width: 960px) {
  .settings-layout {
    grid-template-columns: 1fr;
    gap: 18px;
  }

  .settings-nav {
    position: static;
    flex-direction: row;
    gap: 6px;
    min-width: 0;
    max-width: 100%;
    overflow-x: auto;
    padding: 0 0 2px;
    scrollbar-width: none;
  }

  .settings-nav::-webkit-scrollbar {
    display: none;
  }

  .nav-label {
    display: none;
  }

  .nav-link {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    height: 40px;
    padding: 0 12px;
    border: 1px solid var(--line);
    border-radius: var(--r-sm);
    background: var(--paper);
    font-size: 12px;
    white-space: nowrap;
  }

  .nav-link::before {
    display: none;
  }

  .nav-link.is-active {
    border-color: #c3c9e3;
    background: var(--accent-tint);
  }

  .settings-main {
    max-width: none;
  }

  .save-dock {
    position: static;
  }
}

@media (max-width: 700px) {
  .provider-grid,
  .style-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .settings-section {
    padding: 20px 16px;
  }

  .section-head {
    gap: 10px;
  }

  .section-title {
    font-size: 15px;
  }

  .section-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .section-actions :deep(.el-button) {
    width: 100%;
  }

  .template-actions {
    justify-content: stretch;
  }

  .template-actions :deep(.el-button) {
    width: 100%;
  }

  .save-dock {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .save-dock :deep(.el-button) {
    width: 100%;
  }

  .template-textarea :deep(.el-textarea__inner) {
    min-height: 200px;
    font-size: 11.5px;
  }
}
</style>
