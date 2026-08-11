# MemoryLane · 忆路

> 跨平台聊天记录 AI 记忆分析工具 —— 粘贴聊天文本，AI 自动识人、归档、记忆，帮你记住每段关系里的一切。

[![License](https://img.shields.io/badge/license-AGPL--3.0-green)](LICENSE)
[![Status](https://img.shields.io/badge/status-v0.1.2-blue)]()

---

## ✨ 做什么的

从微信、QQ、抖音、短信等任何平台复制聊天记录，粘贴进来——AI 自动：

- 🧑‍🤝‍🧑 **区分说话人**，自动识别微信/QQ/抖音/短信格式
- 🗂️ **结构化存储**到 Postgres，按联系人/会话归类，SHA-256 去重
- 🧠 **提炼长期记忆**：约定、偏好、个人信息、事件、人设、关系动态（6 类）
- 🔍 **全文搜索**：CJK 字级分词 + tsvector 检索，搜「发烧」能命中「lim 发烧刚好」
- 📇 **联系人管理**：多选合并去重、profile 深合并、级联删除
- 💬 **军师模式**：选联系人 + 输入场景 → LLM 基于历史记忆生成回复建议

> ⚠️ 本项目不需要破解任何 app 的加密数据库。所有数据由用户主动粘贴，隐私可控。

---

## 🏗️ 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.4 + Spring AI 1.0.0-M6 |
| 数据库 | PostgreSQL 16 + pgvector + tsvector（全功能单库） |
| 前端 | Vue 3 + Element Plus + Vite |
| AI | OpenAI / DeepSeek / Ollama / Anthropic / DashScope / ZhiPu / Moonshot（DB 热切换） |
| 部署 | Docker Compose |

---

## 🚀 快速开始

```bash
# 数据库
docker run -d --name memorylane-db -p 5432:5432 \
  -e POSTGRES_DB=memorylane -e POSTGRES_USER=memorylane -e POSTGRES_PASSWORD=memorylane123 \
  pgvector/pgvector:pg16

# 后端（IDEA → File → Open → server/pom.xml → Run MemoryLaneApplication）
# 端口：8080，JDK 17

# 前端
cd web
npm install --legacy-peer-deps
npm run dev
# 端口：3000，代理 /api → localhost:8080
```

---

## 📋 当前进度（v0.1.2）

- [x] 竞品调研（WeLink、ChatLab、Graphiti、Mem0）
- [x] 架构设计文档（724 行，覆盖 DDL/API/产品决策）
- [x] 项目脚手架：Spring Boot 3.4 + Vue 3 + Docker Compose
- [x] 7 种 AI provider：DB 配置热切换，无需重启
- [x] 文本导入管线：格式识别 → 说话人提取 → 去重入库
- [x] 记忆提取：@Async 异步 → LLM 重要性分类 → 6 类结构化记忆 → 3 层合并
- [x] 全文搜索：CJK 字级分词 + tsvector，支持按联系人过滤
- [x] 联系人管理：多选合并、profile 深合并、级联删除
- [x] 记忆详情：点击展开源消息气泡
- [x] 军师模式：基于历史记忆的 LLM 回复建议 + 新话题推荐

### 🔜 下一步

- [ ] pgvector 语义搜索（待 Docker 镜像安装）
- [ ] 截图 OCR 导入
- [ ] 约定自动提醒
- [ ] 关系动态面板

---

## 📂 项目结构

```
MemoryLane/
├── server/                          # Spring Boot 后端
│   └── src/main/java/com/memorylane/
│       ├── adapter/                 # 输入适配器（文本粘贴）
│       ├── parser/                  # 平台格式识别 + 说话人提取
│       ├── memory/                  # 记忆引擎（重要性分类 + 事实提取 + 合并）
│       ├── retrieval/               # 检索层（全文 + 语义 + 混合排序）
│       ├── service/                 # 业务服务（联系人合并、AI 设置、军师）
│       ├── controller/              # REST API
│       ├── config/                  # Spring AI 配置 + 动态模型工厂
│       ├── entity/                  # JPA 实体
│       ├── repository/              # Spring Data Repositories
│       └── dto/                     # 请求/响应 DTO
├── web/                             # Vue 3 前端
│   └── src/
│       ├── views/                   # 页面（首页、联系人、记忆库、军师、设置）
│       ├── stores/                  # Pinia 状态管理
│       ├── api/                     # axios API 封装
│       └── router/                  # 路由配置
├── docs/ARCHITECTURE.md             # 架构设计文档
└── docker-compose.yml               # Docker 部署
```

---

## 📄 License

AGPL-3.0 — 使用本代码的项目必须同样以 AGPL-3.0 开源。
