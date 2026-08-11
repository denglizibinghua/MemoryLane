-- ============================================================
-- V8 — User profile: manual persona + AI-suggested profile
--       injected into advisor prompt for contextual replies.
-- ============================================================

CREATE TABLE IF NOT EXISTS user_profile (
    id                    BIGSERIAL PRIMARY KEY,
    display_name          VARCHAR(100),               -- user's chosen display name
    persona               TEXT,                        -- free-form self-description
    speaking_style        VARCHAR(100),                -- "直接/幽默/温柔/土味/..."
    relationship_default  VARCHAR(200),                -- "和大多数人是同学/朋友/暧昧..."
    ai_suggested          JSONB DEFAULT '{}',          -- last AI suggestion snapshot
    created_at            TIMESTAMPTZ DEFAULT now(),
    updated_at            TIMESTAMPTZ DEFAULT now()
);

-- At most one row
CREATE UNIQUE INDEX idx_user_profile_single ON user_profile((1));
