# AI-MES 智能生产车间管理平台

> 工业应用软件设计 · 题目二「工业生产车间智能管理智能体」  
> Spring Boot 3 + Vue 3 + MySQL + Redis + Coze 智能体

面向制造车间的 Web 管理系统，覆盖 **生产计划、工单派工、工序进度、班组协同、异常停机、缺料预警**，并接入 **Coze 智能客服**（必选）与 **智能排产工作流**（推荐）。

## 功能概览

| 模块 | 说明 | 角色 |
|------|------|------|
| 首页驾驶舱 | 计划/工单/异常/缺料 KPI、班组进度、预警列表 | 全部 |
| 生产计划 | 创建、编辑、下发，自动生成工单 | 管理员、主管 |
| 工单管理 | 新建、编辑、派工、筛选、进度、完工、删除 | 主管派工；员工认领执行 |
| 工序进度 | 工序看板（待认领 / 生产中 / 今日完工） | 管理员、员工 |
| 班组管理 | 班组与产线维护 | 管理员、主管 |
| 异常管理 | 设备/缺料/质量异常上报与处理 | 全部上报；主管处理 |
| 物料预警 | 缺料列表、库存更新、**新增物料** | 管理员、主管 |
| 消息通知 | WebSocket 实时推送（计划下发、异常、缺料、派工） | 按角色推送 |
| AI 智能客服 | Coze 对话，查进度/SOP/异常/班组任务 | 全部 |
| AI 智能排产 | 优先级、瓶颈、派发建议，可一键应用到工单 | 管理员、主管 |
| 系统管理 | 用户、角色权限、Coze 配置 | 管理员 |

其他能力：登录验证码与 IP 防刷、主题切换、个人中心、接口文档（Knife4j）。

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、ECharts |
| 后端 | Spring Boot 3.3、MyBatis-Plus、Sa-Token、WebSocket、MySQL 8、Redis |
| AI | Coze Bot API + Workflow API（密钥仅服务端保存） |
| 文档 | Knife4j → `http://localhost:8080/doc.html` |

## 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Redis | 6+ |
| Maven | 3.9+ |

默认连接：`localhost:3306` / 库名 `AI-MES` / `root` `root`；Redis `localhost:6379`。可在 `backend/src/main/resources/application.yml` 修改。

## 数据库

后端启动**不会**自动执行 SQL，需手动初始化。

### 全新安装

```bash
mysql -uroot -proot < sql/init.sql
```

`init.sql` 包含建库、建表及演示数据（用户、班组、计划、工单、物料、通知等）。

### 已有库增量升级

按编号顺序执行尚未应用过的脚本：

```
sql/migrations/
├── 001_add_sys_coze_config.sql      # Coze 配置表
├── 002_add_sys_role_permission.sql  # 角色权限表
├── 003_add_sys_notification.sql     # 系统通知表（新库可跳过，init 已含）
├── 004_add_user_avatar.sql          # 用户头像字段
├── 005_dedupe_sys_notification.sql  # 清理重复通知（可选）
├── 006_add_foreign_keys.sql         # 核心业务外键约束
└── 009_cleanup_orphan_exception_events.sql  # 清理已删工单遗留的异常记录
```

PowerShell 示例：

```powershell
Get-Content sql\migrations\005_dedupe_sys_notification.sql | mysql -uroot -proot -D AI-MES
```

## 快速启动

### 1. 后端

**方式 A：开发模式（推荐调试）**

```bash
cd backend
mvn spring-boot:run
```

**方式 B：读取 `.env` 后打包运行（推荐联调 Coze）**

```powershell
# 项目根目录
.\scripts\start-backend-dev.ps1
# 或生产式启动
.\scripts\start-backend.ps1
```

- API：`http://localhost:8080`
- 接口文档：`http://localhost:8080/doc.html`

### 2. 前端

```bash
cd frontend
npm install
npm run dev
```

- 访问：`http://localhost:3088`
- 开发环境通过 Vite 将 `/api`、`/ws` 代理到 `8080`

