<template>
  <span
    class="monogram"
    :style="avatarStyle"
    :title="displayName"
    :aria-label="displayName"
    role="img"
  >
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { initials } from '@/utils/presentation'

const props = withDefaults(
  defineProps<{
    name?: string | null
    size?: number
  }>(),
  { name: '', size: 40 }
)

const TONES = [
  { bg: '#e7edf7', fg: '#2c4e86' },
  { bg: '#f2eae4', fg: '#8a4a2a' },
  { bg: '#e6f0ea', fg: '#2f6a50' },
  { bg: '#f1eaf3', fg: '#6e4585' },
  { bg: '#f6ece8', fg: '#9a3d5c' },
  { bg: '#f4eedd', fg: '#86661f' },
]

const displayName = computed(() => props.name || '未命名')

const tone = computed(() => {
  const name = props.name || ''
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) | 0
  return TONES[Math.abs(hash) % TONES.length]!
})

const label = computed(() => initials(props.name))

const avatarStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: tone.value.bg,
  color: tone.value.fg,
  borderRadius: `${Math.max(8, Math.round(props.size * 0.28))}px`,
  fontSize: `${Math.max(11, Math.round(props.size * 0.34))}px`,
}))
</script>

<style scoped>
.monogram {
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
  font-family: var(--font-display);
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1;
  border: 1px solid rgba(27, 26, 22, 0.06);
  user-select: none;
}
</style>
