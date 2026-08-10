# MemoryLane 架构设计

> 最后更新：2026-08-10 | 状态：设计讨论稿，待评审

---

## 0. 设计原则

从 WeLink、ChatLab、Mem0、Graphiti 四个成熟项目的架构中提炼：

| 原则 | 来源 | 说明 |
|---|---|---|
| **数据入口与核心管线解耦** | WeLink 的教训 | DB解密是死路，文本粘贴/截图上传应作为可插拔的输入适配器 |
| **记忆即一等公民** | Mem0 + Graphiti | 不是"聊天记录搜索工具"，而是"从聊天中提取、演化、检索长期记忆" |
| **单数据库原则** | WeLink (SQLite) 的简洁 | 不要 MySQL + Postgres 双数据库，用 Postgres 搞定一切 |
| **模块化 Labs 模式** | WeLink 21个Labs | 核心管线做稳后，新功能以独立 Lab 形式快速迭代 |
| **本地优先，隐私可控** | ChatLab + WeLink | 所有数据留在本地，LLM 调用可选纯离线 |

---

## 1. 技术选型（进化版）

### 1.1 最初方案 vs 进化后

| 层 | 最初方案 | 进化后 | 为什么改 |
|---|---|---|---|
| 数据库 | MySQL（结构）+ Postgres（向量） | **Postgres only** | 双数据库运维太重；Postgres 的 tsvector 做全文检索、pgvector 做语义检索、JSONB 做半结构化，一个库全搞定 |
| 全文检索 | 无 | **Postgres tsvector** | WeLink 用 FTS5 做关键词搜索，效果很好；我们 pg 原生支持 |
| 向量检索 | pgvector | pgvector（不变） | — |
| AI 框架 | Spring AI | Spring AI（不变） | Function Calling 天然适合"AI Agent 调工具"的模式 |
| 前端 | Vue 3 + Element Plus | Vue 3 + Element Plus（不变） | — |
| 部署 | Docker Compose | Docker Compose（简化） | Postgres 单容器替代 MySQL+PG 双容器 |

### 1.2 为什么不用 SQLite

WeLink 用 SQLite 很聪明——单文件、零配置、打包进 Go binary。但我们的场景不同：
- Spring Boot 生态天然适合 Postgres（JDBC 驱动、连接池、Flyway 迁移）
- 多表关联查询、JSONB、全文检索、向量检索——SQLite 每样都要装扩展
- Postgres 在 Docker Compose 里也就是一行 `image: pgvector/pgvector:pg16`

---

## 2. 系统架构

```
┌────────────────────────────────────────────────────────────┐
│                      用户浏览器                             │
│            Vue 3 app → localhost:3000                       │
└────────────────────────┬───────────────────────────────────┘
                         │ REST + SSE
                         ▼
┌────────────────────────────────────────────────────────────┐
│                Spring Boot 3.x (localhost:8080)             │
│                                                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ 输入适配器│  │ 记忆引擎 │  │ 检索服务 │  │ 建议引擎 │  │
│  │ Adapter  │  │ Memory   │  │ Retrieval│  │ Suggestion│  │
│  │ (v1:文本) │  │ Engine   │  │ Service  │  │ (v2)     │  │
│  │ (v2:截图) │  │          │  │          │  │          │  │
│  └─────┬────┘  └────┬─────┘  └────┬─────┘  └──────────┘  │
│        │            │             │                        │
│        └────────────┼─────────────┘                        │
│                     ▼                                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Spring AI (Function Calling)             │  │
│  │  · 格式识别 Tool   · 身份匹配 Tool                    │  │
│  │  · 记忆提取 Tool   · 记忆检索 Tool                    │  │
│  │  · 重要性分类 Tool · 上下文压缩 Tool                  │  │
│  └──────────────────────┬───────────────────────────────┘  │
│                         │                                   │
└─────────────────────────┼───────────────────────────────────┘
                          │
                          ▼
┌────────────────────────────────────────────────────────────┐
│                PostgreSQL 16 + pgvector                     │
│                                                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ contacts │  │conversat.│  │ messages │  │ memories │  │
│  │ 联系人   │  │  会话    │  │ 原始消息 │  │ 结构化记忆│  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
│                                                            │
│  索引: tsvector (全文) + pgvector (语义) + B-tree (排序)    │
└────────────────────────────────────────────────────────────┘
```