根目录也可使用：

```bash
npm run dev:backend   # 后端
npm run dev:frontend  # 前端
```

### 3. 演示账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | 123456 | 管理员 |
| supervisor | 123456 | 车间主管 |
| worker | 123456 | 普通员工 |

## Coze 配置

未配置 Token 时，AI 客服与排产会进入**本地演示模式**（可跑通页面，但答辩/录屏建议配置真实 Coze）。

1. 复制项目根目录 `.env.example` 为 `.env`
2. 填写扣子控制台获取的参数：

```env
COZE_API_TOKEN=pat_xxxxxxxx
COZE_BOT_ID=你的Bot_ID
COZE_WORKFLOW_ID=你的排产工作流ID
COZE_API_URL=https://api.coze.cn/v3
```

3. 使用 `.\scripts\start-backend-dev.ps1` 启动后端（自动加载 `.env`）
4. 管理员登录 → **系统管理 → Coze 配置**，可保存数据库配置并做连通性测试
5. 调用链：`前端 → POST /api/coze/chat` → Coze → 回显`；排产同理 `/api/coze/scheduling`

> Token 勿提交 Git。Bot 人设、知识库、工作流说明见 [docs/coze智能体的人设提示词.txt](docs/coze智能体的人设提示词.txt)

## 消息通知（WebSocket）

系统通知通过 **WebSocket** 实时推送到前端，登录后自动连接，新通知即时更新顶栏铃铛角标并弹出提示。

| 项目 | 说明 |
|------|------|
| 端点 | `ws://localhost:8080/ws/notifications?token=<登录Token>` |
| 鉴权 | 握手时携带 Sa-Token（query 参数 `token`） |
| 推送时机 | 计划下发、工单派工、缺料预警、异常上报等业务创建通知后 |
| 前端实现 | `frontend/src/stores/notifications.ts` |
| 后端实现 | `backend/src/main/java/com/aimes/websocket/` |

列表查询、标记已读仍走 REST：`GET /api/notifications`、`PUT /api/notifications/{id}/read`。

开发环境下 Vite 已将 `/ws` 代理到后端；生产部署时需在 Nginx 中配置 WebSocket 转发（参考 `deploy/nginx.conf`）。

## 项目结构

```
AI-MES/
├── backend/              # Spring Boot 后端（含 WebSocket 通知推送）
├── frontend/             # Vue 3 前端（端口 3088）
├── sql/
│   ├── init.sql          # 全量初始化
│   └── migrations/       # 增量脚本
├── scripts/              # 启动脚本（加载 .env）
├── deploy/               # Nginx 配置示例
├── docs/                 # 设计文档、题目说明、Coze Prompt
├── .env.example
└── README.md
```

## 生产部署（简要）

```bash
cd frontend && npm run build          # 产出 frontend/dist
cd backend && mvn clean package -DskipTests   # 产出 target/ai-mes-backend-1.0.0.jar
```

将 `dist` 静态资源与后端 JAR 部署到服务器，参考 `deploy/nginx.conf` 配置 Nginx 反向代理。

## 文档

| 文档 | 说明 |
|------|------|
| [题目二需求与评分对照](docs/题目2-工业生产车间智能管理智能体.md) | 课程考核要求 |
| [系统设计文档](docs/设计文档.txt) | 架构、库表、接口 |
| [前端 UI 设计文档](docs/前端UI设计文档.md) | 页面与组件规范 |
| [F-12 设备管理模块设计](docs/F-12-设备管理模块设计文档.md) | 设备台账、状态监控、异常/驾驶舱/排产互通 |
| [Coze 智能体提示词](docs/coze智能体的人设提示词.txt) | Bot / 工作流配置 |

## 课程提交提醒

除源码外，试卷还要求：**≥3000 字报告**、**3～5 分钟演示视频**、**Coze 配置与 API 调用佐证**。命名规范见 `docs/题目2-工业生产车间智能管理智能体.md`。
