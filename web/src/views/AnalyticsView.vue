<template>
  <div class="page analytics-page">
    <PageHeader
      title="关系动态"
      description="消息的频率、谁先开口、最近的热度——都在这条曲线里。"
    />

    <!-- Overview loading -->
    <div v-if="overviewLoading" class="analytics-layout" aria-busy="true" aria-label="正在加载联系人">
      <aside class="contact-rail">
        <div class="sk-rail-head">
          <el-skeleton-item variant="text" class="sk-head-line" />
        </div>
        <div v-for="n in 6" :key="n" class="sk-rail-row">
          <el-skeleton-item variant="circle" class="sk-avatar" />
          <el-skeleton-item variant="text" class="sk-name" />
          <el-skeleton-item variant="text" class="sk-count" />
        </div>
      </aside>
      <section class="analytics-main">
        <el-skeleton-item variant="text" class="sk-title" />
        <el-skeleton-item variant="text" class="sk-sub" />
        <div class="stats-panel sk-stats">
          <div v-for="n in 4" :key="n" class="stat-cell">
            <el-skeleton-item variant="text" class="sk-label" />
            <el-skeleton-item variant="text" class="sk-value" />
          </div>
        </div>
        <div class="chart-surface sk-chart">
          <el-skeleton-item variant="text" class="sk-chart-title" />
          <el-skeleton-item variant="rect" class="sk-chart-body" />
        </div>
      </section>
    </div>

    <!-- Overview error -->
    <div v-else-if="overviewError" class="error-band" role="alert">
      <span>{{ overviewError }}</span>
      <el-button size="small" @click="loadOverview">重试</el-button>
    </div>

    <!-- No contacts -->
    <EmptyState
      v-else-if="!overview.length"
      title="还没有联系人数据"
      description="导入一段聊天后，这里会画出你和每个人的消息节奏。"
    >
      <el-button type="primary" @click="$router.push('/')">去导入聊天</el-button>
    </EmptyState>

    <div v-else class="analytics-layout">
      <!-- Contact rail -->
      <aside class="contact-rail" aria-label="按联系人查看关系动态">
        <div class="rail-head">
          <span>联系人</span>
          <span class="rail-count tabular">{{ overview.length }}</span>
        </div>
        <ul class="rail-list">
          <li v-for="s in overview" :key="s.contactId">
            <button
              type="button"
              class="rail-item"
              :class="{ 'is-active': s.contactId === selectedId }"
              @click="handleSelect(String(s.contactId))"
            >
              <MonogramAvatar :name="s.contactName" :size="30" />
              <span class="rail-item-name">{{ s.contactName }}</span>
              <span class="rail-item-count tabular">{{ s.totalMessages }}</span>
              <span
                v-if="s.contactId === selectedId"
                class="rail-current"
                aria-hidden="true"
              ></span>
            </button>
          </li>
        </ul>
      </aside>

      <!-- Detail -->
      <section v-if="selectedContact" class="analytics-main">
        <header class="detail-head">
          <div class="detail-head-copy">
            <h2 class="detail-name">{{ selectedContact.contactName }}</h2>
            <p class="detail-meta">
              <span v-if="periodText">{{ periodText }}</span>
              <span v-if="periodText" class="meta-sep" aria-hidden="true">·</span>
              <span class="tabular">共 {{ selectedContact.totalMessages }} 条消息</span>
            </p>
          </div>
          <span v-if="selectedContact.lastMsgAt" class="recent-note">
            最近消息 · {{ relativeDay(selectedContact.lastMsgAt) }}
          </span>
        </header>

        <!-- Stats -->
        <div class="stats-panel">
          <div class="stats-grid">
            <div class="stat-cell">
              <span class="stat-label">总消息数</span>
              <span class="stat-value tabular">{{ selectedContact.totalMessages }}</span>
            </div>
            <div class="stat-cell">
              <span class="stat-label">我的发言</span>
              <span class="stat-value-row">
                <span class="stat-value tabular">{{ selectedContact.selfCount }}</span>
                <span class="stat-percent tabular">{{ formatPercent(selectedContact.selfRatio) }}</span>
              </span>
            </div>
            <div class="stat-cell">
              <span class="stat-label">对方发言</span>
              <span class="stat-value-row">
                <span class="stat-value tabular">{{ otherCount }}</span>
                <span class="stat-percent tabular">{{ formatPercent(1 - selectedContact.selfRatio) }}</span>
              </span>
            </div>
            <div class="stat-cell">
              <span class="stat-label">谁更主动</span>
              <span class="stat-value">{{ initiatorLabel }}</span>
            </div>
          </div>

          <div class="ratio-block">
            <div
              class="ratio-bar"
              role="img"
              :aria-label="`我占 ${formatPercent(selectedContact.selfRatio)}，对方占 ${formatPercent(1 - selectedContact.selfRatio)}`"
            >
              <span class="ratio-self" :style="{ width: selfShareWidth }"></span>
              <span class="ratio-other"></span>
            </div>
            <div class="ratio-labels">
              <span class="ratio-label">
                <i class="ratio-dot is-self" aria-hidden="true"></i>
                我 {{ formatPercent(selectedContact.selfRatio) }}
              </span>
              <span class="ratio-label">
                <i class="ratio-dot is-other" aria-hidden="true"></i>
                对方 {{ formatPercent(1 - selectedContact.selfRatio) }}
              </span>
            </div>
          </div>
        </div>

        <!-- Trend chart -->
        <div class="chart-surface">
          <header class="chart-head">
            <div class="chart-head-copy">
              <h3 class="chart-title">消息趋势</h3>
              <p class="chart-sub">
                按{{ granularity === 'week' ? '周' : '月' }}汇总 · 总消息含双方全部发言
              </p>
            </div>
            <el-radio-group
              v-model="granularity"
              size="small"
              class="granularity"
              @change="loadTrends"
            >
              <el-radio-button value="week">按周</el-radio-button>
              <el-radio-button value="month">按月</el-radio-button>
            </el-radio-group>
          </header>

          <div class="chart-body">
            <div v-if="trendsLoading" class="chart-skeleton" aria-busy="true" aria-label="趋势加载中">
              <el-skeleton-item variant="text" class="sk-chart-note" />
              <el-skeleton-item variant="rect" class="sk-plot" />
            </div>

            <div v-else-if="trendsError" class="chart-feedback is-error" role="alert">
              <p>{{ trendsError }}</p>
              <el-button size="small" @click="loadTrends">重试</el-button>
            </div>

            <EmptyState
              v-else-if="!trends.length"
              compact
              title="还没有消息可以画"
              description="导入聊天后，这里会画出你们的消息节奏。"
            >
              <el-button type="primary" @click="$router.push('/')">去导入聊天</el-button>
            </EmptyState>

            <div
              v-show="!trendsLoading && !trendsError && trends.length > 0"
              class="chart-canvas-wrap"
            >
              <canvas
                ref="chartCanvas"
                role="img"
                :aria-label="`${selectedContact.contactName} 的消息趋势图`"
              ></canvas>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js'
