# AI-MES 智能生产车间管理平台

> 工业应用软件设计 · 题目二「工业生产车间智能管理智能体」  
> Spring Boot 3 + Vue 3 + MySQL + Redis + Coze 智能体

面向离散制造车间的 Web 型 MES，覆盖 **计划下发 → 工单派工 → 工序认领与报工 → 异常闭环 → 完工领料 → 驾驶舱看板**，并接入 **Coze 智能客服** 与 **智能排产工作流**。同时提供产品/BOM、工艺路线、设备台账、点检保养维修、质量检验、库存流水等制造基础能力。

## 目录

- [典型业务闭环](#典型业务闭环)
- [功能模块](#功能模块)
- [角色与权限](#角色与权限)
- [业务状态](#业务状态)
- [AI 能力](#ai-能力)
- [技术栈](#技术栈)
- [快速启动](#快速启动)
- [数据库](#数据库)
- [配置说明](#配置说明)
- [API 与接口文档](#api-与接口文档)
- [消息通知](#消息通知websocket)
- [项目结构](#项目结构)
- [生产部署](#生产部署)

## 典型业务闭环

```
登录 → 驾驶舱总览 → 制定/下发生产计划（可拆多单）
→ 按产品/BOM 自动生成工单 → 主管派工至班组
→ 员工工序看板认领 → 查看 SOP / 工艺参数 / 检验录入 → 报工
→ 异常上报与处理 → 工单完工（按 BOM 扣减库存）
→ AI 客服查询 / AI 排产辅助决策
```

计划下发、派工、缺料、异常、工艺审批等关键节点通过 **WebSocket** 推送系统通知。

## 功能模块

| 模块 | 路由 | 主要能力 |
|------|------|----------|
| 首页驾驶舱 | `/dashboard` | KPI、班组进度、设备状态汇总、最新异常与缺料预警 |
| 生产计划 | `/plans`、`/plans/:id` | 计划 CRUD、绑定产品、下发预览、一计划多工单拆分 |
| 工单管理 | `/work-orders`、`/work-orders/:id` | 新建/编辑/派工/筛选/进度/完工/删除；BOM 理论用量、排产字段 |
| 工序进度 | `/process` | 看板认领/报工、SOP 预览、工艺参数、质检录入、多设备绑定 |
| 产品管理 | `/products` | 产品 CRUD、成品库存、单层 BOM 维护 |
| 工艺管理 | `/process-management`、`/process-management/:id` | 工艺路线/工序/参数/SOP/设备物料绑定、审批发布、版本记录 |
| 设备管理 | `/devices`、`/devices/:id` | 分类树、台账 CRUD、状态变更、运行统计、履历 |
| 设备点检/保养/维修 | 设备详情页 Tab | 点检计划与记录、保养计划与记录、维修报修与完工 |
| 物料管理 | `/materials` | 库存与安全库存、入出库/调整、库存流水 Tab |
| 班组管理 | `/teams` | 班组、产线、成员规模 |
| 异常管理 | `/exceptions` | 设备/缺料/质量/其他异常；处理闭环；质检不合格自动建单 |
| 智能客服 | `/ai-chat` | 多轮对话、历史会话、快捷提问、流式 SSE 回复 |
| AI 悬浮窗 | 全局右下角 | 任意业务页快速唤起助手 |
| 智能排产 | `/ai-scheduling` | 优先级/瓶颈/派工建议，一键写回工单 |
| 用户管理 | `/admin/users` | 账号 CRUD、重置密码、启用/禁用（禁用二次确认） |
| 角色管理 | `/admin/roles` | 17 项细粒度权限分配 |
| Coze 配置 | `/admin/coze` | Token/Bot/工作流配置、连通性与对话健康检查 |
| 个人中心 | `/profile` | 头像、资料、修改密码 |

菜单与 API 按 `sys_role_permission` 权限字符串控制；`admin` 始终拥有全部权限。

### 制造数据能力

| 能力 | 说明 |
|------|------|
| 产品 + BOM | 计划/工单/工艺绑定 `product_id`；完工可按 BOM 扣减物料 |
| 成品库存 | 产品维度库存与 `inv_transaction` 成品流水 |
| 计划拆分 | 下发时按数量拆多工单（默认每 100 件，`PLAN_SPLIT_QTY`） |
| 工艺路线 | 多版本、审批发布、工序参数/SOP、设备与物料绑定 |
| 质量检验 | 检测工序检验项录入；不合格自动创建质量异常 |
| 设备运维 | 点检/保养计划与执行记录、维修单、设备履历 |
| 操作审计 | `sys_operation_log` 记录关键写操作 |

完工领料开关：`BOM_PICK_ON_COMPLETE`（默认 `true`）。

## 角色与权限

内置 **5 种角色**、**17 项权限**：

| 角色 | `role_key` | 典型菜单 |
|------|------------|----------|
| 管理员 | `admin` | 全部 |
| 车间主管 | `supervisor` | 计划、工单、班组、物料、设备、产品、工艺、异常、排产、AI |
| 计划员 | `planner` | 计划、工单、物料、产品、工艺、排产、AI |
| 工程师 | `engineer` | 设备、工单、工序、异常、工艺、工艺审批、产品、AI |
| 操作工 | `worker` | 工序进度、工单反馈、异常上报、AI |

权限项：生产计划、工单管理、工单反馈、工序进度、异常上报、班组、物料、产品管理、设备、工艺管理、工艺审批、排产、AI 客服、用户管理、角色管理、Coze 配置、系统配置。

## 业务状态

**生产计划：** `draft` 草稿 → `released` 已下发 → `done` 已完成

**工单：** `pending` 待分配 → `assigned` 待领取 → `producing` 生产中 → `exception` 异常处理中 → `done` 已完成

**异常：** `open` 待处理 → `processing` 处理中 → `closed` 已关闭

上报异常后关联工单进入 `exception`，进行中工序暂停；恢复生产后回到 `producing`。

## AI 能力

| 能力 | 说明 |
|------|------|
| 智能客服 | 流式接口 `POST /api/coze/chat/stream`；历史会话、停止生成 |
| 会话记忆 | 当前会话最近 5 轮问答注入 Prompt |
| 数据分流 | 操作/SOP/FAQ 走 Coze 知识库；工单/缺料/设备等走后端实时查库 |
| 排产工作流 | Coze Workflow 生成优先级/瓶颈/派工建议，可写回工单 |
| Coze 配置 | 管理后台保存 Token、Bot ID、工作流 ID；支持健康检查 |
| 演示模式 | 未配置 Coze 或额度不足时，可关闭 Coze 使用本地 Mock |

Coze 配置读取优先级：**数据库 `sys_coze_config` > 根目录 `.env`**。Token 勿提交 Git。

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、ECharts |
| 后端 | Spring Boot 3.3、MyBatis-Plus、Sa-Token（Redis）、WebSocket |
| 数据 | MySQL 8、Redis 6+ |
| AI | Coze Bot API v3、Workflow API |
| 文档 | Knife4j / OpenAPI 3 |

## 快速启动

### 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Redis | 6+ |
| Maven | 3.9+ |

默认：MySQL `localhost:3306` / 库名 `AI-MES` / `root`；Redis `localhost:6379`。

### 1. 初始化数据库

```bash
mysql -uroot -proot < sql/init.sql
```

### 2. 配置环境变量（可选）

```bash
cp .env.example .env
# 编辑 .env 填写 Coze Token、Bot ID 等
```

未配置 Coze 时系统自动使用演示数据回复。

### 3. 启动后端

```bash
cd backend && mvn spring-boot:run
```

Windows 推荐（自动加载 `.env` 并打包运行）：

```powershell
.\scripts\start-backend-dev.ps1
```

- API：`http://localhost:8080`
- 接口文档：`http://localhost:8080/doc.html`

### 4. 启动前端

```bash
cd frontend && npm install && npm run dev
```

- 访问：`http://localhost:3088`（`/api`、`/ws` 代理到 8080）

根目录快捷命令：`npm run dev:backend`、`npm run dev:frontend`

### 5. 演示账号

密码均为 **`123456`**（登录页可快捷填充）：

| 账号 | 角色 |
|------|------|
| `admin` | 管理员 |
| `supervisor` | 车间主管 |
| `planner` | 计划员 |
| `engineer` | 工程师 |
| `worker` | 操作工 |

### 6. 运行测试（可选）

```bash
cd backend && mvn test
```

## 数据库

后端**不会**自动建表，需执行 `sql/init.sql` 完成初始化。

| 项目 | 说明 |
|------|------|
| 数据库名 | `AI-MES` |
| 表数量 | 33 张（生产、主数据、设备、质量、系统、AI 等） |
| 种子数据 | 演示账号、班组、计划/工单、物料、设备、工艺、点检保养记录等 |
| 权限种子 | 5 角色 × 17 权限项 |

主要表分组：生产（`prod_*`）、主数据（`mdm_*`）、物料库存（`mat_*` / `inv_*`）、设备（`dev_*`）、质量（`qms_*`）、异常（`exc_*`）、系统（`sys_*`）、AI（`ai_chat_log`）。

## 配置说明

复制 `.env.example` → `.env`：

| 变量 | 说明 |
|------|------|
| `DB_*` | 数据库连接 |
| `REDIS_*` | Redis 连接 |
| `SERVER_PORT` | 后端端口，默认 8080 |
| `COZE_API_TOKEN` | Coze 个人访问令牌（PAT） |
| `COZE_BOT_ID` | 智能体 Bot ID |
| `COZE_WORKFLOW_ID` | 排产工作流 ID（可选） |
| `COZE_API_URL` | 默认 `https://api.coze.cn/v3` |
| `BOM_PICK_ON_COMPLETE` | 工单完工按 BOM 扣库存，默认 `true` |
| `PLAN_SPLIT_QTY` | 计划下发拆单阈值，默认 `100` |
| `UPLOAD_DIR` | SOP 等上传目录，默认 `uploads` |

管理员还可在 **系统管理 → Coze 配置** 页面在线保存并测试连通性。

## API 与接口文档

| 前缀 | 说明 |
|------|------|
| `/api/auth` | 登录、验证码、当前用户 |
| `/api/dashboard` | 驾驶舱统计 |
| `/api/plans` | 生产计划 |
| `/api/work-orders` | 工单与工序 |
| `/api/products` | 产品与 BOM |
| `/api/process-routes` | 工艺路线、工序、SOP、审批 |
| `/api/devices` | 设备分类与台账 |
| `/api/device-inspections` | 设备点检 |
| `/api/device-maintenances` | 设备保养 |
| `/api/device-repairs` | 设备维修 |
| `/api/materials` | 物料库存与流水 |
| `/api/quality` | 质量检验 |
| `/api/teams` | 班组 |
| `/api/exceptions` | 异常事件 |
| `/api/notifications` | 系统通知 |
| `/api/coze` | AI 客服与排产 |
| `/api/admin/users` | 用户管理 |
| `/api/admin/roles` | 角色权限 |

完整接口与在线调试：启动后端后访问 `http://localhost:8080/doc.html`。

## 消息通知（WebSocket）

| 项目 | 说明 |
|------|------|
| 端点 | `ws://localhost:8080/ws/notifications?token=<Token>` |
| REST | `GET /api/notifications`、标记已读、删除 |
| 前端 | `stores/notifications.ts` |
| 后端 | `websocket/` 包 |

生产环境 Nginx 转发示例见 `deploy/nginx.conf`。

## 项目结构

```
AI-MES/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/       # 业务代码（controller / service / entity）
│   └── src/test/            # 单元测试
├── frontend/                # Vue 3 前端（dev: 3088）
├── sql/
│   └── init.sql             # 全量数据库初始化（33 表 + 种子数据）
├── scripts/                 # 启动脚本（读取 .env）
├── deploy/nginx.conf        # Nginx 部署示例
├── .env.example             # 环境变量模板
└── README.md
```

`.env` 含密钥，已在 `.gitignore` 中忽略，勿提交。

## 生产部署

```bash
cd frontend && npm run build
cd backend && mvn clean package -DskipTests
```

产出：

- 前端静态资源：`frontend/dist`
- 后端可执行包：`backend/target/ai-mes-backend-1.0.0.jar`

按 `deploy/nginx.conf` 配置 Nginx 反向代理静态资源、`/api` 与 WebSocket。
