<template>
  <div class="advisor-page">
    <el-container class="page-container">
      <el-header class="page-header">
        <el-button text @click="$router.push('/')">
          <el-icon><ArrowLeft /></el-icon> 返回首页
        </el-button>
        <h1>🧠 军师模式</h1>
        <span style="width: 100px" />
      </el-header>

      <el-main class="advisor-main">
        <div class="advisor-grid">
          <!-- Left: Input panel -->
          <section class="input-panel">
            <el-card shadow="never" class="panel-card">
              <h2 class="panel-title">输入信息</h2>

              <div class="form-item">
                <label class="form-label">选择联系人</label>
                <el-select
                  v-model="selectedContact"
                  filterable
                  remote
                  reserve-keyword
                  clearable
                  placeholder="输入姓名搜索"
                  :remote-method="handleContactSearch"
                  :loading="contactSearching"
                  value-key="id"
                  style="width: 100%"
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

              <div class="form-item">
                <label class="form-label">对方说了什么</label>
                <el-input
                  v-model="lastMessage"
                  type="textarea"
                  :rows="4"
                  placeholder="粘贴对方发来的消息..."
                />
              </div>

              <div class="form-item">
                <label class="form-label">最近聊了什么 <span class="optional-hint">（可选）</span></label>
                <el-input
                  v-model="recentContext"
                  type="textarea"
                  :rows="3"
                  placeholder="最近聊了什么（每行一条，可选）"
                />
              </div>

              <el-button
                type="primary"
                :loading="loading"
                :disabled="!selectedContact || !lastMessage.trim()"
                @click="getSuggestions"
                class="submit-btn"
              >
                帮我回复
              </el-button>
            </el-card>
          </section>

          <!-- Right: Results panel -->
          <section class="results-panel">
            <!-- Loading state -->
            <div v-if="loading" class="loading-state">
              <el-card shadow="never" class="panel-card">
                <el-skeleton :rows="4" animated />
              </el-card>
            </div>

            <!-- Error state -->
            <el-alert
              v-else-if="error"
              :title="error"
              type="error"
              show-icon
              closable
              style="margin-bottom: 16px"
            >
              <template #default>
                <el-button size="small" @click="getSuggestions">重试</el-button>
              </template>
            </el-alert>

            <!-- Empty state (before first request) -->
            <div v-else-if="!hasResults && !loading && !error" class="empty-intro">
              <el-empty description="填写左侧信息，点击「帮我回复」获取建议" />
            </div>

            <!-- Results -->
            <template v-else-if="hasResults">
              <!-- Reply suggestions -->
              <div class="result-section">
                <h2 class="section-title">💬 回复建议</h2>
                <div v-if="replies.length" class="card-list">
                  <el-card v-for="(r, idx) in replies" :key="'r-' + idx" shadow="hover" class="suggestion-card">
                    <div class="card-header-row">
                      <el-tag :color="getStyleTagColor(r.style)" effect="dark" size="small">
                        {{ r.style }}
                      </el-tag>
                    </div>
                    <p class="suggestion-content">{{ r.content }}</p>
                    <p class="suggestion-reason">基于：{{ r.reason }}</p>
                    <div class="card-footer-row">
                      <el-button text type="primary" size="small" :icon="DocumentCopy" @click="copyToClipboard(r.content)">
                        复制
                      </el-button>
                    </div>
                  </el-card>
                </div>
                <el-empty v-else description="没有生成回复建议" :image-size="80" />
              </div>

              <!-- New topic suggestions -->
              <div class="result-section">
                <h2 class="section-title">💡 新话题建议</h2>
                <div v-if="newTopics.length" class="card-list">
                  <el-card v-for="(t, idx) in newTopics" :key="'t-' + idx" shadow="hover" class="suggestion-card topic-card">
                    <p class="suggestion-content">{{ t.content }}</p>
                    <p class="suggestion-reason">基于：{{ t.reason }}</p>
                  </el-card>
                </div>
                <el-empty v-else description="没有新话题建议" :image-size="80" />
              </div>
            </template>
          </section>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ArrowLeft, DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '@/api'
