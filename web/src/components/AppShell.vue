<template>
  <div class="app-shell">
    <div class="grain" aria-hidden="true"></div>

    <a class="skip-link" href="#main-content">跳到主要内容</a>

    <!-- Desktop rail -->
    <aside class="rail">
      <RouterLink to="/" class="brand" aria-label="MemoryLane 忆路首页">
        <svg class="brand-mark" viewBox="0 0 32 32" fill="none" aria-hidden="true">
          <path
            d="M9 27c0-5.5 4-6.5 4-11.5S8.5 9.5 8.5 5.5"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
          />
          <path
            d="M20 27c0-5.5 4-6.5 4-11.5S19.5 9.5 19.5 5.5"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            opacity=".42"
          />
          <circle cx="8.5" cy="5.5" r="2.5" fill="var(--seal)" />
        </svg>
        <span class="brand-text">
          <span class="brand-cn">忆路</span>
          <span class="brand-en">MemoryLane</span>
        </span>
      </RouterLink>

      <nav class="rail-nav" aria-label="主导航">
        <div v-for="group in navGroups" :key="group.label" class="rail-group">
          <p class="rail-group-label">{{ group.label }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="rail-link"
            :class="{ 'is-active': isActive(item.to) }"
          >
            <el-icon :size="17"><component :is="item.icon" /></el-icon>
            <span class="rail-link-label">{{ item.label }}</span>
            <span v-if="item.to === '/reminders' && pendingCount > 0" class="rail-badge">
              {{ pendingCount > 99 ? '99+' : pendingCount }}
            </span>
          </RouterLink>
        </div>
      </nav>

      <div class="rail-foot">
        <RouterLink to="/settings" class="rail-link" :class="{ 'is-active': isActive('/settings') }">
          <el-icon :size="17"><Setting /></el-icon>
          <span class="rail-link-label">AI 设置</span>
        </RouterLink>

        <RouterLink to="/settings" class="ai-status" :title="aiStatusTitle">
          <span class="ai-dot" :class="{ 'is-ready': !!aiModel }"></span>
          <span class="ai-status-text">
            <span class="ai-status-main">{{ aiModel || 'AI 未配置' }}</span>
            <span class="ai-status-sub">{{ aiProvider || '点击配置模型' }}</span>
          </span>
        </RouterLink>
      </div>
    </aside>

    <!-- Mobile top bar -->
    <header class="mobile-bar">
      <RouterLink to="/" class="brand brand--mobile" aria-label="MemoryLane 忆路首页">
        <svg class="brand-mark" viewBox="0 0 32 32" fill="none" aria-hidden="true">
          <path
            d="M9 27c0-5.5 4-6.5 4-11.5S8.5 9.5 8.5 5.5"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
          />
          <path
            d="M20 27c0-5.5 4-6.5 4-11.5S19.5 9.5 19.5 5.5"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            opacity=".42"
          />
          <circle cx="8.5" cy="5.5" r="2.5" fill="var(--seal)" />
        </svg>
        <span class="brand-cn">忆路</span>
      </RouterLink>
      <span class="mobile-title">{{ currentTitle }}</span>
      <RouterLink to="/settings" class="mobile-settings" aria-label="AI 设置">
        <el-icon :size="18"><Setting /></el-icon>
      </RouterLink>
    </header>

    <div class="app-body">
      <main id="main-content" class="app-main" tabindex="-1">
        <slot />
      </main>
    </div>

    <!-- Mobile bottom tabs -->
    <nav class="mobile-tabs" aria-label="底部导航">
      <RouterLink
        v-for="tab in mobileTabs"
        :key="tab.to"
        :to="tab.to"
        class="mobile-tab"
        :class="{ 'is-active': isActive(tab.to) }"
      >
        <el-icon :size="20"><component :is="tab.icon" /></el-icon>
        <span>{{ tab.label }}</span>
        <span v-if="tab.to === '/reminders' && pendingCount > 0" class="tab-badge"></span>
      </RouterLink>
      <button
        type="button"
        class="mobile-tab"
        :class="{ 'is-active': moreActive }"
        @click="moreOpen = true"
      >
        <el-icon :size="20"><MoreFilled /></el-icon>
        <span>更多</span>
      </button>
    </nav>

    <!-- More sheet -->
    <el-drawer
      v-model="moreOpen"
      direction="rtl"
      size="min(340px, 88vw)"
      :with-header="false"
      class="more-drawer"
    >
      <div class="more-head">
        <div>
          <p class="more-title">更多</p>
          <p class="more-sub">关系、提醒与你的形象</p>
        </div>
        <button type="button" class="more-close" aria-label="关闭" @click="moreOpen = false">
          <el-icon :size="18"><Close /></el-icon>
        </button>
      </div>
      <nav class="more-nav" aria-label="更多导航">
        <RouterLink
          v-for="item in moreItems"
          :key="item.to"
          :to="item.to"
          class="more-link"
          :class="{ 'is-active': isActive(item.to) }"
          @click="moreOpen = false"
        >
          <span class="more-link-icon"><el-icon :size="18"><component :is="item.icon" /></el-icon></span>
          <span class="more-link-text">
            <span class="more-link-label">{{ item.label }}</span>
            <span class="more-link-desc">{{ item.desc }}</span>
          </span>
          <el-icon class="more-chevron" :size="14"><ArrowRight /></el-icon>
        </RouterLink>
      </nav>
      <div class="more-foot">
        <RouterLink to="/settings" class="more-settings" @click="moreOpen = false">
          <el-icon :size="17"><Setting /></el-icon>
          <span>AI 设置</span>
        </RouterLink>
        <div class="more-ai">
          <span class="ai-dot" :class="{ 'is-ready': !!aiModel }"></span>
          <span>{{ aiModel || 'AI 未配置' }}</span>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ArrowRight,
  Avatar,
  Bell,
  ChatLineRound,
  Close,
  HomeFilled,
  Memo,
  MoreFilled,
  Setting,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import { getAiSettings } from '@/api/settings'
import { fetchDueReminders } from '@/api/reminders'

interface NavItem {
  to: string
  label: string
  desc: string
  icon: Component
}

const route = useRoute()
const moreOpen = ref(false)
const aiModel = ref('')
const aiProvider = ref('')
const pendingCount = ref(0)

const providerLabels: Record<string, string> = {
  openai: 'OpenAI',
  deepseek: 'DeepSeek',
  ollama: 'Ollama',
  anthropic: 'Anthropic',
  dashscope: '通义千问',
  zhipuai: '智谱 GLM',
  moonshot: 'Kimi',
  custom: '自定义',
}

const navGroups: { label: string; items: NavItem[] }[] = [
  {
    label: '工作台',
    items: [
      { to: '/', label: '首页', desc: '导入与总览', icon: HomeFilled },
      { to: '/contacts', label: '联系人', desc: '对话里的人', icon: User },
      { to: '/memories', label: '记忆库', desc: '检索与出处', icon: Memo },
      { to: '/advisor', label: '军师', desc: '回复建议', icon: ChatLineRound },
    ],
  },
  {
    label: '留意',
    items: [
      { to: '/analytics', label: '关系动态', desc: '消息趋势', icon: TrendCharts },
      { to: '/reminders', label: '提醒', desc: '约定与日程', icon: Bell },
      { to: '/profile', label: '我的形象', desc: '你的语气', icon: Avatar },
    ],
  },
]

const allItems = computed(() => navGroups.flatMap((g) => g.items))

const mobileTabs: NavItem[] = [
  { to: '/', label: '首页', desc: '', icon: HomeFilled },
  { to: '/contacts', label: '联系人', desc: '', icon: User },
  { to: '/memories', label: '记忆', desc: '', icon: Memo },
  { to: '/advisor', label: '军师', desc: '', icon: ChatLineRound },
]

const moreItems: NavItem[] = [
  { to: '/analytics', label: '关系动态', desc: '消息频率、谁更主动', icon: TrendCharts },
  { to: '/reminders', label: '提醒', desc: '约定到期前提醒你', icon: Bell },
  { to: '/profile', label: '我的形象', desc: '让军师学会你的语气', icon: Avatar },
]

const currentTitle = computed(() => {
  if (route.path.startsWith('/settings')) return 'AI 设置'
  if (route.path.startsWith('/contacts') && route.path !== '/contacts') return '联系人'
  const found = allItems.value.find((item) => isActive(item.to))
  return found?.label || '忆路'
})

const moreActive = computed(() =>
  ['/analytics', '/reminders', '/profile', '/settings'].some((path) => route.path.startsWith(path))
)

const aiStatusTitle = computed(() =>
  aiModel.value ? `当前模型：${aiModel.value}，点击前往设置` : '尚未配置 AI 模型，点击前往设置'
)

function isActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  if (path === '/contacts') return route.path === '/contacts' || route.path.startsWith('/contacts/')
  return route.path === path || route.path.startsWith(`${path}/`)
}

