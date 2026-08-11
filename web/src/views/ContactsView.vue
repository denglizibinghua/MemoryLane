<template>
  <div class="contacts-page">
    <el-container class="page-container">
      <el-header class="page-header">
        <el-button text @click="$router.push('/')">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h1>联系人</h1>
        <el-button type="primary" @click="showAddDialog = true">
          <el-icon><Plus /></el-icon> 添加
        </el-button>
      </el-header>

      <el-main>
        <el-input
          v-model="searchQuery"
          placeholder="搜索联系人..."
          :prefix-icon="Search"
          clearable
          class="search-input"
        />

        <el-empty v-if="!store.contacts.length && !store.loading" description="还没有添加过联系人" />

        <el-row v-loading="store.loading" :gutter="16" class="contact-grid">
          <el-col :xs="24" :sm="12" :md="8" v-for="c in store.contacts" :key="c.id">
            <el-card shadow="hover" class="contact-card" :class="{ selected: selectedIds.has(c.id) }">
              <div class="card-body" @click="$router.push(`/contacts/${c.id}`)">
                <el-checkbox
                  :model-value="selectedIds.has(c.id)"
                  @click.stop
                  @change="toggleSelect(c.id)"
                  class="card-checkbox"
                />
                <div class="contact-avatar">
                  <el-avatar :size="40" :icon="UserFilled" />
                </div>
                <div class="contact-info">
                  <div class="contact-name">{{ c.name }}</div>
                  <div class="contact-platform">
                    <el-tag size="small" :type="platformType(c.platform)">
                      {{ platformLabel(c.platform) }}
                    </el-tag>
                  </div>
                </div>
              </div>
              <el-button
                class="card-delete"
                :icon="Close"
                circle
                size="small"
                text
                @click.stop="handleDelete(c)"
              />
            </el-card>
          </el-col>
        </el-row>
      </el-main>
    </el-container>

    <!-- Action Bar -->
    <transition name="slide-up">
      <div v-if="selectedIds.size > 0" class="action-bar">
        <span class="action-count">已选 {{ selectedIds.size }} 项</span>
        <div class="action-buttons">
          <el-button
            v-if="selectedIds.size >= 2"
            type="primary"
            @click="showMergeDialog = true"
          >
            合并为...
          </el-button>
          <el-button type="danger" plain @click="handleDeleteSelected">
            删除选中
          </el-button>
          <el-button text @click="selectedIds.clear()">取消选择</el-button>
        </div>
      </div>
    </transition>

    <!-- Add Contact Dialog -->
    <el-dialog v-model="showAddDialog" title="添加联系人" width="440px">
      <el-form label-position="top">
        <el-form-item label="姓名">
          <el-input v-model="newContact.name" placeholder="联系人姓名" />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="newContact.platform" placeholder="选择平台" style="width: 100%">
            <el-option label="微信" value="wechat" />
            <el-option label="QQ" value="qq" />
            <el-option label="抖音" value="douyin" />
            <el-option label="短信" value="sms" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- Merge Dialog -->
    <el-dialog v-model="showMergeDialog" title="选择主联系人" width="440px">
      <p class="merge-hint">合并后，其他联系人的对话和记忆将归入主联系人。</p>
      <div class="merge-list">
        <div
          v-for="c in mergeCandidates"
          :key="c.id"
          class="merge-item"
          :class="{ active: mergeTarget === c.id }"
          @click="mergeTarget = c.id"
        >
          <el-avatar :size="32" :icon="UserFilled" />
          <span class="merge-name">{{ c.name }}</span>
          <el-tag size="small" :type="platformType(c.platform)">
            {{ platformLabel(c.platform) }}
          </el-tag>
          <el-icon v-if="mergeTarget === c.id" class="merge-check" color="#409eff"><Check /></el-icon>
        </div>
      </div>
      <template #footer>
        <el-button @click="showMergeDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!mergeTarget" @click="handleMerge">
          确认合并
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ArrowLeft, Plus, Search, UserFilled, Close, Check } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useContactStore } from '@/stores/contacts'
import type { Contact } from '@/stores/contacts'

const store = useContactStore()

const searchQuery = ref('')
const showAddDialog = ref(false)
const showMergeDialog = ref(false)
const newContact = ref({ name: '', platform: '' })
const selectedIds = ref(new Set<number>())
const mergeTarget = ref<number | null>(null)