---

## 3. 模块划分

```
src/main/java/com/memorylane/
├── adapter/                     # 输入适配器层（可插拔）
│   ├── InputAdapter.java        # 接口：统一输入格式
│   ├── TextPasteAdapter.java    # v1: 文本粘贴解析
│   └── ScreenshotAdapter.java   # v2: 截图 OCR（规划）
│
├── parser/                      # 解析层
│   ├── PlatformDetector.java    # 自动识别平台格式
│   ├── SpeakerExtractor.java    # 提取说话人
│   └── MessageNormalizer.java   # 标准化时间/内容格式
│
├── memory/                      # 记忆引擎（核心）
│   ├── MemoryEngine.java        # 记忆主控
│   ├── ImportanceClassifier.java # 消息重要性分类（3层）
│   ├── FactExtractor.java       # 从消息提取结构化事实
│   ├── MemoryMerger.java        # 新记忆与旧记忆合并/冲突
│   └── ContextCompressor.java   # 上下文压缩（摘要生成）
│
├── retrieval/                   # 检索层
│   ├── SearchService.java       # 统一检索入口
│   ├── FullTextSearch.java      # tsvector 全文检索
│   ├── SemanticSearch.java      # pgvector 语义检索
│   └── HybridRanker.java        # 混合排序（RRF 融合）
│
├── suggestion/                   # 建议引擎（v2）
│   └── ReplySuggester.java      # 基于上下文的回复建议
│
├── agent/                        # Spring AI Agent 配置
│   ├── MemoryTools.java          # Function Calling 工具注册
│   └── AgentConfig.java          # Agent 编排配置
│
├── config/                       # 配置
│   ├── PostgresConfig.java
│   └── AiConfig.java
│
└── controller/                   # REST API
    ├── ImportController.java     # 数据导入
    ├── ContactController.java    # 联系人管理
    ├── MemoryController.java     # 记忆查询
    ├── SearchController.java     # 全文+语义搜索
    └── ChatController.java       # AI 对话（SSE 流式）
```

---

## 4. 数据库设计（Postgres-only）

### 4.1 ER 图

```
┌──────────┐       ┌──────────────┐       ┌──────────────┐
│ contacts │       │ conversations│       │   messages   │
├──────────┤       ├──────────────┤       ├──────────────┤
│ id (PK)  │──1:N──│ id (PK)      │──1:N──│ id (PK)      │
│ name     │       │ contact_id   │       │ conv_id (FK) │
│ platform │       │ platform     │       │ speaker      │
│ avatar   │       │ msg_count    │       │ content      │
│ profile  │       │ first_msg_at │       │ raw_time     │
│ (JSONB)  │       │ last_msg_at  │       │ content_hash │
│ created  │       │ created_at   │       │ importance   │
└──────────┘       └──────────────┘       │ (enum:1-3)   │
                                           │ created_at   │
                                           └──────┬───────┘
                                                  │ 1:N (提取)
                                                  ▼
                                           ┌──────────────┐
                                           │   memories   │
                                           ├──────────────┤
                                           │ id (PK)      │
                                           │ contact_id   │
                                           │ category     │
                                           │ (enum)       │
                                           │ content      │
                                           │ confidence   │
                                           │ source_msg_ids│
                                           │ (BIGINT[])   │
                                           │ valid_from   │
                                           │ valid_until  │
                                           │ embedding    │
                                           │ (vector)     │
                                           │ fts          │
                                           │ (tsvector)   │
                                           │ created_at   │
                                           │ updated_at   │
                                           └──────────────┘
```

