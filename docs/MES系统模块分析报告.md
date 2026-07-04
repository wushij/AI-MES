# AI-MES 系统模块全面分析报告

> 生成日期：2026-07-04（P0 修复：2026-07-04 更新）  
> 分析范围：后端、前端、数据库、文档、部署与 AI 集成  
> 分析方式：多子代理并行扫描 + 主报告汇总

---

## 一、项目定位与总体评估

### 1.1 项目定位

**AI-MES** 是一套面向离散制造车间的 Web 型制造执行系统（MES），课程/竞赛场景下的「智能生产车间管理平台」。核心闭环为：

```
登录 → 驾驶舱总览 → 制定/下发生产计划 → 自动生成工单
→ 主管派工至班组 → 员工工序看板认领 → 更新进度 / 上报异常
→ 主管处理异常 → 工单完工 → AI 客服查询 / AI 排产辅助决策
```

**差异化能力**：深度集成 **Coze 智能体**（对话客服 + 排产工作流），在同类轻量 MES 中具备明显的 AI 增强特色。

### 1.2 成熟度评估

| 维度 | 完成度 | 说明 |
|------|--------|------|
| 生产执行闭环 | ★★★★☆ | 计划→工单→工序→完工链路完整 |
| 异常与预警 | ★★★★☆ | 异常闭环、缺料预警、WebSocket 通知 |
| AI 能力 | ★★★★★ | Coze 对话、流式输出、排产工作流、Mock 降级 |
| 主数据管理 | ★★☆☆☆ | 工艺路线、设备已入库；产品/BOM 仍缺 |
| 库存与供应链 | ★★☆☆☆ | 仅安全库存预警，无事务台账 |
| 质量管理 | ★☆☆☆☆ | 异常类型含「质量」，无检验体系 |
| 设备管理 | ★★★★☆ | 分类/台账/详情/履历，与异常/驾驶舱/排产数据互通 |
| 企业级特性 | ★★☆☆☆ | 无审计日志、无多工厂、无 ERP 对接；**环境变量配置已支持** |
| **综合（相对完整 MES）** | **约 35%–45%** | 执行闭环与 AI 强，主数据仍偏薄 |

---

## 二、技术架构

### 2.1 技术栈总览

```
┌─────────────────────────────────────────────────────────────┐
│                    客户端（浏览器）                            │
│              Vue 3 + TypeScript + Element Plus               │
│              Pinia + Vue Router + ECharts + Axios            │
│              开发端口 :3088，生产构建 dist/                   │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP /api  +  WebSocket /ws
┌──────────────────────────▼──────────────────────────────────┐
│              Spring Boot 3.3.2（Java 17）:8080               │
│   11 Controllers │ 16 Services │ 11 MyBatis-Plus Mappers     │
│   Sa-Token 认证 │ Knife4j API 文档 │ WebSocket 通知推送       │
└──────┬───────────────────────────────┬──────────────────────┘
       │                               │
┌──────▼──────┐                 ┌──────▼──────┐
│  MySQL 8    │                 │   Redis     │
│  11 张表    │                 │ 会话/锁登   │
└─────────────┘                 └─────────────┘
                                       │
                              ┌────────▼────────┐
                              │  Coze API       │
                              │  对话 Bot +     │
                              │  排产 Workflow  │
                              └─────────────────┘
```

### 2.2 项目目录结构

| 路径 | 职责 |
|------|------|
| `backend/` | Spring Boot 后端（~71 个 Java 源文件） |
| `frontend/` | Vue 3 前端（~86 个源文件，15 个页面） |
| `sql/init.sql` | 唯一数据库初始化脚本（~457 行，11 张表） |
| `docs/` | 设计文档、提示词、知识库（部分 gitignore） |
| `deploy/nginx.conf` | 生产 Nginx 反向代理示例 |
| `scripts/` | PowerShell 后端启动脚本 |
| `.env` / `.env.example` | 环境变量：DB、Redis、Coze、前端 API 等 |