const mergeCandidates = computed(() =>
  store.contacts.filter(c => selectedIds.value.has(c.id))
)

let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(searchQuery, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    if (val.trim()) {
      store.contacts = await store.search(val)
    } else {
      await store.fetchAll()
    }
  }, 300)
})

function toggleSelect(id: number) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectedIds.value = next
}

async function handleDelete(c: Contact) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${c.name}」及其全部对话和记忆？此操作不可撤销。`,
      '删除联系人',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await store.remove(c.id)
    selectedIds.value.delete(c.id)
    ElMessage.success('已删除')
  } catch {
    // cancelled
  }
}

async function handleDeleteSelected() {
  const names = store.contacts
    .filter(c => selectedIds.value.has(c.id))
    .map(c => c.name)
    .join('、')
  try {
    await ElMessageBox.confirm(
      `确定删除「${names}」及其全部数据？此操作不可撤销。`,
      '批量删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    for (const id of selectedIds.value) {
      await store.remove(id)
    }
    selectedIds.value.clear()
    ElMessage.success('已删除')
  } catch {
    // cancelled
  }
}

async function handleMerge() {
  if (!mergeTarget.value) return
  const sourceIds = [...selectedIds.value].filter(id => id !== mergeTarget.value)
  if (sourceIds.length === 0) return

  const targetName = store.contacts.find(c => c.id === mergeTarget.value)?.name ?? ''
  try {
    await ElMessageBox.confirm(
      `将 ${sourceIds.length} 个联系人的数据合并到「${targetName}」？`,
      '确认合并',
      { confirmButtonText: '合并', cancelButtonText: '取消', type: 'info' }
    )
    await store.merge(mergeTarget.value, sourceIds)
    selectedIds.value.clear()
    mergeTarget.value = null
    showMergeDialog.value = false
    ElMessage.success('合并完成')
  } catch {
    // cancelled
  }
}

function platformType(p: string) {
  const map: Record<string, any> = { wechat: 'success', qq: '', douyin: 'danger', sms: 'info' }
  return map[p] || 'info'
}

function platformLabel(p: string) {
  const map: Record<string, string> = { wechat: '微信', qq: 'QQ', douyin: '抖音', sms: '短信', other: '其他' }
  return map[p] || p
}

async function handleAdd() {
  if (!newContact.value.name) {
    ElMessage.warning('请输入姓名')
    return
  }
  try {
    await store.create(newContact.value.name, newContact.value.platform)
    showAddDialog.value = false
    newContact.value = { name: '', platform: '' }
    ElMessage.success('添加成功')
  } catch {
    ElMessage.error('添加失败，请重试')
  }
}

onMounted(() => store.fetchAll())
</script>

<style scoped>
.contacts-page {
  min-height: 100vh;
  background: #f9fafb;
  padding-bottom: 80px;
}

.page-container {
  max-width: 880px;
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

.search-input {
  margin-bottom: 24px;
}

.contact-grid {
  margin: 0 !important;
}

.contact-card {
  position: relative;
  border-radius: 10px;
  margin-bottom: 16px;
  transition: transform 0.15s, border-color 0.15s;
  border: 2px solid transparent;
}

.contact-card:hover {
  transform: translateY(-2px);
}

.contact-card.selected {
  border-color: #409eff;
}

.contact-card :deep(.el-card__body) {
  padding: 12px;
}

.card-body {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.card-checkbox {
  flex-shrink: 0;
}

.card-delete {
  position: absolute;
  top: 6px;
  right: 6px;
  color: #9ca3af;
}

.card-delete:hover {
  color: #ef4444;
  background-color: #fef2f2;
}

.contact-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

/* Action Bar */
.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  border-top: 1px solid #e5e7eb;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  z-index: 100;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.06);
}

.action-count {
  font-size: 14px;
  color: #6b7280;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.2s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
}

/* Merge Dialog */
.merge-hint {
  color: #6b7280;
  font-size: 13px;
  margin: 0 0 16px 0;
}

.merge-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.merge-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
  border: 2px solid #e5e7eb;
  transition: border-color 0.15s;
}

.merge-item:hover {
  border-color: #93c5fd;
}

.merge-item.active {
  border-color: #409eff;
  background: #eff6ff;
}

.merge-name {
  font-size: 15px;
  font-weight: 500;
  flex: 1;
}

.merge-check {
  font-size: 18px;
}
</style>
