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
        >
          <el-option
            v-for="c in contactStore.contacts"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>

        <el-input
          v-model="searchQuery"
          placeholder="搜索记忆..."
          :prefix-icon="Search"
          clearable
          class="search-input"
        />

        <el-empty v-if="!memoryStore.memories.length" description="暂无可展示的记忆" />

        <el-timeline v-else class="memory-timeline">
          <el-timeline-item
            v-for="m in filteredMemories"
            :key="m.id"
            :timestamp="''"
            :color="categoryColor(m.category)"
            placement="top"
          >
            <el-card shadow="hover" class="memory-card">
              <div class="memory-header">
                <el-tag size="small" :type="categoryTagType(m.category)">
                  {{ categoryLabel(m.category) }}
                </el-tag>
                <span class="memory-confidence">
                  置信度: {{ (m.confidence * 100).toFixed(0) }}%
                </span>
              </div>
              <p class="memory-content">{{ m.content }}</p>
              <div class="memory-source" v-if="m.sourceMsgIds?.length">
                <el-icon><ChatDotRound /></el-icon>
                来自 {{ m.sourceMsgIds.length }} 条消息
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ArrowLeft, Search, ChatDotRound } from '@element-plus/icons-vue'
import { useMemoryStore } from '@/stores/memories'
import { useContactStore } from '@/stores/contacts'

const memoryStore = useMemoryStore()
const contactStore = useContactStore()
const searchQuery = ref('')
const categoryFilter = ref('')
const selectedContactId = ref<number | null>(null)

onMounted(() => {
  contactStore.fetchAll()
})

watch(selectedContactId, (id) => {
  if (id) {
    memoryStore.fetchByContact(id)
  }
})

const filteredMemories = computed(() => {
  let list = memoryStore.memories
  if (categoryFilter.value) {
    list = list.filter((m) => m.category === categoryFilter.value)
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter((m) => m.content?.toLowerCase().includes(q))
  }
  return list
})

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

.search-input {
  margin-bottom: 24px;
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
</style>
