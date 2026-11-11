<template>
  <article class="memory-item">
    <header class="memory-head">
      <div class="memory-head-left">
        <CategoryPill :category="item.category" />
        <RouterLink
          v-if="showContact && displayContact && isSearchResult(item)"
          :to="`/contacts/${item.contactId}`"
          class="contact-chip"
        >
          {{ displayContact }}
          <el-icon :size="10"><ArrowRight /></el-icon>
        </RouterLink>
        <span v-else-if="showContact && displayContact" class="contact-chip is-static">
          {{ displayContact }}
        </span>
      </div>
      <div class="memory-metric" :title="metricTitle">
        <span class="metric-label">{{ metricLabel }}</span>
        <span class="metric-value tabular">{{ metricValue }}</span>
      </div>
    </header>

    <p class="memory-content">{{ item.content }}</p>

    <footer class="memory-foot">
      <span class="memory-ref" :title="fullDate">
        #{{ item.id }}
        <template v-if="!isSearchResult(item)"> · {{ relativeDay(item.createdAt) }}</template>
      </span>
      <button
        type="button"
        class="memory-action"
        :class="{ 'is-open': expanded }"
        :aria-expanded="expanded"
        @click="toggleSources"
      >
        <el-icon :size="13"><ChatLineSquare /></el-icon>
        <span>{{ sourceButtonLabel }}</span>
        <el-icon :size="11" class="chevron"><ArrowDown /></el-icon>
      </button>
      <span class="memory-spacer"></span>
      <button
        type="button"
        class="icon-action"
        title="复制这条记忆"
        aria-label="复制这条记忆"
        @click="copyContent"
      >
        <el-icon :size="14"><DocumentCopy /></el-icon>
      </button>
    </footer>

    <div v-if="expanded" class="source-panel">
      <div class="source-head">
        <span>引用原文</span>
        <span v-if="sources.length" class="source-count">{{ sources.length }} 条</span>
      </div>

      <div v-if="sourcesLoading" class="source-skeletons">
        <el-skeleton :rows="2" animated />
      </div>
      <div v-else-if="sourceError" class="source-feedback">
        <span>{{ sourceError }}</span>
        <button type="button" class="text-action" @click="loadSources">重试</button>
      </div>
      <div v-else-if="!sources.length" class="source-feedback">
        这条记忆没有关联到可展示的原始消息。
      </div>
      <ul v-else class="source-list">
        <li v-for="msg in sources" :key="msg.id" class="source-item">
          <div class="source-meta">
            <span class="source-speaker">{{ msg.speaker === 'self' ? '我' : msg.speaker }}</span>
            <span class="source-time tabular">{{ formatDateTime(msg.rawTime) }}</span>
          </div>
          <p class="source-text">{{ msg.content }}</p>
        </li>
      </ul>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, ArrowRight, ChatLineSquare, DocumentCopy } from '@element-plus/icons-vue'
import type { Memory, SearchResult } from '@/stores/memories'
import { formatDateTime, isSearchResult, percent, relativeDay } from '@/utils/presentation'
import CategoryPill from '@/components/CategoryPill.vue'
import api from '@/api'

interface MessageSource {
  id: number
  speaker: string
  content: string
  rawTime: string
  conversationId?: number
}

const props = withDefaults(
  defineProps<{
    item: Memory | SearchResult
    contactName?: string
    showContact?: boolean
    rank?: number
  }>(),
  { showContact: false }
)

const expanded = ref(false)
const sources = ref<MessageSource[]>([])
const sourcesLoading = ref(false)
const sourcesLoaded = ref(false)
const sourceError = ref('')

const displayContact = computed(() => {
  if (isSearchResult(props.item)) return props.item.contactName
  return props.contactName || ''
})

const metricLabel = computed(() => (isSearchResult(props.item) ? '检索结果' : '置信度'))
const metricValue = computed(() => {
  if (isSearchResult(props.item)) {
    return typeof props.rank === 'number' ? `#${props.rank}` : '命中'
  }
  return percent(props.item.confidence)
})
const metricTitle = computed(() =>
  isSearchResult(props.item) ? '按检索相关度排序' : 'AI 提炼这条记忆时的置信度'
)

