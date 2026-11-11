<template>
  <header class="page-head">
    <div class="page-head-copy">
      <RouterLink v-if="backTo" :to="backTo" class="back-link">
        <el-icon :size="13"><ArrowLeft /></el-icon>
        <span>{{ backLabel }}</span>
      </RouterLink>
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="description" class="page-desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-head-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue'

withDefaults(
  defineProps<{
    title: string
    description?: string
    backTo?: string
    backLabel?: string
  }>(),
  { backLabel: '返回' }
)
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 30px;
}

.page-head-copy {
  min-width: 0;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 12px;
  font-size: 12.5px;
  color: var(--muted);
  transition: color 0.15s ease;
}

.back-link:hover {
  color: var(--ink);
}

.page-title {
  font-size: 30px;
  font-weight: 660;
  line-height: 1.15;
  letter-spacing: -0.03em;
  color: var(--ink);
}

.page-desc {
  max-width: 62ch;
  margin-top: 8px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--muted);
}

.page-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding-bottom: 2px;
}

@media (max-width: 700px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
    margin-bottom: 22px;
  }

  .page-title {
    font-size: 25px;
  }

  .page-head-actions {
    padding-bottom: 0;
  }

  .page-head-actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
