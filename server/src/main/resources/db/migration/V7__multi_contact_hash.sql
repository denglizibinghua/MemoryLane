-- ============================================================
-- V7 — Multi-contact support: allow same content across
--       different conversations (group-chat dedup fix).
-- ============================================================

-- Drop old single-column unique index
DROP INDEX IF EXISTS idx_messages_hash;

-- New composite index: same content allowed in different conversations
CREATE UNIQUE INDEX idx_messages_conv_hash ON messages(conversation_id, content_hash);
