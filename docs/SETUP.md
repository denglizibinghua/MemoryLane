# 安装指南 · 从零到跑起来

> 针对国内网络 + 非开发者朋友。全程不需要写代码，复制粘贴即可。

---

## 前置准备：装 Docker Desktop

Docker 就像一个轻量虚拟机，把 MemoryLane 需要的一切（数据库、Java、AI 模型）打包好，你只需要装这一个东西。

### Windows

1. 打开 [Docker Desktop 下载页](https://www.docker.com/products/docker-desktop/)（国内可直接访问）
2. 下载 Windows 版（约 600 MB），双击安装
3. 安装过程一路「下一步」，中途会提示重启电脑——照做
4. 重启后 Docker Desktop 自动启动，右下角托盘出现鲸鱼图标 🐳

> ⚠️ 如果提示「需要开启 Hyper-V」但你的系统是 Windows 家庭版：不用担心，Docker 会自动引导你安装 **WSL2**，跟着弹窗提示点确认就行，全程自动。

### macOS

1. 打开 [Docker Desktop 下载页](https://www.docker.com/products/docker-desktop/)
2. 根据芯片选版本：Apple Silicon 选 **Apple Chip**，Intel 选 **Intel Chip**
3. 双击 `.dmg` 文件，把 Docker 拖进 Applications
4. 首次打开会要求授权，输入开机密码即可

---

## 配置镜像加速（国内必做）

直接拉镜像会很慢，配一下国内镜像源：

1. Docker Desktop 右上角齿轮 ⚙️ → **Docker Engine**
2. 把里面的内容**全部替换**成下面这段：

```json
{
  "builder": {
    "gc": {
      "defaultKeepStorage": "20GB",
      "enabled": true
    }
  },
  "experimental": false,
  "registry-mirrors": [
    "https://docker.1panel.live",
    "https://hub.rat.dev"
  ]
}
```

3. 点 **Apply & Restart**，等 Docker 重启完

---

## 下载项目

1. 打开 https://github.com/denglizibinghua/MemoryLane
2. 点绿色 **<> Code** 按钮 → **Download ZIP**
3. 把下载的 `MemoryLane-master.zip` 解压到任意位置（桌面即可）

解压后你会看到一个 `MemoryLane-master` 文件夹，里面长这样：

```
MemoryLane-master/
├── docker-compose.yml
├── .env.example
├── server/
├── web/
├── README.md
└── ...
```

---

## 配置 AI API Key

MemoryLane 需要 AI 才能分析和生成回复。先搞一个 API Key：

| 推荐 | 平台 | 注册地址 | 费用 |
|------|------|---------|------|
| ⭐ | **DeepSeek** | [platform.deepseek.com](https://platform.deepseek.com) | 充值 10 块能用很久 |
| | 通义千问（DashScope）| [dashscope.aliyun.com](https://dashscope.aliyun.com) | 有免费额度 |
| | OpenAI | [platform.openai.com](https://platform.openai.com) | 需国外手机号 |

拿到 Key 后：

1. 打开解压后的 `MemoryLane-master` 文件夹
2. 找到 `.env.example`，复制一份，**改名为 `.env`**（注意：就是把 `.example` 删掉）
3. 用记事本打开 `.env`，找到这一行：

```
LLM_API_KEY=sk-your-api-key-here
```

把 `sk-your-api-key-here` 替换成你拿到的 Key，比如：

```
LLM_API_KEY=sk-abc123def456
```

保存关闭。

---

## 启动

1. 打开 `MemoryLane-master` 文件夹
2. 在地址栏点一下，输入 `cmd`，按回车（会弹出一个黑窗口）
3. 在黑窗口里输入：

```bash
docker compose up -d
```

4. 等 3-5 分钟（首次要下载和编译，之后每次启动秒开）
5. 看到三个 `Started` 就成功了：

```
✔ Container memorylane-db        Started
✔ Container memorylane-backend   Started
✔ Container memorylane-frontend  Started
```

6. 打开浏览器，输入：**http://localhost:3000**

---

## 开始使用

打开页面后：

1. **粘贴聊天记录** — 从微信/QQ/抖音/短信复制任意聊天，粘贴进输入框 → 点导入
2. **等记忆生成** — 切到「记忆库」页面，几秒后刷新就能看到 AI 提炼的记忆
3. **搜索** — 搜「发烧」「生日」等关键词，精准定位
4. **军师模式** — 模拟聊天，AI 逐句建议怎么回复，支持 6 种风格
5. **AI 设置** — 进入「设置」页面，可以切换 AI 平台、改模型、自定义 Prompt

> 💡 建议先导入 `test-conversations.txt`（项目自带的测试对话），感受完整流程。

---

## 常见问题

### Docker Desktop 启动后一直转圈

等 2-3 分钟，它在后台启动 Linux 虚拟机。如果超过 5 分钟还是转圈：

- Windows：在开始菜单搜「启用或关闭 Windows 功能」，确认「Hyper-V」和「适用于 Linux 的 Windows 子系统」都勾上了
- macOS：确认系统版本 ≥ macOS 11

### 提示「端口被占用」

关掉本地的 Postgres 或其他占用了 5432/8080/3000 端口的程序。

### docker compose up 报错「no configuration file provided」

确认你在 `MemoryLane-master` 文件夹里打开的 cmd。黑窗口里输入 `dir`，应该能看到 `docker-compose.yml`。

### 导入聊天后没有自动生成记忆

检查 AI Key 是否填对：打开「设置」页面 → 确认 AI 平台和 Key 正确 → 点「测试连接」。

### 如何停止

```bash
docker compose down
```

数据保留在 Docker 卷里，下次 `docker compose up -d` 数据还在。

### 如何彻底删除（包括数据）

```bash
docker compose down -v
```

---

## 关闭和重启

```bash
# 关闭（数据保留）
docker compose down

# 重启
docker compose up -d

# 重启并重新构建（更新代码后）
docker compose up -d --build
```
