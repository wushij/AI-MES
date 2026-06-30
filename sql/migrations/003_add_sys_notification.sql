-- =============================================================================
-- Migration 003: Create sys_notification table and dynamically populate from active records
-- =============================================================================

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
