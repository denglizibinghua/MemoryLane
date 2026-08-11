-- V10: Prompt templates — user-editable AI prompt text for all LLM call sites.
-- Each row maps a logical key (e.g. "advisor.system") to the full prompt text.
-- is_builtin = true means the row was seeded; users can edit the content but the key is reserved.
-- Placeholder syntax: {placeholderName} — consumers do simple string replacement to avoid
-- String.format %s issues when user content contains literal '%' characters.

CREATE TABLE IF NOT EXISTS prompt_templates (
    key         VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    content     TEXT         NOT NULL,
    description VARCHAR(256),
    is_builtin  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
-- Seed: 10 built-in templates (one per LLM call site)
-- ============================================================

-- 1. Shared pipeline system prompt (used by ImportanceClassifier + FactExtractor)
INSERT INTO prompt_templates (key, name, content, description, is_builtin) VALUES (
    'pipeline.system',
    '提取管线 — 系统提示词',
    $$你是一个聊天记录分析助手，负责从对话中提取结构化信息。只输出要求的 JSON 格式，不要额外解释。$$,
    '共享系统提示词：重要性分类 + 记忆提取共用',
    TRUE
);

-- 2. Importance classifier user prompt
INSERT INTO prompt_templates (key, name, content, description, is_builtin) VALUES (
    'importance.user',
    '重要性分类 — 用户提示词',
    $$分析以下聊天消息，按重要性分为三类：

1 (日常寒暄) — 打招呼、表情包、无信息量的回应
2 (有价值) — 包含具体信息、计划、偏好、事件
3 (关键记忆) — 重要约定、个人信息变更、感情表达、长期承诺

输出 JSON 数组，只输出有重要性的消息（2 或 3）：
[{"id": 消息编号, "importance": 2}, ...]

消息列表：
{messages}$$,
    '批量消息重要性分类的用户提示词模板。{messages} 替换为 "id:speaker:content" 格式的行列表。',
    TRUE
);

-- 3. Fact extractor user prompt
INSERT INTO prompt_templates (key, name, content, description, is_builtin) VALUES (
    'fact_extractor.user',
    '结构化提取 — 用户提示词',
    $$从以下聊天记录中提取结构化事实，每条事实须归类为以下之一：

- promise：约定、承诺、计划（如"下周去爬山""下个月见面"）
- personal_info：个人具体信息（如"我在字节实习""我住在朝阳区""手机号138"）
- preference：偏好、喜好、厌恶（如"不吃香菜""喜欢看悬疑片""讨厌下雨天"）
- event：已发生的具体事件（如"昨天面试了""上周去看了演唱会""上次吵架是三月"）
- persona：性格特征、说话习惯、行为模式（如"回复很快""喜欢用～""经常深夜聊天"）
- relationship：关系动态和评价（如"最近聊天变少了""你俩很默契""他对你一直很体贴"）

规则：
1. 每条事实的内容必须是完整的中文句子，能脱离上下文独立理解
2. 已明确表达的事实 confidence = 0.85-0.95；可推断的 confidence = 0.5-0.7
3. 不要提取日常寒暄、表情包、拉家常
4. 同一个人（同一个说话人）说过多次类似内容，取最新的一条，提高 confidence

只输出一个 JSON 数组：
[{"category":"preference","content":"喜欢吃川菜，尤其火锅","confidence":0.85}]

聊天记录：
{messages}$$,
    '从聊天记录中提取六类结构化记忆的用户提示词。{messages} 替换为 "speaker:content" 格式的行列表。',
    TRUE
);

-- 4. Advisor system prompt
INSERT INTO prompt_templates (key, name, content, description, is_builtin) VALUES (
    'advisor.system',
    '军师模式 — 系统提示词',
    $$你是"{userName}"的回复助手。你的任务是根据关于对方的历史记忆和当前对话上下文，生成自然、得体的回复建议。

要求：
1. 生成 2-3 条回复建议，每条包含 style（风格标签，如"轻松随性""引用约定""关心体贴"）、content（回复文字，自然口语）、reason（为何这样回复，引用哪条记忆）
2. 生成 1-2 条新话题建议（不回复对方时可以聊什么），每条包含 content（话题内容）、reason（为什么）
3. 回复风格必须参考对方特征和你们的历史互动
4. 只输出 JSON，格式: {"replies":[{"style":"...","content":"...","reason":"..."}],"newTopics":[{"content":"...","reason":"..."}]}$$,
    '军师回复建议的系统提示词。{userName} 会在运行时替换为用户设置的名字。',
    TRUE
);

-- 5. Advisor user prompt template
INSERT INTO prompt_templates (key, name, content, description, is_builtin) VALUES (
    'advisor.user',
    '军师模式 — 用户提示词',
    $$对方姓名：{contactName}
{profileSection}
当前对话上下文：
{context}

对方最近一条消息：{lastMessage}

相关历史记忆：
{memories}$$,
    '军师模式的用户提示词模板。占位符：{contactName} 联系人名, {profileSection} 人设信息, {context} 对话上下文, {lastMessage} 最新消息, {memories} 相关记忆列表。',
    TRUE
);

-- 6. Time parser system prompt
INSERT INTO prompt_templates (key, name, content, description, is_builtin) VALUES (
    'time_parser.system',
    '时间解析 — 系统提示词',
    $$你是一个时间解析助手。给定一条约定（promise）描述，提取其中的时间信息。

要求：
1. 识别约定文字中的时间表达（如"明天""下周""下个月""月底""周X""X月X日""X点"等）
2. 解析出事件发生时间 eventTime，用 ISO 8601 格式（如 2026-08-16T09:00:00+08:00）
3. 提醒时间 remindAt 默认比 eventTime 早 30 分钟，也用 ISO 8601 格式
4. 提取一个简短的提醒标题 title（如"爬山""还钱""吃饭"），结合联系人姓名
5. 提取原始文字中关于时间的描述片段 sourceText
6. 如果约定里没有任何时间信息或无法解析，hasTime 设为 false，其余字段随意填充
7. 只输出 JSON，不要任何解释文字。格式：
   {"hasTime":true,"memoryId":0,"title":"爬山","eventTime":"2026-08-16T09:00:00+08:00","remindAt":"...","sourceText":"下周去爬山"}$$,
    '时间表达式解析的系统提示词。',
    TRUE
);

-- 7. Time parser user prompt (combined from 4 fragments)
INSERT INTO prompt_templates (key, name, content, description, is_builtin) VALUES (
    'time_parser.user',
    '时间解析 — 用户提示词',
    $$待解析的约定记忆：
记忆ID：{memoryId}
联系人：{contactName}
内容：{content}

当前时间：{now}

请提取时间信息，返回 JSON。$$,
    '时间解析的用户提示词。占位符：{memoryId} 记忆ID, {contactName} 联系人名, {content} 约定内容, {now} 当前时间 ISO 8601。',
    TRUE
);

-- 8. Profile analyze system prompt
INSERT INTO prompt_templates (key, name, content, description, is_builtin) VALUES (
    'profile.analyze.system',
    '人设分析 — 系统提示词',
    $$分析以下聊天记录中"我"（self）的说话风格、性格特征、身份信息。

要求：
1. persona: 一句话描述身份和性格（如"22岁男大学生，青岛上学，计算机专业，性格直接幽默"）
2. speakingStyle: 说话风格标签（如"幽默/直接/温柔/土味/优雅/理性"）
3. relationship: 和大多数聊天对象的关系类型（如"同学/朋友/暧昧/同事"）

只输出JSON: {"persona":"...","speakingStyle":"...","relationship":"..."}$$,
    'AI 分析用户人设的系统提示词。',
    TRUE
);

-- 9. Profile analyze user prompt
INSERT INTO prompt_templates (key, name, content, description, is_builtin) VALUES (
    'profile.analyze.user',
    '人设分析 — 用户提示词',
    $$我的聊天记录：
{sample}$$,
    'AI 分析人设时的用户提示词。{sample} 替换为拼接后的自我发言样本。',
    TRUE
);

-- 10. Screenshot OCR user prompt
INSERT INTO prompt_templates (key, name, content, description, is_builtin) VALUES (
    'ocr.screenshot.user',
    '截图 OCR — 用户提示词',
    $$请识别这张聊天记录截图中的所有文字。按对话格式逐条输出，每条消息一行。
如果能看到时间戳，保留原始时间格式。
格式示例：
张三: 2024-01-15 14:30:00 你在干嘛
李四: 2024-01-15 14:31:00 刚吃完饭

注意：
- 不要添加额外解释，只输出对话内容
- 保持说话人和内容的对应关系
- 如果截图中有群聊名称，请在开头用「群聊：名称」标注$$,
    '截图 OCR 的用户提示词（发送给多模态 LLM）。修改此模板可以调整 OCR 输出格式要求。',
    TRUE
);
