SET NAMES utf8mb4;

-- P3-T8 设备保养 + 独立维修
-- 执行：mysql -u root -p ai_mes < sql/migrations/006_dev_maintenance_repair.sql

USE `AI-MES`;

CREATE TABLE IF NOT EXISTS dev_maintenance_plan (
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

CREATE TABLE IF NOT EXISTS dev_maintenance_record (
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

CREATE TABLE IF NOT EXISTS dev_repair_order (
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

INSERT IGNORE INTO dev_maintenance_plan (plan_code, plan_name, device_id, category_id, cycle_type, maintenance_items, next_due_date, last_maintenance_date, enabled, remark, created_time, updated_time) VALUES
('MAINT-P-001', '装配设备月度保养', NULL, 2, 'monthly', '["导轨润滑","传动皮带检查","电气接线紧固","过滤器清洁","安全回路测试"]', '2026-07-01', '2026-06-01', 1, '装配类设备月度保养', NOW(), NOW()),
('MAINT-P-002', '检测设备季度保养', NULL, 3, 'monthly', '["校准证书核查","传感器清洁","基准块校验","软件版本确认","接地电阻测试"]', '2026-08-01', '2026-07-01', 1, '检测类设备保养', NOW(), NOW()),
('MAINT-P-003', 'A1工位专项保养', 1, NULL, 'weekly', '["伺服驱动器散热","丝杠润滑","气管接头","急停回路","工装磨损检查"]', '2026-07-06', '2026-06-29', 1, 'DEV-001 周保养', NOW(), NOW());

INSERT IGNORE INTO dev_maintenance_record (record_no, device_id, plan_id, plan_name, maintainer_id, maintainer_name, maintenance_time, maintenance_items, is_completed, remark, created_time) VALUES
('MAINT-20260629-001', 1, 3, 'A1工位专项保养', 2, '张主管', '2026-06-29 16:00:00',
 '[{"itemName":"伺服驱动器散热","done":true,"remark":""},{"itemName":"丝杠润滑","done":true,"remark":""},{"itemName":"气管接头","done":true,"remark":""},{"itemName":"急停回路","done":true,"remark":""},{"itemName":"工装磨损检查","done":true,"remark":"轻微磨损"}]',
 1, '周保养完成', '2026-06-29 16:00:00');

INSERT IGNORE INTO dev_repair_order (repair_no, device_id, event_id, fault_reason, fault_code, description, status, reporter_id, reporter_name, repairer_id, repairer_name, report_time, start_time, end_time, repair_minutes, repair_action, repair_result, remark, created_time, updated_time) VALUES
('REP-20260704-001', 1, 3, '伺服电机异响', 'E-SERVO-01', '装配工位 A1 伺服电机运行时有轻微异响，影响加工精度。', 'open', 3, '李员工', NULL, NULL, '2026-07-04 09:25:00', NULL, NULL, NULL, NULL, NULL, '待安排维修', NOW(), NOW());

INSERT IGNORE INTO dev_device_history (device_id, action_type, action_desc, operator_id, operator_name, related_event_id, before_value, after_value, create_time) VALUES
(1, 'maintenance', '设备保养：A1工位专项保养', 2, '张主管', NULL, NULL, 'completed', '2026-06-29 16:00:00'),
(1, 'repair', '设备报修：伺服电机异响', 3, '李员工', NULL, 'fault', 'open', '2026-07-04 09:25:00');