onMounted(async () => {
  try {
    const settings = await getAiSettings()
    aiModel.value = settings.model || ''
    aiProvider.value = providerLabels[settings.provider] || settings.provider || ''
  } catch {
    // Shell status is informational only.
  }
  try {
    const due = await fetchDueReminders()
    pendingCount.value = due.pendingCount || 0
  } catch {
    // Reminder service may be unavailable; keep the badge hidden.
  }
})
</script>

<style scoped>
.app-shell {
  position: relative;
  z-index: 0;
  min-height: 100dvh;
}

.grain {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: 0.5;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/%3E%3CfeColorMatrix type='saturate' values='0'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)' opacity='0.22'/%3E%3C/svg%3E");
  background-size: 160px 160px;
}

.skip-link {
  position: fixed;
  top: 8px;
  left: 8px;
  z-index: 3000;
  padding: 8px 14px;
  background: var(--ink);
  color: var(--paper);
  border-radius: var(--r-sm);
  font-size: 13px;
  transform: translateY(-160%);
  transition: transform 0.16s ease;
}

.skip-link:focus {
  transform: translateY(0);
}

/* ── Desktop rail ──────────────────────────────────────────── */

.rail {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 2;
  width: var(--rail-w);
  display: flex;
  flex-direction: column;
  padding: 22px 14px 18px;
  background: var(--rail);
  color: var(--rail-text);
  border-right: 1px solid rgba(0, 0, 0, 0.25);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 10px 22px;
  color: var(--rail-text);
}

