-- ============================================================
-- MemoryLane V9 — Embedding (语义搜索) Opt-in Settings
-- 默认关闭，用户可在设置页自行开启并选择 provider
-- ============================================================

ALTER TABLE ai_settings
    ADD COLUMN IF NOT EXISTS embedding_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(50),
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(100);
