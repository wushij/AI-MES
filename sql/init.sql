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
--   admin      → 管理员（全部权限）
--   supervisor → 车间主管
--   planner    → 计划与物控
--   engineer   → 设备与品质工程师
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
DROP TABLE IF EXISTS sys_operation_log;
DROP TABLE IF EXISTS sys_coze_config;
DROP TABLE IF EXISTS ai_chat_log;
DROP TABLE IF EXISTS exc_event;
DROP TABLE IF EXISTS prod_process_record;
DROP TABLE IF EXISTS prod_work_order;
DROP TABLE IF EXISTS prod_plan;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS prod_team;
DROP TABLE IF EXISTS qms_inspection_record;
DROP TABLE IF EXISTS qms_inspection_plan;
DROP TABLE IF EXISTS inv_transaction;
DROP TABLE IF EXISTS mdm_bom_item;
DROP TABLE IF EXISTS mdm_bom;
DROP TABLE IF EXISTS mdm_product;
DROP TABLE IF EXISTS mat_material;
DROP TABLE IF EXISTS mdm_operation_material;
DROP TABLE IF EXISTS mdm_operation_device;
DROP TABLE IF EXISTS mdm_operation_sop;
DROP TABLE IF EXISTS mdm_operation_param;
DROP TABLE IF EXISTS mdm_routing_history;
DROP TABLE IF EXISTS mdm_operation;
DROP TABLE IF EXISTS mdm_routing;
DROP TABLE IF EXISTS dev_repair_order;
DROP TABLE IF EXISTS dev_maintenance_record;
DROP TABLE IF EXISTS dev_maintenance_plan;
DROP TABLE IF EXISTS dev_inspection_record;
DROP TABLE IF EXISTS dev_inspection_plan;
DROP TABLE IF EXISTS dev_device_history;
DROP TABLE IF EXISTS dev_device;
DROP TABLE IF EXISTS dev_device_category;
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
    product_id BIGINT NULL COMMENT '关联产品主数据ID',
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
    product_id BIGINT NULL COMMENT '关联产品主数据ID',
    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
    order_qty INT NOT NULL DEFAULT 1 COMMENT '工单生产数量',
    team_id BIGINT NULL COMMENT '负责班组ID',
    routing_id BIGINT NULL COMMENT '引用工艺路线ID',
    route_version VARCHAR(16) NULL COMMENT '工艺版本快照',
    process_name VARCHAR(50) NULL COMMENT '当前工序',
    progress INT NOT NULL DEFAULT 0 COMMENT '进度 0-100',
    status VARCHAR(20) NOT NULL COMMENT '状态：pending/assigned/producing/exception/done',
    priority INT NOT NULL DEFAULT 2 COMMENT '优先级：1高 2中 3低',
    deadline DATETIME NULL COMMENT '交期',
    scheduled_start_time DATETIME NULL COMMENT 'AI建议开工时间',
    estimated_hours DECIMAL(6, 2) NULL COMMENT 'AI预计工时(小时)',
    scheduling_rank INT NULL COMMENT 'AI建议生产次序',
    scheduling_reason VARCHAR(500) NULL COMMENT 'AI排产排序理由',
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
    operation_id BIGINT NULL COMMENT '工序主数据ID',
    device_id BIGINT NULL COMMENT '报工主设备（兼容）',
    device_ids VARCHAR(255) NULL COMMENT '报工使用设备ID列表，逗号分隔',
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
    KEY idx_exception_device (device_id),
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

-- 产品主数据
CREATE TABLE mdm_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    product_code VARCHAR(32) NOT NULL COMMENT '产品编码',
    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
    spec VARCHAR(100) NULL COMMENT '规格型号',
    unit VARCHAR(10) NOT NULL DEFAULT '件' COMMENT '单位',
    stock_qty DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '成品库存',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态：active/inactive',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_product_code (product_code),
    KEY idx_product_name (product_name),
    KEY idx_product_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品主数据';