const fullDate = computed(() => {
  if (isSearchResult(props.item)) return `记忆 #${props.item.id}`
  return formatDateTime(props.item.createdAt)
})

const knownSourceCount = computed(() => {
  if (isSearchResult(props.item)) return sourcesLoaded.value ? sources.value.length : 0
  return props.item.sourceMsgIds?.length || 0
})

const sourceButtonLabel = computed(() => {
  if (expanded.value) return '收起引用'
  if (knownSourceCount.value > 0) return `引用 ${knownSourceCount.value} 条`
  return '查看原文'
})

async function toggleSources() {
  if (expanded.value) {
    expanded.value = false
    return
  }
  expanded.value = true
  if (!sourcesLoaded.value) await loadSources()
}

async function loadSources() {
  sourcesLoading.value = true
  sourceError.value = ''
  try {
    const res = await api.get<MessageSource[]>(`/memories/${props.item.id}/sources`)
    sources.value = res.data || []
    sourcesLoaded.value = true
  } catch (e: any) {
    sourceError.value = e?.response?.data?.message || '出处加载失败，请重试'
  } finally {
    sourcesLoading.value = false
  }
}

async function copyContent() {
  const text = props.item.content
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择文本')
  }
}
</script>

<style scoped>
.memory-item {
  position: relative;
  padding: 18px 20px 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  transition: border-color 0.16s ease, background-color 0.16s ease;
}

.memory-item:hover {
  border-color: var(--line-strong);
  background: var(--paper);
}

.memory-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.memory-head-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.contact-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  max-width: 180px;
  font-size: 11.5px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contact-chip:hover {
  color: var(--accent);
}

.contact-chip.is-static {
  color: var(--faint);
}

.memory-metric {
  display: flex;
  align-items: baseline;
  gap: 5px;
  flex-shrink: 0;
  white-space: nowrap;
}

.metric-label {
  font-size: 10.5px;
  color: var(--faint);
}

.metric-value {
  font-size: 11.5px;
  font-weight: 550;
  color: var(--muted);
}

.memory-content {
  margin-top: 10px;
  font-size: 14.5px;
  line-height: 1.75;
  color: var(--ink-2);
  overflow-wrap: anywhere;
}

.memory-foot {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}

.memory-ref {
  font-size: 10.5px;
  color: var(--faint);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.memory-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 11.5px;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s ease;
}

.memory-action:hover,
.memory-action.is-open {
  color: var(--accent);
}

.chevron {
  transition: transform 0.18s ease;
}

.memory-action.is-open .chevron {
  transform: rotate(180deg);
}

.memory-spacer {
  flex: 1;
}

.icon-action {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: var(--r-xs);
  background: transparent;
  color: var(--faint);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, background-color 0.15s ease, color 0.15s ease;
}

.memory-item:hover .icon-action,
.icon-action:focus-visible {
  opacity: 1;
}

.icon-action:hover {
  background: var(--wash);
  color: var(--ink);
}

.source-panel {
  margin-top: 14px;
  padding: 14px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
}

.source-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--faint);
}

.source-count {
  padding: 0 6px;
  height: 17px;
  border-radius: var(--r-xs);
  background: var(--wash);
  color: var(--muted);
  font-size: 10px;
  line-height: 17px;
  letter-spacing: 0;
  text-transform: none;
}

.source-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.source-item {
  padding: 10px 12px;
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: var(--r-xs);
}

.source-meta {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.source-speaker {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-2);
}

.source-time {
  font-size: 10.5px;
  color: var(--faint);
}

.source-text {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-2);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.source-feedback {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--muted);
}

.text-action {
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.text-action:hover {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.source-skeletons {
  padding: 4px 0;
}

@media (max-width: 480px) {
  .memory-item {
    padding: 15px 15px 12px;
  }

  .memory-content {
    font-size: 14px;
  }

  .metric-label {
    display: none;
  }

  .icon-action {
    opacity: 1;
  }
}
</style>
