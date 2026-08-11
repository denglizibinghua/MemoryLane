<template>
  <div class="profile-page">
    <el-container class="page-container">
      <el-header class="page-header">
        <el-button text @click="$router.push('/')">
          <el-icon><ArrowLeft /></el-icon> 返回首页
        </el-button>
        <h1>我的形象</h1>
        <span style="width: 80px" />
      </el-header>

      <el-main class="profile-main">
        <div class="profile-grid">
          <!-- Left: manual edit -->
          <section class="edit-panel">
            <el-card shadow="never" class="panel-card">
              <h2 class="panel-title">👤 手动编辑</h2>
              <p class="panel-desc">告诉军师你是谁、怎么说话，他会据此调整回复风格。</p>

              <el-form label-position="top">
                <el-form-item label="显示名称">
                  <el-input v-model="form.displayName" placeholder="给自己起个名字" />
                </el-form-item>
                <el-form-item label="个人描述">
                  <el-input
                    v-model="form.persona"
                    type="textarea"
                    :rows="3"
                    placeholder="自由描述你是谁——年龄、身份、性格、所在地……"
                  />
                </el-form-item>
                <el-form-item label="说话风格">
                  <el-select v-model="form.speakingStyle" allow-create filterable placeholder="选择或输入">
                    <el-option label="幽默 / 爱用梗" value="幽默/爱用梗" />
                    <el-option label="直接坦率" value="直接坦率" />
                    <el-option label="温柔体贴" value="温柔体贴" />
                    <el-option label="土味情话" value="土味情话" />
                    <el-option label="优雅文艺" value="优雅文艺" />
                    <el-option label="理性分析" value="理性分析" />
                    <el-option label="撒娇可爱" value="撒娇可爱" />
                  </el-select>
                </el-form-item>
                <el-form-item label="与大多数人的关系">
                  <el-select v-model="form.relationshipDefault" allow-create filterable placeholder="选择或输入">
                    <el-option label="同学" value="同学" />
                    <el-option label="朋友" value="朋友" />
                    <el-option label="暧昧对象" value="暧昧对象" />
                    <el-option label="恋人" value="恋人" />
                    <el-option label="同事" value="同事" />
                    <el-option label="网友" value="网友" />
                  </el-select>
                </el-form-item>
                <el-button type="primary" :loading="saving" @click="handleSave" style="width: 100%">
                  保存
                </el-button>
              </el-form>
            </el-card>
          </section>

          <!-- Right: AI analyze -->
          <section class="analyze-panel">
            <el-card shadow="never" class="panel-card">
              <h2 class="panel-title">🤖 AI 分析</h2>
              <p class="panel-desc">
                让 AI 读取你的聊天记录，分析你的性格和说话风格，生成建议后你可以选择采纳。
                需要先导入聊天记录才有数据。
              </p>

              <el-button
                type="primary"
                plain
                :loading="analyzing"
                @click="handleAnalyze"
                style="width: 100%; margin-bottom: 16px"
              >
                开始分析
              </el-button>

              <!-- Hint -->
              <el-alert v-if="analyzeHint" :title="analyzeHint" type="warning" show-icon :closable="false" />

              <!-- AI result -->
              <div v-if="analysis" class="analysis-result">
                <el-divider />
                <h3 class="analysis-title">AI 建议</h3>

                <div class="analysis-item">
                  <span class="analysis-label">个人描述</span>
                  <span class="analysis-value">{{ analysis.persona || '（未识别）' }}</span>
                </div>
                <div class="analysis-item">
                  <span class="analysis-label">说话风格</span>
                  <el-tag>{{ analysis.speakingStyle || '未识别' }}</el-tag>
                </div>
                <div class="analysis-item">
                  <span class="analysis-label">主要关系</span>
                  <span class="analysis-value">{{ analysis.relationship || '未识别' }}</span>
                </div>

                <el-button
                  type="success"
                  :loading="saving"
                  @click="handleAdopt"
                  style="width: 100%; margin-top: 12px"
                  :disabled="!analysis.persona && !analysis.speakingStyle"
                >
                  采纳建议
                </el-button>
              </div>

              <!-- Error -->
              <el-alert
                v-if="analyzeError"
                :title="analyzeError"
                type="error"
                show-icon
                :closable="false"
                style="margin-top: 12px"
              />
            </el-card>
          </section>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '@/api'

interface UserProfile {
  id?: number
  displayName?: string
  persona?: string
  speakingStyle?: string
  relationshipDefault?: string
}

interface AnalysisResult {
  persona?: string
  speakingStyle?: string
  relationship?: string
  hint?: string
  error?: string
}

const form = ref<UserProfile>({
  displayName: '',
  persona: '',
  speakingStyle: '',
  relationshipDefault: '',
})
const saving = ref(false)
const analyzing = ref(false)
const analysis = ref<AnalysisResult | null>(null)
const analyzeHint = ref('')
const analyzeError = ref('')

onMounted(async () => {
  try {
    const res = await api.get('/profile')
    if (res.data) {
      form.value = {
        displayName: res.data.displayName || '',
        persona: res.data.persona || '',
        speakingStyle: res.data.speakingStyle || '',
        relationshipDefault: res.data.relationshipDefault || '',
      }
    }
  } catch {
    // no profile yet — fine
  }
})

async function handleSave() {
  saving.value = true
  try {
    await api.put('/profile', form.value)
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleAnalyze() {
  analyzing.value = true
  analysis.value = null
  analyzeHint.value = ''
  analyzeError.value = ''
  try {
    const res = await api.post('/profile/analyze')
    const data = res.data as AnalysisResult
    if (data.hint) {
      analyzeHint.value = data.hint
    } else if (data.error) {
      analyzeError.value = data.error
    } else {
      analysis.value = data
    }
  } catch (e: any) {
    analyzeError.value = e?.response?.data?.message || '分析失败'
  } finally {
    analyzing.value = false
  }
}

async function handleAdopt() {
  if (!analysis.value) return
  saving.value = true
  try {
    form.value.persona = analysis.value.persona || form.value.persona
    form.value.speakingStyle = analysis.value.speakingStyle || form.value.speakingStyle
    form.value.relationshipDefault = analysis.value.relationship || form.value.relationshipDefault
    await api.put('/profile', form.value)
    analysis.value = null
    ElMessage.success('已采纳并保存')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 50%, #fae8ff 100%);
}

.page-container {
  max-width: 960px;
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
  color: #1e1b4b;
  margin: 0;
}

.profile-main {
  padding: 8px 0 60px;
}

.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  align-items: start;
}

.panel-card {
  border-radius: 12px;
}

.panel-card :deep(.el-card__body) {
  padding: 24px;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: #1f2937;
}

.panel-desc {
  font-size: 13px;
  color: #9ca3af;
  margin: 0 0 16px 0;
  line-height: 1.6;
}

.analysis-result {
  margin-top: 4px;
}

.analysis-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin: 0 0 12px 0;
}

.analysis-item {
  margin-bottom: 10px;
}

.analysis-label {
  font-size: 12px;
  color: #9ca3af;
  display: block;
  margin-bottom: 2px;
}

.analysis-value {
  font-size: 14px;
  color: #1f2937;
}

@media (max-width: 700px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