.brand-mark {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.brand-cn {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.brand-en {
  margin-top: 3px;
  font-size: 8.5px;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--rail-muted);
}

.rail-nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: 12px;
}

.rail-nav::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.14);
  border-color: var(--rail);
}

.rail-group + .rail-group {
  margin-top: 22px;
}

.rail-group-label {
  padding: 0 12px 7px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--rail-muted);
}

.rail-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 38px;
  padding: 0 11px;
  margin: 2px 0;
  border-radius: var(--r-sm);
  color: #bdb8ab;
  font-size: 13.5px;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.rail-link:hover {
  background: rgba(255, 255, 255, 0.055);
  color: var(--rail-text);
}

.rail-link:focus-visible,
.rail :focus-visible {
  outline-color: #d9896f;
}

.rail-link.is-active {
  background: rgba(255, 255, 255, 0.09);
  color: #fff;
}

.rail-link.is-active :deep(.el-icon) {
  color: #d9896f;
}

.rail-link-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 99px;
  background: var(--seal);
  color: #fff;
  font-size: 10px;
  font-weight: 650;
  line-height: 18px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.rail-foot {
  padding-top: 12px;
  border-top: 1px solid var(--line-dark);
}

.ai-status {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 10px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
  background: rgba(255, 255, 255, 0.04);
  transition: background-color 0.15s ease;
}

.ai-status:hover {
  background: rgba(255, 255, 255, 0.07);
}

.ai-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #7a766b;
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.05);
  flex-shrink: 0;
}

.ai-dot.is-ready {
  background: #79b892;
  box-shadow: 0 0 0 3px rgba(121, 184, 146, 0.14);
}

.ai-status-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.25;
}