import { fetchOverview, fetchTrends, type ContactStats, type TrendPoint } from '@/api/analytics'
import { formatDate, percent as formatPercentValue, relativeDay } from '@/utils/presentation'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import MonogramAvatar from '@/components/MonogramAvatar.vue'

Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Legend,
  Filler
)

const overview = ref<ContactStats[]>([])
const overviewLoading = ref(true)
const overviewError = ref('')
const selectedId = ref<number | null>(null)
const trends = ref<TrendPoint[]>([])
const trendsLoading = ref(false)
const trendsError = ref('')
const granularity = ref('week')
const chartCanvas = ref<HTMLCanvasElement | null>(null)
let chartInstance: Chart | null = null
let trendRequest = 0

const selectedContact = computed(
  () => overview.value.find((s) => s.contactId === selectedId.value) ?? null
)

const otherCount = computed(() => {
  const c = selectedContact.value
  return c ? c.totalMessages - c.selfCount : 0
})

const initiatorLabel = computed(() => {
  if (!selectedContact.value) return '—'
  return selectedContact.value.selfRatio >= 0.5 ? '我' : '对方'
})

const periodText = computed(() => {
  const c = selectedContact.value
  if (!c) return ''
  if (c.firstMsgAt && c.lastMsgAt) {
    return `${formatDate(c.firstMsgAt)} – ${formatDate(c.lastMsgAt)}`
  }
  if (c.lastMsgAt) return `最近活跃于 ${formatDate(c.lastMsgAt)}`
  return ''
})

