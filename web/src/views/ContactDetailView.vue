<template>
  <div class="page detail-page">
    <PageHeader
      back-to="/contacts"
      back-label="联系人"
      :title="contact?.name || '联系人'"
      :description="pageDescription"
    >
      <template #actions>
        <el-button type="primary" :disabled="!contact || loading" @click="openAdvisor">
          <el-icon><ChatLineRound /></el-icon>
          军师模式
        </el-button>
        <el-dropdown
          v-if="contact"
          trigger="click"
          placement="bottom-end"
          :disabled="deleting"
          @command="handleCommand"
        >
          <button type="button" class="more-trigger" aria-label="更多操作">
            <el-icon :size="16"><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="delete">
                <span class="menu-danger">删除联系人</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </PageHeader>

    <!-- Loading -->
    <div v-if="loading" class="detail-skeletons" aria-label="正在加载联系人">
      <div class="summary-skeleton">
        <el-skeleton animated :rows="3" />
      </div>
      <div class="memory-skeletons">
        <div v-for="n in 3" :key="n" class="memory-skeleton">
          <el-skeleton :rows="3" animated />
        </div>
      </div>
    </div>

    <!-- Error -->
    <div v-else-if="loadError" class="feedback" role="alert">
      <el-icon :size="18"><WarningFilled /></el-icon>
      <p>{{ loadError }}</p>
      <el-button size="small" @click="load">重试</el-button>
    </div>

    <!-- Not found -->
    <EmptyState
      v-else-if="notFound"
      title="联系人不存在"
      description="这个联系人可能已经被删除，或链接有误。"
    >
      <el-button @click="$router.push('/contacts')">返回联系人</el-button>
    </EmptyState>

    <template v-else-if="contact">
      <!-- Summary -->
      <section class="summary">
        <div class="summary-main">
          <MonogramAvatar :name="contact.name" :size="64" />
          <div class="summary-copy">
            <h2 class="summary-name">{{ contact.name }}</h2>
            <div class="summary-meta">
              <span class="platform-chip">{{ platformLabel(contact.platform) }}</span>
              <span class="meta-item tabular">添加于 {{ formatDate(contact.createdAt) }}</span>
              <span class="meta-dot" aria-hidden="true"></span>
              <span class="meta-item tabular">{{ memoryCount }} 条记忆</span>
            </div>
          </div>
        </div>

        <div class="category-row">
          <template v-if="categoryBreakdown.length">
            <span v-for="cat in categoryBreakdown" :key="cat.key" class="cat-item">
              <span class="cat-dot" :style="{ background: cat.color }"></span>
              <span class="cat-label">{{ cat.label }}</span>
              <span class="cat-count tabular">{{ cat.count }}</span>
            </span>
          </template>
          <span v-else class="cat-empty">还没有可分类的记忆</span>
        </div>
      </section>

      <!-- Memories -->
      <section class="memory-section">
        <header class="section-head">
          <h2>
            记忆
            <span class="section-count tabular">{{ memoryCount }}</span>
          </h2>
          <RouterLink to="/memories" class="quiet-link">
            查看全部记忆
            <el-icon :size="11"><ArrowRight /></el-icon>
          </RouterLink>
        </header>

        <div v-if="memoryLoading" class="memory-skeletons" aria-label="正在加载记忆">
          <div v-for="n in 3" :key="n" class="memory-skeleton">
            <el-skeleton :rows="3" animated />
          </div>
        </div>

        <div v-else-if="memoryError" class="feedback is-inline" role="alert">
          <p>{{ memoryError }}</p>
          <el-button size="small" @click="loadMemories(contact.id)">重试</el-button>
        </div>

        <EmptyState
          v-else-if="!memoryCount"
          title="这段关系还没有被提炼"
          description="导入更多聊天后，AI 会把约定、偏好和事件整理到这里。"
        >
          <el-button type="primary" @click="$router.push('/')">再导入一段聊天</el-button>
        </EmptyState>

        <ol v-else class="memory-lane">
          <li v-for="m in memories" :key="m.id" class="lane-item">
            <span class="lane-node" :style="{ background: categoryMeta(m.category).color }"></span>
            <MemoryItem :item="m" :show-contact="false" />
          </li>
        </ol>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, ChatLineRound, MoreFilled, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'
import { useMemoryStore } from '@/stores/memories'
import type { Memory } from '@/stores/memories'
import { useContactStore } from '@/stores/contacts'
import type { Contact } from '@/stores/contacts'
import { categoryMeta, formatDate, platformLabel } from '@/utils/presentation'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import MonogramAvatar from '@/components/MonogramAvatar.vue'
import MemoryItem from '@/components/MemoryItem.vue'

const route = useRoute()
const router = useRouter()
const contactStore = useContactStore()
const memoryStore = useMemoryStore()

const contact = ref<Contact | null>(null)
const memories = ref<Memory[]>([])
const loading = ref(true)
const memoryLoading = ref(true)
const notFound = ref(false)
const loadError = ref('')
const memoryError = ref('')
const deleting = ref(false)

const memoryCount = computed(() => memories.value.length)

