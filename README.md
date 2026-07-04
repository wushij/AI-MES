# AI-MES 智能生产车间管理平台

> 工业应用软件设计 · 题目二「工业生产车间智能管理智能体」  
> Spring Boot 3 + Vue 3 + MySQL + Redis + Coze 智能体

面向离散制造车间的 Web 型 MES：覆盖 **计划下发 → 工单派工 → 工序认领与报工 → 异常闭环 → 完工领料 → 驾驶舱看板**，并接入 **Coze 智能客服**与 **智能排产工作流**。在课程/竞赛场景下，已扩展 **产品主数据、BOM、工艺路线、设备台账、库存流水、质量检验** 等制造基础能力。

## 典型业务闭环

```
登录 → 驾驶舱总览 → 制定/下发生产计划（可拆多单）
→ 按产品/BOM 自动生成工单 → 主管派工至班组
→ 员工工序看板认领 → 查看 SOP / 工艺参数 / 检验录入 → 报工
→ 异常上报与处理 → 工单完工（按 BOM 扣减库存）
→ AI 客服查询 / AI 排产辅助决策
```

计划下发、派工、缺料、异常、工艺审批等关键节点会通过 **WebSocket** 推送系统通知。

## 功能概览

| 模块 | 路由 | 主要能力 | 默认角色 |
|------|------|----------|----------|
| 首页驾驶舱 | `/dashboard` | KPI、班组进度、设备状态、最新异常与缺料 | 全部 |
| 生产计划 | `/plans` | 计划 CRUD、绑定产品、下发预览、一计划多工单拆分 | 管理员、主管、计划员 |
| 计划详情 | `/plans/:id` | 计划信息、关联工单、下发与完成进度 | 同上 |
| 工单管理 | `/work-orders` | 新建/编辑/派工/筛选/进度/完工/删除 | 主管、计划员、工程师 |
| 工单详情 | `/work-orders/:id` | 工单信息、BOM 理论用量、工序记录、排产字段、关联异常 | 同上 + 员工反馈 |
| 工序进度 | `/process` | 看板认领/报工、SOP 预览、工艺参数、质检录入、设备选择 | 员工、工程师 |
| 产品管理 | `/products` | 产品 CRUD、单层 BOM 维护 | 管理员、主管、计划员、工程师 |
| 工艺管理 | `/process-management` | 工艺路线/工序/参数/SOP/设备物料绑定、审批发布、版本记录 | 管理员、主管、计划员、工程师 |
| 工艺详情 | `/process-management/:id` | 工序配置、复制、提交审批 | 同上 |
| 设备管理 | `/devices` | 分类树、台账 CRUD、状态变更、今日报警 | 管理员、主管、工程师 |
| 设备详情 | `/devices/:id` | 基本信息、运行状态、运行记录、维修/异常、履历 | 同上 |
| 物料管理 | `/materials` | 库存与安全库存、入出库/调整、**库存流水** Tab | 管理员、主管、计划员 |
| 班组管理 | `/teams` | 班组、产线、成员规模 | 管理员、主管 |
| 异常管理 | `/exceptions` | 设备/缺料/质量/其他异常；处理闭环；质量检验不合格自动建单 | 全部可上报；主管/工程师处理 |
| 智能客服 | `/ai-chat` | 多轮对话、历史会话、快捷提问；Coze 未配置时 Mock | 全部 |
| AI 小窗 | 全局悬浮 | 任意业务页快速唤起 AI 助手 | 全部 |
| 智能排产 | `/ai-scheduling` | 优先级/瓶颈/派工建议，一键应用到工单 | 管理员、主管、计划员 |
| 系统管理 | `/admin/*` | 用户、角色权限、Coze 配置与健康检查 | 管理员 |
| 个人中心 | `/profile` | 头像、资料、修改密码 | 全部 |

菜单与路由按 **`sys_role_permission` 权限字符串** 控制；管理员在「角色管理」中可调整各角色可见菜单（`admin` 始终拥有全部权限）。

### 角色与默认权限

系统内置 **5 种角色**、**17 项权限**：

| 角色 | `role_key` | 典型职责 |
|------|------------|----------|
| 管理员 | `admin` | 全部权限 |
| 车间主管 | `supervisor` | 计划、工单、班组、物料、设备、排产、工艺配置（无工艺审批） |
| 计划员 | `planner` | 计划、工单查看、物料、产品、排产、工艺配置 |
| 工程师 | `engineer` | 设备、工序执行、异常、工艺配置与**工艺审批**、产品 |
| 操作工 | `worker` | 工序进度、工单反馈、异常上报、AI 客服 |

