<template>
  <div class="memories-page">
    <el-container class="page-container">
      <el-header class="page-header">
        <el-button text @click="$router.push('/')">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h1>记忆库</h1>
        <el-select v-model="categoryFilter" placeholder="全部分类" clearable style="width: 160px">
          <el-option label="约定/承诺" value="promise" />
          <el-option label="个人信息" value="personal_info" />
          <el-option label="偏好" value="preference" />
          <el-option label="事件" value="event" />
          <el-option label="人设特征" value="persona" />
          <el-option label="关系动态" value="relationship" />
        </el-select>
      </el-header>

      <el-main>
        <el-select
          v-model="selectedContactId"
          placeholder="选择联系人"
          clearable
          style="width: 100%; margin-bottom: 16px"
          @change="onContactChange"
        >
          <el-option
            v-for="c in contactStore.contacts"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>

        <div v-if="loading || memoryStore.searching" style="text-align: center; padding: 40px">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p style="color: #9ca3af; margin-top: 12px">{{ memoryStore.searching ? '搜索中...' : '加载中...' }}</p>
        </div>

        <div v-else-if="errorMsg" style="text-align: center; padding: 40px">
          <p style="color: #ef4444">{{ errorMsg }}</p>
          <el-button size="small" @click="retry" style="margin-top: 12px">重试</el-button>
        </div>

        <template v-else>
          <div class="search-row">
            <el-input
              v-model="searchQuery"
              placeholder="搜索记忆..."
              :prefix-icon="Search"
              clearable
              class="search-input"
            />
            <el-tag
              size="small"
              :type="embeddingActive ? '' : 'info'"
              :effect="embeddingActive ? 'light' : 'plain'"
              class="search-mode-tag"
            >
              {{ embeddingActive ? '混合搜索' : '关键词搜索' }}
            </el-tag>
          </div>

          <el-empty v-if="!displayResults.length" :description="isSearchMode ? '未找到匹配的记忆' : '暂无可展示的记忆'" />

          <el-timeline v-else class="memory-timeline">
          <el-timeline-item
            v-for="m in displayResults"
            :key="m.id"
            :timestamp="''"
            :color="categoryColor(m.category)"
            placement="top"
          >
            <el-card shadow="hover" class="memory-card">
              <div class="memory-header">
                <div style="display: flex; align-items: center; gap: 8px">
                  <el-tag size="small" :type="categoryTagType(m.category)">
                    {{ categoryLabel(m.category) }}
                  </el-tag>
                  <el-tag v-if="isSearchResult(m) && !selectedContactId" size="small" effect="plain" type="info">
                    {{ m.contactName }}
                  </el-tag>
                </div>
                <span class="memory-confidence">
                  <template v-if="isSearchResult(m)">
                    相关度: {{ (m.score * 100).toFixed(0) }}%
                  </template>
                  <template v-else>
                    置信度: {{ (m.confidence * 100).toFixed(0) }}%
                  </template>
                </span>
              </div>
              <p class="memory-content">{{ m.content }}</p>
              <div
                class="memory-source"
                :class="{ clickable: !isSearchResult(m) && m.sourceMsgIds?.length }"
                @click.stop="!isSearchResult(m) && m.sourceMsgIds?.length && toggleSources(m.id)"
              >
                <el-icon><ChatDotRound /></el-icon>
                <template v-if="isSearchResult(m)">搜索匹配</template>
                <template v-else-if="m.sourceMsgIds?.length">
                  来自 {{ m.sourceMsgIds.length }} 条消息
                  <el-icon v-if="expandedMemoryId === m.id" class="expand-icon"><ArrowUp /></el-icon>
                  <el-icon v-else class="expand-icon"><ArrowDown /></el-icon>
                </template>
              </div>
              <div v-if="expandedMemoryId === m.id && sourcesLoading" class="source-loading">
                加载中...
              </div>
              <div v-else-if="expandedMemoryId === m.id && sourceMessages.length" class="source-messages">
                <div v-for="msg in sourceMessages" :key="msg.id" class="source-msg">
                  <span class="source-speaker">{{ msg.speaker }}</span>
                  <span class="source-time">{{ formatSourceTime(msg.rawTime) }}</span>
                  <p class="source-content">{{ msg.content }}</p>
                </div>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        </template>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ArrowLeft, Search, ChatDotRound, Loading, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { useMemoryStore } from '@/stores/memories'
import type { SearchResult } from '@/stores/memories'
import { useContactStore } from '@/stores/contacts'
import api from '@/api'
import { getAiSettings } from '@/api/settings'

interface MessageSource {
  id: number
  speaker: string
  content: string
  rawTime: string
}

