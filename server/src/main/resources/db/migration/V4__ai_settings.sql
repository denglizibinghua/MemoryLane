-- ============================================================
-- MemoryLane V4 — AI Provider Settings
-- Hot-swappable AI provider configuration (stored in DB, no restart)
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_settings (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL DEFAULT 'openai',
    api_key VARCHAR(512),
    api_base VARCHAR(512),
    model VARCHAR(100) NOT NULL DEFAULT 'gpt-4o-mini',
    temperature DOUBLE PRECISION NOT NULL DEFAULT 0.3,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Insert default row so the table is never empty
INSERT INTO ai_settings (id, provider, api_key, model)
VALUES (1, 'openai', NULL, 'gpt-4o-mini')
ON CONFLICT (id) DO NOTHING;
