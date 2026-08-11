-- V6: 提醒功能加固
--   1. UNIQUE on reminders(memory_id) — 防并发重复提醒
--   2. 重建部分索引为 WHERE status='confirmed' — 匹配 triggerDueReminders 查询
--   3. 加 memories.reminder_scanned_at — 防未解析记忆被 LLM 重复扫描

-- ============================================================
-- 1. 唯一约束：一个记忆只对应一个提醒
-- ============================================================
-- 先清理可能已存在的重复行（保留最新的）
DELETE FROM reminders a
USING reminders b
WHERE a.memory_id = b.memory_id AND a.id < b.id;

ALTER TABLE reminders
    ADD CONSTRAINT uq_reminders_memory_id UNIQUE (memory_id);

-- ============================================================
-- 2. 重建部分索引，匹配 triggerDueReminders 的 status='confirmed' 查询
-- ============================================================
DROP INDEX IF EXISTS idx_reminders_status_time;

CREATE INDEX idx_reminders_status_time ON reminders(status, remind_at)
    WHERE status = 'confirmed';

-- ============================================================
-- 3. 记忆表加扫描时间戳，防止无时间表达式的 promise 记忆被 LLM 反复扫描
-- ============================================================
ALTER TABLE memories
    ADD COLUMN reminder_scanned_at TIMESTAMPTZ;
