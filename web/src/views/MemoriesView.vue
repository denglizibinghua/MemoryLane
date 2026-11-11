<template>
  <div class="memories">
    <div class="page memories-inner">
      <PageHeader
        title="记忆库"
        description="每一句被记住的细节，都能翻回它出现的那段对话。搜索会查所有联系人。"
      >
        <template #actions>
          <el-button @click="$router.push('/')">
            <el-icon><EditPen /></el-icon>
            导入聊天
          </el-button>
        </template>
      </PageHeader>

      <div v-if="!contactStore.contacts.length && !contactStore.loading" class="no-contacts">
        <EmptyState
          title="记忆库还是空的"
          description="先导入一段聊天。忆路会把约定、偏好、个人信息这些细节提炼出来，按联系人放好。"
        >
          <el-button type="primary" @click="$router.push('/')">去导入第一段聊天</el-button>
        </EmptyState>
      </div>

      <div v-else class="memory-layout">
        <!-- Contact rail -->
        <aside class="contact-rail" aria-label="按联系人浏览">
          <div class="rail-head">
            <span>按联系人</span>
            <span class="rail-count tabular">{{ contactStore.contacts.length }}</span>
          </div>

          <div class="rail-search">
            <el-input
              v-model="contactQuery"
              size="small"
              placeholder="筛选联系人"
              :prefix-icon="Search"
              clearable
            />
          </div>

          <ul v-if="filteredContacts.length" class="rail-list">
            <li v-for="c in filteredContacts" :key="c.id">
              <button
                type="button"
                class="rail-item"
                :class="{ 'is-active': selectedId === c.id && !isSearchMode }"
                @click="selectContact(c.id)"
              >
                <MonogramAvatar :name="c.name" :size="30" />
                <span class="rail-item-copy">
                  <span class="rail-item-name">{{ c.name }}</span>
                  <span class="rail-item-meta">{{ platformLabel(c.platform) }}</span>
                </span>
                <span v-if="selectedId === c.id" class="rail-current"></span>
              </button>
            </li>
          </ul>
          <p v-else class="rail-empty">没有匹配的联系人</p>

          <RouterLink to="/contacts" class="rail-manage">
            管理联系人
            <el-icon :size="11"><ArrowRight /></el-icon>
          </RouterLink>
        </aside>

        <!-- Memory main -->
        <section class="memory-main">
          <div class="search-block">
            <el-input
              v-model="searchQuery"
              size="large"
              class="memory-search"
              placeholder="搜索所有联系人的记忆，比如「发烧」「爬山」「香菜」"
              :prefix-icon="Search"
              clearable
            />
            <span
              class="search-mode"
              :class="{ 'is-plain': !embeddingActive }"
              :title="embeddingActive ? '全文与语义混合检索已开启' : '当前为全文检索，语义检索可在设置中开启'"
            >
              {{ embeddingActive ? '混合检索' : '全文检索' }}
            </span>
          </div>

          <div class="category-row" role="group" aria-label="按记忆类别筛选">
            <button
              type="button"
              class="category-chip"
              :class="{ 'is-active': !categoryFilter }"
              @click="categoryFilter = ''"
            >
              全部
              <span class="chip-count tabular">{{ baseItems.length }}</span>
            </button>
            <button
              v-for="cat in MEMORY_CATEGORIES"
              :key="cat.key"
              type="button"
              class="category-chip"
              :class="{ 'is-active': categoryFilter === cat.key }"
              @click="categoryFilter = cat.key"
            >
              <span class="chip-dot" :style="{ background: cat.color }"></span>
              {{ cat.label }}
              <span class="chip-count tabular">{{ categoryCounts[cat.key] || 0 }}</span>
            </button>
          </div>

          <div class="memory-headline">
            <div>
              <h2 v-if="isSearchMode">
                找到 {{ displayResults.length }} 条记忆
              </h2>
              <h2 v-else-if="selectedContact">
                {{ selectedContact.name }}
              </h2>
              <h2 v-else>记忆</h2>
              <p v-if="isSearchMode" class="headline-sub">
                搜索范围：全部联系人 · 关键词「{{ searchQuery.trim() }}」
              </p>
              <p v-else-if="selectedContact" class="headline-sub">
                {{ platformLabel(selectedContact.platform) }} · {{ displayResults.length }} 条有效记忆
              </p>
            </div>
            <span v-if="!isSearchMode && selectedContact" class="headline-tag">
              按置信度
            </span>
          </div>

          <!-- Loading -->
          <div v-if="loading || memoryStore.searching" class="memory-skeletons" aria-label="加载中">
            <div v-for="n in 3" :key="n" class="memory-skeleton">
              <el-skeleton :rows="3" animated />
            </div>
          </div>

          <!-- Error -->
          <div v-else-if="errorMsg" class="memory-feedback is-error">
            <p>{{ errorMsg }}</p>
            <el-button size="small" @click="retry">重试</el-button>
          </div>

          <!-- Empty: search -->
          <EmptyState
            v-else-if="isSearchMode && !displayResults.length"
            title="没有找到这条记忆"
            description="换一个更短的词试试。忆路会按单个汉字拆分检索，搜「发」也能命中「发烧」。"
          >
            <div class="suggestion-terms">
              <button
                v-for="term in suggestionTerms"
                :key="term"
                type="button"
                class="term-chip"
                @click="searchQuery = term"
              >
                {{ term }}
              </button>
            </div>
          </EmptyState>

          <!-- Empty: selected contact -->
          <EmptyState
            v-else-if="!displayResults.length && selectedContact"
            :title="categoryFilter ? '这个类别下还没有记忆' : '这段关系还没有被提炼'"
            :description="
              categoryFilter
                ? '换一个类别看看，或者去掉筛选。'
                : '导入更多聊天后，AI 会自动把约定、偏好和事件整理到这里。'
            "
          >
            <el-button v-if="categoryFilter" @click="categoryFilter = ''">查看全部类别</el-button>
            <el-button v-else type="primary" @click="$router.push('/')">再导入一段聊天</el-button>
          </EmptyState>

          <!-- Memory lane -->
          <ol v-else class="memory-lane">
            <li v-for="(item, index) in displayResults" :key="item.id" class="lane-item">
              <span class="lane-node" :style="{ background: categoryMeta(item.category).color }"></span>
              <MemoryItem
                :item="item"
                :rank="index + 1"
                :show-contact="isSearchMode"
                :contact-name="!isSearchResult(item) ? selectedContact?.name : undefined"
              />
            </li>
          </ol>

          <p v-if="displayResults.length > 0" class="lane-end">
            — 已经到底了 —
          </p>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowRight, EditPen, Search } from '@element-plus/icons-vue'
