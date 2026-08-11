<template>
  <div class="analytics">
    <el-container class="analytics-container">
      <el-header class="analytics-header">
        <div class="back-row">
          <el-button text @click="$router.push('/')">
            <el-icon><ArrowLeft /></el-icon>
            返回首页
          </el-button>
          <h2 class="page-title">关系动态</h2>
        </div>
      </el-header>

      <el-container>
        <!-- 左侧联系人列表 -->
        <el-aside class="contact-sidebar" width="220px">
          <el-card shadow="never" class="sidebar-card">
            <template #header>
              <span class="sidebar-title">联系人</span>
            </template>
            <div v-if="overviewLoading" class="sidebar-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              加载中...
            </div>
            <el-menu
              v-else
              :default-active="String(selectedId)"
              @select="handleSelect"
            >
              <el-menu-item
                v-for="s in overview"
                :key="s.contactId"
                :index="String(s.contactId)"
              >
                <div class="contact-item">
                  <span class="contact-name">{{ s.contactName }}</span>
                  <el-tag size="small" round>{{ s.totalMessages }} 条</el-tag>
                </div>
              </el-menu-item>
            </el-menu>
            <div v-if="!overviewLoading && overview.length === 0" class="sidebar-empty">
              暂无联系人数据
            </div>
          </el-card>
        </el-aside>

        <!-- 右侧详情 -->
        <el-main class="detail-area">
          <!-- 未选择状态 -->
          <el-empty
            v-if="!selectedContact"
            description="左侧选择联系人查看趋势"
            :image-size="120"
          />

          <!-- 已选择联系人 -->
          <template v-else>
            <!-- 统计卡片 -->
            <div class="stat-cards">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">总消息数</div>
                <div class="stat-value">{{ selectedContact.totalMessages }}</div>
              </el-card>
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">我的发言</div>
                <div class="stat-value">{{ selectedContact.selfCount }}</div>
                <div class="stat-sub">{{ formatPercent(selectedContact.selfRatio) }}</div>
              </el-card>
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">对方发言</div>
                <div class="stat-value">{{ selectedContact.totalMessages - selectedContact.selfCount }}</div>
                <div class="stat-sub">{{ formatPercent(1 - selectedContact.selfRatio) }}</div>
              </el-card>
              <el-card shadow="never" class="stat-card" :class="initiatorClass">
                <div class="stat-label">谁更主动</div>
                <div class="stat-value">{{ initiatorLabel }}</div>
              </el-card>
            </div>

            <!-- 趋势图 -->
            <el-card shadow="never" class="chart-card">
              <template #header>
                <div class="chart-header">
                  <span>消息趋势</span>
                  <el-radio-group
                    v-model="granularity"
                    size="small"
                    @change="loadTrends"
                  >
                    <el-radio-button value="week">按周</el-radio-button>
                    <el-radio-button value="month">按月</el-radio-button>
                  </el-radio-group>
                </div>
              </template>
              <div v-if="trendsLoading" class="chart-loading">
                <el-icon class="is-loading"><Loading /></el-icon>
              </div>
              <canvas ref="chartCanvas" v-show="!trendsLoading && trends.length > 0" />
              <el-empty
                v-if="!trendsLoading && trends.length === 0"
                description="暂无趋势数据"
                :image-size="80"
              />
            </el-card>
          </template>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import { Chart, LineController, LineElement, PointElement, LinearScale, CategoryScale, Tooltip, Legend, Filler } from 'chart.js'
import { fetchOverview, fetchTrends, type ContactStats, type TrendPoint } from '@/api/analytics'

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Tooltip, Legend, Filler)

const overview = ref<ContactStats[]>([])
const overviewLoading = ref(false)
const selectedId = ref<number | null>(null)
const trends = ref<TrendPoint[]>([])
const trendsLoading = ref(false)
const granularity = ref('week')
const chartCanvas = ref<HTMLCanvasElement | null>(null)
let chartInstance: Chart | null = null

const selectedContact = computed(() =>
  overview.value.find(s => s.contactId === selectedId.value) ?? null
)

const initiatorLabel = computed(() => {
  if (!selectedContact.value) return '-'
  return selectedContact.value.selfRatio >= 0.5 ? '我' : '对方'
})

const initiatorClass = computed(() => {
  if (!selectedContact.value) return ''
  return selectedContact.value.selfRatio >= 0.5 ? 'initiator-self' : 'initiator-other'
})

function formatPercent(ratio: number): string {
  return (ratio * 100).toFixed(1) + '%'
}

