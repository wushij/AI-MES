# AI-MES 智能生产车间管理平台

> 工业应用软件设计 · 题目二「工业生产车间智能管理智能体」  
> Spring Boot 3 + Vue 3 + MySQL + Redis + Coze 智能体

面向离散制造车间的 Web 型 MES：覆盖 **计划下发 → 工单派工 → 工序认领与报工 → 异常闭环 → 缺料预警 → 驾驶舱看板**，并接入 **Coze 智能客服**与 **智能排产工作流**。

## 典型业务闭环

```
登录 → 首页驾驶舱总览 → 制定/下发生产计划 → 自动生成工单
→ 主管派工至班组 → 员工工序看板认领 → 更新进度 / 上报异常
→ 主管处理异常 → 工单完工 → AI 客服查询 / AI 排产辅助决策
```

计划下发、派工、缺料、异常等关键节点会通过 **WebSocket** 推送系统通知。

## 功能概览

| 模块 | 路由 | 主要能力 | 默认角色 |
|------|------|----------|----------|
| 首页驾驶舱 | `/dashboard` | 今日计划/在制/异常/缺料 KPI、班组进度图、近 7 日产出、最新异常与缺料列表 | 全部 |
| 生产计划 | `/plans` | 计划 CRUD、下发（`released` 后自动生成标准工序工单） | 管理员、主管 |
| 工单管理 | `/work-orders` | 新建、编辑、派工、筛选、进度维护、完工、删除（级联清理关联数据） | 主管管理；员工工单反馈 |
| 工单详情 | `/work-orders/:id` | 工单信息、工序记录、关联异常 | 主管、员工 |
| 工序进度 | `/process` | 看板：待认领 / 进行中 / 今日完工；认领、报工、跳转异常上报 | 员工 |
| 班组管理 | `/teams` | 班组、产线、成员规模维护 | 管理员、主管 |
| 异常管理 | `/exceptions` | 设备/缺料/质量/其他异常上报；主管处理闭环；管理员可删除记录 | 全部可上报；主管处理 |
| 物料预警 | `/materials` | 库存与安全库存对比、预警状态、补货更新、新增物料 | 管理员、主管 |
| 智能客服 | `/ai-chat` | 多轮对话、历史会话、角色快捷提问、停止生成；Coze 未配置时本地演示 | 全部 |
| AI 小窗 | 全局悬浮 | 任意业务页快速唤起 AI 助手（不含快捷提问条） | 全部 |
| 智能排产 | `/ai-scheduling` | 优先级/瓶颈/派工建议，支持一键应用到工单 | 管理员、主管 |
| 系统管理 | `/admin/*` | 用户管理、角色权限、Coze 配置与连通性测试 | 管理员 |
| 个人中心 | `/profile` | 头像、资料、修改密码 | 全部 |

菜单与路由按 **`sys_role_permission` 权限字符串** 控制；管理员在「角色管理」中可调整各角色可见菜单（管理员自身始终拥有全部权限）。

### 角色默认权限

| 角色 | 权限项 |
|------|--------|
| **管理员** `admin` | 全部 13 项 |
| **车间主管** `supervisor` | 生产计划、工单管理、班组、物料、排产、异常上报、AI 客服 |
| **普通员工** `worker` | 工序进度、工单反馈、异常上报、AI 客服 |

权限项完整列表：`生产计划`、`工单管理`、`班组`、`物料`、`排产`、`工序进度`、`工单反馈`、`异常上报`、`AI 客服`、`用户管理`、`角色管理`、`Coze 配置`、`系统配置`。

### 工单与异常状态

**工单：** `pending` 待分配 → `assigned` 待领取 → `producing` 生产中 → `exception` 异常处理中 → `done` 已完成  

**异常：** `open` 待处理 → `processing` 处理中 → `closed` 已关闭  

上报异常后，关联工单自动进入 `exception`，当前进行中的工序暂停；处理完成且恢复生产后工单回到 `producing`。

### 演示数据（`init.sql`）

| 类型 | 示例 |
|------|------|
| 班组 | 甲班（产线 A）、乙班（产线 B） |
| 计划 | PLAN-2026-001（已下发）、PLAN-2026-002（草稿） |
| 工单 | WO-2026-001（甲班生产中）、WO-2026-002（乙班待领）、WO-2026-003（待分配） |
| 异常 | EXC-2026-001（缺料）、EXC-2026-002（质量） |
| 物料预警 | 精密螺丝 M3、装配胶水（低于安全库存） |

标准工序链：**备料 → 装配 → 检测 → 包装**。

## AI 能力说明

| 能力 | 说明 |
|------|------|
| 智能客服页 | 对话历史列表、新对话、按角色展示快捷提问、Markdown 回复、回复中可停止 |
| 会话记忆 | **仅当前会话内**最近 5 轮问答注入 Prompt，支持「它/刚才那个工单」类追问；新对话不跨会话 |
| 数据分流 | 操作/SOP 类走 Coze 知识库；工单进度/概况/缺料等走后端实时查库注入 |
| Coze 配置 | 管理员可配置 Token、Bot ID、工作流 ID、欢迎语；支持健康检查 |
| 排产工作流 | 调用 Coze Workflow，解析优先级、瓶颈、派工建议并写回工单 |
| 演示模式 | 未配置 Coze 时客服与排产使用本地 Mock，便于无密钥环境跑通界面 |