import { useMemoryStore } from '@/stores/memories'
import type { Memory, SearchResult } from '@/stores/memories'
import { useContactStore } from '@/stores/contacts'
import { getAiSettings } from '@/api/settings'
import {
  categoryMeta,
  isSearchResult,
  MEMORY_CATEGORIES,
  platformLabel,
} from '@/utils/presentation'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import MonogramAvatar from '@/components/MonogramAvatar.vue'
import MemoryItem from '@/components/MemoryItem.vue'

const memoryStore = useMemoryStore()
const contactStore = useContactStore()

const contactQuery = ref('')
const selectedId = ref<number | null>(null)
const searchQuery = ref('')
const categoryFilter = ref('')
const loading = ref(false)
const errorMsg = ref('')
const embeddingActive = ref(false)

const suggestionTerms = ['发烧', '喜欢', '约定', '搬家', '生日', '工作']

let debounceTimer: ReturnType<typeof setTimeout> | null = null

const filteredContacts = computed(() => {
  const q = contactQuery.value.trim().toLowerCase()
  if (!q) return contactStore.contacts
  return contactStore.contacts.filter((c) => c.name.toLowerCase().includes(q))
})

const selectedContact = computed(() =>
  contactStore.contacts.find((c) => c.id === selectedId.value) ?? null
)