### 4.2 核心表 DDL

```sql
-- 联系人
CREATE TABLE contacts (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    platform    VARCHAR(20),              -- 'wechat' | 'qq' | 'douyin' | 'sms' | 'other'
    avatar      TEXT,
    profile     JSONB DEFAULT '{}',       -- { "gender": "男", "birthday": "2003-05", "tags": ["同学"] }
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- 会话（一个联系人可以有多个平台的多个会话）
CREATE TABLE conversations (
    id            BIGSERIAL PRIMARY KEY,
    contact_id    BIGINT REFERENCES contacts(id),
    platform      VARCHAR(20),
    message_count INT DEFAULT 0,
    first_msg_at  TIMESTAMPTZ,
    last_msg_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ DEFAULT now()
);

-- 原始消息
CREATE TABLE messages (
    id            BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT REFERENCES conversations(id),
    speaker       VARCHAR(50) NOT NULL,   -- 'self' | contact.name
    content       TEXT NOT NULL,
    raw_time      TIMESTAMPTZ,            -- 聊天记录里的原始时间
    content_hash  VARCHAR(64),            -- SHA-256，用于去重
    importance    SMALLINT DEFAULT 0,     -- 0=未分类, 1=日常寒暄, 2=有价值, 3=关键记忆
    created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX idx_messages_hash ON messages(content_hash);

-- 结构化记忆（从消息中提炼）
CREATE TYPE memory_category AS ENUM (
    'promise',      -- 约定/承诺（"下周去爬山"）
    'personal_info',-- 个人信息（"我住朝阳区"）
    'preference',   -- 偏好（"不吃香菜"）
    'event',        -- 事件（"昨天面试了"）
    'persona',      -- 人设特征（"说话喜欢用～"）
    'relationship', -- 关系动态（"最近吵架了"）
    'other'
);

CREATE TABLE memories (
    id              BIGSERIAL PRIMARY KEY,
    contact_id      BIGINT REFERENCES contacts(id),
    category        memory_category NOT NULL,
    content         TEXT NOT NULL,           -- AI 提炼后的记忆描述
    confidence      FLOAT DEFAULT 1.0,       -- 置信度 0-1
    source_msg_ids  BIGINT[],                -- 溯源：从哪些消息提取的
    valid_from      TIMESTAMPTZ,             -- 记忆生效时间
    valid_until     TIMESTAMPTZ,             -- NULL = 至今有效
    embedding       vector(1536),            -- pgvector 语义检索
    fts             tsvector,                -- 全文检索
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

-- 向量索引（IVFFlat，先建表后创建）
-- CREATE INDEX idx_memories_embedding ON memories 
--   USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 全文检索索引（自动维护，weight=A 最高权重）
CREATE INDEX idx_memories_fts ON memories USING GIN (fts);

-- 自动更新 tsvector 的触发器
CREATE FUNCTION memories_fts_trigger() RETURNS trigger AS $$
BEGIN
    NEW.fts := setweight(to_tsvector('simple', coalesce(NEW.content, '')), 'A');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_memories_fts 
    BEFORE INSERT OR UPDATE ON memories 
    FOR EACH ROW EXECUTE FUNCTION memories_fts_trigger();
```

### 4.3 关键索引策略

```sql
-- 消息时间范围查询
CREATE INDEX idx_messages_conv_time ON messages(conversation_id, raw_time DESC);

-- 记忆按联系人+分类检索
CREATE INDEX idx_memories_contact_cat ON memories(contact_id, category);

-- 记忆时效性过滤
CREATE INDEX idx_memories_valid ON memories(contact_id, valid_until) 
    WHERE valid_until IS NULL OR valid_until > now();
```

### 4.4 提醒表（约定自动提醒）

