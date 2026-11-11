<template>
  <span class="cat-pill" :style="{ color: meta.color, background: meta.tint }">
    <span class="cat-dot" :style="{ background: meta.color }"></span>
    <span>{{ meta.label }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { categoryMeta } from '@/utils/presentation'

const props = defineProps<{ category?: string | null }>()
const meta = computed(() => categoryMeta(props.category))
</script>

<style scoped>
.cat-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 8px;
  border-radius: var(--r-xs);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.cat-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  flex-shrink: 0;
}
</style>
