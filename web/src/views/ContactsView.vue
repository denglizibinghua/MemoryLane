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
            <el-card shadow="hover" class="contact-card" @click="$router.push(`/contacts/${c.id}`)">
              <div class="contact-avatar">
                <el-avatar :size="48" :icon="UserFilled" />
              </div>
              <div class="contact-info">
                <div class="contact-name">{{ c.name }}</div>
                <div class="contact-platform">
                  <el-tag size="small" :type="platformType(c.platform)">
                    {{ platformLabel(c.platform) }}
                  </el-tag>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-main>
    </el-container>

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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ArrowLeft, Plus, Search, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useContactStore } from '@/stores/contacts'
import { useMemoryStore } from '@/stores/memories'

const store = useContactStore()
const memoryStore = useMemoryStore()

const searchQuery = ref('')
const showAddDialog = ref(false)
const newContact = ref({ name: '', platform: '' })

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
  cursor: pointer;
  border-radius: 10px;
  margin-bottom: 16px;
  transition: transform 0.15s;
}

.contact-card:hover {
  transform: translateY(-2px);
}

.contact-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
}

.contact-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 6px;
}
</style>
