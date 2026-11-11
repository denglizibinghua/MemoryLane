import type { Memory, SearchResult } from '@/stores/memories'

/* ── Memory categories ─────────────────────────────────────── */

export interface CategoryMeta {
  key: string
  label: string
  color: string
  tint: string
}

const CATEGORY_META: Record<string, CategoryMeta> = {
  promise: { key: 'promise', label: '约定', color: 'var(--cat-promise)', tint: 'var(--cat-promise-tint)' },
  personal_info: {
    key: 'personal_info',
    label: '个人信息',
    color: 'var(--cat-personal)',
    tint: 'var(--cat-personal-tint)',
  },
  preference: {
    key: 'preference',
    label: '偏好',
    color: 'var(--cat-preference)',
    tint: 'var(--cat-preference-tint)',
  },
  event: { key: 'event', label: '事件', color: 'var(--cat-event)', tint: 'var(--cat-event-tint)' },
  persona: { key: 'persona', label: '人设', color: 'var(--cat-persona)', tint: 'var(--cat-persona-tint)' },
  relationship: {
    key: 'relationship',
    label: '关系动态',
    color: 'var(--cat-relationship)',
    tint: 'var(--cat-relationship-tint)',
  },
  other: { key: 'other', label: '其他', color: 'var(--cat-other)', tint: 'var(--cat-other-tint)' },
}

export const MEMORY_CATEGORIES = Object.values(CATEGORY_META)

export function categoryMeta(category?: string | null): CategoryMeta {
  return CATEGORY_META[category || 'other'] || CATEGORY_META.other
}

/* ── Platforms ─────────────────────────────────────────────── */

export interface PlatformMeta {
  label: string
  short: string
}

const PLATFORM_META: Record<string, PlatformMeta> = {
  wechat: { label: '微信', short: 'WX' },
  qq: { label: 'QQ', short: 'QQ' },
  douyin: { label: '抖音', short: 'DY' },
  sms: { label: '短信', short: 'SMS' },
  other: { label: '其他', short: '—' },
  auto: { label: '自动识别', short: 'AUTO' },
}

export function platformMeta(platform?: string | null): PlatformMeta {
  return PLATFORM_META[platform || 'other'] || { label: platform || '其他', short: '—' }
}

export function platformLabel(platform?: string | null): string {
  return platformMeta(platform).label
}

/* ── Dates ─────────────────────────────────────────────────── */

function asDate(value?: string | null): Date | null {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? null : d
}

export function formatDate(value?: string | null): string {
  const d = asDate(value)
  if (!d) return '—'
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

export function formatShortDate(value?: string | null): string {
  const d = asDate(value)
  if (!d) return '—'
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

export function formatDateTime(value?: string | null): string {
  const d = asDate(value)
  if (!d) return '—'
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(value?: string | null): string {
  const d = asDate(value)
  if (!d) return ''
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

export function relativeDay(value?: string | null): string {
  const d = asDate(value)
  if (!d) return '—'
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const target = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const days = Math.round((start - target) / 86_400_000)
  if (days <= 0) return '今天'
  if (days === 1) return '昨天'
  if (days === 2) return '前天'
  if (days < 30) return `${days} 天前`
  if (d.getFullYear() === now.getFullYear()) {
    return d.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })
  }
  return formatDate(value)
}

export function monthLabel(value?: string | null): string {
  const d = asDate(value)
  if (!d) return '未知时间'
  const now = new Date()
  if (d.getFullYear() === now.getFullYear()) {
    return d.toLocaleDateString('zh-CN', { month: 'long' })
  }
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long' })
}

/* ── Numbers + text ────────────────────────────────────────── */

export function percent(value?: number | null, digits = 0): string {
  if (value == null || Number.isNaN(value)) return '—'
  return `${(value * 100).toFixed(digits)}%`
}

export function initials(name?: string | null): string {
  const trimmed = (name || '').trim()
  if (!trimmed) return '·'
  const chars = Array.from(trimmed)
  if (/^[\u3400-\u9fff]/.test(trimmed)) return chars[0]!
  return chars[0]!.toUpperCase()
}

export function truncate(text: string, max = 120): string {
  if (text.length <= max) return text
  return `${text.slice(0, max).trimEnd()}…`
}

/* ── Item discrimination ───────────────────────────────────── */

export function isSearchResult(item: Memory | SearchResult): item is SearchResult {
  return 'score' in item && 'contactName' in item
}
