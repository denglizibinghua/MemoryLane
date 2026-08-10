-- ============================================================
-- MemoryLane V1 — Initial Schema (runs on bare PostgreSQL)
-- ============================================================

-- ============================================================
-- 1. Enums
-- ============================================================
CREATE TYPE memory_category AS ENUM (
    'promise',        -- 约定/承诺
    'personal_info',  -- 个人信息
    'preference',     -- 偏好
    'event',          -- 事件
    'persona',        -- 人设特征
    'relationship',   -- 关系动态
    'other'
);

-- ============================================================
-- 2. Contacts — 联系人
-- ============================================================
CREATE TABLE contacts (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    platform    VARCHAR(20),                        -- 'wechat' | 'qq' | 'douyin' | 'sms' | 'other'
    avatar      TEXT,
    profile     JSONB DEFAULT '{}',                 -- { "gender": "男", "birthday": "2003-05", "tags": ["同学"] }
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX idx_contacts_name_platform ON contacts(name, platform)
    WHERE platform IS NOT NULL;

-- ============================================================
-- 3. Conversations — 会话
-- ============================================================
CREATE TABLE conversations (
    id             BIGSERIAL PRIMARY KEY,
    contact_id     BIGINT NOT NULL REFERENCES contacts(id),
    platform       VARCHAR(20),
    message_count  INT DEFAULT 0,
    first_msg_at   TIMESTAMPTZ,
    last_msg_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_conversations_contact ON conversations(contact_id, last_msg_at DESC);

-- ============================================================
-- 4. Messages — 原始消息
-- ============================================================
CREATE TABLE messages (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT NOT NULL REFERENCES conversations(id),
    speaker          VARCHAR(50) NOT NULL,          -- 'self' | contact.name
    content          TEXT NOT NULL,
    raw_time         TIMESTAMPTZ,                   -- 聊天记录里的原始时间
    content_hash     VARCHAR(64),                   -- SHA-256，用于去重
    importance       INT DEFAULT 0,             -- 0=未分类, 1=日常寒暄, 2=有价值, 3=关键记忆
    created_at       TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX idx_messages_hash ON messages(content_hash);
CREATE INDEX idx_messages_conv_time ON messages(conversation_id, raw_time DESC);
CREATE INDEX idx_messages_importance ON messages(conversation_id, importance)
    WHERE importance >= 2;

-- ============================================================
-- 5. Memories — 结构化记忆
-- ============================================================
CREATE TABLE memories (
    id              BIGSERIAL PRIMARY KEY,
    contact_id      BIGINT NOT NULL REFERENCES contacts(id),
    category        memory_category NOT NULL,
    content         TEXT NOT NULL,                  -- AI 提炼后的记忆描述
    confidence      FLOAT DEFAULT 1.0,              -- 置信度 0-1
    source_msg_ids  BIGINT[],                       -- 溯源：从哪些消息提取的
    valid_from      TIMESTAMPTZ,                    -- 记忆生效时间
    valid_until     TIMESTAMPTZ,                    -- NULL = 至今有效
    fts             tsvector,                       -- 全文检索（触发器自动维护）
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

-- Indexes
CREATE INDEX idx_memories_contact_cat ON memories(contact_id, category);
CREATE INDEX idx_memories_contact_conf ON memories(contact_id, confidence DESC);
CREATE INDEX idx_memories_valid ON memories(contact_id, valid_until)
    WHERE valid_until IS NULL;
CREATE INDEX idx_memories_fts ON memories USING GIN (fts);

-- ============================================================
-- 6. Reminders — 提醒
-- ============================================================
CREATE TABLE reminders (
    id              BIGSERIAL PRIMARY KEY,
    memory_id       BIGINT REFERENCES memories(id),
    contact_id      BIGINT NOT NULL REFERENCES contacts(id),
    title           VARCHAR(200) NOT NULL,          -- "和张三吃饭"
    remind_at       TIMESTAMPTZ NOT NULL,           -- 提醒时间
    event_time      TIMESTAMPTZ,                    -- 约定本身的时间
    status          VARCHAR(20) DEFAULT 'pending',  -- pending | confirmed | triggered | dismissed
    source_text     TEXT,                           -- 原始消息片段
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_reminders_status_time ON reminders(status, remind_at)
    WHERE status = 'pending';

-- ============================================================
-- 7. Triggers & Functions
-- ============================================================

-- Auto-update tsvector for memories
CREATE FUNCTION memories_fts_trigger() RETURNS trigger AS $$
BEGIN
    NEW.fts := setweight(to_tsvector('simple', coalesce(NEW.content, '')), 'A');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_memories_fts
    BEFORE INSERT OR UPDATE OF content ON memories
    FOR EACH ROW EXECUTE FUNCTION memories_fts_trigger();

-- Auto-update updated_at for contacts
CREATE FUNCTION update_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_contacts_updated_at
    BEFORE UPDATE ON contacts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_conversations_updated_at
    BEFORE UPDATE ON conversations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_memories_updated_at
    BEFORE UPDATE ON memories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_reminders_updated_at
    BEFORE UPDATE ON reminders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