## 其他系统能力

- **认证安全**：Sa-Token + Redis 会话持久化；登录验证码、IP 失败次数限制
- **消息通知**：顶栏铃铛、未读角标、单条已读、清除已读
- **界面体验**：侧边栏折叠、多主题色、车间摘要卡片、响应式布局
- **数据完整性**：核心业务表外键约束；删工单时级联清理工序、异常、相关通知
- **接口文档**：Knife4j 在线调试 → `http://localhost:8080/doc.html`

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、ECharts |
| 后端 | Spring Boot 3.3、MyBatis-Plus、Sa-Token（Redis）、WebSocket、MySQL 8、Redis |
| AI | Coze Bot API v3 + Workflow API（密钥仅存服务端 / `.env`） |
| 文档 | Knife4j |

## 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Redis | 6+ |
| Maven | 3.9+ |

默认：`localhost:3306` / 库名 `AI-MES` / `root` `root`；Redis `localhost:6379`。见 `backend/src/main/resources/application.yml`。

## 数据库

后端**不会**自动建表，需手动执行：

```bash
mysql -uroot -proot < sql/init.sql
```

含建库、表结构、外键、演示数据与角色权限种子。**已有库升级请对照 `init.sql` 自行增量调整。**

## 快速启动

### 后端

```bash
cd backend && mvn spring-boot:run
```

联调 Coze 推荐（自动加载根目录 `.env`）：

```powershell
.\scripts\start-backend-dev.ps1
```

- API：`http://localhost:8080`
- 文档：`http://localhost:8080/doc.html`（Knife4j）；亦可访问 `http://localhost:8080/swagger-ui/index.html`

### 前端

```bash
cd frontend && npm install && npm run dev
```

- 访问：`http://localhost:3088`（`/api`、`/ws` 代理到 8080）

根目录：`npm run dev:backend` / `npm run dev:frontend`

### 演示账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | 123456 | 管理员 |
| supervisor | 123456 | 车间主管 |
| worker | 123456 | 普通员工 |

## Coze 配置

1. 复制 `.env.example` → `.env`
2. 填写 `COZE_API_TOKEN`、`COZE_BOT_ID`、`COZE_WORKFLOW_ID`、`COZE_API_URL`
3. `.\scripts\start-backend-dev.ps1` 启动后端
4. 管理员 → **系统管理 → Coze 配置** 保存并测试

主要接口：`POST /api/coze/chat`、`POST /api/coze/scheduling`、`GET /api/coze/chat/history`。

> Token 勿提交 Git。Bot 人设与知识库在 Coze 控制台维护。

## 消息通知（WebSocket）

| 项目 | 说明 |
|------|------|
| 端点 | `ws://localhost:8080/ws/notifications?token=<Token>` |
| REST | `GET /api/notifications`、`PUT /api/notifications/{id}/read`、`DELETE /api/notifications/read` |
| 代码 | 前端 `stores/notifications.ts`；后端 `websocket/` 包 |

生产部署 WebSocket 转发见 `deploy/nginx.conf`。

## 主要 API 分组

| 前缀 | 说明 |
|------|------|
| `/api/auth` | 登录、验证码、当前用户 |
| `/api/dashboard` | 驾驶舱统计、班组进度、预警 |
| `/api/plans` | 生产计划 |
| `/api/work-orders` | 工单与工序 |
| `/api/teams` | 班组 |
| `/api/exceptions` | 异常事件 |
| `/api/materials` | 物料库存 |
| `/api/notifications` | 系统通知 |
| `/api/coze` | AI 客服与排产 |
| `/api/admin/users` | 用户管理 |
| `/api/admin/roles` | 角色权限 |

## 项目结构

```
AI-MES/
├── backend/              # Spring Boot
├── frontend/             # Vue 3（3088）
├── sql/init.sql          # 数据库初始化
├── scripts/              # 启动脚本（读 .env）
├── deploy/nginx.conf     # 部署示例
├── docs/                 # 设计文档
├── .env.example
└── README.md
```

## 生产部署

```bash
cd frontend && npm run build
cd backend && mvn clean package -DskipTests
```

产出 `frontend/dist` 与 `backend/target/ai-mes-backend-1.0.0.jar`，按 `deploy/nginx.conf` 配置 Nginx。

## 文档

| 文档 | 说明 |
|------|------|
| [题目二需求对照](docs/题目2-工业生产车间智能管理智能体.md) | 课程要求与评分点 |
| [系统设计文档](docs/设计文档.txt) | 架构、库表、接口详设 |
| [前端 UI 设计](docs/前端UI设计文档.md) | 页面与交互规范 |