const selfShareWidth = computed(() => {
  const ratio = selectedContact.value?.selfRatio ?? 0
  return `${Math.min(100, Math.max(0, ratio * 100))}%`
})

function formatPercent(ratio: number): string {
  return formatPercentValue(ratio, 1)
}

onMounted(async () => {
  Chart.defaults.font.family = getComputedStyle(document.body).fontFamily
  Chart.defaults.color = '#6b675d'
  await loadOverview()
})

onBeforeUnmount(() => {
  chartInstance?.destroy()
  chartInstance = null
})

async function loadOverview() {
  overviewLoading.value = true
  overviewError.value = ''
  let loaded = false
  try {
    overview.value = await fetchOverview()
    loaded = true
  } catch (e: any) {
    overviewError.value = e?.response?.data?.message || e?.message || '联系人数据加载失败，请重试'
  } finally {
    overviewLoading.value = false
  }
  if (!loaded) return

  if (overview.value.length > 0) {
    const stillThere = overview.value.some((s) => s.contactId === selectedId.value)
    if (!stillThere) selectedId.value = overview.value[0]!.contactId
    await loadTrends()
  } else {
    selectedId.value = null
    trends.value = []
  }
}

function handleSelect(index: string) {
  selectedId.value = Number(index)
  loadTrends()
}

async function loadTrends() {
  const contactId = selectedId.value
  if (!contactId) return

  const requestId = ++trendRequest
  trendsLoading.value = true
  trendsError.value = ''
  chartInstance?.destroy()
  chartInstance = null

  try {
    const data = await fetchTrends(contactId, granularity.value)
    if (requestId !== trendRequest) return
    trends.value = data
  } catch (e: any) {
    if (requestId !== trendRequest) return
    trends.value = []
    trendsError.value = e?.response?.data?.message || e?.message || '趋势数据加载失败，请重试'
  } finally {
    if (requestId === trendRequest) {
      trendsLoading.value = false
      await nextTick()
      renderChart()
    }
  }
}