-- BOM 头
CREATE TABLE mdm_bom (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    version VARCHAR(16) NOT NULL DEFAULT 'V1.0' COMMENT 'BOM版本',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态：draft/active',
    is_default TINYINT NOT NULL DEFAULT 1 COMMENT '是否默认版本',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_bom_product (product_id),
    CONSTRAINT fk_bom_product FOREIGN KEY (product_id) REFERENCES mdm_product(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM头';

-- BOM 行
CREATE TABLE mdm_bom_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    bom_id BIGINT NOT NULL COMMENT 'BOM头ID',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    qty DECIMAL(10,4) NOT NULL DEFAULT 1 COMMENT '单位用量',
    unit VARCHAR(10) NOT NULL COMMENT '单位',
    loss_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '损耗率(%)',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_bom_item_bom (bom_id),
    KEY idx_bom_item_material (material_id),
    CONSTRAINT fk_bom_item_bom FOREIGN KEY (bom_id) REFERENCES mdm_bom(id) ON DELETE CASCADE,
    CONSTRAINT fk_bom_item_material FOREIGN KEY (material_id) REFERENCES mat_material(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM行';

-- 库存事务流水
CREATE TABLE inv_transaction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    material_id BIGINT NULL COMMENT '物料ID（物料流水时填写）',
    product_id BIGINT NULL COMMENT '产品ID（成品流水时填写）',
    txn_type VARCHAR(16) NOT NULL COMMENT '类型：in/out/pick/return/adjust',
    qty DECIMAL(10,2) NOT NULL COMMENT '变动数量（正数）',
    before_qty DECIMAL(10,2) NOT NULL COMMENT '变动前库存',
    after_qty DECIMAL(10,2) NOT NULL COMMENT '变动后库存',
    ref_type VARCHAR(32) NULL COMMENT '关联类型：material/work_order/manual',
    ref_id BIGINT NULL COMMENT '关联ID',
    operator_id BIGINT NULL COMMENT '操作人ID',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_inv_txn_material (material_id),
    KEY idx_inv_txn_product (product_id),
    KEY idx_inv_txn_time (created_time),
    KEY idx_inv_txn_type (txn_type),
    CONSTRAINT fk_inv_txn_material FOREIGN KEY (material_id) REFERENCES mat_material(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_txn_product FOREIGN KEY (product_id) REFERENCES mdm_product(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存事务流水';

-- 检验计划（按工序）
CREATE TABLE qms_inspection_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    operation_id BIGINT NOT NULL COMMENT '工序ID',
    item_name VARCHAR(64) NOT NULL COMMENT '检验项名称',
    standard VARCHAR(64) NULL COMMENT '标准值',
    min_value VARCHAR(32) NULL COMMENT '下限',
    max_value VARCHAR(32) NULL COMMENT '上限',
    unit VARCHAR(16) NULL COMMENT '单位',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_qms_plan_operation (operation_id),
    CONSTRAINT fk_qms_plan_operation FOREIGN KEY (operation_id) REFERENCES mdm_operation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检验计划';

-- 检验记录
CREATE TABLE qms_inspection_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    work_order_id BIGINT NOT NULL COMMENT '工单ID',
    process_record_id BIGINT NULL COMMENT '工序记录ID',
    operation_id BIGINT NULL COMMENT '工序ID',
    plan_id BIGINT NULL COMMENT '检验计划ID',
    item_name VARCHAR(64) NOT NULL COMMENT '检验项名称',
    measured_value VARCHAR(64) NULL COMMENT '实测值',
    result VARCHAR(16) NOT NULL COMMENT '结果：pass/fail',
    inspector_id BIGINT NULL COMMENT '检验人ID',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_qms_record_order (work_order_id),
    KEY idx_qms_record_process (process_record_id),
    CONSTRAINT fk_qms_record_order FOREIGN KEY (work_order_id) REFERENCES prod_work_order(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检验记录';

-- 设备分类
CREATE TABLE dev_device_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父分类ID，0为根',
    category_name VARCHAR(64) NOT NULL COMMENT '分类名称',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_category_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分类';

-- 设备台账
CREATE TABLE dev_device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    device_code VARCHAR(32) NOT NULL COMMENT '设备编号',
    device_name VARCHAR(64) NOT NULL COMMENT '设备名称',
    category_id BIGINT NULL COMMENT '分类ID',
    device_type VARCHAR(32) NULL COMMENT '设备类型（兼容字段）',
    brand VARCHAR(64) NULL COMMENT '品牌',
    model VARCHAR(64) NULL COMMENT '型号',
    serial_number VARCHAR(64) NULL COMMENT '序列号',
    workshop VARCHAR(64) NULL COMMENT '所属车间',
    line_name VARCHAR(64) NULL COMMENT '所属产线',
    station VARCHAR(64) NULL COMMENT '所属工位',
    manager_id BIGINT NULL COMMENT '负责人用户ID',
    purchase_date DATE NULL COMMENT '购买日期',
    install_date DATE NULL COMMENT '安装日期',
    enable_date DATE NULL COMMENT '启用日期',
    warranty_date DATE NULL COMMENT '保修截止',
    status VARCHAR(16) NOT NULL DEFAULT 'idle' COMMENT '状态：idle/running/paused/stopped/maintenance/repairing/fault/scrapped',
    team_id BIGINT NULL COMMENT '责任班组',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_device_code (device_code),
    KEY idx_device_team (team_id),
    KEY idx_device_status (status),
    KEY idx_device_category (category_id),
    KEY idx_device_manager (manager_id),
    CONSTRAINT fk_device_category FOREIGN KEY (category_id) REFERENCES dev_device_category(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备台账';

-- 设备履历
CREATE TABLE dev_device_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    action_type VARCHAR(32) NOT NULL COMMENT '动作类型',
    action_desc VARCHAR(255) NOT NULL COMMENT '动作描述',
    operator_id BIGINT NULL COMMENT '操作人ID',
    operator_name VARCHAR(50) NULL COMMENT '操作人姓名',
    related_event_id BIGINT NULL COMMENT '关联异常ID',
    before_value VARCHAR(64) NULL COMMENT '变更前值',
    after_value VARCHAR(64) NULL COMMENT '变更后值',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_history_device (device_id),
    KEY idx_history_time (create_time),
    CONSTRAINT fk_history_device FOREIGN KEY (device_id) REFERENCES dev_device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备履历';

-- 设备点检计划
CREATE TABLE dev_inspection_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plan_code VARCHAR(32) NOT NULL COMMENT '计划编号',
    plan_name VARCHAR(64) NOT NULL COMMENT '计划名称',
    device_id BIGINT NULL COMMENT '绑定设备，NULL 表示非设备专属',
    category_id BIGINT NULL COMMENT '绑定分类',
    cycle_type VARCHAR(16) NOT NULL DEFAULT 'daily' COMMENT '周期：daily/weekly/monthly',
    check_items TEXT NOT NULL COMMENT '点检项目 JSON 数组',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_inspection_plan_code (plan_code),
    KEY idx_inspection_plan_device (device_id),
    KEY idx_inspection_plan_category (category_id),
    CONSTRAINT fk_inspection_plan_device FOREIGN KEY (device_id) REFERENCES dev_device(id) ON DELETE CASCADE,
    CONSTRAINT fk_inspection_plan_category FOREIGN KEY (category_id) REFERENCES dev_device_category(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备点检计划';

-- 设备点检记录
CREATE TABLE dev_inspection_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    record_no VARCHAR(32) NOT NULL COMMENT '点检单号',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    plan_id BIGINT NULL COMMENT '关联计划ID',
    plan_name VARCHAR(64) NULL COMMENT '计划名称快照',
    inspector_id BIGINT NULL COMMENT '点检人ID',
    inspector_name VARCHAR(50) NULL COMMENT '点检人姓名',
    inspect_time DATETIME NOT NULL COMMENT '点检时间',
    check_items TEXT NOT NULL COMMENT '点检项及结果 JSON',
    is_normal TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0异常',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_inspection_record_no (record_no),
    KEY idx_inspection_record_device (device_id),
    KEY idx_inspection_record_time (inspect_time),
    CONSTRAINT fk_inspection_record_device FOREIGN KEY (device_id) REFERENCES dev_device(id) ON DELETE CASCADE,
    CONSTRAINT fk_inspection_record_plan FOREIGN KEY (plan_id) REFERENCES dev_inspection_plan(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备点检记录';

-- 设备保养计划
CREATE TABLE dev_maintenance_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plan_code VARCHAR(32) NOT NULL COMMENT '计划编号',
    plan_name VARCHAR(64) NOT NULL COMMENT '计划名称',
    device_id BIGINT NULL COMMENT '绑定设备',
    category_id BIGINT NULL COMMENT '绑定分类',
    cycle_type VARCHAR(16) NOT NULL DEFAULT 'monthly' COMMENT '周期：daily/weekly/monthly',
    maintenance_items TEXT NOT NULL COMMENT '保养项目 JSON 数组',
    next_due_date DATE NULL COMMENT '下次保养到期日',
    last_maintenance_date DATE NULL COMMENT '上次保养日期',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_maintenance_plan_code (plan_code),
    KEY idx_maintenance_plan_device (device_id),
    KEY idx_maintenance_plan_due (next_due_date),
    CONSTRAINT fk_maintenance_plan_device FOREIGN KEY (device_id) REFERENCES dev_device(id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_plan_category FOREIGN KEY (category_id) REFERENCES dev_device_category(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备保养计划';

-- 设备保养记录
CREATE TABLE dev_maintenance_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    record_no VARCHAR(32) NOT NULL COMMENT '保养单号',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    plan_id BIGINT NULL COMMENT '关联计划ID',
    plan_name VARCHAR(64) NULL COMMENT '计划名称快照',
    maintainer_id BIGINT NULL COMMENT '保养人ID',
    maintainer_name VARCHAR(50) NULL COMMENT '保养人姓名',
    maintenance_time DATETIME NOT NULL COMMENT '保养时间',
    maintenance_items TEXT NOT NULL COMMENT '保养项及结果 JSON',
    is_completed TINYINT NOT NULL DEFAULT 1 COMMENT '1完成 0未完成',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_maintenance_record_no (record_no),
    KEY idx_maintenance_record_device (device_id),
    KEY idx_maintenance_record_time (maintenance_time),
    CONSTRAINT fk_maintenance_record_device FOREIGN KEY (device_id) REFERENCES dev_device(id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_record_plan FOREIGN KEY (plan_id) REFERENCES dev_maintenance_plan(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备保养记录';

-- 设备维修单
CREATE TABLE dev_repair_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    repair_no VARCHAR(32) NOT NULL COMMENT '维修单号',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    event_id BIGINT NULL COMMENT '关联异常ID',
    fault_reason VARCHAR(255) NOT NULL COMMENT '故障原因',
    fault_code VARCHAR(32) NULL COMMENT '故障代码',
    description TEXT NULL COMMENT '故障描述',
    status VARCHAR(16) NOT NULL DEFAULT 'open' COMMENT '状态：open/processing/completed/cancelled',
    reporter_id BIGINT NULL COMMENT '报修人ID',
    reporter_name VARCHAR(50) NULL COMMENT '报修人姓名',
    repairer_id BIGINT NULL COMMENT '维修人ID',
    repairer_name VARCHAR(50) NULL COMMENT '维修人姓名',
    report_time DATETIME NOT NULL COMMENT '报修时间',
    start_time DATETIME NULL COMMENT '开始维修时间',
    end_time DATETIME NULL COMMENT '完成时间',
    repair_minutes INT NULL COMMENT '维修耗时（分钟）',
    repair_action TEXT NULL COMMENT '维修措施',
    repair_result VARCHAR(64) NULL COMMENT '维修结果',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_repair_no (repair_no),
    KEY idx_repair_device (device_id),
    KEY idx_repair_status (status),
    KEY idx_repair_report_time (report_time),
    CONSTRAINT fk_repair_device FOREIGN KEY (device_id) REFERENCES dev_device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备维修单';

-- 工艺路线
CREATE TABLE mdm_routing (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    route_code VARCHAR(32) NOT NULL COMMENT '路线编码',
    route_name VARCHAR(64) NOT NULL COMMENT '路线名称',
    product_id BIGINT NULL COMMENT '关联产品主数据ID',
    product_name VARCHAR(100) NULL COMMENT '适用产品（可选，空为通用）',
    version VARCHAR(16) NOT NULL DEFAULT 'V1.0' COMMENT '当前版本',
    status VARCHAR(16) NOT NULL DEFAULT 'published' COMMENT '状态：draft/pending_approval/published/rejected/disabled',
    rejected_reason VARCHAR(255) NULL COMMENT '驳回原因',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认路线',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_route_code (route_code),
    KEY idx_routing_product (product_name),
    KEY idx_routing_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工艺路线';

-- 工艺工序
CREATE TABLE mdm_operation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    routing_id BIGINT NOT NULL COMMENT '路线ID',
    operation_code VARCHAR(32) NULL COMMENT '工序编号',
    seq_no INT NOT NULL COMMENT '工序序号',
    operation_name VARCHAR(50) NOT NULL COMMENT '工序名称',
    standard_hours DECIMAL(8,2) NULL COMMENT '标准工时(小时)',
    prep_hours DECIMAL(8,2) NULL COMMENT '准备时间(小时)',
    changeover_hours DECIMAL(8,2) NULL COMMENT '换型时间(小时)',
    need_report TINYINT NOT NULL DEFAULT 1 COMMENT '是否报工',
    need_check TINYINT NOT NULL DEFAULT 0 COMMENT '是否质检',
    need_scan TINYINT NOT NULL DEFAULT 0 COMMENT '是否扫码',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_routing_seq (routing_id, seq_no),
    KEY idx_operation_routing (routing_id),
    CONSTRAINT fk_operation_routing FOREIGN KEY (routing_id) REFERENCES mdm_routing(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工艺工序';

-- 工艺参数
CREATE TABLE mdm_operation_param (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    operation_id BIGINT NOT NULL COMMENT '工序ID',
    param_name VARCHAR(64) NOT NULL COMMENT '参数名称',
    param_value VARCHAR(64) NULL COMMENT '标准值',
    min_value VARCHAR(32) NULL COMMENT '最小值',
    max_value VARCHAR(32) NULL COMMENT '最大值',
    unit VARCHAR(16) NULL COMMENT '单位',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_param_operation (operation_id),
    CONSTRAINT fk_param_operation FOREIGN KEY (operation_id) REFERENCES mdm_operation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工艺参数';

-- 工艺版本/变更记录
CREATE TABLE mdm_routing_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    routing_id BIGINT NOT NULL COMMENT '路线ID',
    version VARCHAR(16) NOT NULL COMMENT '版本号',
    action_type VARCHAR(32) NOT NULL COMMENT '动作类型',
    action_desc VARCHAR(255) NOT NULL COMMENT '描述',
    operator_id BIGINT NULL COMMENT '操作人ID',
    operator_name VARCHAR(50) NULL COMMENT '操作人姓名',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_routing_history_route (routing_id),
    CONSTRAINT fk_routing_history FOREIGN KEY (routing_id) REFERENCES mdm_routing(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工艺变更记录';

-- 工序 SOP
CREATE TABLE mdm_operation_sop (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    operation_id BIGINT NOT NULL COMMENT '工序ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type VARCHAR(32) NOT NULL COMMENT 'pdf/image/video/doc',
    file_path VARCHAR(512) NOT NULL COMMENT '存储相对路径',
    file_size BIGINT NULL COMMENT '字节数',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_sop_operation (operation_id),
    CONSTRAINT fk_sop_operation FOREIGN KEY (operation_id) REFERENCES mdm_operation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序SOP';

-- 工序设备绑定
CREATE TABLE mdm_operation_device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    operation_id BIGINT NOT NULL COMMENT '工序ID',
    device_id BIGINT NULL COMMENT '具体设备',
    category_id BIGINT NULL COMMENT '设备分类',
    bind_type VARCHAR(16) NOT NULL COMMENT 'device/category',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_op_device_operation (operation_id),
    KEY idx_op_device_device (device_id),
    KEY idx_op_device_category (category_id),
    CONSTRAINT fk_op_device_operation FOREIGN KEY (operation_id) REFERENCES mdm_operation(id) ON DELETE CASCADE,
    CONSTRAINT fk_op_device_device FOREIGN KEY (device_id) REFERENCES dev_device(id) ON DELETE CASCADE,
    CONSTRAINT fk_op_device_category FOREIGN KEY (category_id) REFERENCES dev_device_category(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序设备绑定';

-- 工序物料绑定
CREATE TABLE mdm_operation_material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    operation_id BIGINT NOT NULL COMMENT '工序ID',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    qty DECIMAL(12,4) NOT NULL DEFAULT 1 COMMENT '用量',
    unit VARCHAR(16) NULL COMMENT '单位',
    material_type VARCHAR(16) NOT NULL DEFAULT 'raw' COMMENT 'raw/semi/aux/tooling',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_op_material_operation (operation_id),
    KEY idx_op_material_material (material_id),
    CONSTRAINT fk_op_material_operation FOREIGN KEY (operation_id) REFERENCES mdm_operation(id) ON DELETE CASCADE,
    CONSTRAINT fk_op_material_material FOREIGN KEY (material_id) REFERENCES mat_material(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序物料绑定';

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
-- 增量升级脚本见：sql/migrations/
CREATE TABLE sys_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NULL COMMENT '操作人ID',
    username VARCHAR(50) NULL COMMENT '操作人账号',
    real_name VARCHAR(50) NULL COMMENT '操作人姓名',
    module VARCHAR(32) NOT NULL COMMENT '模块',
    action VARCHAR(32) NOT NULL COMMENT '操作',
    target_type VARCHAR(32) NULL COMMENT '目标类型',
    target_id VARCHAR(64) NULL COMMENT '目标ID',
    description VARCHAR(255) NULL COMMENT '描述',
    ip_address VARCHAR(64) NULL COMMENT 'IP地址',
    success TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功：1成功 0失败',
    error_msg VARCHAR(255) NULL COMMENT '失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    KEY idx_op_log_user (user_id),
    KEY idx_op_log_time (create_time),
    KEY idx_op_log_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志';

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
-- admin 拥有所有权限
('admin', '生产计划'), ('admin', '工单管理'), ('admin', '班组'), ('admin', '物料'), ('admin', '产品管理'), ('admin', '设备'), ('admin', '排产'),
('admin', '工序进度'), ('admin', '工单反馈'), ('admin', '异常上报'), ('admin', 'AI 客服'),
('admin', '用户管理'), ('admin', '角色管理'), ('admin', 'Coze 配置'), ('admin', '系统配置'), ('admin', '工艺管理'), ('admin', '工艺审批'),
-- supervisor（车间主管）：全面管理车间运行，含工单反馈查阅；工艺审批移至专业工程师
('supervisor', '生产计划'), ('supervisor', '工单管理'), ('supervisor', '工单反馈'), ('supervisor', '班组'),
('supervisor', '物料'), ('supervisor', '设备'), ('supervisor', '排产'), ('supervisor', '异常上报'), ('supervisor', 'AI 客服'), ('supervisor', '工艺管理'), ('supervisor', '产品管理'),
-- planner（计划与物控）：主导计划、排产、物料与工艺，需查看工单执行情况
('planner', '生产计划'), ('planner', '工单管理'), ('planner', '物料'), ('planner', '产品管理'), ('planner', '排产'), ('planner', '工艺管理'), ('planner', 'AI 客服'),
-- engineer（设备与品质工程师）：设备运维 + 工序质量管控 + 工艺审批
('engineer', '设备'), ('engineer', '工单管理'), ('engineer', '工序进度'), ('engineer', '异常上报'), ('engineer', '工艺管理'), ('engineer', '工艺审批'), ('engineer', 'AI 客服'), ('engineer', '产品管理'),
-- worker（普通操作工）：仅限执行层操作
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
(3, 'worker',     '$2b$12$a.77zLvBGe70Uew9BZlQau4SlwoGi2vMiSUcvaq/BrHcVwHA2IZyG', '王芳',       'worker',     1,    1, NOW(), NOW()),
(4, 'planner',    '$2b$12$a.77zLvBGe70Uew9BZlQau4SlwoGi2vMiSUcvaq/BrHcVwHA2IZyG', '李计划',     'planner',    NULL, 1, NOW(), NOW()),
(5, 'engineer',   '$2b$12$a.77zLvBGe70Uew9BZlQau4SlwoGi2vMiSUcvaq/BrHcVwHA2IZyG', '赵工',       'engineer',   NULL, 1, NOW(), NOW());

UPDATE prod_team SET leader_id = 2 WHERE id = 1;

-- 设备分类
INSERT INTO dev_device_category (id, parent_id, category_name, sort_no, created_time, updated_time) VALUES
(1, 0, '生产设备', 1, NOW(), NOW()),
(2, 1, '装配设备', 1, NOW(), NOW()),
(3, 1, '检测设备', 2, NOW(), NOW()),
(4, 0, '物流设备', 2, NOW(), NOW());

-- 设备台账
INSERT INTO dev_device (id, device_code, device_name, category_id, device_type, brand, model, workshop, line_name, station, manager_id, status, team_id, enable_date, remark, created_time, updated_time) VALUES
(1, 'DEV-001', '装配工位 A1', 2, 'assembly',   '精工', 'ASM-100', '一车间', '产线A', '工位A1', 2, 'fault',       1, '2025-01-10', '甲班主装配位', NOW(), NOW()),
(2, 'DEV-002', '检测台 Q1',   3, 'inspection', '海克斯', 'QC-200', '一车间', '产线A', '工位Q1', 2, 'idle',        1, '2025-02-01', '尺寸检测',     NOW(), NOW()),
(3, 'DEV-003', '包装线 P1',   4, 'packaging',  '联塑',   'PK-50',  '二车间', '产线B', '工位P1', 2, 'idle',        2, '2025-03-15', '乙班包装',     NOW(), NOW());

-- 产品主数据
INSERT INTO mdm_product (id, product_code, product_name, spec, unit, status, remark, created_time, updated_time) VALUES
(1, 'PRD-001', '精密组件C', 'C-Type Precision', '件', 'active', '重点产品', NOW(), NOW()),
(2, 'PRD-002', '标准组件B', 'B-Type Standard',  '件', 'active', '常规产品', NOW(), NOW());

-- 默认工艺路线
INSERT INTO mdm_routing (id, route_code, route_name, product_id, product_name, version, status, is_default, enabled, remark, created_time, updated_time) VALUES
(1, 'ROUTE-STD', '标准装配路线', NULL, NULL, 'V1.0', 'published', 1, 1, '默认四道工序，适用于通用产品', NOW(), NOW()),
(2, 'ROUTE-C',   '精密组件C工艺', 1, '精密组件C', 'V1.0', 'published', 0, 1, '精密组件C专用路线', NOW(), NOW());

INSERT INTO mdm_operation (id, routing_id, operation_code, seq_no, operation_name, standard_hours, prep_hours, need_report, need_check, need_scan, remark, created_time, updated_time) VALUES
(1, 1, 'OP10', 1, '备料', 1.00, 0.25, 1, 0, 0, NULL, NOW(), NOW()),
(2, 1, 'OP20', 2, '装配', 4.00, 0.50, 1, 0, 1, NULL, NOW(), NOW()),
(3, 1, 'OP30', 3, '检测', 1.50, 0.20, 1, 1, 0, '需全检', NOW(), NOW()),
(4, 1, 'OP40', 4, '包装', 1.00, 0.15, 1, 0, 0, NULL, NOW(), NOW()),
(5, 2, 'OP10', 1, '备料', 1.20, 0.30, 1, 0, 0, '精密件备料', NOW(), NOW()),
(6, 2, 'OP20', 2, '装配', 5.00, 0.60, 1, 0, 1, '高精度装配', NOW(), NOW()),
(7, 2, 'OP30', 3, '检测', 2.00, 0.25, 1, 1, 1, '三坐标检测', NOW(), NOW()),
(8, 2, 'OP40', 4, '包装', 1.00, 0.15, 1, 0, 0, NULL, NOW(), NOW());

INSERT INTO mdm_operation_param (operation_id, param_name, param_value, min_value, max_value, unit, created_time, updated_time) VALUES
(2, '扭矩', '12', '10', '14', 'N·m', NOW(), NOW()),
(2, '温度', '25', '20', '30', '℃', NOW(), NOW()),
(3, '尺寸公差', '0.05', '0', '0.05', 'mm', NOW(), NOW()),
(6, '扭矩', '15', '13', '17', 'N·m', NOW(), NOW()),
(7, '尺寸公差', '0.02', '0', '0.02', 'mm', NOW(), NOW());

-- 检验计划（检测工序）
INSERT INTO qms_inspection_plan (operation_id, item_name, standard, min_value, max_value, unit, sort_no, created_time, updated_time) VALUES
(3, '外观检查', '无划痕', NULL, NULL, NULL, 1, NOW(), NOW()),
(3, '尺寸公差', '0.05', '0', '0.05', 'mm', 2, NOW(), NOW()),
(7, '尺寸公差', '0.02', '0', '0.02', 'mm', 1, NOW(), NOW()),
(7, '表面粗糙度', 'Ra1.6', NULL, 'Ra1.6', NULL, 2, NOW(), NOW());

INSERT INTO mdm_routing_history (routing_id, version, action_type, action_desc, operator_id, operator_name, create_time) VALUES
(1, 'V1.0', 'publish', '初始发布标准装配路线', 2, '张主管', NOW()),
(2, 'V1.0', 'publish', '初始发布精密组件C工艺', 2, '张主管', NOW());

INSERT INTO mdm_operation_device (operation_id, device_id, category_id, bind_type, created_time) VALUES
(2, NULL, 2, 'category', NOW()),
(2, 2, NULL, 'device', NOW());

INSERT INTO mdm_operation_material (operation_id, material_id, qty, unit, material_type, remark, created_time, updated_time) VALUES
(1, 1, 2.0000, 'kg', 'raw', '备料主材', NOW(), NOW()),
(1, 2, 1.0000, 'pcs', 'aux', '辅料', NOW(), NOW());

-- 生产计划
INSERT INTO prod_plan (id, plan_no, product_id, product_name, plan_qty, plan_date, status, created_by, remark, release_time, created_time, updated_time) VALUES
(1, 'PLAN-2026-001', 1, '精密组件C', 300, '2026-07-01', 'released', 2, '重点订单，优先安排', NOW(), NOW(), NOW()),
(2, 'PLAN-2026-002', 2, '标准组件B', 180, '2026-07-02', 'draft',    2, '常规生产计划',       NULL, NOW(), NOW());

-- 生产工单
INSERT INTO prod_work_order (id, order_no, plan_id, product_id, product_name, order_qty, team_id, routing_id, route_version, process_name, progress, status, priority, deadline, claim_user_id, remark, created_time, updated_time) VALUES
(1, 'WO-2026-001', 1,    1, '精密组件C', 100, 1,    2, 'V1.0', '装配', 65, 'producing', 1, '2026-07-02 18:00:00', 3,    '核心客户订单',     NOW(), NOW()),
(2, 'WO-2026-002', 1,    1, '精密组件C', 100, 2,    2, 'V1.0', '备料',  0, 'assigned',  2, '2026-07-03 18:00:00', NULL, '待班组领取',       NOW(), NOW()),
(3, 'WO-2026-003', NULL, 2, '标准组件B',  50, NULL, 1, 'V1.0', '备料',  0, 'pending',   3, '2026-07-04 18:00:00', NULL, '手动新增工单样例', NOW(), NOW());

-- 工序进度（含 operation_id / device_id 以贯通质量与设备）
INSERT INTO prod_process_record (work_order_id, operation_id, device_id, process_name, seq_no, status, start_time, end_time, remark, created_time, updated_time) VALUES
(1, 5, NULL, '备料', 1, 'done',    '2026-06-30 08:00:00', '2026-06-30 09:00:00', '物料齐套',     NOW(), NOW()),
(1, 6, 1,    '装配', 2, 'running', '2026-07-05 08:00:00', NULL,                  '今日报工运行', NOW(), NOW()),
(2, 7, 2,    '检测', 3, 'done',    '2026-07-05 09:00:00', '2026-07-05 10:30:00', '今日检测完成', NOW(), NOW()),
(1, 7, NULL, '检测', 3, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(1, 8, NULL, '包装', 4, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(2, 5, NULL, '备料', 1, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(2, 6, NULL, '装配', 2, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(2, 7, NULL, '检测', 3, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(2, 8, NULL, '包装', 4, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(3, 1, NULL, '备料', 1, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(3, 2, NULL, '装配', 2, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(3, 3, NULL, '检测', 3, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW()),
(3, 4, NULL, '包装', 4, 'waiting', NULL,                  NULL,                  NULL,           NOW(), NOW());

-- 异常事件
INSERT INTO exc_event (id, event_no, event_type, work_order_id, device_id, description, status, reporter_id, handler_id, occur_time, create_time, handle_time, handle_action, handle_result) VALUES
(1, 'EXC-2026-001', 'shortage', 1, NULL, '装配工位反馈部分紧固件短缺，需要仓库补料。',       'processing', 3, 2,    '2026-06-30 10:30:00', NOW(), NULL, NULL, NULL),
(2, 'EXC-2026-002', 'quality',  2, NULL, '首件检测出现尺寸偏差，待主管确认处理方案。',       'open',       3, NULL, '2026-06-30 11:15:00', NOW(), NULL, NULL, NULL),
(3, 'EXC-2026-003', 'device',   1, 1,    '装配工位 A1 伺服电机异响，已暂停该工位生产。',     'open',       3, NULL, '2026-07-04 09:20:00', NOW(), NULL, NULL, NULL);

-- 设备履历（演示）
INSERT INTO dev_device_history (device_id, action_type, action_desc, operator_id, operator_name, related_event_id, before_value, after_value, create_time) VALUES
(1, 'create', '新建设备台账', 2, '张主管', NULL, NULL, 'idle', '2025-01-10 08:00:00'),
(1, 'status', '设备投入运行', 2, '张主管', NULL, 'idle', 'running', '2025-01-10 09:00:00'),
(1, 'exception', '设备异常上报：伺服电机异响', 3, '李员工', 3, 'running', 'fault', '2026-07-04 09:20:00'),
(1, 'inspection', '设备点检：发现异常（伺服电机异响）', 2, '张主管', NULL, NULL, 'abnormal', '2026-07-04 08:30:00'),
(2, 'inspection', '设备点检：全部正常', 2, '张主管', NULL, NULL, 'normal', '2026-07-05 08:00:00'),
(1, 'maintenance', '设备保养：A1工位专项保养', 2, '张主管', NULL, NULL, 'completed', '2026-06-29 16:00:00'),
(1, 'repair', '设备报修：伺服电机异响', 3, '李员工', NULL, 'fault', 'open', '2026-07-04 09:25:00');

-- 设备点检计划
INSERT INTO dev_inspection_plan (id, plan_code, plan_name, device_id, category_id, cycle_type, check_items, enabled, remark, created_time, updated_time) VALUES
(1, 'INSP-P-001', '装配设备日点检', NULL, 2, 'daily', '["设备外观与清洁","润滑油位","紧固件检查","安全装置有效性","运行异响检查"]', 1, '装配类设备通用日点检', NOW(), NOW()),
(2, 'INSP-P-002', '检测设备日点检', NULL, 3, 'daily', '["外观与清洁","校准状态","测量精度抽检","数据线/接口","安全标识"]', 1, '检测类设备通用日点检', NOW(), NOW()),
(3, 'INSP-P-003', 'A1工位专项点检', 1, NULL, 'daily', '["伺服电机异响","导轨润滑","气压表读数","急停按钮","工装夹具"]', 1, 'DEV-001 专项点检', NOW(), NOW());

-- 设备点检记录
INSERT INTO dev_inspection_record (id, record_no, device_id, plan_id, plan_name, inspector_id, inspector_name, inspect_time, check_items, is_normal, remark, created_time) VALUES
(1, 'INSP-20260704-001', 1, 3, 'A1工位专项点检', 2, '张主管', '2026-07-04 08:30:00',
 '[{"itemName":"伺服电机异响","isNormal":false,"remark":"轻微异响"},{"itemName":"导轨润滑","isNormal":true,"remark":""},{"itemName":"气压表读数","isNormal":true,"remark":""},{"itemName":"急停按钮","isNormal":true,"remark":""},{"itemName":"工装夹具","isNormal":true,"remark":""}]',
 0, '发现伺服电机异响，已报修', '2026-07-04 08:30:00'),
(2, 'INSP-20260705-001', 2, 2, '检测设备日点检', 2, '张主管', '2026-07-05 08:00:00',
 '[{"itemName":"外观与清洁","isNormal":true,"remark":""},{"itemName":"校准状态","isNormal":true,"remark":""},{"itemName":"测量精度抽检","isNormal":true,"remark":""},{"itemName":"数据线/接口","isNormal":true,"remark":""},{"itemName":"安全标识","isNormal":true,"remark":""}]',
 1, NULL, '2026-07-05 08:00:00');

-- 设备保养计划
INSERT INTO dev_maintenance_plan (id, plan_code, plan_name, device_id, category_id, cycle_type, maintenance_items, next_due_date, last_maintenance_date, enabled, remark, created_time, updated_time) VALUES
(1, 'MAINT-P-001', '装配设备月度保养', NULL, 2, 'monthly', '["导轨润滑","传动皮带检查","电气接线紧固","过滤器清洁","安全回路测试"]', '2026-07-01', '2026-06-01', 1, '装配类设备月度保养', NOW(), NOW()),
(2, 'MAINT-P-002', '检测设备季度保养', NULL, 3, 'monthly', '["校准证书核查","传感器清洁","基准块校验","软件版本确认","接地电阻测试"]', '2026-08-01', '2026-07-01', 1, '检测类设备保养', NOW(), NOW()),
(3, 'MAINT-P-003', 'A1工位专项保养', 1, NULL, 'weekly', '["伺服驱动器散热","丝杠润滑","气管接头","急停回路","工装磨损检查"]', '2026-07-06', '2026-06-29', 1, 'DEV-001 周保养', NOW(), NOW());

-- 设备保养记录
INSERT INTO dev_maintenance_record (id, record_no, device_id, plan_id, plan_name, maintainer_id, maintainer_name, maintenance_time, maintenance_items, is_completed, remark, created_time) VALUES
(1, 'MAINT-20260629-001', 1, 3, 'A1工位专项保养', 2, '张主管', '2026-06-29 16:00:00',
 '[{"itemName":"伺服驱动器散热","done":true,"remark":""},{"itemName":"丝杠润滑","done":true,"remark":""},{"itemName":"气管接头","done":true,"remark":""},{"itemName":"急停回路","done":true,"remark":""},{"itemName":"工装磨损检查","done":true,"remark":"轻微磨损"}]',
 1, '周保养完成', '2026-06-29 16:00:00');

-- 设备维修单
INSERT INTO dev_repair_order (id, repair_no, device_id, event_id, fault_reason, fault_code, description, status, reporter_id, reporter_name, repairer_id, repairer_name, report_time, start_time, end_time, repair_minutes, repair_action, repair_result, remark, created_time, updated_time) VALUES
(1, 'REP-20260704-001', 1, 3, '伺服电机异响', 'E-SERVO-01', '装配工位 A1 伺服电机运行时有轻微异响，影响加工精度。', 'open', 3, '李员工', NULL, NULL, '2026-07-04 09:25:00', NULL, NULL, NULL, NULL, NULL, '待安排维修', NOW(), NOW());

-- 物料库存
INSERT INTO mat_material (id, material_code, material_name, stock_qty, safety_stock, unit, alert_status, remark, created_time, updated_time) VALUES
(1, 'MAT-001', '精密螺丝 M3',  50.00,  200.00, '个', 'warning', '需尽快补料', NOW(), NOW()),
(2, 'MAT-002', '标准外壳 B',  560.00,  200.00, '件', 'normal',  '库存充足',   NOW(), NOW()),
(3, 'MAT-003', '装配胶水',     18.00,   30.00, '瓶', 'warning', '采购在途',   NOW(), NOW());

-- BOM（精密组件C）
INSERT INTO mdm_bom (id, product_id, version, status, is_default, remark, created_time, updated_time) VALUES
(1, 1, 'V1.0', 'active', 1, '精密组件C默认BOM', NOW(), NOW());

INSERT INTO mdm_bom_item (bom_id, material_id, qty, unit, loss_rate, remark, created_time, updated_time) VALUES
(1, 1, 4.0000, '个', 2.00, '紧固件', NOW(), NOW()),
(1, 2, 1.0000, '件', 0.00, '外壳',   NOW(), NOW()),
(1, 3, 0.5000, '瓶', 5.00, '胶水',   NOW(), NOW());

-- 库存流水（演示）
INSERT INTO inv_transaction (material_id, txn_type, qty, before_qty, after_qty, ref_type, ref_id, operator_id, remark, created_time) VALUES
(1, 'in', 250.00, 0.00, 250.00, 'material', 1, 1, '期初入库', '2026-06-01 08:00:00'),
(1, 'out', 200.00, 250.00, 50.00, 'manual', NULL, 4, '生产领用', '2026-06-28 14:00:00'),
(2, 'in', 560.00, 0.00, 560.00, 'material', 2, 1, '期初入库', '2026-06-01 08:00:00');

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

-- -----------------------------------------------------------------------------
-- 5. 外键约束（演示数据加载后添加，与应用层 ReferentialIntegrityService 双重保障）
-- -----------------------------------------------------------------------------
ALTER TABLE sys_user
  ADD CONSTRAINT fk_user_team
  FOREIGN KEY (team_id) REFERENCES prod_team(id)
  ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE prod_team
  ADD CONSTRAINT fk_team_leader
  FOREIGN KEY (leader_id) REFERENCES sys_user(id)
  ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE prod_plan
  ADD CONSTRAINT fk_plan_created_by
  FOREIGN KEY (created_by) REFERENCES sys_user(id)
  ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE prod_work_order
  ADD CONSTRAINT fk_order_plan
  FOREIGN KEY (plan_id) REFERENCES prod_plan(id)
  ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT fk_order_team
  FOREIGN KEY (team_id) REFERENCES prod_team(id)
  ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT fk_order_claim_user
  FOREIGN KEY (claim_user_id) REFERENCES sys_user(id)
  ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE prod_process_record
  ADD CONSTRAINT fk_process_order
  FOREIGN KEY (work_order_id) REFERENCES prod_work_order(id)
  ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT fk_process_device
  FOREIGN KEY (device_id) REFERENCES dev_device(id)
  ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE exc_event
  ADD CONSTRAINT fk_exc_order
  FOREIGN KEY (work_order_id) REFERENCES prod_work_order(id)
  ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT fk_exc_reporter
  FOREIGN KEY (reporter_id) REFERENCES sys_user(id)
  ON DELETE RESTRICT ON UPDATE CASCADE,
  ADD CONSTRAINT fk_exc_handler
  FOREIGN KEY (handler_id) REFERENCES sys_user(id)
  ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT fk_exc_device
  FOREIGN KEY (device_id) REFERENCES dev_device(id)
  ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE dev_device
  ADD CONSTRAINT fk_device_team
  FOREIGN KEY (team_id) REFERENCES prod_team(id)
  ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT fk_device_manager
  FOREIGN KEY (manager_id) REFERENCES sys_user(id)
  ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE ai_chat_log
  ADD CONSTRAINT fk_chat_user
  FOREIGN KEY (user_id) REFERENCES sys_user(id)
  ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE sys_notification
  ADD CONSTRAINT fk_notification_user
  FOREIGN KEY (user_id) REFERENCES sys_user(id)
  ON DELETE CASCADE ON UPDATE CASCADE;

SET FOREIGN_KEY_CHECKS = 1;

-- 清理孤立异常记录（工单已删除但异常仍残留；新库执行时通常无影响）
DELETE e
FROM exc_event e
LEFT JOIN prod_work_order wo ON e.work_order_id = wo.id
WHERE wo.id IS NULL;

-- =============================================================================
-- 初始化完成
-- =============================================================================
