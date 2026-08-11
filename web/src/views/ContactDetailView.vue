<template>
  <div class="detail-page">
    <el-container class="page-container">
      <el-header class="page-header">
        <el-button text @click="$router.push('/contacts')">
          <el-icon><ArrowLeft /></el-icon> 返回联系人
        </el-button>
        <h1>{{ contact?.name || '加载中...' }}</h1>
        <el-button v-if="contact" type="danger" plain size="small" @click="handleDelete">
          <el-icon><Delete /></el-icon> 删除
        </el-button>
      </el-header>

      <el-main>
        <el-empty v-if="!contact" description="联系人不存在" />

        <template v-else>
          <el-descriptions :column="2" border class="info-card">
            <el-descriptions-item label="平台">
              <el-tag>{{ platformLabel(contact.platform) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="添加时间">
              {{ formatTime(contact.createdAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <el-divider />

          <h3>关联记忆</h3>
          <el-empty v-if="!memoryStore.memories.length" description="暂无关联记忆" />
          <div v-else class="memory-list">
            <el-card v-for="m in memoryStore.memories" :key="m.id" shadow="hover" class="memory-card">
              <div class="memory-header">
                <el-tag size="small" :type="categoryTagType(m.category)">
                  {{ categoryLabel(m.category) }}
                </el-tag>
                <span class="memory-confidence">置信度: {{ (m.confidence * 100).toFixed(0) }}%</span>
              </div>
              <p class="memory-content">{{ m.content }}</p>
            </el-card>
          </div>
        </template>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'
import { useMemoryStore } from '@/stores/memories'
import { useContactStore } from '@/stores/contacts'
import type { Contact } from '@/stores/contacts'

const route = useRoute()
const router = useRouter()
const contact = ref<Contact | null>(null)
const memoryStore = useMemoryStore()
const contactStore = useContactStore()

async function handleDelete() {
  if (!contact.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除「${contact.value.name}」及其全部对话和记忆？此操作不可撤销。`,
      '删除联系人',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await contactStore.remove(contact.value.id)
    ElMessage.success('已删除')
    router.push('/contacts')
  } catch {
    // cancelled
  }
}

function platformLabel(p: string) {
  const map: Record<string, string> = { wechat: '微信', qq: 'QQ', douyin: '抖音', sms: '短信', other: '其他' }
  return map[p] || p
}

function formatTime(t: string) {
  return t ? new Date(t).toLocaleString('zh-CN') : '-'
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

onMounted(async () => {
  try {
    const id = route.params.id
    const res = await api.get<Contact>(`/contacts/${id}`)
    contact.value = res.data
    await memoryStore.fetchByContact(Number(id))
  } catch {
    // 404 or error
  }
})
</script>

<style scoped>
.detail-page {
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

.info-card {
  border-radius: 10px;
}

.memory-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
</style>