const isSearchMode = computed(() => searchQuery.value.trim().length > 0)

const baseItems = computed<(Memory | SearchResult)[]>(() => {
  if (isSearchMode.value) return memoryStore.searchResults
  return memoryStore.memories
})

const categoryCounts = computed<Record<string, number>>(() => {
  const counts: Record<string, number> = {}
  for (const item of baseItems.value) {
    counts[item.category] = (counts[item.category] || 0) + 1
  }
  return counts
})

const displayResults = computed(() => {
  if (!categoryFilter.value) return baseItems.value
  return baseItems.value.filter((item) => item.category === categoryFilter.value)
})

onMounted(async () => {
  try {
    await contactStore.fetchAll()
    if (contactStore.contacts.length > 0) {
      await selectContact(contactStore.contacts[0]!.id)
    }
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '联系人加载失败'
  }

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
    categoryFilter.value = ''
    return
  }
  debounceTimer = setTimeout(async () => {
    try {
      await memoryStore.searchMemories(val.trim())
    } catch (e: any) {
      errorMsg.value = e?.response?.data?.message || e?.message || '搜索失败'
    }
  }, 300)
})

async function selectContact(id: number) {
  selectedId.value = id
  searchQuery.value = ''
  categoryFilter.value = ''
  loading.value = true
  errorMsg.value = ''
  try {
    await memoryStore.fetchByContact(id)
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '记忆加载失败'
  } finally {
    loading.value = false
  }
}

function retry() {
  if (isSearchMode.value) {
    memoryStore.searchMemories(searchQuery.value.trim())
  } else if (selectedId.value) {
    selectContact(selectedId.value)
  }
}
</script>

<style scoped>
.memories {
  min-height: 100%;
}

.memory-layout {
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  gap: 42px;
  align-items: start;
  min-width: 0;
}

/* ── Contact rail ──────────────────────────────────────────── */

.contact-rail {
  position: sticky;
  top: 28px;
  padding-top: 2px;
  min-width: 0;
  max-height: calc(100dvh - 56px);
  overflow-y: auto;
}

.rail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line-strong);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--muted);
}

.rail-count {
  color: var(--faint);
  font-weight: 500;
}

.rail-search {
  margin-top: 10px;
}

.rail-search :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  padding-left: 0;
}

.rail-search :deep(.el-input__wrapper:hover),
.rail-search :deep(.el-input__wrapper.is-focus) {
  box-shadow: none;
  background: transparent;
}

.rail-search :deep(.el-input__inner) {
  font-size: 12px;
}

.rail-list {
  width: 100%;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.rail-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 8px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-2);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.rail-item:hover {
  background: var(--wash);
}

.rail-item.is-active {
  background: var(--paper);
  box-shadow: 0 0 0 1px var(--line);
}

.rail-item-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.rail-item-name {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-item-meta {
  margin-top: 1px;
  font-size: 10px;
  color: var(--faint);
}

.rail-current {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  flex-shrink: 0;
}

.rail-empty {
  padding: 14px 8px;
  font-size: 11.5px;
  color: var(--faint);
}

.rail-manage {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 12px;
  padding: 6px 8px;
  font-size: 11px;
  color: var(--faint);
}

.rail-manage:hover {
  color: var(--accent);
}

/* ── Main ──────────────────────────────────────────────────── */

.memory-main {
  min-width: 0;
}

.search-block {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.memory-search {
  flex: 1;
  min-width: 0;
}

.memory-search :deep(.el-input__wrapper) {
  min-height: 42px;
  padding: 0 14px;
  background: var(--paper);
  border-radius: var(--r-md);
}

.memory-search :deep(.el-input__inner) {
  font-size: 13.5px;
}

.search-mode {
  flex-shrink: 0;
  padding: 3px 8px;
  border-radius: var(--r-xs);
  background: var(--accent-tint);
  color: var(--accent);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.search-mode.is-plain {
  background: var(--wash);
  color: var(--muted);
}

.category-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  overflow-x: auto;
  padding-bottom: 2px;
  max-width: 100%;
  scrollbar-width: none;
}

.category-row::-webkit-scrollbar {
  display: none;
}

.category-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 9px;
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--muted);
  font-size: 11.5px;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.category-chip:hover {
  background: var(--wash);
  color: var(--ink-2);
}

.category-chip.is-active {
  background: var(--paper);
  border-color: var(--line-strong);
  color: var(--ink);
  font-weight: 600;
}

.chip-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
}