```sql
CREATE TABLE reminders (
    id              BIGSERIAL PRIMARY KEY,
    memory_id       BIGINT REFERENCES memories(id),  -- 关联的记忆
    contact_id      BIGINT REFERENCES contacts(id),
    title           VARCHAR(200) NOT NULL,           -- "和张三吃饭"
    remind_at       TIMESTAMPTZ NOT NULL,            -- 提醒时间（如提前30分钟）
    event_time      TIMESTAMPTZ,                     -- 约定本身的时间（如明天10点）
    status          VARCHAR(20) DEFAULT 'pending',   -- pending | triggered | dismissed
    source_text     TEXT,                            -- 原始消息片段
    created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_reminders_status_time ON reminders(status, remind_at)
    WHERE status = 'pending';
```

---

## 5. 核心数据流

### 5.1 文本粘贴导入流程

```
用户粘贴文本
    │
    ▼
┌─────────────────────────────┐
│ ① PlatformDetector          │
│ 正则匹配平台格式：            │
│ · 微信: "张三 下午 2:30"     │
│ · QQ: "张三 2024-03-15 14:30"│
│ · 通用: 按换行+冒号/空格分割  │
└──────────┬──────────────────┘
           ▼
┌─────────────────────────────┐
│ ② SpeakerExtractor          │
│ 提取每行的说话人+时间+内容    │
│ 输出: List<RawMessage>       │
└──────────┬──────────────────┘
           ▼
┌─────────────────────────────┐
│ ③ MessageNormalizer         │
│ · 时间标准化 → TIMESTAMPTZ   │
│ · 内容去重 → SHA-256 hash    │
│ · 说话人匹配已有联系人        │
└──────────┬──────────────────┘
           ▼
┌─────────────────────────────┐
│ ④ 批量入库                   │
│ · 新联系人 → INSERT contacts │
│ · 新会话 → INSERT conv       │
│ · 消息 → INSERT messages     │
│   (ON CONFLICT hash 跳过重复) │
└──────────┬──────────────────┘
           ▼
┌─────────────────────────────┐
│ ⑤ ImportanceClassifier (LLM) │
│ 批量判断每条消息的重要性：     │
│ · L1 日常寒暄 → 只计数       │
│ · L2 有价值 → 保留原文+摘要  │
│ · L3 关键记忆 → 永久保留原文 │
└──────────┬──────────────────┘
           ▼
┌─────────────────────────────┐
│ ⑥ FactExtractor (LLM)       │
│ 从 L2/L3 消息中提取结构化事实 │
│ 输出: List<Memory>           │
└──────────┬──────────────────┘
           ▼
┌─────────────────────────────┐
│ ⑦ PromiseDetector (LLM)     │
│ 如果 category = 'promise':   │
│ · 解析时间表达式 → 时间戳    │
│ · 计算提醒提前量              │
│ · 拆分场景：                  │
│   - 批量导入(>3条)：暂存     │
│   - 日常对话(≤3条)：立即弹出  │
└──────────┬──────────────────┘
           ▼
┌─────────────────────────────┐
│ ⑧ MemoryMerger              │
│ 新记忆与已有记忆合并：        │
│ · 语义相同 → 更新置信度      │
│ · 信息冲突 → valid_until 标记 │
│ · 全新信息 → INSERT          │
│ 生成 embedding + tsvector    │
│ → 如有待确认提醒 → INSERT    │
│   reminders (status=pending) │
└─────────────────────────────┘
```

### 5.2 记忆检索流程（用户提问时）