function renderChart() {
  chartInstance?.destroy()
  chartInstance = null
  if (!chartCanvas.value || trends.value.length === 0) return

  const labels = trends.value.map((t) => t.bucket)
  const totalData = trends.value.map((t) => t.msgCount)
  const selfData = trends.value.map((t) => t.selfCount)
  const otherData = trends.value.map((t) => t.msgCount - t.selfCount)

  chartInstance = new Chart(chartCanvas.value, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: '总消息',
          data: totalData,
          borderColor: '#2d3f9e',
          backgroundColor: 'rgba(45, 63, 158, 0.07)',
          borderWidth: 2,
          tension: 0.32,
          fill: true,
          pointRadius: 3,
          pointHoverRadius: 5,
          pointBackgroundColor: '#2d3f9e',
          pointBorderColor: '#fcfbf7',
          pointBorderWidth: 1.5,
        },
        {
          label: '我的发言',
          data: selfData,
          borderColor: '#b94a2d',
          borderWidth: 1.5,
          borderDash: [5, 4],
          tension: 0.32,
          fill: false,
          pointRadius: 3,
          pointHoverRadius: 5,
          pointBackgroundColor: '#b94a2d',
          pointBorderColor: '#fcfbf7',
          pointBorderWidth: 1.5,
        },
        {
          label: '对方发言',
          data: otherData,
          borderColor: '#6b675d',
          borderWidth: 1.5,
          borderDash: [5, 4],
          tension: 0.32,
          fill: false,
          pointRadius: 3,
          pointHoverRadius: 5,
          pointBackgroundColor: '#6b675d',
          pointBorderColor: '#fcfbf7',
          pointBorderWidth: 1.5,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        intersect: false,
        mode: 'index',
      },
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            usePointStyle: true,
            pointStyle: 'circle',
            boxWidth: 6,
            boxHeight: 6,
            padding: 18,
            color: '#6b675d',
            font: { size: 11.5 },
          },
        },
        tooltip: {
          backgroundColor: '#1b1a16',
          titleColor: '#ffffff',
          bodyColor: 'rgba(255, 255, 255, 0.86)',
          titleFont: { size: 12.5, weight: 600 },
          bodyFont: { size: 12 },
          padding: 12,
          cornerRadius: 8,
          usePointStyle: true,
          boxPadding: 4,
        },
      },
      scales: {
        x: {
          border: { display: false },
          grid: { display: false },
          ticks: {
            color: '#948f85',
            font: { size: 10.5 },
            maxRotation: 0,
            autoSkip: true,
            maxTicksLimit: 8,
          },
        },
        y: {
          beginAtZero: true,
          border: { display: false },
          grid: { color: 'rgba(27, 26, 22, 0.06)' },
          ticks: {
            color: '#948f85',
            font: { size: 10.5 },
            precision: 0,
            maxTicksLimit: 5,
          },
        },
      },
    },
  })
}
</script>

<style scoped>
.analytics-page {
  min-height: 100%;
}

.analytics-layout {
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  gap: 42px;
  align-items: start;
}

/* ── Contact rail ──────────────────────────────────────────── */

.contact-rail {
  position: sticky;
  top: 28px;
  min-width: 0;
  padding-top: 2px;
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

.rail-list {
  width: 100%;
  max-width: 100%;
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
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-2);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.rail-item:hover {
  background: var(--wash);
}

.rail-item.is-active {
  background: var(--paper);
  border-color: var(--line);
}

.rail-item-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-weight: 550;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-item-count {
  flex-shrink: 0;
  font-size: 10.5px;
  color: var(--faint);
}

.rail-current {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  flex-shrink: 0;
}

/* ── Detail head ───────────────────────────────────────────── */

.analytics-main {
  min-width: 0;
}

.detail-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.detail-head-copy {
  min-width: 0;
}

.detail-name {
  font-size: 20px;
  font-weight: 640;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--faint);
}

.meta-sep {
  color: var(--line-strong);
}

.recent-note {
  flex-shrink: 0;
  padding-bottom: 2px;
  font-size: 10.5px;
  color: var(--faint);
  white-space: nowrap;
}

/* ── Stats ─────────────────────────────────────────────────── */

.stats-panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.stat-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 17px 20px 15px;
}

.stat-cell + .stat-cell {
  border-left: 1px solid var(--line);
}

.stat-label {
  font-size: 10.5px;
  letter-spacing: 0.04em;
  color: var(--faint);
}

.stat-value-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  margin-top: 7px;
}

.stat-value {
  margin-top: 7px;
  font-size: 28px;
  font-weight: 650;
  line-height: 1.05;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.stat-value-row .stat-value {
  margin-top: 0;
}

.stat-percent {
  font-size: 12px;
  color: var(--muted);
}

.ratio-block {
  padding: 13px 20px 16px;
  border-top: 1px solid var(--line);
}

.ratio-bar {
  display: flex;
  height: 6px;
  border-radius: 99px;
  background: var(--wash-strong);
  overflow: hidden;
}

.ratio-self {
  background: var(--accent);
  border-radius: 99px 0 0 99px;
}

.ratio-other {
  flex: 1;
  background: var(--wash-strong);
}

.ratio-labels {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  font-size: 10.5px;
  color: var(--muted);
}

.ratio-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}

.ratio-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.ratio-dot.is-self {
  background: var(--accent);
}

.ratio-dot.is-other {
  background: var(--wash-strong);
  border: 1px solid var(--line-strong);
}

