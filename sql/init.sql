-- =============================================================================
-- AI-MES 智能生产车间管理平台 · 完整数据库初始化脚本
-- =============================================================================
-- 适用：MySQL 8.0+
-- 默认连接：localhost:3306，账号 root / root
--
-- 执行方式（任选其一）：
--   mysql -uroot -proot < sql/init.sql
--   或在 Navicat / DBeaver 中直接运行本文件
--
-- 演示账号（密码均为 123456，BCrypt 加密存储）：
--   admin      → 管理员
--   supervisor → 车间主管
--   worker     → 普通员工
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. 创建数据库
-- -----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `AI-MES`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `AI-MES`;

-- -----------------------------------------------------------------------------
-- 2. 删除旧表（按依赖顺序）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS sys_coze_config;
DROP TABLE IF EXISTS ai_chat_log;
DROP TABLE IF EXISTS exc_event;
DROP TABLE IF EXISTS prod_process_record;
DROP TABLE IF EXISTS prod_work_order;
DROP TABLE IF EXISTS prod_plan;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS prod_team;
DROP TABLE IF EXISTS mat_material;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_notification;

-- -----------------------------------------------------------------------------
-- 3. 建表
-- -----------------------------------------------------------------------------

-- 班组
CREATE TABLE prod_team (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    team_code VARCHAR(20) NOT NULL COMMENT '班组编号',
    team_name VARCHAR(50) NOT NULL COMMENT '班组名称',
    leader_id BIGINT NULL COMMENT '班组长用户ID',
    member_count INT NOT NULL DEFAULT 0 COMMENT '成员数量',
    line_name VARCHAR(50) NULL COMMENT '所属产线',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_team_code (team_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产班组';

-- 系统用户
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50) NOT NULL COMMENT '登录账号',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt）',
    real_name VARCHAR(50) NOT NULL COMMENT '姓名',
    avatar MEDIUMTEXT NULL COMMENT '头像Base64',
    role VARCHAR(20) NOT NULL COMMENT '角色：admin/supervisor/worker',
    team_id BIGINT NULL COMMENT '所属班组ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_team (team_id),
    KEY idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

-- 生产计划
CREATE TABLE prod_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plan_no VARCHAR(30) NOT NULL COMMENT '计划编号',
    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
    plan_qty INT NOT NULL COMMENT '计划数量',
    plan_date DATE NOT NULL COMMENT '计划日期',
    status VARCHAR(20) NOT NULL COMMENT '状态：draft/released/done',
    created_by BIGINT NOT NULL COMMENT '创建人用户ID',
    remark VARCHAR(255) NULL COMMENT '备注',
    release_time DATETIME NULL COMMENT '下发时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_plan_no (plan_no),
    KEY idx_plan_status (status),
    KEY idx_plan_date (plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产计划';

-- 生产工单
CREATE TABLE prod_work_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    order_no VARCHAR(30) NOT NULL COMMENT '工单号',
    plan_id BIGINT NULL COMMENT '关联计划ID',
    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
    team_id BIGINT NULL COMMENT '负责班组ID',
    process_name VARCHAR(50) NULL COMMENT '当前工序',
    progress INT NOT NULL DEFAULT 0 COMMENT '进度 0-100',
    status VARCHAR(20) NOT NULL COMMENT '状态：pending/assigned/producing/exception/done',
    priority INT NOT NULL DEFAULT 2 COMMENT '优先级：1高 2中 3低',
    deadline DATETIME NULL COMMENT '交期',
    claim_user_id BIGINT NULL COMMENT '认领人用户ID',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_work_order_no (order_no),
    KEY idx_work_order_plan (plan_id),
    KEY idx_work_order_team (team_id),
    KEY idx_work_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产工单';

-- 工序进度记录
CREATE TABLE prod_process_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    work_order_id BIGINT NOT NULL COMMENT '工单ID',
    process_name VARCHAR(50) NOT NULL COMMENT '工序名称',
    seq_no INT NOT NULL COMMENT '工序序号',
    status VARCHAR(20) NOT NULL COMMENT '状态：waiting/running/done',
    start_time DATETIME NULL COMMENT '开始时间',
    end_time DATETIME NULL COMMENT '结束时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_process_order (work_order_id),
    KEY idx_process_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序进度';

-- 异常事件
CREATE TABLE exc_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    event_no VARCHAR(30) NOT NULL COMMENT '异常编号',
    event_type VARCHAR(20) NOT NULL COMMENT '类型：device/shortage/quality/other',
    work_order_id BIGINT NOT NULL COMMENT '关联工单ID',
    device_id BIGINT NULL COMMENT '设备ID',
    description TEXT NOT NULL COMMENT '异常描述',
    status VARCHAR(20) NOT NULL COMMENT '状态：open/processing/closed',
    reporter_id BIGINT NOT NULL COMMENT '上报人用户ID',
    handler_id BIGINT NULL COMMENT '处理人用户ID',
    occur_time DATETIME NOT NULL COMMENT '发生时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    handle_time DATETIME NULL COMMENT '处理时间',
    handle_action TEXT NULL COMMENT '处理措施',
    handle_result VARCHAR(50) NULL COMMENT '处理结果',
    KEY idx_exception_order (work_order_id),
    KEY idx_exception_status (status),
    KEY idx_exception_type (event_type),
    UNIQUE KEY uk_event_no (event_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常事件';

-- 物料库存
CREATE TABLE mat_material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    material_code VARCHAR(30) NOT NULL COMMENT '物料编码',
    material_name VARCHAR(100) NOT NULL COMMENT '物料名称',
    stock_qty DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '当前库存',
    safety_stock DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '安全库存',
    unit VARCHAR(10) NOT NULL COMMENT '单位',
    alert_status VARCHAR(10) NOT NULL COMMENT '预警状态：normal/warning',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_material_code (material_code),
    KEY idx_material_alert (alert_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料库存';

-- AI 客服聊天记录
CREATE TABLE ai_chat_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_message TEXT NOT NULL COMMENT '用户消息',
    ai_response TEXT NOT NULL COMMENT 'AI 回复',
    bot_id VARCHAR(64) NULL COMMENT 'Coze Bot ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_chat_user (user_id),
    KEY idx_chat_session (session_id),
    KEY idx_chat_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服聊天记录';

-- Coze 集成配置（单行表，id 固定为 1）
-- 增量升级脚本见：sql/migrations/001_add_sys_coze_config.sql
CREATE TABLE sys_coze_config (
    id BIGINT PRIMARY KEY COMMENT '固定为 1',
    api_token VARCHAR(512) NULL COMMENT 'Coze API Token / 密钥',
    bot_id VARCHAR(128) NULL COMMENT 'Bot ID',
    api_url VARCHAR(255) NOT NULL DEFAULT 'https://api.coze.cn/v3' COMMENT 'API 基础地址',
    workflow_id VARCHAR(128) NULL COMMENT '排产工作流 ID（可选）',
    welcome_message VARCHAR(500) NULL COMMENT '欢迎语',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Coze 集成配置';

-- 角色权限配置
CREATE TABLE sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    role_key VARCHAR(20) NOT NULL COMMENT '角色标识',
    permission VARCHAR(50) NOT NULL COMMENT '权限名称',
    UNIQUE KEY uk_role_permission (role_key, permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限配置';

INSERT INTO sys_coze_config (id, bot_id, api_url, workflow_id, welcome_message, enabled, update_time) VALUES
(1, '7656623287480991779', 'https://api.coze.cn/v3', '7656988797431791635', '您好，我是 AI-MES 智能助手，可协助查询工单进度、异常处理及 SOP 指导。', 1, NOW());

-- 角色权限初始数据
INSERT INTO sys_role_permission (role_key, permission) VALUES
('admin', '系统配置'), ('admin', '用户管理'), ('admin', 'Coze 配置'),
('supervisor', '生产计划'), ('supervisor', '班组'), ('supervisor', '物料'), ('supervisor', '排产'),
('worker', '工序进度'), ('worker', '工单反馈'), ('worker', '异常上报'), ('worker', 'AI 客服');

-- -----------------------------------------------------------------------------
-- 4. 演示数据
-- -----------------------------------------------------------------------------

-- 班组（leader_id 稍后回填）
INSERT INTO prod_team (id, team_code, team_name, leader_id, member_count, line_name, created_time, updated_time) VALUES
(1, 'T-001', '甲班', NULL, 2, '产线A', NOW(), NOW()),
(2, 'T-002', '乙班', NULL, 1, '产线B', NOW(), NOW());

-- 用户（密码 123456）
INSERT INTO sys_user (id, username, password, real_name, role, team_id, status, create_time, update_time) VALUES
(1, 'admin',      '$2b$12$a.77zLvBGe70Uew9BZlQau4SlwoGi2vMiSUcvaq/BrHcVwHA2IZyG', '系统管理员', 'admin',      NULL, 1, NOW(), NOW()),
(2, 'supervisor', '$2b$12$a.77zLvBGe70Uew9BZlQau4SlwoGi2vMiSUcvaq/BrHcVwHA2IZyG', '张伟',       'supervisor', 1,    1, NOW(), NOW()),
(3, 'worker',     '$2b$12$a.77zLvBGe70Uew9BZlQau4SlwoGi2vMiSUcvaq/BrHcVwHA2IZyG', '王芳',       'worker',     1,    1, NOW(), NOW());

UPDATE prod_team SET leader_id = 2 WHERE id = 1;

-- 生产计划
INSERT INTO prod_plan (id, plan_no, product_name, plan_qty, plan_date, status, created_by, remark, release_time, created_time, updated_time) VALUES
(1, 'PLAN-2026-001', '精密组件C', 300, '2026-07-01', 'released', 2, '重点订单，优先安排', NOW(), NOW(), NOW()),
(2, 'PLAN-2026-002', '标准组件B', 180, '2026-07-02', 'draft',    2, '常规生产计划',       NULL, NOW(), NOW());

-- 生产工单
INSERT INTO prod_work_order (id, order_no, plan_id, product_name, team_id, process_name, progress, status, priority, deadline, claim_user_id, remark, created_time, updated_time) VALUES
(1, 'WO-2026-001', 1,    '精密组件C', 1,    '装配', 65, 'producing', 1, '2026-07-02 18:00:00', 3,    '核心客户订单',     NOW(), NOW()),
(2, 'WO-2026-002', 1,    '精密组件C', 2,    '备料',  0, 'assigned',  2, '2026-07-03 18:00:00', NULL, '待班组领取',       NOW(), NOW()),
(3, 'WO-2026-003', NULL, '标准组件B', NULL, '备料',  0, 'pending',   3, '2026-07-04 18:00:00', NULL, '手动新增工单样例', NOW(), NOW());

-- 工序进度
INSERT INTO prod_process_record (work_order_id, process_name, seq_no, status, start_time, end_time, remark, created_time, updated_time) VALUES
(1, '备料', 1, 'done',    '2026-06-30 08:00:00', '2026-06-30 09:00:00', '物料齐套',     NOW(), NOW()),
(1, '装配', 2, 'running', '2026-06-30 09:30:00', NULL,                  '当前进行中',   NOW(), NOW()),
(1, '检测', 3, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(1, '包装', 4, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(2, '备料', 1, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(2, '装配', 2, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(2, '检测', 3, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(2, '包装', 4, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(3, '备料', 1, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(3, '装配', 2, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(3, '检测', 3, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(3, '包装', 4, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW());

-- 异常事件
INSERT INTO exc_event (id, event_no, event_type, work_order_id, device_id, description, status, reporter_id, handler_id, occur_time, create_time, handle_time, handle_action, handle_result) VALUES
(1, 'EXC-2026-001', 'shortage', 1, NULL, '装配工位反馈部分紧固件短缺，需要仓库补料。',       'processing', 3, 2,    '2026-06-30 10:30:00', NOW(), NULL, NULL, NULL),
(2, 'EXC-2026-002', 'quality',  2, NULL, '首件检测出现尺寸偏差，待主管确认处理方案。',       'open',       3, NULL, '2026-06-30 11:15:00', NOW(), NULL, NULL, NULL);

-- 物料库存
INSERT INTO mat_material (id, material_code, material_name, stock_qty, safety_stock, unit, alert_status, remark, created_time, updated_time) VALUES
(1, 'MAT-001', '精密螺丝 M3',  50.00,  200.00, '个', 'warning', '需尽快补料', NOW(), NOW()),
(2, 'MAT-002', '标准外壳 B',  560.00,  200.00, '件', 'normal',  '库存充足',   NOW(), NOW()),
(3, 'MAT-003', '装配胶水',     18.00,   30.00, '瓶', 'warning', '采购在途',   NOW(), NOW());

-- AI 客服示例记录
INSERT INTO ai_chat_log (user_id, session_id, user_message, ai_response, bot_id, create_time) VALUES
(3, 'sess_demo_001', 'WO-2026-001 进度多少？', 'WO-2026-001 当前进度 65%，处于装配工序，负责班组：甲班。', 'demo-bot', NOW());

-- 系统通知表
CREATE TABLE IF NOT EXISTS `sys_notification` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '接收用户ID',
    `title` VARCHAR(100) NOT NULL COMMENT '标题',
    `content` VARCHAR(255) NOT NULL COMMENT '内容',
    `type` VARCHAR(20) NOT NULL COMMENT '类型: info/warning/danger',
    `target_url` VARCHAR(255) NOT NULL COMMENT '跳转路径',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读: 0未读 1已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_notification_user` (`user_id`),
    KEY `idx_notification_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';

-- 演示用户初始化未读系统通知
-- 1. 动态生成当前真实的库存预警（为 admin 角色和 supervisor 角色用户插入）
INSERT INTO `sys_notification` (`user_id`, `title`, `content`, `type`, `target_url`, `is_read`, `create_time`)
SELECT 
    u.id, 
    '库存预警', 
    CONCAT('物料 “', m.material_name, '” 库存报警（缺口 ', CAST(m.safety_stock - m.stock_qty AS SIGNED), ' ', m.unit, '）'), 
    'warning', 
    '/materials', 
    0, 
    NOW()
FROM `sys_user` u
CROSS JOIN `mat_material` m
WHERE u.role IN ('admin', 'supervisor')
  AND m.stock_qty < m.safety_stock
  AND NOT EXISTS (
    SELECT 1 FROM `sys_notification` sn
    WHERE sn.user_id = u.id
      AND sn.content = CONCAT('物料 “', m.material_name, '” 库存报警（缺口 ', CAST(m.safety_stock - m.stock_qty AS SIGNED), ' ', m.unit, '）')
  );

-- 2. 动态生成当前真实的未处理异常通知（为 admin 角色和 supervisor 角色用户插入）
INSERT INTO `sys_notification` (`user_id`, `title`, `content`, `type`, `target_url`, `is_read`, `create_time`)
SELECT 
    u.id, 
    '异常报警', 
    CONCAT('工单 ', wo.order_no, ' 存在未处理的 ', 
           CASE e.event_type 
               WHEN 'shortage' THEN '缺料' 
               WHEN 'material' THEN '缺料' 
               WHEN 'quality' THEN '质量' 
               WHEN 'device' THEN '设备' 
               ELSE '生产' 
           END, ' 异常！'), 
    'danger', 
    '/exceptions', 
    0, 
    NOW()
FROM `sys_user` u
CROSS JOIN `exc_event` e
JOIN `prod_work_order` wo ON e.work_order_id = wo.id
WHERE u.role IN ('admin', 'supervisor')
  AND e.status IN ('open', 'processing')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_notification` sn
    WHERE sn.user_id = u.id
      AND sn.content = CONCAT('工单 ', wo.order_no, ' 存在未处理的 ',
           CASE e.event_type
               WHEN 'shortage' THEN '缺料'
               WHEN 'material' THEN '缺料'
               WHEN 'quality' THEN '质量'
               WHEN 'device' THEN '设备'
               ELSE '生产'
           END, ' 异常！')
  );

-- 3. 动态生成当前已下发的生产计划通知（仅为管理员和主管插入）
INSERT INTO `sys_notification` (`user_id`, `title`, `content`, `type`, `target_url`, `is_read`, `create_time`)
SELECT 
    u.id, 
    '计划发布', 
    CONCAT('生产计划 “', p.plan_no, '” 状态已更新为已下发。'), 
    'info', 
    '/plans', 
    0, 
    COALESCE(p.release_time, p.created_time)
FROM `sys_user` u
CROSS JOIN `prod_plan` p
WHERE u.role IN ('admin', 'supervisor')
  AND p.status = 'released'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_notification` sn
    WHERE sn.user_id = u.id
      AND sn.content = CONCAT('生产计划 “', p.plan_no, '” 状态已更新为已下发。')
  );

-- 4. 动态生成已指派给班组的工单通知（为该班组的所有普通员工/用户插入）
INSERT INTO `sys_notification` (`user_id`, `title`, `content`, `type`, `target_url`, `is_read`, `create_time`)
SELECT 
    u.id, 
    '新工单指派', 
    CONCAT('您有新的工单任务：工单 ', wo.order_no, '（产品：', wo.product_name, '）已下发至您的班组，请及时开工。'), 
    'info', 
    '/work-orders', 
    0, 
    NOW()
FROM `sys_user` u
JOIN `prod_work_order` wo ON u.team_id = wo.team_id
WHERE u.role = 'worker'
  AND wo.status = 'assigned'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_notification` sn
    WHERE sn.user_id = u.id
      AND sn.content = CONCAT('您有新的工单任务：工单 ', wo.order_no, '（产品：', wo.product_name, '）已下发至您的班组，请及时开工。')
  );

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 初始化完成
-- =============================================================================