权限项：`生产计划`、`工单管理`、`班组`、`物料`、`产品管理`、`设备`、`排产`、`工序进度`、`工单反馈`、`异常上报`、`AI 客服`、`用户管理`、`角色管理`、`Coze 配置`、`系统配置`、`工艺管理`、`工艺审批`。

### 工单与异常状态

**工单：** `pending` 待分配 → `assigned` 待领取 → `producing` 生产中 → `exception` 异常处理中 → `done` 已完成

**异常：** `open` 待处理 → `processing` 处理中 → `closed` 已关闭

上报异常后，关联工单进入 `exception`，进行中工序暂停；恢复生产后回到 `producing`。

### 制造扩展能力（相对初版 README）

| 能力 | 说明 |
|------|------|
| 产品 + BOM | `mdm_product` / `mdm_bom`，计划/工单/工艺绑定 `product_id` |
| 计划拆分 | 下发时可按数量拆分多工单（默认每 100 件，`aimes.plan-split-qty`） |
| 工艺路线 | 多路线、工序参数、SOP、设备/物料绑定、审批发布、工单引用 `routing_id` |
| 库存流水 | `inv_transaction`；物料页流水 Tab；完工按 BOM 写 `pick` 流水 |
| 质量检验 | 检测工序检验项录入；不合格自动创建 `quality` 异常 |
| 设备 V1 | 分类树、台账、履历、运行记录（来自报工 `device_id`） |
| 操作审计 | `sys_operation_log` + `@OperationLog` 记录关键写操作 |

完工领料开关：`aimes.bom-pick-on-complete`（环境变量 `BOM_PICK_ON_COMPLETE`，默认 `true`）。

## AI 能力说明

| 能力 | 说明 |
|------|------|
| 智能客服 | 对话历史、新对话、角色快捷提问、Markdown 回复、停止生成 |
| 会话记忆 | 当前会话内最近 5 轮问答注入 Prompt |
| 数据分流 | 操作/SOP 走 Coze 知识库；工单/缺料等走后端实时查库 |
| Coze 配置 | 管理员配置 Token、Bot ID、工作流 ID；支持健康检查 |
| 排产工作流 | Coze Workflow → 优先级/瓶颈/派工建议 → 写回工单 |
| 演示模式 | 未配置 Coze 时使用本地 Mock |

## 其他系统能力

- **认证安全**：Sa-Token + Redis 会话；登录验证码、IP 失败次数限制
- **消息通知**：顶栏铃铛、未读角标、WebSocket 实时推送
- **数据完整性**：外键约束；删工单级联清理工序、异常、通知
- **接口文档**：Knife4j → `http://localhost:8080/doc.html`
- **单元测试**：工单状态机、异常流转、工艺审批（`backend/src/test`）
- **增量迁移**：`sql/migrations/` 002–004（审计、P2 主数据、工单/工序字段）

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、ECharts |
| 后端 | Spring Boot 3.3、MyBatis-Plus、Sa-Token（Redis）、WebSocket |
| 数据 | MySQL 8、Redis 6+ |
| AI | Coze Bot API v3 + Workflow API |
| 文档 | Knife4j / OpenAPI 3 |

## 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Redis | 6+ |
| Maven | 3.9+ |

默认连接：`localhost:3306` / 库名 `AI-MES` / 用户 `root`；Redis `localhost:6379`。详见 `backend/src/main/resources/application.yml` 与 `.env.example`。

## 数据库

后端**不会**自动建表，需手动初始化：

### 全新环境

```bash
mysql -uroot -proot < sql/init.sql
```

含建库、表结构、外键、演示数据、5 角色权限种子。

### 已有环境升级

按序号执行尚未运行的迁移脚本（详见 `sql/migrations/README.md`）：

```bash
mysql -uroot -proot AI-MES < sql/migrations/002_add_sys_operation_log.sql
mysql -uroot -proot AI-MES < sql/migrations/003_add_p2_tables.sql
mysql -uroot -proot AI-MES < sql/migrations/004_add_order_qty_and_data_interop.sql
```

## 快速启动

### 1. 准备依赖

确保 MySQL、Redis 已启动，并执行 `sql/init.sql`。

### 2. 后端

```bash
cd backend && mvn spring-boot:run
```

Windows 联调 Coze 推荐（自动加载根目录 `.env`）：

```powershell
.\scripts\start-backend-dev.ps1
```

- API：`http://localhost:8080`
- 文档：`http://localhost:8080/doc.html`

### 3. 前端

```bash
cd frontend && npm install && npm run dev
```

