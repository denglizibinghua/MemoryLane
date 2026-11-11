<template>
  <div class="empty-state" :class="{ compact }">
    <svg class="empty-art" viewBox="0 0 150 92" fill="none" aria-hidden="true">
      <path
        d="M10 74c28 0 28-48 62-48s34 48 68 48"
        stroke="var(--line-strong)"
        stroke-width="1.2"
        stroke-dasharray="3 5"
        stroke-linecap="round"
      />
      <circle cx="10" cy="74" r="3" fill="var(--accent)" />
      <circle cx="72" cy="26" r="3" fill="var(--seal)" />
      <circle cx="140" cy="74" r="3" fill="var(--faint)" />
      <rect
        x="50"
        y="47"
        width="44"
        height="27"
        rx="5"
        fill="var(--paper)"
        stroke="var(--line-strong)"
      />
      <path d="M58 57h28M58 64h18" stroke="var(--line-strong)" stroke-width="1.4" stroke-linecap="round" />
    </svg>
    <h3 class="empty-title">{{ title }}</h3>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <div v-if="$slots.default" class="empty-actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    compact?: boolean
  }>(),
  { compact: false }
)
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}

.empty-state.compact {
  padding: 40px 20px;
}

.empty-art {
  width: 150px;
  height: 92px;
  margin-bottom: 18px;
  opacity: 0.95;
}

.empty-title {
  font-size: 16px;
  font-weight: 620;
  color: var(--ink);
  letter-spacing: -0.01em;
}

.empty-desc {
  max-width: 46ch;
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--muted);
}

.empty-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
  flex-wrap: wrap;
}
</style>
