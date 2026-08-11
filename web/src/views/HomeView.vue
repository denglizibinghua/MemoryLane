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
import { ref } from 'vue'
import { Timer, List, Collection, EditPen, ChatDotRound, DataAnalysis, MagicStick, Setting, Bell, TrendCharts, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useMemoryStore } from '@/stores/memories'
import type { ImportResult, PreviewResult } from '@/stores/memories'

const memoryStore = useMemoryStore()

const pasteText = ref('')
const platform = ref('auto')
const selfName = ref('')
const previewResult = ref<PreviewResult | null>(null)
const previewLoading = ref(false)
const importing = ref(false)
const importResult = ref<ImportResult | null>(null)
const importError = ref('')

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
  } catch (e: any) {
    importError.value = e?.response?.data?.message || e?.message || '导入失败，请重试'
  } finally {
    importing.value = false
  }
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