- 访问：`http://localhost:3088`（`/api`、`/ws` 代理到 8080）

根目录快捷命令：

```bash
npm run dev:backend
npm run dev:frontend
```

### 4. 演示账号

密码均为 **`123456`**：

| 账号 | 角色 | 说明 |
|------|------|------|
| `admin` | 管理员 | 全部功能 |
| `supervisor` | 车间主管 | 生产调度与现场管理 |
| `planner` | 计划员 | 计划、物料、排产 |
| `engineer` | 工程师 | 设备、工艺审批、工序质量 |
| `worker` | 操作工 | 工序看板、报工、异常上报 |

### 5. 运行测试（可选）

```bash
cd backend && mvn test
```

## 配置说明

复制 `.env.example` → `.env`，按需填写：

| 变量 | 说明 |
|------|------|
| `DB_*` / `REDIS_*` | 数据库与 Redis |
| `SERVER_PORT` | 后端端口，默认 8080 |
| `COZE_API_TOKEN` / `COZE_BOT_ID` / `COZE_WORKFLOW_ID` | Coze 集成 |
| `BOM_PICK_ON_COMPLETE` | 工单完工是否按 BOM 扣库存，默认 `true` |
| `PLAN_SPLIT_QTY` | 计划下发拆单数量阈值，默认 `100` |
| `UPLOAD_DIR` | SOP 等上传目录，默认 `uploads` |

管理员还可在 **系统管理 → Coze 配置** 页面保存并测试连通性。

> Token 与密钥勿提交 Git。

## 消息通知（WebSocket）

| 项目 | 说明 |
|------|------|
| 端点 | `ws://localhost:8080/ws/notifications?token=<Token>` |
| REST | `GET /api/notifications`、`PUT .../read`、`DELETE .../read` |
| 代码 | 前端 `stores/notifications.ts`；后端 `websocket/` 包 |

生产部署 WebSocket 转发见 `deploy/nginx.conf`。

## 主要 API 分组

| 前缀 | 说明 |
|------|------|
| `/api/auth` | 登录、验证码、当前用户 |
| `/api/dashboard` | 驾驶舱统计 |
| `/api/plans` | 生产计划、下发预览 |
| `/api/work-orders` | 工单与工序 |
| `/api/products` | 产品与 BOM |
| `/api/process-routes` | 工艺路线、工序、SOP、审批 |
| `/api/devices` | 设备分类与台账 |
| `/api/materials` | 物料库存与流水 |
| `/api/quality` | 质量检验 |
| `/api/teams` | 班组 |
| `/api/exceptions` | 异常事件 |
| `/api/notifications` | 系统通知 |
| `/api/coze` | AI 客服与排产 |
| `/api/admin/users` | 用户管理 |
| `/api/admin/roles` | 角色权限 |

完整接口见 Knife4j 文档。

## 项目结构

```
AI-MES/
├── backend/                 # Spring Boot 后端
│   └── src/test/            # 单元测试
├── frontend/                # Vue 3 前端（dev: 3088）
├── sql/
│   ├── init.sql             # 全量初始化
│   └── migrations/          # 增量迁移 002–004
├── scripts/                 # 启动脚本（读取 .env）
├── deploy/nginx.conf        # Nginx 部署示例
├── docs/                    # 设计与分析报告
├── .env.example
└── README.md
```

## 生产部署

```bash
cd frontend && npm run build
cd backend && mvn clean package -DskipTests
```

产出 `frontend/dist` 与 `backend/target/ai-mes-backend-1.0.0.jar`，按 `deploy/nginx.conf` 配置 Nginx 反向代理与 WebSocket。

## 文档

| 文档 | 说明 |
|------|------|
| [题目二需求对照](docs/题目2-工业生产车间智能管理智能体.md) | 课程要求与评分点 |
| [模块分析报告](docs/MES系统模块分析报告.md) | 现状、缺口与演进路线 |
| [下一步完善计划](docs/下一步完善计划.md) | P0–P3 任务与验收（当前 P0–P2 已完成） |
| [工艺管理设计](docs/工艺管理设计.txt) | 工艺模块需求 |
| [设备管理设计](docs/设备管理设计.txt) | 设备模块需求 |

## 已知限制与后续方向

相对完整 MES 约 **60%–70%** 完成度。尚未覆盖或仅部分实现的能力包括：设备点检/保养/OEE、工艺版本回滚与硬拦截报工、NCR/CAPA、批次追溯、Flyway/CI、Excel 导入导出等。详见 `docs/下一步完善计划.md` §七（P3）。

---

*最后更新：2026-07-05 · 与代码库 P0–P2 实施状态对齐*
