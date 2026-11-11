<template>
  <div class="page contacts-page" :class="{ 'is-selecting': selectedIds.size > 0 }">
    <PageHeader
      title="联系人"
      description="每位联系人都带着自己的对话与记忆；同名的人可以合并。"
    >
      <template #actions>
        <el-button type="primary" @click="showAddDialog = true">
          <el-icon><Plus /></el-icon>
          添加联系人
        </el-button>
      </template>
    </PageHeader>

    <!-- Duplicate detection -->
    <div v-if="duplicateGroups.length" class="dup-band" role="status">
      <el-icon class="dup-icon" :size="16"><WarningFilled /></el-icon>
      <p class="dup-text">发现 {{ duplicateGroups.length }} 组疑似重复的联系人</p>
      <div class="dup-actions">
        <button type="button" class="dup-link" @click="showDedupDialog = true">查看并合并</button>
        <span class="dup-sep" aria-hidden="true"></span>
        <button type="button" class="dup-link is-quiet" @click="dismissDuplicates">忽略</button>
      </div>
    </div>

    <!-- Search -->
    <div class="toolbar">
      <el-input
        v-model="searchQuery"
        class="search-input"
        placeholder="搜索联系人"
        :prefix-icon="Search"
        clearable
      />
      <div class="toolbar-meta">
        <span v-if="searching" class="searching">
          <el-icon class="is-loading" :size="12"><Loading /></el-icon>
          搜索中
        </span>
        <span v-else-if="isSearchMode" class="count tabular">{{ displayedContacts.length }} 个结果</span>
        <span v-else class="count tabular">共 {{ store.contacts.length }} 位</span>
      </div>
    </div>

    <!-- Error -->
    <div v-if="loadError" class="feedback" role="alert">
      <el-icon :size="18"><WarningFilled /></el-icon>
      <p>{{ loadError }}</p>
      <el-button size="small" @click="retryLoad">重试</el-button>
    </div>

    <!-- Loading skeleton -->
    <div v-else-if="store.loading && !store.contacts.length" class="contact-grid" aria-label="正在加载联系人">
      <div v-for="n in 6" :key="n" class="skeleton-card" aria-hidden="true">
        <el-skeleton animated>
          <template #template>
            <div class="skeleton-row">
              <el-skeleton-item
                variant="rect"
                style="width: 44px; height: 44px; border-radius: 12px; flex-shrink: 0; margin-left: 34px"
              />
              <div class="skeleton-copy">
                <el-skeleton-item variant="text" style="width: 44%; height: 12px" />
                <el-skeleton-item variant="text" style="width: 70%; height: 10px; margin-top: 10px" />
              </div>
            </div>
          </template>
        </el-skeleton>
      </div>
    </div>

    <!-- Empty: no contacts -->
    <EmptyState
      v-else-if="!store.contacts.length && !isSearchMode"
      title="还没有联系人"
      description="先导入一段聊天。忆路会认出对话里的人，把约定、偏好和细节收在他们名下。"
    >
      <el-button type="primary" @click="$router.push('/')">去导入第一段聊天</el-button>
    </EmptyState>

    <!-- Empty: search -->
    <EmptyState
      v-else-if="isSearchMode && !displayedContacts.length"
      compact
      title="没有找到匹配的联系人"
      description="换一个名字试试，或清除搜索查看全部联系人。"
    >
      <el-button @click="clearSearch">清除搜索</el-button>
    </EmptyState>

    <!-- Contact grid -->
    <div v-else class="contact-grid">
      <article
        v-for="c in displayedContacts"
        :key="c.id"
        class="contact-card"
        :class="{ 'is-selected': selectedIds.has(c.id) }"
      >
        <RouterLink :to="`/contacts/${c.id}`" class="card-link" :aria-label="`查看 ${c.name} 的联系人详情`">
          <MonogramAvatar :name="c.name" :size="44" />
          <span class="card-copy">
            <span class="card-name">{{ c.name }}</span>
            <span class="card-sub">
              <span class="platform-chip">{{ platformLabel(c.platform) }}</span>
              <span class="card-date tabular">添加于 {{ formatDate(c.createdAt) }}</span>
            </span>
          </span>
          <el-icon class="card-chevron" :size="15"><ArrowRight /></el-icon>
        </RouterLink>

        <el-checkbox
          class="card-check"
          :model-value="selectedIds.has(c.id)"
          :aria-label="`选择 ${c.name}`"
          @click.stop
          @change="toggleSelect(c.id)"
        />

        <el-dropdown
          trigger="click"
          placement="bottom-end"
          class="card-menu"
          :disabled="deletingId === c.id"
          @command="handleDelete(c)"
        >
          <button type="button" class="card-menu-btn" :aria-label="`${c.name} 的更多操作`" @click.stop>
            <el-icon :size="15"><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="delete">
                <el-icon><Delete /></el-icon>
                <span class="menu-danger">删除联系人</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </article>
    </div>

    <!-- Selection action bar -->
    <transition name="selection">
      <div v-if="selectedIds.size > 0" class="selection-bar" role="region" aria-label="联系人批量操作">
        <span class="selection-count">
          已选 <strong class="tabular">{{ selectedIds.size }}</strong> 位
        </span>
        <div class="selection-actions">
          <el-button
            v-if="selectedIds.size >= 2"
            type="primary"
            :disabled="bulkDeleting"
            @click="showMergeDialog = true"
          >
            合并为...
          </el-button>
          <el-button type="danger" plain :loading="bulkDeleting" @click="handleDeleteSelected">
            删除选中
          </el-button>
          <el-button text :disabled="bulkDeleting" @click="clearSelection">取消选择</el-button>
        </div>
      </div>
    </transition>

    <!-- Add contact dialog -->
    <el-dialog
      v-model="showAddDialog"
      title="添加联系人"
      width="min(440px, 92vw)"
      :close-on-click-modal="!addSubmitting"
      :show-close="!addSubmitting"
    >
      <el-form label-position="top" @submit.prevent="handleAdd">
        <el-form-item label="姓名">
          <el-input
            v-model="newContact.name"
            placeholder="联系人姓名"
            maxlength="40"
            @keyup.enter="handleAdd"
          />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="newContact.platform" placeholder="选择平台" class="field-full">
            <el-option label="微信" value="wechat" />
            <el-option label="QQ" value="qq" />
            <el-option label="抖音" value="douyin" />
            <el-option label="短信" value="sms" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="addSubmitting" @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" @click="handleAdd">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- Merge dialog -->
    <el-dialog v-model="showMergeDialog" title="选择主联系人" width="min(440px, 92vw)">
      <p class="dialog-hint">合并后，其他联系人的对话和记忆将归入主联系人。</p>
      <div class="pick-list">
        <button
          v-for="c in mergeCandidates"
          :key="c.id"
          type="button"
          class="pick-row"
          :class="{ 'is-active': mergeTarget === c.id }"
          @click="mergeTarget = c.id"
        >
          <MonogramAvatar :name="c.name" :size="34" />
          <span class="pick-copy">
            <span class="pick-name">{{ c.name }}</span>
            <span class="pick-meta">
              <el-tag size="small" type="info">{{ platformLabel(c.platform) }}</el-tag>
              <span class="pick-count tabular">添加于 {{ formatDate(c.createdAt) }}</span>
            </span>
          </span>
          <el-icon v-if="mergeTarget === c.id" class="pick-check" :size="15"><Check /></el-icon>
        </button>
      </div>
      <template #footer>
        <el-button :disabled="mergeSubmitting" @click="showMergeDialog = false">取消</el-button>
        <el-button type="primary" :loading="mergeSubmitting" :disabled="!mergeTarget" @click="handleMerge">
          确认合并
        </el-button>
      </template>
    </el-dialog>

    <!-- Duplicate merge dialog -->
    <el-dialog v-model="showDedupDialog" title="疑似重复联系人" width="min(540px, 94vw)">
      <p class="dialog-hint">以下联系人可能是同一个人，选择每组的保留对象后合并。</p>
      <div class="dedup-scroll">
        <section v-for="(group, gIdx) in duplicateGroups" :key="gIdx" class="dedup-group">
          <header class="dedup-head">
            <span class="dedup-reason">{{ group.reason }}</span>
            <span class="dedup-conf tabular">{{ Math.round(group.confidence * 100) }}% 置信</span>
          </header>
          <div class="pick-list">
            <button
              v-for="c in group.candidates"
              :key="c.id"
              type="button"
              class="pick-row"
              :class="{ 'is-active': dedupTarget[gIdx] === c.id }"
              @click="dedupTarget[gIdx] = c.id"
            >
              <MonogramAvatar :name="c.name" :size="34" />
              <span class="pick-copy">
                <span class="pick-name">{{ c.name }}</span>
                <span class="pick-meta">
                  <el-tag size="small" type="info">{{ platformLabel(c.platform) }}</el-tag>
                  <span class="pick-count tabular">{{ c.messageCount }} 条消息</span>
                </span>
              </span>
              <el-icon v-if="dedupTarget[gIdx] === c.id" class="pick-check" :size="15"><Check /></el-icon>
            </button>
          </div>
          <div class="dedup-actions">
            <el-button
              size="small"
              type="primary"
              :loading="dedupMerging === gIdx"
              :disabled="!dedupTarget[gIdx] || dedupMerging !== null"
              @click="handleDedupMerge(gIdx)"
            >
              合并此组
            </el-button>
          </div>
        </section>
      </div>
      <template #footer>
        <el-button @click="showDedupDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  ArrowRight,
  Check,
  Delete,
  Loading,
  MoreFilled,
  Plus,
  Search,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useContactStore, type DuplicateGroup } from '@/stores/contacts'
