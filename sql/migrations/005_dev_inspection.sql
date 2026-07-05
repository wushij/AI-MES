SET NAMES utf8mb4;

-- P3-T6 设备点检：计划模板 + 执行记录
-- 执行：mysql -u root -p ai_mes < sql/migrations/005_dev_inspection.sql

USE `AI-MES`;

CREATE TABLE IF NOT EXISTS dev_inspection_plan (
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

CREATE TABLE IF NOT EXISTS dev_inspection_record (
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

-- 示例点检计划
INSERT IGNORE INTO dev_inspection_plan (plan_code, plan_name, device_id, category_id, cycle_type, check_items, enabled, remark, created_time, updated_time) VALUES
('INSP-P-001', '装配设备日点检', NULL, 2, 'daily', '["设备外观与清洁","润滑油位","紧固件检查","安全装置有效性","运行异响检查"]', 1, '装配类设备通用日点检', NOW(), NOW()),
('INSP-P-002', '检测设备日点检', NULL, 3, 'daily', '["外观与清洁","校准状态","测量精度抽检","数据线/接口","安全标识"]', 1, '检测类设备通用日点检', NOW(), NOW()),
('INSP-P-003', 'A1工位专项点检', 1, NULL, 'daily', '["伺服电机异响","导轨润滑","气压表读数","急停按钮","工装夹具"]', 1, 'DEV-001 专项点检', NOW(), NOW());

-- 示例点检记录
INSERT IGNORE INTO dev_inspection_record (record_no, device_id, plan_id, plan_name, inspector_id, inspector_name, inspect_time, check_items, is_normal, remark, created_time) VALUES
('INSP-20260704-001', 1, 3, 'A1工位专项点检', 2, '张主管', '2026-07-04 08:30:00',
 '[{"itemName":"伺服电机异响","isNormal":false,"remark":"轻微异响"},{"itemName":"导轨润滑","isNormal":true,"remark":""},{"itemName":"气压表读数","isNormal":true,"remark":""},{"itemName":"急停按钮","isNormal":true,"remark":""},{"itemName":"工装夹具","isNormal":true,"remark":""}]',
 0, '发现伺服电机异响，已报修', '2026-07-04 08:30:00'),
('INSP-20260705-001', 2, 2, '检测设备日点检', 2, '张主管', '2026-07-05 08:00:00',
 '[{"itemName":"外观与清洁","isNormal":true,"remark":""},{"itemName":"校准状态","isNormal":true,"remark":""},{"itemName":"测量精度抽检","isNormal":true,"remark":""},{"itemName":"数据线/接口","isNormal":true,"remark":""},{"itemName":"安全标识","isNormal":true,"remark":""}]',
 1, NULL, '2026-07-05 08:00:00');

INSERT IGNORE INTO dev_device_history (device_id, action_type, action_desc, operator_id, operator_name, related_event_id, before_value, after_value, create_time) VALUES
(1, 'inspection', '设备点检：发现异常（伺服电机异响）', 2, '张主管', NULL, NULL, 'abnormal', '2026-07-04 08:30:00'),
(2, 'inspection', '设备点检：全部正常', 2, '张主管', NULL, NULL, 'normal', '2026-07-05 08:00:00');
