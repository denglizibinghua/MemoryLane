-- MemoryLane: 清理开发数据，保留 schema 和配置
-- FK 安全顺序：先子表后父表

TRUNCATE TABLE messages        RESTART IDENTITY CASCADE;
TRUNCATE TABLE reminders       RESTART IDENTITY CASCADE;
TRUNCATE TABLE memories        RESTART IDENTITY CASCADE;
TRUNCATE TABLE conversations   RESTART IDENTITY CASCADE;
TRUNCATE TABLE contacts        RESTART IDENTITY CASCADE;
TRUNCATE TABLE user_profile     RESTART IDENTITY CASCADE;

-- 保留 ai_settings、prompt_templates（不清）

-- 验证
SELECT 'messages'       AS tbl, count(*) FROM messages
UNION ALL SELECT 'reminders',    count(*) FROM reminders
UNION ALL SELECT 'memories',     count(*) FROM memories
UNION ALL SELECT 'conversations',count(*) FROM conversations
UNION ALL SELECT 'contacts',     count(*) FROM contacts
UNION ALL SELECT 'user_profile',  count(*) FROM user_profile
UNION ALL SELECT 'ai_settings',  count(*) FROM ai_settings
UNION ALL SELECT 'prompt_templates', count(*) FROM prompt_templates;