.ai-status-main {
  font-size: 11.5px;
  font-weight: 550;
  color: var(--rail-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-status-sub {
  margin-top: 2px;
  font-size: 10px;
  color: var(--rail-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Body ──────────────────────────────────────────────────── */

.app-body {
  position: relative;
  z-index: 1;
  margin-left: var(--rail-w);
  min-height: 100dvh;
}

.app-main {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}

/* ── Mobile chrome ─────────────────────────────────────────── */

.mobile-bar,
.mobile-tabs {
  display: none;
}

/* ── More drawer ───────────────────────────────────────────── */

.more-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 24px 22px 18px;
}

.more-title {
  font-size: 19px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.more-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--muted);
}

.more-close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--muted);
  cursor: pointer;
}

.more-close:hover {
  color: var(--ink);
  background: var(--wash);
}

.more-nav {
  padding: 0 12px;
}

.more-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 10px;
  border-radius: var(--r-md);
  transition: background-color 0.15s ease;
}

.more-link:hover,
.more-link.is-active {
  background: var(--wash);
}

.more-link-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: var(--r-sm);
  background: var(--paper);
  border: 1px solid var(--line);
  color: var(--ink-2);
  flex-shrink: 0;
}

.more-link.is-active .more-link-icon {
  color: var(--accent);
  border-color: #c9cfe8;
}

.more-link-text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.more-link-label {
  font-size: 14px;
  font-weight: 550;
  color: var(--ink);
}

.more-link-desc {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--faint);
}

.more-chevron {
  color: var(--faint);
}

.more-foot {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px 22px calc(20px + env(safe-area-inset-bottom));
  border-top: 1px solid var(--line);
  background: var(--paper);
}

.more-settings {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13.5px;
  font-weight: 550;
  color: var(--ink-2);
}

.more-ai {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 12px;
  font-size: 11.5px;
  color: var(--faint);
}

.more-ai .ai-dot {
  box-shadow: none;
}

/* ── Responsive ────────────────────────────────────────────── */

@media (max-width: 960px) {
  .rail {
    display: none;
  }

  .app-body {
    margin-left: 0;
    padding-top: var(--mobile-bar-h);
    padding-bottom: calc(var(--mobile-tabs-h) + env(safe-area-inset-bottom));
  }

  .app-main {
    min-height: calc(100dvh - var(--mobile-bar-h) - var(--mobile-tabs-h));
  }

  .mobile-bar {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    z-index: 30;
    display: flex;
    align-items: center;
    gap: 10px;
    height: var(--mobile-bar-h);
    padding: 0 16px;
    background: rgba(252, 251, 247, 0.92);
    backdrop-filter: blur(10px);
    border-bottom: 1px solid var(--line);
  }

  .brand--mobile {
    gap: 7px;
    padding: 0;
    color: var(--ink);
  }

  .brand--mobile .brand-mark {
    width: 23px;
    height: 23px;
  }

  .brand--mobile .brand-cn {
    font-size: 15.5px;
  }

  .mobile-title {
    flex: 1;
    min-width: 0;
    padding-left: 10px;
    border-left: 1px solid var(--line-strong);
    font-size: 12px;
    color: var(--muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-settings {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    border-radius: var(--r-sm);
    color: var(--muted);
  }

  .mobile-settings:active {
    background: var(--wash);
  }

  .mobile-tabs {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 30;
    display: flex;
    justify-content: space-around;
    height: calc(var(--mobile-tabs-h) + env(safe-area-inset-bottom));
    padding-bottom: env(safe-area-inset-bottom);
    background: rgba(252, 251, 247, 0.96);
    backdrop-filter: blur(12px);
    border-top: 1px solid var(--line);
  }

  .mobile-tab {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
    flex: 1;
    min-width: 0;
    border: 0;
    background: transparent;
    color: var(--faint);
    font-size: 10.5px;
    font-weight: 500;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;
  }

  .mobile-tab.is-active {
    color: var(--ink);
  }

  .mobile-tab.is-active :deep(.el-icon) {
    color: var(--accent);
  }

  .tab-badge {
    position: absolute;
    top: 8px;
    left: 50%;
    width: 5px;
    height: 5px;
    margin-left: 7px;
    border-radius: 50%;
    background: var(--seal);
  }
}

@media (max-width: 380px) {
  .mobile-title {
    display: none;
  }
}
</style>
