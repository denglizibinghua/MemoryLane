<template>
  <div class="page">
    <PageHeader
      title="我的形象"
      description="告诉军师你是谁、怎么说话。它会靠近你的语气，而不是替你说话。"
    />

    <div v-if="loading" class="profile-loading" aria-label="正在加载你的形象">
      <div class="surface loading-panel">
        <el-skeleton :rows="7" animated />
      </div>
      <div class="loading-aside">
        <div class="surface loading-panel">
          <el-skeleton :rows="4" animated />
        </div>
        <div class="surface loading-panel">
          <el-skeleton :rows="3" animated />
        </div>
      </div>
    </div>

    <template v-else>
      <div v-if="loadError" class="load-error" role="alert">
        <el-icon :size="14"><WarningFilled /></el-icon>
        <span>{{ loadError }}</span>
        <el-button text size="small" @click="loadProfile">重试</el-button>
      </div>

      <div class="profile-layout">
        <!-- Form -->
        <section class="surface form-surface">
          <header class="surface-head">
            <h2 class="surface-title">你的基本盘</h2>
            <p class="surface-sub">军师会用这些信息调整称呼、语气和关系判断。</p>
          </header>

          <el-form label-position="top" class="profile-form" @submit.prevent>
            <el-form-item label="显示名称">
              <el-input v-model="form.displayName" placeholder="比如：阿哲、小林" />
              <p class="field-hint">军师在建议里会这样称呼你。</p>
            </el-form-item>

            <el-form-item label="个人描述">
              <el-input
                v-model="form.persona"
                type="textarea"
                :rows="3"
                placeholder="比如：28 岁，在杭州做产品，慢热但熟了话很多，喜欢摇滚和爬山。"
              />
              <p class="field-hint">写具体的身份、性格和近况，比形容词更有用。</p>
            </el-form-item>

            <el-form-item label="说话风格">
              <el-select
                v-model="form.speakingStyle"
                allow-create
                filterable
                clearable
                placeholder="选择或输入，比如「冷幽默」"
                aria-label="说话风格"
              >
                <el-option label="幽默 / 爱用梗" value="幽默/爱用梗" />
                <el-option label="直接坦率" value="直接坦率" />
                <el-option label="温柔体贴" value="温柔体贴" />
                <el-option label="土味情话" value="土味情话" />
                <el-option label="优雅文艺" value="优雅文艺" />
                <el-option label="理性分析" value="理性分析" />
                <el-option label="撒娇可爱" value="撒娇可爱" />
              </el-select>
              <p class="field-hint">可以选现成的，也可以直接敲一个自己的说法。</p>
            </el-form-item>

            <el-form-item label="与大多数人的关系">
              <el-select
                v-model="form.relationshipDefault"
                allow-create
                filterable
                clearable
                placeholder="选择或输入，比如「同事」"
                aria-label="与大多数人的关系"
              >
                <el-option label="同学" value="同学" />
                <el-option label="朋友" value="朋友" />
                <el-option label="暧昧对象" value="暧昧对象" />
                <el-option label="恋人" value="恋人" />
                <el-option label="同事" value="同事" />
                <el-option label="网友" value="网友" />
              </el-select>
              <p class="field-hint">默认关系会影响军师对分寸的判断。</p>
            </el-form-item>
          </el-form>

          <el-button type="primary" class="save-btn" :loading="saving" @click="handleSave">
            保存形象
          </el-button>
        </section>

        <!-- Live preview + AI analysis -->
        <aside class="profile-aside">
          <section class="surface preview-surface">
            <header class="aside-head">
              <h2 class="aside-title">军师眼中的你</h2>
              <span class="aside-kicker">实时预览</span>
            </header>

            <div class="preview-person">
              <MonogramAvatar :name="form.displayName || '我'" :size="52" />
              <div class="preview-identity">
                <p class="preview-name">{{ form.displayName || '还没有名字' }}</p>
                <p class="preview-meta">{{ previewMeta }}</p>
              </div>
            </div>

            <p class="preview-persona" :class="{ 'is-empty': !form.persona }">
              {{ form.persona || '写一句对自己的描述，军师会用这个语气和你说话。' }}
            </p>

            <div v-if="form.speakingStyle || form.relationshipDefault" class="preview-chips">
              <span v-if="form.speakingStyle" class="preview-chip">{{ form.speakingStyle }}</span>
              <span v-if="form.relationshipDefault" class="preview-chip">
                {{ form.relationshipDefault }}
              </span>
            </div>

            <hr class="hairline preview-rule" />
            <p class="preview-note">这段形象会参与军师的回复建议。</p>
          </section>

          <section class="surface analysis-surface">
            <header class="aside-head">
              <h2 class="aside-title">AI 分析</h2>
            </header>
            <p class="analysis-desc">
              让 AI 读完你的聊天记录，总结你的性格和说话方式。需要先导入聊天，才有内容可读。
            </p>

            <el-button
              type="primary"
              plain
              class="analyze-btn"
              :loading="analyzing"
              @click="handleAnalyze"
            >
              <el-icon v-if="!analyzing" :size="14"><MagicStick /></el-icon>
              <span>{{ analyzing ? '正在读你的自述…' : '读一遍我的聊天' }}</span>
            </el-button>

            <div v-if="analyzeHint" class="band is-warning" role="status">
              <el-icon :size="14"><WarningFilled /></el-icon>
              <span>{{ analyzeHint }}</span>
            </div>

            <div v-if="analyzeError" class="band is-danger" role="alert">
              <el-icon :size="14"><CircleCloseFilled /></el-icon>
              <span>{{ analyzeError }}</span>
            </div>

            <div v-if="analysis" class="result">
              <div class="result-wash">
                <div class="result-item">
                  <span class="result-label">个人描述</span>
                  <p class="result-value">{{ analysis.persona || '（未识别）' }}</p>
                </div>
                <div class="result-item">
                  <span class="result-label">说话风格</span>
                  <p class="result-value">{{ analysis.speakingStyle || '未识别' }}</p>
                </div>
                <div class="result-item">
                  <span class="result-label">主要关系</span>
                  <p class="result-value">{{ analysis.relationship || '未识别' }}</p>
                </div>
              </div>
              <el-button
                type="primary"
                class="adopt-btn"
                :loading="saving"
                :disabled="!analysis.persona && !analysis.speakingStyle"
                @click="handleAdopt"
              >
                采纳建议
              </el-button>
            </div>

            <p
              v-else-if="!analyzeHint && !analyzeError && !analyzing"
              class="analysis-empty"
            >
              还没有分析过
            </p>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CircleCloseFilled, MagicStick, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import MonogramAvatar from '@/components/MonogramAvatar.vue'

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
const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const analyzing = ref(false)
const analysis = ref<AnalysisResult | null>(null)
const analyzeHint = ref('')
const analyzeError = ref('')