const joinedLabel = computed(() => {
  const raw = contact.value?.createdAt
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}年${d.getMonth() + 1}月加入`
})

const pageDescription = computed(() => {
  if (!contact.value) return ''
  const parts = [platformLabel(contact.value.platform)]
  if (joinedLabel.value) parts.push(joinedLabel.value)
  return parts.join(' · ')
})

const categoryBreakdown = computed(() => {
  const counts: Record<string, number> = {}
  for (const m of memories.value) {
    counts[m.category] = (counts[m.category] || 0) + 1
  }
  return Object.keys(counts)
    .map((key) => ({ ...categoryMeta(key), count: counts[key]! }))
    .sort((a, b) => b.count - a.count)
})

onMounted(load)

async function load() {
  loading.value = true
  notFound.value = false
  loadError.value = ''
  memoryError.value = ''
  memoryLoading.value = true
  contact.value = null
  memories.value = []

  const id = Number(route.params.id)
  if (!Number.isFinite(id)) {
    loading.value = false
    memoryLoading.value = false
    notFound.value = true
    return
  }

  try {
    const res = await api.get<Contact>(`/contacts/${id}`)
    if (!res.data) {
      notFound.value = true
      loading.value = false
      memoryLoading.value = false
      return
    }
    contact.value = res.data
  } catch (e: any) {
    if (e?.response?.status === 404) {
      notFound.value = true
    } else {
      loadError.value = e?.response?.data?.message || e?.message || '联系人加载失败，请重试'
    }
    loading.value = false
    memoryLoading.value = false
    return
  }

  loading.value = false
  await loadMemories(id)
}

async function loadMemories(id: number) {
  memoryLoading.value = true
  memoryError.value = ''
  try {
    await memoryStore.fetchByContact(id)
    memories.value = [...memoryStore.memories]
  } catch (e: any) {
    memories.value = []
    memoryError.value = e?.response?.data?.message || e?.message || '记忆加载失败，请重试'
  } finally {
    memoryLoading.value = false
  }
}

function openAdvisor() {
  if (!contact.value) return
  router.push({ path: '/advisor', query: { contactId: String(contact.value.id) } })
}

function handleCommand(command: string) {
  if (command === 'delete') handleDelete()
}

async function handleDelete() {
  if (!contact.value || deleting.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除「${contact.value.name}」及其全部对话和记忆？此操作不可撤销。`,
      '删除联系人',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await contactStore.remove(contact.value.id)
    ElMessage.success('已删除')
    router.push('/contacts')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败，请重试')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
/* ── Summary ───────────────────────────────────────────────── */

.summary {
  padding: 20px 22px 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.summary-main {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.summary-copy {
  flex: 1;
  min-width: 0;
}

.summary-name {
  font-size: 21px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 7px;
}

.platform-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 7px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-xs);
  font-size: 10.5px;
  font-weight: 550;
  color: var(--muted);
  white-space: nowrap;
}

.meta-item {
  font-size: 11px;
  color: var(--faint);
  white-space: nowrap;
}

.meta-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--line-strong);
}

.category-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px 18px;
  margin-top: 16px;
  padding-top: 13px;
  border-top: 1px solid var(--line);
}

.cat-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--muted);
}

.cat-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex-shrink: 0;
}

.cat-label {
  color: var(--ink-2);
}

.cat-count {
  font-size: 10.5px;
  color: var(--faint);
}

.cat-empty {
  font-size: 11.5px;
  color: var(--faint);
}

/* ── Memories ──────────────────────────────────────────────── */

.memory-section {
  margin-top: 34px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.section-head h2 {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 17px;
  font-weight: 640;
  letter-spacing: -0.02em;
}

.section-count {
  font-size: 11px;
  font-weight: 500;
  color: var(--faint);
}

.quiet-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--faint);
  white-space: nowrap;
  transition: color 0.15s ease;
}

.quiet-link:hover {
  color: var(--accent);
}

.memory-lane {
  position: relative;
  margin: 14px 0 0;
  padding: 2px 0 0 24px;
  list-style: none;
}

.memory-lane::before {
  content: '';
  position: absolute;
  top: 12px;
  bottom: 10px;
  left: 5px;
  width: 1px;
  background: linear-gradient(to bottom, var(--line-strong), var(--line) 70%, transparent);
}

.lane-item {
  position: relative;
  margin-bottom: 12px;
}

.lane-node {
  position: absolute;
  top: 23px;
  left: -23px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  box-shadow: 0 0 0 3px var(--bg);
}

/* ── States ────────────────────────────────────────────────── */

.detail-skeletons {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-skeleton {
  padding: 22px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.memory-skeletons {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.memory-skeleton {
  padding: 18px 20px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}

.feedback {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 54px 20px;
  text-align: center;
  color: var(--muted);
}

.feedback :deep(.el-icon) {
  color: var(--danger);
}

.feedback p {
  max-width: 46ch;
  font-size: 13px;
  line-height: 1.7;
}

.feedback.is-inline {
  padding: 34px 16px;
}

.more-trigger {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--muted);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.more-trigger:hover {
  background: var(--wash);
  color: var(--ink);
}

.menu-danger {
  color: var(--danger);
}

/* ── Responsive ────────────────────────────────────────────── */

@media (max-width: 700px) {
  .summary {
    padding: 16px 16px 14px;
  }

  .summary-main {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .summary-copy {
    width: 100%;
  }

  .summary-name {
    font-size: 19px;
  }

  .category-row {
    gap: 7px 14px;
  }

  .memory-section {
    margin-top: 26px;
  }

  .memory-lane {
    padding-left: 18px;
  }

  .memory-lane::before {
    left: 4px;
  }

  .lane-node {
    top: 21px;
    left: -18px;
  }

  .more-trigger {
    width: 40px;
    height: 40px;
  }
}
</style>