import type { Contact } from '@/stores/contacts'
import { formatDate, platformLabel } from '@/utils/presentation'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import MonogramAvatar from '@/components/MonogramAvatar.vue'

const store = useContactStore()

const searchQuery = ref('')
const showAddDialog = ref(false)
const showMergeDialog = ref(false)
const showDedupDialog = ref(false)
const newContact = ref({ name: '', platform: '' })
const selectedIds = ref(new Set<number>())
const mergeTarget = ref<number | null>(null)
const duplicateGroups = ref<DuplicateGroup[]>([])
const dedupTarget = reactive<Record<number, number>>({})

const searchResults = ref<Contact[] | null>(null)
const searching = ref(false)
const loadError = ref('')
const addSubmitting = ref(false)
const mergeSubmitting = ref(false)
const bulkDeleting = ref(false)
const deletingId = ref<number | null>(null)
const dedupMerging = ref<number | null>(null)

let searchTimer: ReturnType<typeof setTimeout> | null = null

const isSearchMode = computed(() => searchQuery.value.trim().length > 0)
const displayedContacts = computed(() => searchResults.value ?? store.contacts)
const mergeCandidates = computed(() => store.contacts.filter((c) => selectedIds.value.has(c.id)))

watch(searchQuery, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  const q = val.trim()
  if (!q) {
    searchResults.value = null
    searching.value = false
    loadError.value = ''
    return
  }
  searchTimer = setTimeout(() => {
    runSearch(q)
  }, 300)
})