const previewMeta = computed(() => {
  const hasPersona = !!form.value.persona
  const hasVoice = !!(form.value.speakingStyle || form.value.relationshipDefault)
  if (hasPersona && hasVoice) return '形象已完整'
  if (hasPersona || hasVoice) return '还在补充'
  return '空白形象'
})

onMounted(() => {
  loadProfile()
})

async function loadProfile() {
  loading.value = true
  loadError.value = ''
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
  } catch (e: any) {
    // 404 means there is no profile yet — that is an empty state, not an error.
    if (e?.response?.status !== 404) {
      loadError.value = e?.response?.data?.message || e?.message || '形象加载失败'
    }
  } finally {
    loading.value = false
  }
}

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
.profile-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 32px;
  align-items: start;
}

/* ── Form ──────────────────────────────────────────────────── */

.form-surface {
  min-width: 0;
  padding: 26px 26px 24px;
}

.surface-head {
  margin-bottom: 4px;
}

.surface-title {
  font-size: 16px;
  font-weight: 620;
  letter-spacing: -0.015em;
  color: var(--ink);
}

.surface-sub {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted);
}

.profile-form {
  margin-top: 12px;
}

.profile-form :deep(.el-form-item) {
  margin-bottom: 0;
  padding: 16px 0;
  border-bottom: 1px solid var(--line);
}