```
用户输入: "张三喜欢吃什么"
    │
    ▼
┌─────────────────────────────┐
│ ① Hybrid Search             │
│ 并行两路检索：               │
│ · tsvector @@ '张三 & 喜欢 & 吃'  │
│ · pgvector <=> embedding(question) │
└──────────┬──────────────────┘
           ▼
┌─────────────────────────────┐
│ ② RRF (Reciprocal Rank Fusion)│
│ score = 1/(k + rank_fts)    │
│       + 1/(k + rank_semantic)│
│ 取 Top-N                     │
└──────────┬──────────────────┘
           ▼
┌─────────────────────────────┐
│ ③ LLM 总结                  │
│ 将 Top-N 记忆 + 用户问题     │
│ 丢给 LLM 生成自然语言回答    │
│ "根据聊天记录，张三多次提到   │
│  喜欢吃川菜，尤其钟爱火锅..." │
└─────────────────────────────┘
```

---

## 6. 记忆引擎设计（核心差异化）

### 6.1 记忆分类体系

| 类别 | 示例 | 时效性 | 更新策略 |
|---|---|---|---|
| **promise**（约定） | "下周去爬山"、"下个月还你钱" | 约定时间过后自动降权 | 到时标记 expired |
| **personal_info**（个人信息） | "我在字节实习"、"手机号138xxxx" | 长期，但可能变更 | 新信息冲突时标记旧信息 valid_until |
| **preference**（偏好） | "不吃香菜"、"喜欢看悬疑片" | 较稳定 | 置信度累加/衰减 |
| **event**（事件） | "上周面试了字节"、"昨天去看了演唱会" | 发生后转为历史 | 不更新，只追加 |
| **persona**（人设） | "回复快"、"喜欢用叹号"、"经常深夜聊天" | 逐渐演变 | 定期重新采样评估 |
| **relationship**（关系动态） | "最近聊天变少了"、"之前每天聊现在一周一次" | 周期性更新 | 基于消息频率变化自动计算 |

### 6.2 记忆冲突策略

参考 Graphiti 的 bi-temporal 模型，不做物理删除，只标记有效期：

```
场景：用户先粘贴 "我住在朝阳区"，三个月后又粘贴 "搬到海淀了"

处理：
1. 旧记忆: content="住在朝阳区", valid_until=2026-08-10 (新信息的时间)
2. 新记忆: content="住在海淀区", valid_from=2026-08-10, valid_until=NULL
3. 检索时：默认只返回 valid_until IS NULL 的记忆
4. 历史查询时：可以看"过去住在哪"
```

### 6.3 置信度模型

```
每条记忆有一个 confidence (0.0 - 1.0)：

初始值：
  · 用户明确声明（"我的手机号是..."）     → 0.95
  · 从多次对话中推断（3次提到同一个偏好）  → 0.80
  · 单次对话中推断                         → 0.50

更新规则：
  · 再次被提及 → confidence += 0.1 (上限 0.95)
  · 3 个月未被提及 → confidence *= 0.9 (衰减)
  · 与新信息冲突 → 旧记忆 valid_until 标记，confidence 不变
```

---

## 7. API 设计

### 7.1 导入

```
POST /api/v1/import/text
Content-Type: application/json

{
  "contactName": "张三",        // 可选，不填则 AI 自动识别
  "platform": "auto",           // auto | wechat | qq | sms
  "content": "张三 下午 2:30\n周末去不去爬山？\n\n我 下午 2:31\n行啊，哪里集合？"
}

Response 202:
{
  "taskId": "uuid",
  "stats": {
    "newMessages": 2,
    "duplicates": 0,
    "memoriesExtracted": 0    // 异步处理中
  }
}
```

### 7.2 记忆查询

```
GET /api/v1/memories?contactId=1&category=preference

Response:
{
  "memories": [
    {
      "id": 42,
      "content": "喜欢吃川菜，尤其是火锅",
      "confidence": 0.85,
      "source": ["2024-03-15: 今天去吃火锅吧，好久没吃了", "2024-05-20: 川菜 yyds"],
      "validFrom": "2024-03-15T00:00:00Z",
      "validUntil": null
    }
  ]
}
```

### 7.3 AI 对话（SSE 流式）