### 2.3 环境变量配置（已实现）

通过根目录 `.env` + 启动类 `DotEnvLoader` 注入系统属性，`application.yml` 使用 `${变量名:默认值}` 读取，**未配置时保持本地开发默认值**。

| 变量组 | 变量 | 用途 | 默认值 |
|--------|------|------|--------|
| MySQL | `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | 数据库连接 | `localhost:3306` / `AI-MES` / `root` / `root` |
| Redis | `REDIS_HOST` `REDIS_PORT` `REDIS_DATABASE` | 会话与登录保护 | `localhost:6379` / `0` |
| 服务 | `SERVER_PORT` | 后端端口 | `8080` |
| Coze | `COZE_API_TOKEN` `COZE_BOT_ID` `COZE_WORKFLOW_ID` 等 | AI 客服与排产 | 空（Mock 降级） |
| 前端 | `VITE_API_BASE_URL` | 开发环境 API 前缀 | `/api` |

模板见 `.env.example`；本地 `.env` 已在 `.gitignore` 中，不会提交 Git。

### 2.4 缺失的基础设施

| 能力 | 状态 |
|------|------|
| CI/CD（GitHub Actions 等） | ❌ 未配置 |
| 数据库迁移（Flyway/Liquibase） | ❌ 仅手动 `init.sql` |
| 自动化测试 | ❌ `src/test` 为空 |

---

## 三、已实现模块详表

### 3.1 后端模块（按业务域）

| 模块 | Controller | Service | Entity | API 数 | 成熟度 |
|------|------------|---------|--------|--------|--------|
| 认证与账户 | `AuthController` | `AuthService` | `SysUser` | 7 | ✅ 完整 |
| 生产计划 | `PlanController` | `PlanService` | `ProdPlan` | 6 | ✅ 完整 |
| 工单执行 | `WorkOrderController` | `WorkOrderService` | `ProdWorkOrder`, `ProdProcessRecord` | 10 | ✅ 完整 |
| 班组管理 | `TeamController` | `TeamService` | `ProdTeam` | 5 | ✅ 完整 |
| 物料预警 | `MaterialController` | `MaterialService` | `MatMaterial` | 5 | ⚠️ 基础 |
| 异常管理 | `ExceptionController` | `ExceptionService` | `ExcEvent` | 5 | ✅ 完整 |
| 驾驶舱 | `DashboardController` | `DashboardService` | —（聚合查询） | 4 | ⚠️ 基础 |
| AI / Coze | `CozeController` | `CozeService`（~2150 行） | `AiChatLog`, `SysCozeConfig` | 13 | ✅ 完整 |
| 用户管理 | `UserAdminController` | `UserAdminService` | `SysUser` | 7 | ✅ 完整 |
| 角色权限 | `RoleController` | `RoleService` | `SysRolePermission` | 2 | ✅ 完整 |
| 消息通知 | `SysNotificationController` | `SysNotificationService` | `SysNotification` | 5 + WS | ✅ 完整 |

**支撑服务**：`WorkOrderNoService`（工单编号）、`CozeConfigService`（配置合并）、`ReferentialIntegrityService`（删除保护）、`CaptchaService`、`LoginProtectionService`。

### 3.2 前端模块（按路由）

| 路由 | 页面 | 权限 | 后端对齐 | 状态 |
|------|------|------|----------|------|
| `/login` | `Login.vue` | 公开 | `/api/auth` | ✅ 完整 |
| `/dashboard` | `Dashboard.vue` | 全部 | `/api/dashboard` | ✅ 完整 |
| `/plans` | `Plans.vue` | 生产计划 | `/api/plans` | ✅ 完整 |
| `/work-orders` | `WorkOrders.vue` | 工单管理/反馈 | `/api/work-orders` | ✅ 完整 |
| `/work-orders/:id` | `WorkOrderDetail.vue` | 同上 | 同上 | ⚠️ **静态 Stub** |
| `/process` | `Process.vue` | 工序进度 | `/api/work-orders/process-board` | ✅ 完整 |
| `/teams` | `Teams.vue` | 班组 | `/api/teams` | ✅ 完整 |
| `/exceptions` | `Exceptions.vue` | 异常上报 | `/api/exceptions` | ✅ 完整 |
| `/materials` | `Materials.vue` | 物料 | `/api/materials` | ✅ 完整 |
| `/ai-chat` | `AiChat.vue` | AI 客服 | `/api/coze/chat` | ✅ 完整（空「新对话」置顶） |
| `/ai-scheduling` | `AiScheduling.vue` | 排产 | `/api/coze/scheduling` | ✅ 完整 |
| `/admin/users` | `admin/Users.vue` | 用户管理 | `/api/admin/users` | ✅ 完整 |
| `/admin/roles` | `admin/Roles.vue` | 角色管理 | `/api/admin/roles` | ✅ 完整 |
| `/admin/coze` | `admin/Coze.vue` | Coze 配置 | `/api/coze/config` | ✅ 完整 |
| `/profile` | `Profile.vue` | 个人中心 | `/api/auth/profile` | ✅ 完整 |
| 全局悬浮 | `AiFloatBtn` + `AiChatPanel` | AI 客服 | Coze SSE | ✅ 完整 |

**前端架构特点**：Fat Views（业务逻辑集中在 `.vue`）、6 个 Pinia Store、14 个 API 模块与后端 1:1 对应、三层权限控制（路由 → 菜单 → 按钮）。

### 3.3 数据库实体（11 张表）

| 表名 | 实体 | 业务含义 |
|------|------|----------|
| `prod_team` | `ProdTeam` | 班组、产线 |
| `sys_user` | `SysUser` | 用户（admin/supervisor/worker） |
| `prod_plan` | `ProdPlan` | 生产计划 |
| `prod_work_order` | `ProdWorkOrder` | 工单（含 AI 排产字段） |
| `prod_process_record` | `ProdProcessRecord` | 工单工序记录 |
| `exc_event` | `ExcEvent` | 异常事件 |
| `mat_material` | `MatMaterial` | 物料库存 |
| `ai_chat_log` | `AiChatLog` | AI 对话历史 |
| `sys_coze_config` | `SysCozeConfig` | Coze 配置（单例） |
| `sys_role_permission` | `SysRolePermission` | 角色菜单权限 |
| `sys_notification` | `SysNotification` | 系统通知 |
| `dev_device` | `DevDevice` | 设备台账（含分类、品牌、位置、负责人等） |
| `dev_device_category` | `DevDeviceCategory` | 设备分类树 |
| `dev_device_history` | `DevDeviceHistory` | 设备履历（新建/状态/异常联动） |
| `mdm_routing` | `MdmRouting` | 工艺路线（版本、产品绑定、状态） |
| `mdm_operation` | `MdmOperation` | 工艺工序（编号、工时、报工/质检/扫码） |
| `mdm_operation_param` | `MdmOperationParam` | 工艺参数 |
| `mdm_routing_history` | `MdmRoutingHistory` | 工艺变更/版本记录 |

**工艺路线**（`mdm_routing` + `mdm_operation` + `mdm_operation_param`，入口「工艺管理」）：

```
备料 → 装配 → 检测 → 包装
```

### 3.4 角色与权限（14 项）

| 角色 | 默认权限 |
|------|----------|
| **管理员** `admin` | 全部 14 项 |
| **车间主管** `supervisor` | 生产计划、工单管理、班组、物料、**设备**、排产、异常上报、AI 客服 |
| **普通员工** `worker` | 工序进度、工单反馈、异常上报、AI 客服 |

权限项：`生产计划`、`工单管理`、`班组`、`物料`、**`设备`**、`排产`、`工序进度`、`工单反馈`、`异常上报`、`AI 客服`、`用户管理`、`角色管理`、`Coze 配置`、`系统配置`。

> API 层已使用 `@SaCheckPermission` 与 `sys_role_permission` 对齐；驾驶舱/通知等只读接口使用 `@SaCheckLogin`。

---

## 四、核心业务流程现状

### 4.1 工单状态机

```
pending（待分配）
  → assigned（待领取）  [主管派工]
  → producing（生产中） [员工认领]
  → exception（异常中） [上报异常，工序暂停]
  → producing（恢复）   [异常处理完成]
  → done（已完成）     [进度 100%]
