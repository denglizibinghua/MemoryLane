<template>
  <div class="home">
    <el-container class="hero-container">
      <el-header class="hero-header">
        <div class="logo">
          <el-icon :size="28" color="#6366f1"><Timer /></el-icon>
          <span class="logo-text">MemoryLane</span>
        </div>
        <div class="header-actions">
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
            />
            <div class="card-footer">
              <div style="display: flex; gap: 8px">
                <el-input v-model="contactName" placeholder="联系人（可选）" style="width: 140px" />
                <el-select v-model="platform" placeholder="自动识别" style="width: 140px">
                  <el-option label="自动识别" value="auto" />
                  <el-option label="微信" value="wechat" />
                  <el-option label="QQ" value="qq" />
                  <el-option label="抖音" value="douyin" />
                  <el-option label="通用文本" value="generic" />
                </el-select>
              </div>
              <el-button type="primary" @click="handleImport" :loading="importing">
                开始分析
              </el-button>
            </div>
          </el-card>
        </div>

        <el-alert
          v-if="importResult"
          :title="`导入完成：${importResult.stats.newMessages} 条新消息，${importResult.stats.duplicates} 条重复`"
          type="success"
          show-icon
          closable
          style="max-width: 640px; margin: 0 auto 16px"
        />
        <el-alert
          v-if="importError"
          :title="importError"
          type="error"
          show-icon
          closable
          style="max-width: 640px; margin: 0 auto 16px"
        />

        <div class="features">
          <el-row :gutter="24">
            <el-col :span="8" v-for="f in features" :key="f.title">
              <el-card shadow="never" class="feature-card">
                <el-icon :size="32" :color="f.color"><component :is="f.icon" /></el-icon>
                <h3>{{ f.title }}</h3>
                <p>{{ f.desc }}</p>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Timer, List, Collection, EditPen, ChatDotRound, DataAnalysis, MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useMemoryStore } from '@/stores/memories'
import type { ImportResult } from '@/stores/memories'

const memoryStore = useMemoryStore()
const pasteText = ref('')
const platform = ref('auto')
const contactName = ref('')
const importing = ref(false)
const importResult = ref<ImportResult | null>(null)
const importError = ref('')

const features = [
  {
    icon: ChatDotRound,
    color: '#6366f1',
    title: '多平台支持',
    desc: '自动识别微信、QQ、抖音、短信等聊天格式',
  },
  {
    icon: DataAnalysis,
    color: '#a855f7',
    title: '记忆提炼',
    desc: 'AI 自动提取约定、偏好、个人信息等结构化记忆',
  },
  {
    icon: MagicStick,
    color: '#ec4899',
    title: '军师模式',
    desc: '基于历史聊天记忆，智能生成回复建议',
  },
]

async function handleImport() {
  if (!pasteText.value.trim()) {
    ElMessage.warning('请先粘贴聊天记录')
    return
  }
  importing.value = true
  importResult.value = null
  importError.value = ''
  try {
    const result = await memoryStore.importText(contactName.value, platform.value, pasteText.value)
    importResult.value = result
    pasteText.value = ''
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
  margin: 0 auto 60px;
  border-radius: 12px;
}

.import-card :deep(.el-card__header) {
  font-weight: 600;
  color: #374151;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}

.features {
  max-width: 800px;
  margin: 0 auto;
}

.feature-card {
  text-align: center;
  border: none;
  border-radius: 12px;
  transition: transform 0.2s;
}

.feature-card:hover {
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
