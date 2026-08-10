# MemoryLane · 忆路

> 跨平台聊天记录 AI 记忆分析工具 —— 粘贴聊天文本，AI 自动识人、归档、记忆，帮你记住每段关系里的一切。

[![License](https://img.shields.io/badge/license-AGPL--3.0-green)](LICENSE)
[![Status](https://img.shields.io/badge/status-pre--alpha-orange)]()

---

## ✨ 做什么的

从微信、QQ、抖音、短信等任何平台复制聊天记录，粘贴进来——AI 自动：

- 🧑‍🤝‍🧑 **区分说话人**，识别平台格式
- 🗂️ **结构化存储**到数据库，按联系人/会话归类
- 🧠 **提炼长期记忆**：约定、偏好、个人信息、关系动态
- 🔍 **智能检索**：问「张三喜欢吃什么」，从历史聊天里找答案
- 💬 **辅助回复建议**（规划中）：基于历史上下文生成自然回复

> ⚠️ 本项目不需要破解任何 app 的加密数据库。所有数据由用户主动粘贴或截图上传，隐私可控。

---

## 🏗️ 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.x + Spring AI |
| 数据库 | MySQL（结构化存储）+ PostgreSQL + pgvector（向量检索） |
| 前端 | Vue 3 + Element Plus + Vite |
| AI | 通义千问 / DeepSeek（可替换） |
| 部署 | Docker Compose |

---

## 🚧 开发状态

**pre-alpha** —— 架构设计阶段，代码尚未开始编写。

当前进度：
- [x] 竞品调研（WeLink、ChatLab、ex-ai 等）
- [x] 数据库模型设计
- [x] 技术选型
- [ ] 项目初始化
- [ ] 核心管线 MVP

---

## 📄 License

AGPL-3.0 — 使用本代码的项目必须同样以 AGPL-3.0 开源。