.chip-count {
  font-size: 10px;
  color: var(--faint);
}

.memory-headline {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin: 28px 0 14px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.memory-headline h2 {
  font-size: 18px;
  font-weight: 640;
  letter-spacing: -0.02em;
}

.headline-sub {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--faint);
}

.headline-tag {
  font-size: 10px;
  color: var(--faint);
  letter-spacing: 0.06em;
}

/* ── Lane ──────────────────────────────────────────────────── */

.memory-lane {
  position: relative;
  margin: 0;
  padding: 2px 0 0 24px;
  list-style: none;
}

.memory-lane::before {
  content: '';
  position: absolute;
  top: 12px;
  bottom: 24px;
  left: 5px;
  width: 1px;
  background: linear-gradient(
    to bottom,
    var(--line-strong),
    var(--line) 70%,
    transparent
  );
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

.lane-end {
  margin: 26px 0 0 24px;
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--faint);
  text-align: center;
}

/* ── States ────────────────────────────────────────────────── */

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

.memory-feedback {
  padding: 40px 20px;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
}

.memory-feedback.is-error p {
  margin-bottom: 12px;
  color: var(--danger);
}

.suggestion-terms {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}

.term-chip {
  height: 30px;
  padding: 0 11px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  background: var(--paper);
  color: var(--ink-2);
  font-size: 12px;
  cursor: pointer;
}

.term-chip:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.no-contacts {
  max-width: 560px;
  margin: 0 auto;
  padding-top: 40px;
}

/* ── Responsive ────────────────────────────────────────────── */

@media (max-width: 1100px) {
  .memory-layout {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .contact-rail {
    position: static;
    padding-top: 0;
    overflow: hidden;
    max-height: none;
  }

  .rail-head,
  .rail-search,
  .rail-manage {
    display: none;
  }

  .rail-list {
    display: flex;
    gap: 6px;
    overflow-x: auto;
    margin: 0;
    padding: 0 0 2px;
    scrollbar-width: none;
  }

  .rail-list::-webkit-scrollbar {
    display: none;
  }

  .rail-item {
    width: auto;
    flex-shrink: 0;
    padding: 5px 9px 5px 5px;
    background: var(--paper);
    border: 1px solid var(--line);
  }

  .rail-item.is-active {
    border-color: #c3c9e3;
    background: var(--accent-tint);
    box-shadow: none;
  }

  .rail-item-copy {
    flex-direction: row;
    align-items: center;
    gap: 6px;
  }

  .rail-item-meta,
  .rail-current {
    display: none;
  }

  .rail-empty {
    padding: 8px 0;
  }

  .memory-headline {
    margin-top: 20px;
  }
}

@media (max-width: 560px) {
  .memory-headline h2 {
    font-size: 16.5px;
  }

  .search-block {
    gap: 8px;
  }

  .search-mode {
    display: none;
  }

  .memory-lane {
    padding-left: 18px;
  }

  .memory-lane::before {
    left: 4px;
  }

  .lane-node {
    left: -18px;
    top: 21px;
  }

  .lane-end {
    margin-left: 18px;
  }
}
</style>