```
POST /api/v1/chat/ask
Content-Type: application/json

{
  "contactId": 1,
  "question": "张三喜欢吃什么？"
}

Response (SSE):
data: {"type":"thinking","content":"正在搜索关于张三的记忆..."}
data: {"type":"memories","count":3}
data: {"type":"token","content":"根据"}
data: {"type":"token","content":"聊天"}
data: {"type":"token","content":"记录..."}
data: {"type":"done"}
```

### 7.4 搜索

```
GET /api/v1/search?q=爬山&contactId=1&mode=hybrid

Response:
{
  "results": [
    {
      "type": "memory",
      "content": "约定：周末去爬山（2024-08-10提出，待确认）",
      "score": 0.92
    },
    {
      "type": "message",
      "content": "张三: 周末去不去爬山？",
      "time": "2024-08-10T14:30:00Z",
      "score": 0.87
    }
  ]
}
```

---

## 8. 产品设计决策

### 8.1 批量导入 vs 日常对话：同一条管线，两个入口

**不需要区分。** 后端走的是完全相同的流程：粘贴文本 → AI 解析 → 结构化入库 → 提取记忆。

| 场景 | 用户心智 | 数据量 | 前端入口 | 响应期望 |
|---|---|---|---|---|
| 批量导入 | "把我跟张三三个月的聊天倒进来" | 几百条 | 大文本框 + [粘贴并分析] | 可以等几秒 |
| 日常对话 | "张三刚说了一句话" | 1-3 条 | 快捷输入框 (Alt+V) | 秒回 |

**两个入口，一条管线。** 用户自己决定什么时候用哪个，AI 不需要区分模式。

### 8.2 约定提醒：分场景触发

AI 从 `promise` 类记忆中自动检测时间表达式 → 解析为具体时间戳 → 弹出确认。

**分场景触发的智能策略：**

| 导入量 | 触发方式 |
|---|---|
| ≤ 3 条（日常对话） | **即刻弹出确认框**："检测到约定「明天10点吃饭」。设为日程提醒？" [是] [忽略] |
| > 3 条（批量导入） | **导入完成后统一列表**："检测到 4 个约定。[查看并设置提醒]" → 勾选确认 |
| 提醒时间 | 默认提前 30 分钟；用户可自定义提前量 |
| 提醒方式 | 浏览器通知 (Notification API) + 页面内 badge |

### 8.3 多人/群聊：数据模型天然支持

`contacts.type` 区分个体/群体，`conversations.type` 区分私聊/群聊。区别只在 AI 解析阶段——群聊需识别多人发言。

群聊的价值：**对方在群体中的社交风格 ≠ 私聊中 TA 对你的态度，这是两套独立画像。**

### 8.4 军师模式：回复建议 + 新话题建议合一

不是两个按钮，**一个「🧠 军师」按钮就够了。** 点一下同时出两段内容：

- **上段：回复建议**（2-3 条不同调性，基于记忆库中的历史上下文）
- **下段：新话题建议**（如果不想回复对方，AI 基于对方兴趣和近期盲区推荐话题）

回复建议标签示例：`[轻松随性] "有啊！想去哪？"` / `[引用约定] "终于约我了！爬山去不？"` / `[诚实留余地] "周末可能要加班...周日行吗"`

每条建议旁边带 📋 一键复制按钮。

---

## 9. API 设计（补充）

### 9.1 军师模式（回复建议 + 新话题）

```
POST /api/v1/advisor/suggest
Content-Type: application/json

{
  "contactId": 1,
  "lastMessage": "周末有空吗？",       // 对方最新一句话
  "recentContext": ["最近聊了爬山"]     // 可选，不传则自动拉最近消息
}

Response:
{
  "replies": [
    {
      "style": "轻松随性",
      "content": "有啊！想去哪？",
      "reason": "张三偏好直接沟通风格"
    },
    {
      "style": "引用约定",
      "content": "终于约我了！爬山去不？",
      "reason": "你们上周约过爬山，还没定日期"
    }
  ],
  "newTopics": [
    {
      "content": "他上次说想学吉他，问问他开始学了没",
      "reason": "2周前提到，之后再没聊过这个话题"
    }
  ]
}
```