.profile-form :deep(.el-form-item:first-child) {
  padding-top: 4px;
}

.profile-form :deep(.el-form-item__label) {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--ink-2);
}

.profile-form :deep(.el-select) {
  width: 100%;
}

.field-hint {
  width: 100%;
  margin-top: 5px;
  font-size: 11px;
  line-height: 1.55;
  color: var(--faint);
}

.save-btn {
  width: 100%;
  height: 40px;
  margin-top: 20px;
}

/* ── Aside ─────────────────────────────────────────────────── */

.profile-aside {
  position: sticky;
  top: 28px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.preview-surface,
.analysis-surface {
  padding: 20px;
}

.aside-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.aside-title {
  font-size: 15px;
  font-weight: 620;
  letter-spacing: -0.01em;
  color: var(--ink);
}

.aside-kicker {
  font-size: 10px;
  font-weight: 550;
  letter-spacing: 0.08em;
  color: var(--faint);
  white-space: nowrap;
}

.preview-person {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-top: 16px;
}

.preview-identity {
  min-width: 0;
}

.preview-name {
  font-size: 15px;
  font-weight: 620;
  line-height: 1.3;
  letter-spacing: -0.01em;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.preview-meta {
  margin-top: 2px;
  font-size: 10.5px;
  color: var(--faint);
}

.preview-persona {
  margin-top: 14px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-2);
  overflow-wrap: anywhere;
}

.preview-persona.is-empty {
  color: var(--faint);
}

.preview-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.preview-chip {
  display: inline-flex;
  align-items: center;
  height: 24px;
  max-width: 100%;
  padding: 0 8px;
  border-radius: var(--r-xs);
  background: var(--wash);
  color: var(--ink-2);
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.preview-rule {
  margin: 16px 0 10px;
}

.preview-note {
  font-size: 10.5px;
  line-height: 1.55;
  color: var(--faint);
}

.analysis-desc {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--muted);
}

.analyze-btn {
  width: 100%;
  margin-top: 14px;
}

.band {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 12px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
  font-size: 11.5px;
  line-height: 1.6;
}

.band :deep(.el-icon) {
  margin-top: 1px;
  flex-shrink: 0;
}

.band.is-warning {
  background: var(--warning-tint);
  color: #7d500f;
}

.band.is-danger {
  background: var(--danger-tint);
  color: #8f2f25;
}

.analysis-empty {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  font-size: 11.5px;
  color: var(--faint);
  text-align: center;
}

.result {
  margin-top: 16px;
}

.result-wash {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 13px;
  background: var(--wash);
  border-radius: var(--r-md);
}

.result-label {
  display: block;
  font-size: 10px;
  font-weight: 550;
  letter-spacing: 0.07em;
  color: var(--faint);
}

.result-value {
  margin-top: 2px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.adopt-btn {
  width: 100%;
  margin-top: 12px;
}

/* ── States ────────────────────────────────────────────────── */

.load-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--danger-tint);
  color: #8f2f25;
  font-size: 12px;
  line-height: 1.6;
}

.load-error span {
  flex: 1;
  min-width: 0;
}

.profile-loading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 32px;
  align-items: start;
}

.loading-panel {
  padding: 24px;
}

.loading-aside {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ── Responsive ────────────────────────────────────────────── */

@media (max-width: 960px) {
  .profile-layout,
  .profile-loading {
    grid-template-columns: 1fr;
    gap: 22px;
  }

  .profile-aside {
    position: static;
  }

  .loading-aside {
    gap: 14px;
  }
}

@media (max-width: 560px) {
  .form-surface {
    padding: 20px 16px;
  }

  .preview-surface,
  .analysis-surface {
    padding: 18px 16px;
  }

  .surface-title {
    font-size: 15px;
  }

  .profile-form :deep(.el-form-item) {
    padding: 15px 0;
  }

  .save-btn {
    margin-top: 18px;
  }
}
</style>