/* ── Chart surface ─────────────────────────────────────────── */

.chart-surface {
  margin-top: 20px;
  padding: 18px 20px 20px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.chart-head-copy {
  min-width: 0;
}

.chart-title {
  font-size: 13.5px;
  font-weight: 620;
  letter-spacing: -0.01em;
  color: var(--ink);
}

.chart-sub {
  margin-top: 2px;
  font-size: 11px;
  color: var(--faint);
}

.granularity {
  flex-shrink: 0;
}

.chart-body {
  margin-top: 14px;
}

.chart-canvas-wrap {
  position: relative;
  height: 320px;
}

.chart-canvas-wrap canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.chart-feedback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 220px;
  font-size: 12.5px;
  color: var(--muted);
  text-align: center;
}

.chart-feedback.is-error p {
  color: var(--danger);
}

/* ── Loading ───────────────────────────────────────────────── */

.sk-head-line {
  width: 72px;
  height: 10px;
}

.sk-rail-head {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.sk-rail-row {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 8px;
}

.sk-avatar {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
}

.sk-name {
  flex: 1;
  height: 11px;
}

.sk-count {
  width: 26px;
  height: 9px;
}

.sk-title {
  width: 148px;
  height: 20px;
}

.sk-sub {
  width: 220px;
  height: 10px;
  margin-top: 10px;
}

.sk-stats {
  margin-top: 16px;
}

.sk-label {
  width: 54px;
  height: 9px;
}

.sk-value {
  width: 70px;
  height: 24px;
  margin-top: 10px;
}

.sk-chart {
  margin-top: 20px;
}

.sk-chart-title {
  width: 88px;
  height: 12px;
}

.sk-chart-note {
  width: 160px;
  height: 9px;
}

.sk-chart-body {
  width: 100%;
  height: 250px;
  margin-top: 16px;
  border-radius: var(--r-md);
}

.sk-plot {
  width: 100%;
  height: 260px;
  margin-top: 16px;
  border-radius: var(--r-md);
}

/* ── Error band ────────────────────────────────────────────── */

.error-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--danger-tint);
  color: #8f2f25;
  font-size: 12.5px;
}

/* ── Responsive ────────────────────────────────────────────── */

@media (max-width: 960px) {
  .analytics-layout {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .contact-rail {
    position: static;
    padding-top: 0;
    max-height: none;
    overflow: visible;
  }

  .rail-head {
    display: none;
  }

  .rail-list {
    display: flex;
    gap: 6px;
    width: 100%;
    max-width: 100%;
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
    padding: 5px 10px 5px 5px;
    background: var(--paper);
    border-color: var(--line);
  }

  .rail-item.is-active {
    border-color: #c3c9e3;
    background: var(--accent-tint);
  }

  .rail-item-name {
    flex: none;
    font-size: 12px;
  }

  .rail-item-count,
  .rail-current {
    display: none;
  }

  .sk-rail-row {
    display: inline-flex;
    width: 110px;
  }

  .sk-rail-head {
    display: none;
  }
}

@media (max-width: 640px) {
  .detail-head {
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 6px 14px;
  }

  .recent-note {
    padding-bottom: 0;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat-cell {
    padding: 15px 16px 13px;
  }

  .stat-cell:nth-child(odd) {
    border-left: 0;
  }

  .stat-cell:nth-child(n + 3) {
    border-top: 1px solid var(--line);
  }

  .stat-value {
    font-size: 26px;
  }

  .ratio-block {
    padding: 12px 16px 15px;
  }

  .chart-surface {
    margin-top: 16px;
    padding: 16px;
  }

  .chart-head {
    flex-wrap: wrap;
  }

  .chart-canvas-wrap {
    height: 260px;
  }

  .sk-chart-body,
  .sk-plot {
    height: 210px;
  }
}

@media (max-width: 400px) {
  .ratio-labels {
    font-size: 10px;
    gap: 8px;
  }

  .ratio-label {
    gap: 4px;
  }
}
</style>