import { suggestAdvisor, type AdvisorReply, type NewTopic } from '@/api/advisor'

interface ContactOption {
  id: number
  name: string
  platform: string
}

const loading = ref(false)
const error = ref('')
const contactSearching = ref(false)
const contactSearch = ref('')
const contactOptions = ref<ContactOption[]>([])
const selectedContact = ref<ContactOption | null>(null)
const lastMessage = ref('')
const recentContext = ref('')
const replies = ref<AdvisorReply[]>([])
const newTopics = ref<NewTopic[]>([])

const hasResults = computed(() => replies.value.length > 0 || newTopics.value.length > 0)

// Debounced contact search (300ms)
let debounceTimer: ReturnType<typeof setTimeout> | null = null

function handleContactSearch(query: string) {
  contactSearch.value = query
  if (debounceTimer) clearTimeout(debounceTimer)
  if (!query || query.trim().length === 0) {
    contactOptions.value = []
    contactSearching.value = false
    return
  }
  contactSearching.value = true
  debounceTimer = setTimeout(async () => {
    try {
      const res = await api.get('/contacts/search', { params: { q: query } })
      contactOptions.value = res.data as ContactOption[]
    } catch {
      contactOptions.value = []
    } finally {
      contactSearching.value = false
    }
  }, 300)
}

// Watch to also reset options when selection cleared
watch(selectedContact, (val) => {
  if (!val) {
    contactOptions.value = []
  }
})

async function getSuggestions() {
  if (!selectedContact.value || !lastMessage.value.trim()) return
  loading.value = true
  error.value = ''
  replies.value = []
  newTopics.value = []
  try {
    const result = await suggestAdvisor({
      contactId: selectedContact.value.id,
      lastMessage: lastMessage.value.trim(),
      recentContext: recentContext.value.trim()
        ? recentContext.value.trim().split('\n').filter(Boolean)
        : undefined,
    })
    replies.value = result.replies || []
    newTopics.value = result.newTopics || []
    if (replies.value.length === 0 && newTopics.value.length === 0) {
      ElMessage.info('没有生成建议，请尝试提供更多上下文')
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || '请求失败，请检查后端是否启动'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.error('复制失败')
  )
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

function platformLabel(p: string) {
  const map: Record<string, string> = {
    wechat: '微信',
    qq: 'QQ',
    douyin: '抖音',
    sms: '短信',
    other: '其他',
  }
  return map[p] || p
}
</script>

<style scoped>
.advisor-page {
  min-height: 100vh;
  background: #f9fafb;
}

.page-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0;
  height: auto;
}

.page-header h1 {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  color: #1e1b4b;
}

.advisor-main {
  padding: 8px 0 60px;
}

.advisor-grid {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: 24px;
  align-items: start;
}

.panel-card {
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}

.panel-card :deep(.el-card__body) {
  padding: 20px;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px 0;
  color: #1f2937;
}

.form-item {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}

.optional-hint {
  color: #9ca3af;
  font-weight: 400;
}

.contact-platform-tag {
  float: right;
  margin-left: 8px;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px 0;
  color: #1f2937;
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.suggestion-card {
  border-radius: 10px;
  transition: transform 0.15s, box-shadow 0.15s;
}

.suggestion-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06);
}

.suggestion-card :deep(.el-card__body) {
  padding: 16px;
}

.card-header-row {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.suggestion-content {
  font-size: 15px;
  color: #1f2937;
  line-height: 1.7;
  margin: 0 0 8px 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.suggestion-reason {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.6;
  margin: 0 0 8px 0;
}

.card-footer-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.topic-card {
  background: #fffbeb;
}

.topic-card:hover {
  background: #fef9e7;
}

.result-section {
  margin-bottom: 24px;
}

.loading-state .panel-card,
.empty-intro .panel-card {
  background: #fff;
}

.empty-intro {
  padding: 40px 0;
}

@media (max-width: 900px) {
  .advisor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