onMounted(async () => {
  await loadContacts()
  await loadDuplicates()
})

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

async function loadContacts() {
  loadError.value = ''
  try {
    await store.fetchAll()
  } catch (e: any) {
    loadError.value = e?.response?.data?.message || e?.message || '联系人加载失败，请重试'
  }
}

async function runSearch(query: string) {
  searching.value = true
  try {
    searchResults.value = await store.search(query)
    loadError.value = ''
  } catch (e: any) {
    searchResults.value = []
    loadError.value = e?.response?.data?.message || e?.message || '搜索失败，请重试'
  } finally {
    searching.value = false
  }
}

async function retryLoad() {
  if (searchTimer) clearTimeout(searchTimer)
  if (isSearchMode.value) {
    await runSearch(searchQuery.value.trim())
    return
  }
  await loadContacts()
  if (!duplicateGroups.value.length) await loadDuplicates()
}

function toggleSelect(id: number) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectedIds.value = next
}

function clearSelection() {
  selectedIds.value = new Set()
}

function clearSearch() {
  searchQuery.value = ''
  searchResults.value = null
  loadError.value = ''
}

function syncSearchResults() {
  if (!searchResults.value) return
  const live = new Set(store.contacts.map((c) => c.id))
  searchResults.value = searchResults.value.filter((c) => live.has(c.id))
}