```

### 4.2 计划下发逻辑

1. 创建草稿计划（`draft`）
2. 下发（`released`）→ 按**产品名匹配工艺**（无则默认路线）创建工单 + 工序快照，并写入 `routing_id` / `route_version` / `estimated_hours`
3. 所有关联工单 `done` 后计划变为 `done`

**局限**：一个计划只拆一个工单；无法按数量/批次拆分；工艺路线已可配置但尚未按产品绑定。

### 4.3 AI 能力矩阵

| 能力 | 实现方式 | 降级策略 |
|------|----------|----------|
| 智能客服 | Coze Bot API + MES 实时数据注入 Prompt | 本地 Mock 回复 |
| 流式对话 | SSE `/api/coze/chat/stream` | — |
| 会话记忆 | 当前会话最近 5 轮 | 新对话不跨会话 |
| 智能排产 | Coze Workflow | Mock 排产结果 |
| 排产应用 | 写回 `scheduledStartTime`、`estimatedHours`、`schedulingRank` | — |
| 健康检查 | `/api/coze/health/*` | — |

### 4.4 实时通知

- 计划下发、派工、缺料、异常等节点创建 `sys_notification`
- WebSocket `/ws/notifications?token=...` 推送到前端
- 前端 `notifications` Store 含断线重连

---

## 五、与完整 MES 的差距分析

### 5.1 标准 MES 功能域对照

| MES 功能域 | 本项目 | 缺口说明 |
|------------|--------|----------|
| **生产计划（MPS/MRP）** | 部分 | 有简单计划，无 MRP、无产能平衡 |
| **工单管理** | ✅ | 核心能力完整 |
| **工艺路线 / 工序** | ✅ 基础 | 多路线 CRUD、产品绑定、参数、版本；工单工序快照 |
| **高级排产（APS）** | AI 辅助 | 无确定性排产引擎、无班次日历 |
| **车间数据采集** | 部分 | 手工报工%，无条码/RFID/工位登录 |
| **物料管理** | 基础 | 无 BOM、无领料/退料、无库位 |
| **质量管理（QMS）** | 极弱 | 无检验计划、SPC、NCR/CAPA |
| **设备管理（EAM）** | ✅ 基础 | 分类+台账+详情+履历；异常双向同步状态；驾驶舱/排产注入设备负荷 |
| **人员 / 班组** | 基础 | 无技能矩阵、无工时卡、无班次 |
| **异常 / Andon** | ✅ | 较完整 |
| **看板 / OEE** | 部分 | KPI 看板有，无 OEE 计算 |
| **追溯** | ❌ | 无批次/序列号 |
| **文档 / SOP** | AI 口述 | 无文件管理 UI |
| **系统集成** | ❌ | 无 ERP/WMS/PLC 接口 |
| **审计 / 合规** | ❌ | 设计文档 F-15 有规划，未实现 |

### 5.2 主数据缺失清单

| 主数据类型 | 当前替代方案 | 建议新增表（示例） |
|------------|--------------|-------------------|
| 产品 / SKU | `product_name` 自由文本 | `mdm_product` |
| BOM | 无 | `mdm_bom`, `mdm_bom_item` |
| 工艺路线 | `mdm_routing` + `mdm_operation` | ✅ 已实现；产品名绑定、版本管理 |
| 工作中心 / 产线 | `line_name` 字符串 | `mdm_work_center` |
| 设备 | `dev_device` | ✅ 已实现 |
| 供应商 | 无 | `mdm_supplier` |
| 仓库 / 库位 | 无 | `wms_warehouse`, `wms_location` |
| 计量单位 | `unit` 字符串 | `mdm_uom` |

### 5.3 设计文档与实现偏差

| 设计文档 | 实际实现 |
|----------|----------|
| `dev_device` 设备表 | ✅ 已建表 + CRUD |
| `mdm_routing` / `mdm_operation` | ✅ 已建表 + 管理页 |
| `mat_alert` 预警表 | 由 `mat_material.alert_status` 计算 |
| `db/migration/` 迁移目录 | `sql/migrations/002_p0_device_routing.sql` |
| 计划状态 `completed` | SQL 使用 `done` |
| 异常类型 `shutdown` | 代码使用 `device` |
| 知识库管理 UI | 文档有规划，无前端页面 |
| `WorkOrderDetail` 详情页 | ✅ 对接 API，含工序时间线与异常 |

---

## 六、内部技术债与待完善项

### 6.1 高优先级（现有范围内可快速补齐）

| 编号 | 问题 | 状态 | 实现说明 |
|------|------|------|----------|
| P0-1 | `WorkOrderDetail.vue` 为静态 Stub | ✅ 已修复 | 对接 `GET /work-orders/{id}`，展示基本信息与关联异常 |
| P0-2 | 后端返回 `processRecords`，前端未展示 | ✅ 已修复 | `ProcessRecordTimeline` 组件用于详情页与工单抽屉 |
| P0-3 | `device_id` 无设备主数据 | ✅ 已修复 | `dev_device` 表 + `/api/devices` + `/devices` 台账页 |
| P0-4 | 工艺路线硬编码 | ✅ 已修复 | 工艺管理模块 + 产品匹配 + 工单快照 |
| P0-5 | 权限字符串与 API 角色校验不一致 | ✅ 已修复 | `@SaCheckPermission` 对齐 `sys_role_permission` |

### 6.2 中优先级（增强产品完整度）

| 编号 | 模块 | 说明 |
|------|------|------|
| P1-1 | 产品主数据 | SKU、规格、版本管理 |
| P1-2 | BOM + 物料消耗 | 工单完工按 BOM 扣减库存 |
| P1-3 | 质量检验 | 在「检测」工序挂检验单 |
| P1-4 | 操作审计日志 | `sys_operation_log` 表 + 切面记录 |
| P1-5 | 计划详情页 | 展示关联工单、下发历史 |
| P1-6 | 知识库管理 | Coze 知识库文档 CRUD 界面 |
| P1-7 | Flyway 迁移 | 版本化 schema，支持升级 |

### 6.3 低优先级（企业级扩展）

| 编号 | 模块 | 说明 |
|------|------|------|
| P2-1 | 仓储 WMS | 库位、拣货、调拨 |
| P2-2 | 采购管理 | PO、收货、供应商 |
| P2-3 | 设备 OEE | 稼动率、性能、质量三要素 |
| P2-4 | IoT / SCADA | OPC-UA、MQTT 设备数据 |
| P2-5 | ERP 集成 | REST/Webhook/消息队列 |
| P2-6 | 多工厂 | 租户/工厂维度 |
| P2-7 | 报表中心 | 自定义报表、Excel 导出 |

---

## 七、新模块建设路线图（建议）

### Phase 1：夯实现有闭环（1–2 周）

```
目标：把「能用」变成「好用」，不引入大架构变更

☑ 工单详情页真实化（工序时间线、关联异常、排产信息）
□ 计划详情与子工单关联视图
☑ 设备主数据 CRUD（dev_device）+ 异常关联修复
☑ 工艺路线可配置（mdm_routing + mdm_operation）
□ 操作审计日志（关键写操作）
□ 单元测试：工单状态机、异常暂停/恢复
```

### Phase 2：主数据层（2–4 周）

```
目标：从「文本驱动」升级为「主数据驱动」

□ 产品主数据（mdm_product）
☑ 工艺路线配置（mdm_routing + mdm_operation）
□ 工单生成时按产品选路线（路线已可配，待产品绑定）
□ BOM 基础版（单层 BOM + 物料用量）
□ 计划下发支持「一计划多工单」拆分策略
```

### Phase 3：制造深度（4–8 周）

```
目标：接近中小型工厂真实需求

□ 物料事务台账（入库/出库/领料/退料）
□ BOM 倒冲 / 工单领料
□ 质量检验单（工序检验、不合格处置）
□ 班次日历 + 基础产能模型
□ 排产结果持久化 + 甘特图手工微调
□ 批次号 / 简单追溯
```

### Phase 4：平台化（8 周+）

```
目标：企业可部署、可集成

□ Flyway 数据库迁移
□ CI 流水线（构建 + 测试）
□ ERP/WMS 标准接口层
□ 多工厂 / 数据隔离
□ 报表与 BI 导出
```

### 推荐新增模块优先级矩阵

```
                    业务价值
                 高 │  Phase 2        Phase 3
                    │  产品/BOM/路线   质量/库存台账
                    │  设备主数据      批次追溯
                    │
                 低 │  Phase 1        Phase 4
                    │  详情页/审计     ERP/IoT/多工厂
                    └──────────────────────────────→
                              低          实现难度          高
```

---

## 八、模块扩展详细设计建议

### 8.1 设备管理模块（Phase 1 已实现）

**设计依据**：`docs/设备管理设计.txt` 第一阶段（分类、台账、详情、状态管理）。

**数据表**：
- `dev_device_category` — 设备分类
- `dev_device` — 扩展字段（品牌/型号/车间/工位/负责人/日期等）
- `dev_device_history` — 操作履历
- `exc_event.device_id` — 外键关联，设备停机异常必填

**数据互通**：
| 模块 | 联动方式 |
|------|----------|
| 异常管理 | 上报设备停机 → 设备状态变 `fault`；处理完成 → 恢复 `idle`/`repairing`；写入履历 |
| 驾驶舱 | KPI 含设备总数/运行/故障/今日报警；设备状态卡片 |
| AI 排产 | `schedulingContext` 与 Coze 工作流注入 `devices_json`、设备负荷 |
| 班组 | 删除班组前检查 `dev_device.team_id` |

**API**（`/api/devices`）：
- 台账 CRUD、`/options`、`/form-options`、`/summary`
- 详情 `/full`（含异常记录、履历、运行统计）
- 状态 `PUT /{id}/status`
- 分类 `/categories` CRUD

**前端**：`/devices` 设备管理 + `/devices/:id` 详情 Tab 页；异常列表/详情展示关联设备；驾驶舱设备面板；排产设备负荷卡片。

**存量库升级**：`sql/migrations/003_device_management_v1.sql`（在 002 之后执行）。

### 8.2 工艺管理模块（Phase 1 + Phase 2 已实现）

**设计依据**：`docs/工艺管理设计.txt` 第一、二阶段（路线、工序、参数、版本、SOP、设备/物料绑定、审批）。

**Phase 1 能力**：
- 多工艺路线 CRUD、复制、设默认、启停
- 工序：编号、标准/准备/换型工时、报工/质检/扫码标志
- 工艺参数：标准值 + 上下限 + 单位
- 版本：保存发布时自动升版（V1.0 → V1.1），`mdm_routing_history` 记录变更
- **产品绑定**：`mdm_routing.product_name` 匹配计划/工单产品，否则用默认路线
- **工单互通**：下发/建单写入 `routing_id`、`route_version`、`estimated_hours`；工序快照含 `operation_id`

**Phase 2 能力（数据互通）**：
- **SOP 管理**：`mdm_operation_sop` 按工序上传 PDF/图片/视频/文档；工序看板可预览 SOP
- **设备绑定**：`mdm_operation_device` 支持按设备分类或具体设备绑定；报工时校验设备合法性，写入 `prod_process_record.device_id`
- **物料绑定**：`mdm_operation_material` 工序 ↔ 物料用量（原材料/半成品/辅料/工装）；工序看板展示当前工序物料清单
- **审批流程**：状态 `draft → pending_approval → published/rejected`；权限 `工艺审批`（admin/supervisor）；驳回原因记录在 `rejected_reason`
- **执行上下文**：`GET /execution/{workOrderId}` 供工序看板加载完整工艺（参数/设备/物料/SOP）

**API**（`/api/process-routes`）：
- 列表/详情/CRUD、复制、设默认、启停、默认路线
- `PUT /{id}/submit`、`PUT /{id}/approve`、`PUT /{id}/reject`
- `GET /execution/{workOrderId}`
- `POST /operations/{operationId}/sop`、`DELETE /sop/{sopId}`、`GET /sop/{sopId}/file`
- 物料选项：`GET /api/materials/options`（工艺管理可读）

**前端**：`/process-management` 列表（审批状态） + 详情（参数/设备/物料/SOP Tab + 审批按钮）；工序看板联动 SOP 预览与设备选择

**存量库升级**：`004_process_management_v1.sql` → `005_process_management_v2.sql`（按序执行）

### 8.3 产品 + BOM

**产品表** `mdm_product`：编码、名称、规格 — 待建（当前以 `product_name` 字符串绑定工艺）  
**BOM 表** `mdm_bom` + `mdm_bom_item`：待建

**检验计划** `qms_inspection_plan`：关联工序、检验项、抽样规则  
**检验记录** `qms_inspection_record`：实测值、合格/不合格  
**不合格品** `qms_ncr`：处置方式（返工/报废/让步）

与现有异常模块关系：检验不合格可自动创建 `exc_event`（type=quality）。

### 8.4 库存事务模块

**库存流水** `inv_transaction`：类型（入库/出库/领料/退料/调整）、物料、数量、关联工单、操作人  
**当前 `mat_material.stock_qty`** 改为由流水汇总或双写保持一致

前端：物料详情增加「流水明细」Tab；工单详情增加「领料记录」。

---

## 九、API 端点总览（已实现 70+）

<details>
<summary>点击展开完整 API 列表</summary>

### 认证 `/api/auth`
- `POST /login`、`POST /logout`、`GET /info`
- `GET /captcha`、`GET /captcha/required`
- `PUT /profile`、`PUT /password`

### 生产计划 `/api/plans`
- `GET /`、`GET /{id}`、`POST /`、`PUT /{id}`、`DELETE /{id}`
- `POST /{id}/release`

### 工单 `/api/work-orders`
- `GET /`、`GET /process-board`、`GET /{id}`
- `POST /`、`PUT /{id}`、`DELETE /{id}`
- `POST /{id}/assign`、`POST /{id}/claim`
- `PUT /{id}/progress`、`POST /{id}/complete`

### 班组 `/api/teams`
- `GET /`、`GET /{id}`、`POST /`、`PUT /{id}`、`DELETE /{id}`

### 物料 `/api/materials`
- `GET /`、`GET /alerts`、`GET /options`、`POST /`、`PUT /{id}`、`DELETE /{id}`

### 异常 `/api/exceptions`
- `GET /`、`GET /{id}`、`POST /`、`PUT /{id}/handle`、`DELETE /{id}`

### 设备 `/api/devices`
- `GET /`、`GET /options`、`GET /form-options`、`GET /summary`
- `GET /categories`、`POST /categories`、`PUT /categories/{id}`、`DELETE /categories/{id}`
- `GET /{id}`、`GET /{id}/full`、`POST /`、`PUT /{id}`、`PUT /{id}/status`、`DELETE /{id}`

### 工艺 `/api/process-routes`
- `GET /`、`GET /default`、`GET /operations`、`GET /{id}`、`GET /execution/{workOrderId}`
- `POST /`、`PUT /{id}`、`PUT /default`、`DELETE /{id}`
- `POST /{id}/copy`、`PUT /{id}/default`、`PUT /{id}/toggle`
- `PUT /{id}/submit`、`PUT /{id}/approve`、`PUT /{id}/reject`
- `POST /operations/{operationId}/sop`、`DELETE /sop/{sopId}`、`GET /sop/{sopId}/file`

### 驾驶舱 `/api/dashboard`
- `GET /stats`、`GET /progress`、`GET /alerts`、`GET /device-summary`、`GET /workshop-summary`

### Coze `/api/coze`
- `GET /config`、`GET /welcome`、`PUT /config`
- `POST /chat`、`POST /chat/stream`
- `GET /chat/history`、`DELETE /chat/history`
- `POST /scheduling`、`GET /scheduling/context`、`POST /scheduling/apply`
- `GET /health`、`GET /health/chat`、`GET /health/workflow`

### 用户管理 `/api/admin/users`
- `GET /`、`GET /{id}`、`POST /`、`PUT /{id}`
- `POST /{id}/reset-password`、`POST /{id}/toggle-status`、`DELETE /{id}`

### 角色 `/api/admin/roles`
- `GET /`、`PUT /{roleKey}/permissions`

### 通知 `/api/notifications`
- `GET /`、`GET /unread`、`POST /read-all`
- `PUT /{id}/read`、`DELETE /read`

### WebSocket
- `WS /ws/notifications?token=...`

</details>

---

## 十、文档资产清单

| 文档 | 路径 | 状态 |
|------|------|------|
| 项目 README | `README.md` | ✅ 完整运行手册 |
| 系统设计 | `docs/设计文档.txt` | ✅ ~914 行 |
| 前端 UI 设计 | `docs/前端UI设计文档.md` | ✅ ~1600 行 |
| 课程要求 | `docs/题目2-工业生产车间智能管理智能体.md` | ✅ |
| 排产提示词 | `docs/排产工作流提示词.txt` | ✅ |
| Coze 人设 | `docs/coze智能体的人设提示词.txt` | 本地/gitignore |
| 知识库 | `docs/knowledge-base/*.md` | 本地/gitignore |
| **本报告** | `docs/MES系统模块分析报告.md` | ✅ 新建 |

---

## 十一、总结与行动建议

### 已有优势（应保留并强化）

1. **完整的生产执行闭环**：计划→工单→工序→异常→完工，状态机清晰
2. **AI 深度集成**：Coze 对话 + 排产工作流是核心竞争力，Mock 降级设计成熟
3. **前后端对齐度高**：14 个 API 模块与 11 个 Controller 基本 1:1
4. **权限与通知体系**：RBAC + WebSocket 实时推送已可用
5. **UI 完成度高**：Element Plus 主题系统、驾驶舱图表、排产甘特图

### 核心缺口（完善 MES 必补）

1. **主数据层仍不完整**：产品、BOM 缺；工艺路线与设备已补齐
2. **库存只有预警没有台账**：无法支撑真实物料闭环
3. **质量模块名存实亡**：需检验单与不合格品流程
4. **计划详情与子工单视图**：Phase 1 剩余项
5. **工程化不足**：增量迁移脚本已提供，仍无 Flyway、无自动化测试

### 建议的下一步（若只选 3 件事）

| 顺序 | 动作 | 理由 |
|------|------|------|
| 1 | 产品主数据 + 计划/工单绑定 SKU | 工艺路线已可配，下一步按产品选路线 |
| 2 | BOM + 库存事务 | 物料从预警走向真实闭环 |
| 3 | 计划详情页 + 操作审计 | 管理侧可追溯性 |

---

*本报告由多子代理并行分析后端、前端、数据库与文档后汇总生成。如需针对某一 Phase 输出详细技术设计或开始实现，可指定优先级模块。*
