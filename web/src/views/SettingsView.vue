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
  type AiSettings,
  type ProviderInfo,
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
})

const serverProviders = ref<ProviderInfo[]>([])

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
  }
}

onMounted(() => {
  loadSettings()
})
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
</style>