onMounted(async () => {
  overviewLoading.value = true
  try {
    overview.value = await fetchOverview()
    if (overview.value.length > 0) {
      selectedId.value = overview.value[0].contactId
      await loadTrends()
    }
  } finally {
    overviewLoading.value = false
  }
})

onBeforeUnmount(() => {
  chartInstance?.destroy()
})

function handleSelect(index: string) {
  selectedId.value = Number(index)
  loadTrends()
}

async function loadTrends() {
  if (!selectedId.value) return
  trendsLoading.value = true
  try {
    trends.value = await fetchTrends(selectedId.value, granularity.value)
    await nextTick()
    renderChart()
  } finally {
    trendsLoading.value = false
  }
}

function renderChart() {
  chartInstance?.destroy()
  if (!chartCanvas.value || trends.value.length === 0) return

  const labels = trends.value.map(t => t.bucket)
  const totalData = trends.value.map(t => t.msgCount)
  const selfData = trends.value.map(t => t.selfCount)
  const otherData = trends.value.map(t => t.msgCount - t.selfCount)

  chartInstance = new Chart(chartCanvas.value, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: '总消息',
          data: totalData,
          borderColor: '#6366f1',
          backgroundColor: 'rgba(99, 102, 241, 0.08)',
          borderWidth: 2,
          tension: 0.3,
          fill: true,
          pointRadius: 3,
          pointHoverRadius: 5,
        },
        {
          label: '我的发言',
          data: selfData,
          borderColor: '#ec4899',
          backgroundColor: 'rgba(236, 72, 153, 0.05)',
          borderWidth: 2,
          tension: 0.3,
          fill: true,
          pointRadius: 3,
          pointHoverRadius: 5,
          borderDash: [5, 3],
        },
        {
          label: '对方发言',
          data: otherData,
          borderColor: '#06b6d4',
          backgroundColor: 'rgba(6, 182, 212, 0.05)',
          borderWidth: 2,
          tension: 0.3,
          fill: true,
          pointRadius: 3,
          pointHoverRadius: 5,
          borderDash: [5, 3],
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
            padding: 20,
          },
        },
        tooltip: {
          backgroundColor: '#1e1b4b',
          titleFont: { size: 13 },
          bodyFont: { size: 12 },
          padding: 12,
          cornerRadius: 8,
        },
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { color: '#9ca3af', font: { size: 11 } },
        },
        y: {
          beginAtZero: true,
          grid: { color: 'rgba(0,0,0,0.04)' },
          ticks: {
            color: '#9ca3af',
            font: { size: 11 },
            stepSize: 1,
          },
        },
      },
    },
  })
}
</script>

<style scoped>
.analytics {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 50%, #fae8ff 100%);
}

.analytics-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 24px;
}

.analytics-header {
  padding: 20px 0;
  height: auto;
}

.back-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #1e1b4b;
  margin: 0;
}

/* 左侧联系人 */
.contact-sidebar {
  margin-right: 24px;
}

.sidebar-card {
  border-radius: 12px;
  background: #fff;
}

.sidebar-card :deep(.el-card__header) {
  padding: 14px 16px 10px;
  border-bottom: 1px solid #f3f4f6;
}

.sidebar-title {
  font-weight: 600;
  color: #374151;
  font-size: 14px;
}

.sidebar-loading,
.sidebar-empty {
  padding: 24px 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.sidebar-card :deep(.el-menu) {
  border-right: none;
}

.sidebar-card :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  font-size: 13px;
  border-radius: 6px;
  margin: 2px 8px;
}

.sidebar-card :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(168, 85, 247, 0.08));
  color: #6366f1;
  font-weight: 600;
}

.contact-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 8px;
}

.contact-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 右侧详情 */
.detail-area {
  padding: 0;
}

/* 统计卡片 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  text-align: center;
  border: none;
  border-radius: 12px;
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-label {
  font-size: 13px;
  color: #9ca3af;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1e1b4b;
}

.stat-sub {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}

.stat-card.initiator-self {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.06), rgba(99, 102, 241, 0.02));
}

.stat-card.initiator-other {
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.06), rgba(6, 182, 212, 0.02));
}

/* 趋势图 */
.chart-card {
  border-radius: 12px;
}

.chart-card :deep(.el-card__header) {
  padding: 16px 20px 12px;
  border-bottom: 1px solid #f3f4f6;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: #374151;
}

.chart-card :deep(.el-card__body) {
  padding: 20px;
}

.chart-card canvas {
  height: 320px !important;
}

.chart-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 320px;
  color: #9ca3af;
  font-size: 24px;
}
</style>
