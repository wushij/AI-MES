-- =============================================================================
-- 增量脚本 002：新增角色权限配置表
-- =============================================================================
-- 适用场景：已有 AI-MES 库，需在线升级角色权限管理功能
-- 全新安装请直接使用 sql/init.sql，无需单独执行本脚本
--
-- 执行方式：
--   mysql -uroot -proot AI-MES < sql/migrations/002_add_sys_role_permission.sql
--
-- 特性：可重复执行（CREATE TABLE IF NOT EXISTS + 种子数据幂等插入）
-- 日期：2026-06-30
-- =============================================================================

USE `AI-MES`;

-- 角色权限配置
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    role_key VARCHAR(20) NOT NULL COMMENT '角色标识',
    permission VARCHAR(50) NOT NULL COMMENT '权限名称',
    UNIQUE KEY uk_role_permission (role_key, permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限配置';

-- 初始化默认角色权限（已存在则跳过）
INSERT INTO sys_role_permission (role_key, permission)
SELECT 'admin', '系统配置' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'admin' AND permission = '系统配置')
UNION ALL
SELECT 'admin', '用户管理' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'admin' AND permission = '用户管理')
UNION ALL
SELECT 'admin', 'Coze 配置' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'admin' AND permission = 'Coze 配置')
UNION ALL
SELECT 'supervisor', '生产计划' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'supervisor' AND permission = '生产计划')
UNION ALL
SELECT 'supervisor', '班组' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'supervisor' AND permission = '班组')
UNION ALL
SELECT 'supervisor', '物料' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'supervisor' AND permission = '物料')
UNION ALL
SELECT 'supervisor', '排产' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'supervisor' AND permission = '排产')
UNION ALL
SELECT 'worker', '工序进度' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'worker' AND permission = '工序进度')
UNION ALL
SELECT 'worker', '工单反馈' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'worker' AND permission = '工单反馈')
UNION ALL
SELECT 'worker', '异常上报' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'worker' AND permission = '异常上报')
UNION ALL
SELECT 'worker', 'AI 客服' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_key = 'worker' AND permission = 'AI 客服');