### 9.2 提醒管理

```
GET  /api/v1/reminders?status=pending     # 待触发提醒列表
POST /api/v1/reminders/{id}/confirm       # 确认提醒（批量导入后用户勾选）
POST /api/v1/reminders/{id}/dismiss       # 忽略提醒
```

---

## 10. 迭代路线

> **边学边造**：学习和项目不冲突。学到 Spring AI 的 Function Calling → 直接落地到 Agent 模块；学到 pgvector → 直接落地到检索层。每个技术点学完就有对应的代码要写。

### v0.1 —— 文本粘贴 → 记忆检索闭环

```
✅ 项目骨架（Spring Boot + Vue 3 + Docker Compose）
✅ 文本粘贴导入 → 平台识别 → 结构化入库
✅ 联系人/会话/消息 CRUD
✅ 记忆提取（LLM 批量异步）
✅ 记忆检索（关键词 + 语义混合）
✅ 基础 Web UI（联系人列表 + 记忆浏览 + 简单搜索）
```

### v0.2

```
📋 截图 OCR 导入（多模态 LLM）
📋 军师模式（回复建议 + 新话题建议）
📋 约定自动提醒（promise → 日程提醒）
📋 关系动态面板（消息频率趋势）
📋 导出（Markdown / JSON）
```

### v0.3+

```
📋 多人关系图谱
📋 定时报告（"本周社交摘要"）
📋 MCP Server（让 Claude Code 直接查聊天记忆）
📋 Labs 创意模块（参考 WeLink 的高光瞬间、灵魂提问机等）
```

---

## 11. 部署架构

```yaml
# docker-compose.yml
services:
  db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: memorylane
      POSTGRES_USER: memorylane
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./db/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "5432:5432"

  backend:
    build: ./server
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/memorylane
      SPRING_AI_OPENAI_API_KEY: ${LLM_API_KEY}
    ports:
      - "8080:8080"
    depends_on:
      - db

  frontend:
    build: ./web
    ports:
      - "3000:80"
    depends_on:
      - backend

volumes:
  pgdata:
```

---

## 12. 从前辈那里"偷"来的关键设计

| 偷自 | 偷了什么 | 用在哪 |
|---|---|---|
| **WeLink** | 记忆提炼管线：LLM 批读 → 抽取事实 → 持久化，后续对话直接调记忆 | 整个 Memory 模块 |
| **WeLink** | Labs 模式：核心管线不难，难的是持续产出好玩的小功能 | 项目组织方式 |
| **WeLink** | 关系动态预测的四档判定 + 多维信号 | v0.2 的关系面板 |
| **ChatLab** | SQL + AI Agent 工具模式：Function Calling 让 LLM 自己选工具 | Agent 层设计 |
| **Graphiti** | bi-temporal 模型：不删旧记忆，标记 valid_until | 记忆冲突策略 |
| **Mem0** | ADD-only 提取 + entity linking：一次 LLM 调用全搞定 | FactExtractor |
| **Memobase** | 用户画像 Profile 设计：把零散记忆组织成结构化的用户画像 | contacts.profile JSONB |
| **ex-ai** | 截图 OCR 说话人分离：微信固定气泡布局（左=对方，右=自己） | ScreenshotAdapter (v2) |

---

## 13. 待讨论的开放问题

1. **LLM 成本控制**：批量导入时，每 100 条消息的 LLM 调用量预估多少 token？要不要做本地小模型降级？
2. **实时性 vs 批处理**：用户粘贴完是立刻等 AI 分析完，还是后台异步 + 通知？
3. **多租户**：MVP 要不要做多用户？（建议不做——先做单用户本地工具）
4. **移动端**：WeLink 做了移动端配对（扫码查看），我们要不要？优先级多高？