async function handleDelete(c: Contact) {
  if (deletingId.value !== null) return
  try {
    await ElMessageBox.confirm(
      `确定删除「${c.name}」及其全部对话和记忆？此操作不可撤销。`,
      '删除联系人',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  deletingId.value = c.id
  try {
    await store.remove(c.id)
    syncSearchResults()
    const next = new Set(selectedIds.value)
    next.delete(c.id)
    selectedIds.value = next
    ElMessage.success('已删除')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败，请重试')
  } finally {
    deletingId.value = null
  }
}

async function handleDeleteSelected() {
  if (bulkDeleting.value || selectedIds.value.size === 0) return
  const names = store.contacts
    .filter((c) => selectedIds.value.has(c.id))
    .map((c) => c.name)
    .join('、')
  try {
    await ElMessageBox.confirm(
      `确定删除「${names}」及其全部数据？此操作不可撤销。`,
      '批量删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  bulkDeleting.value = true
  try {
    for (const id of selectedIds.value) {
      await store.remove(id)
    }
    selectedIds.value = new Set()
    syncSearchResults()
    ElMessage.success('已删除')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败，请重试')
  } finally {
    bulkDeleting.value = false
  }
}

async function handleMerge() {
  if (!mergeTarget.value || mergeSubmitting.value) return
  const sourceIds = [...selectedIds.value].filter((id) => id !== mergeTarget.value)
  if (sourceIds.length === 0) return

  const targetName = store.contacts.find((c) => c.id === mergeTarget.value)?.name ?? ''
  try {
    await ElMessageBox.confirm(
      `将 ${sourceIds.length} 个联系人的数据合并到「${targetName}」？`,
      '确认合并',
      { confirmButtonText: '合并', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }

  mergeSubmitting.value = true
  try {
    await store.merge(mergeTarget.value, sourceIds)
    syncSearchResults()
    selectedIds.value = new Set()
    mergeTarget.value = null
    showMergeDialog.value = false
    ElMessage.success('合并完成')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '合并失败，请重试')
  } finally {
    mergeSubmitting.value = false
  }
}

async function handleAdd() {
  if (addSubmitting.value) return
  if (!newContact.value.name.trim()) {
    ElMessage.warning('请输入姓名')
    return
  }
  addSubmitting.value = true
  try {
    await store.create(newContact.value.name.trim(), newContact.value.platform)
    showAddDialog.value = false
    newContact.value = { name: '', platform: '' }
    ElMessage.success('添加成功')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '添加失败，请重试')
  } finally {
    addSubmitting.value = false
  }
}

async function loadDuplicates() {
  try {
    const groups = await store.fetchDuplicates()
    duplicateGroups.value = groups
    for (const key of Object.keys(dedupTarget)) {
      delete dedupTarget[Number(key)]
    }
    groups.forEach((group, idx) => {
      const first = group.candidates[0]
      if (first) dedupTarget[idx] = first.id
    })
  } catch {
    // duplicates API unavailable — ignore
  }
}

function dismissDuplicates() {
  duplicateGroups.value = []
}

async function handleDedupMerge(gIdx: number) {
  if (dedupMerging.value !== null) return
  const group = duplicateGroups.value[gIdx]
  const targetId = dedupTarget[gIdx]
  if (!targetId || !group) return
  const sourceIds = group.candidates.filter((c) => c.id !== targetId).map((c) => c.id)
  if (sourceIds.length === 0) return

  dedupMerging.value = gIdx
  try {
    await store.merge(targetId, sourceIds)
    ElMessage.success('合并完成')
    await store.fetchAll()
    syncSearchResults()
    const next = new Set(selectedIds.value)
    for (const id of [...next]) {
      if (!store.contacts.some((c) => c.id === id)) next.delete(id)
    }
    selectedIds.value = next
    await loadDuplicates()
    showDedupDialog.value = duplicateGroups.value.length > 0
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '合并失败，请重试')
  } finally {
    dedupMerging.value = null
  }
}
</script>

<style scoped>
/* ── Duplicate notice ──────────────────────────────────────── */

.dup-band {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  padding: 9px 12px;
  background: var(--warning-tint);
  border: 1px solid rgba(166, 106, 20, 0.16);
  border-radius: var(--r-md);
  color: #7d500f;
}

.dup-icon {
  flex-shrink: 0;
}

.dup-text {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-weight: 550;
  line-height: 1.5;
}

.dup-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.dup-link {
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: #7d500f;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
  text-decoration-thickness: 1px;
}

.dup-link:hover {
  color: var(--ink);
}

.dup-link.is-quiet {
  font-weight: 500;
  color: var(--muted);
  text-decoration: none;
}

.dup-link.is-quiet:hover {
  color: var(--ink-2);
}

.dup-sep {
  width: 1px;
  height: 11px;
  background: rgba(166, 106, 20, 0.28);
}

/* ── Toolbar ───────────────────────────────────────────────── */

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.search-input {
  max-width: 360px;
}

.search-input :deep(.el-input__wrapper) {
  min-height: 40px;
  padding: 0 12px;
  background: var(--paper);
  border-radius: var(--r-md);
}

.search-input :deep(.el-input__inner) {
  font-size: 13px;
}

.toolbar-meta {
  flex-shrink: 0;
  font-size: 11.5px;
  color: var(--faint);
  white-space: nowrap;
}

.searching {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.count {
  color: var(--faint);
}

/* ── Grid + cards ──────────────────────────────────────────── */

.contact-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.contact-card {
  position: relative;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  transition: border-color 0.16s ease, background-color 0.16s ease, transform 0.16s ease;
}

.contact-card:hover,
.contact-card:focus-within {
  border-color: var(--line-strong);
  transform: translateY(-1px);
}

.contact-card.is-selected {
  border-color: var(--accent);
  background: var(--accent-tint);
}

.card-link {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 84px;
  padding: 14px 40px 14px 46px;
  border-radius: var(--r-md);
}

.card-link:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.card-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.card-name {
  font-size: 14px;
  font-weight: 620;
  line-height: 1.35;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-sub {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 5px;
}

.platform-chip {
  display: inline-flex;
  align-items: center;
  height: 19px;
  padding: 0 6px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-xs);
  font-size: 10.5px;
  font-weight: 550;
  color: var(--muted);
  white-space: nowrap;
}

.card-date {
  font-size: 10.5px;
  color: var(--faint);
  white-space: nowrap;
}

.card-chevron {
  flex-shrink: 0;
  color: var(--faint);
  transition: color 0.15s ease, transform 0.15s ease;
}

.contact-card:hover .card-chevron {
  color: var(--accent);
  transform: translateX(2px);
}

/* Selection checkbox */

.card-check {
  position: absolute;
  top: 7px;
  left: 7px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin: 0;
  opacity: 0.42;
  transition: opacity 0.15s ease;
}

.contact-card:hover .card-check,
.contact-card:focus-within .card-check,
.contact-card.is-selected .card-check {
  opacity: 1;
}

.card-check :deep(.el-checkbox__label) {
  display: none;
}

/* Card menu */

.card-menu {
  position: absolute;
  top: 5px;
  right: 5px;
  z-index: 2;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.contact-card:hover .card-menu,
.contact-card:focus-within .card-menu,
.contact-card.is-selected .card-menu {
  opacity: 1;
}

.card-menu-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--faint);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.card-menu-btn:hover {
  background: var(--wash);
  color: var(--ink);
}

.menu-danger {
  color: var(--danger);
}

/* ── States ────────────────────────────────────────────────── */

.feedback {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 54px 24px;
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

.skeleton-card {
  min-height: 84px;
  padding: 20px 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}

.skeleton-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.skeleton-copy {
  flex: 1;
  min-width: 0;
}

/* ── Selection bar ─────────────────────────────────────────── */

.selection-bar {
  position: fixed;
  z-index: 60;
  bottom: 24px;
  left: calc(50% + var(--rail-w) / 2);
  transform: translateX(-50%);
  width: min(680px, calc(100% - 48px));
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 10px 9px 18px;
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-md);
}

.selection-count {
  font-size: 12.5px;
  color: var(--muted);
  white-space: nowrap;
}

.selection-count strong {
  font-weight: 650;
  color: var(--ink);
}

.selection-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selection-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.selection-enter-active,
.selection-leave-active {
  transition: opacity 0.18s ease, transform 0.18s cubic-bezier(0.2, 0.7, 0.3, 1);
}

.selection-enter-from,
.selection-leave-to {
  opacity: 0;
  transform: translate(-50%, 12px);
}

/* ── Dialogs ───────────────────────────────────────────────── */

.field-full {
  width: 100%;
}

.dialog-hint {
  margin: 0 0 14px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--muted);
}

.pick-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pick-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.pick-row:hover {
  border-color: var(--line-strong);
}

.pick-row.is-active {
  border-color: var(--accent);
  background: var(--accent-tint);
}

.pick-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.pick-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pick-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 4px;
  min-width: 0;
}

.pick-count {
  font-size: 10.5px;
  color: var(--faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pick-check {
  flex-shrink: 0;
  color: var(--accent);
}

.dedup-scroll {
  max-height: min(56vh, 440px);
  overflow-y: auto;
  margin-right: -4px;
  padding-right: 4px;
}

.dedup-group + .dedup-group {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.dedup-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.dedup-reason {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--ink-2);
}

.dedup-conf {
  flex-shrink: 0;
  padding: 2px 7px;
  border-radius: var(--r-xs);
  background: var(--warning-tint);
  color: #7d500f;
  font-size: 10.5px;
  font-weight: 600;
}

.dedup-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

/* ── Responsive ────────────────────────────────────────────── */

@media (min-width: 900px) {
  .contact-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) {
  .contact-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .selection-bar {
    left: 50%;
    bottom: calc(var(--mobile-tabs-h) + 16px + env(safe-area-inset-bottom));
    width: calc(100% - 32px);
    padding: 8px 10px 8px 14px;
    gap: 10px;
  }
}

@media (max-width: 639px) {
  .toolbar {
    display: block;
    margin-bottom: 14px;
  }

  .search-input {
    max-width: none;
  }

  .toolbar-meta {
    margin-top: 8px;
    text-align: right;
  }

  .card-check {
    top: 4px;
    left: 4px;
    width: 40px;
    height: 40px;
    opacity: 1;
  }

  .card-menu {
    top: 50%;
    right: 4px;
    transform: translateY(-50%);
    opacity: 1;
  }

  .card-menu-btn {
    width: 40px;
    height: 40px;
  }

  .card-chevron {
    display: none;
  }

  .card-link {
    min-height: 88px;
    padding-right: 48px;
  }

  .dup-band {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .dup-text {
    flex-basis: calc(100% - 26px);
  }

  .dup-actions {
    margin-left: 26px;
  }

  .selection-bar {
    flex-wrap: wrap;
    justify-content: center;
    gap: 6px;
    padding: 8px 10px;
  }

  .selection-count {
    flex-basis: 100%;
    text-align: center;
    font-size: 11.5px;
  }

  .selection-actions {
    width: 100%;
    gap: 6px;
  }

  .selection-actions :deep(.el-button) {
    flex: 1;
    height: 40px;
    padding: 0 8px;
    font-size: 12.5px;
    margin-left: 0;
  }

  .contacts-page.is-selecting {
    padding-bottom: calc(var(--mobile-tabs-h) + 150px + env(safe-area-inset-bottom));
  }

  .pick-row {
    padding: 8px 9px;
  }

  .dedup-scroll {
    max-height: 52vh;
  }
}

@media (hover: none) {
  .card-check {
    top: 4px;
    left: 4px;
    width: 40px;
    height: 40px;
    opacity: 1;
  }

  .card-menu {
    top: 50%;
    right: 4px;
    transform: translateY(-50%);
    opacity: 1;
  }

  .card-menu-btn {
    width: 40px;
    height: 40px;
  }

  .card-chevron {
    display: none;
  }

  .card-link {
    padding-right: 48px;
  }
}
</style>