const memoryStore = useMemoryStore()
const contactStore = useContactStore()
const searchQuery = ref('')
const categoryFilter = ref('')
const selectedContactId = ref<number | null>(null)
const loading = ref(false)
const errorMsg = ref('')
const expandedMemoryId = ref<number | null>(null)
const sourceMessages = ref<MessageSource[]>([])
const sourcesLoading = ref(false)
const embeddingActive = ref(false)

let debounceTimer: ReturnType<typeof setTimeout> | null = null

onMounted(async () => {
  contactStore.fetchAll()
  try {
    const settings = await getAiSettings()
    embeddingActive.value = !!settings.embeddingActive
  } catch {
    embeddingActive.value = false
  }
})

watch(searchQuery, (val) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (!val.trim()) {
    memoryStore.searchResults = []
    return
  }
  debounceTimer = setTimeout(() => {
    memoryStore.searchMemories(val.trim(), selectedContactId.value ?? undefined)
  }, 300)
})

const isSearchMode = computed(() => searchQuery.value.trim().length > 0)

async function onContactChange(id: number | null) {
  if (!id) return
  selectedContactId.value = id
  loading.value = true
  errorMsg.value = ''
  try {
    await memoryStore.fetchByContact(id)
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function retry() {
  if (selectedContactId.value) onContactChange(selectedContactId.value)
}

async function toggleSources(memoryId: number) {
  if (expandedMemoryId.value === memoryId) {
    expandedMemoryId.value = null
    sourceMessages.value = []
    return
  }
  expandedMemoryId.value = memoryId
  sourcesLoading.value = true
  sourceMessages.value = []
  try {
    const res = await api.get<MessageSource[]>(`/memories/${memoryId}/sources`)
    sourceMessages.value = res.data
  } catch {
    sourceMessages.value = []
  } finally {
    sourcesLoading.value = false
  }
}

function formatSourceTime(t: string) {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const filteredMemories = computed(() => {
  let list = memoryStore.memories
  if (categoryFilter.value) {
    list = list.filter((m) => m.category === categoryFilter.value)
  }
  return list
})

const displayResults = computed<any[]>(() => {
  if (isSearchMode.value) return memoryStore.searchResults
  return filteredMemories.value
})

function isSearchResult(item: any): item is SearchResult {
  return item && 'contactName' in item && 'score' in item
}

function categoryColor(cat: string) {
  const map: Record<string, string> = {
    promise: '#f59e0b', personal_info: '#6366f1', preference: '#a855f7',
    event: '#10b981', persona: '#ec4899', relationship: '#f97316',
  }
  return map[cat] || '#9ca3af'
}

function categoryTagType(cat: string) {
  const map: Record<string, any> = {
    promise: 'warning', personal_info: '', preference: 'success',
    event: 'info', persona: 'danger', relationship: '',
  }
  return map[cat] || 'info'
}

function categoryLabel(cat: string) {
  const map: Record<string, string> = {
    promise: '约定', personal_info: '个人信息', preference: '偏好',
    event: '事件', persona: '人设', relationship: '关系',
  }
  return map[cat] || cat
}
</script>

<style scoped>
.memories-page {
  min-height: 100vh;
  background: #f9fafb;
}

.page-container {
  max-width: 800px;
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
}

.search-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
}

.search-input {
  flex: 1;
}

.search-mode-tag {
  flex-shrink: 0;
  font-size: 11px;
  white-space: nowrap;
}

.memory-card {
  border-radius: 10px;
}

.memory-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.memory-confidence {
  font-size: 12px;
  color: #9ca3af;
}

.memory-content {
  font-size: 15px;
  color: #374151;
  line-height: 1.7;
  margin: 0;
}

.memory-source {
  margin-top: 10px;
  font-size: 12px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 4px;
}

.memory-source.clickable {
  cursor: pointer;
  user-select: none;
}

.memory-source.clickable:hover {
  color: #409eff;
}

.expand-icon {
  font-size: 12px;
  margin-left: 2px;
}

.source-loading {
  margin-top: 12px;
  padding: 12px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  background: #f9fafb;
  border-radius: 8px;
}

.source-messages {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.source-msg {
  position: relative;
  background: #f0f7ff;
  border-left: 3px solid #409eff;
  border-radius: 0 8px 8px 0;
  padding: 10px 14px;
}

.source-speaker {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-right: 10px;
}

.source-time {
  font-size: 11px;
  color: #9ca3af;
}

.source-content {
  margin: 6px 0 0 0;
  font-size: 14px;
  color: #374151;
  line-height: 1.6;
}
</style>
