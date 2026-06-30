-- =============================================================================
-- 增量脚本 001：新增 Coze 集成配置表
-- =============================================================================
-- 适用场景：已有 AI-MES 库（早期版本未包含 sys_coze_config），需在线升级
-- 全新安装请直接使用 sql/init.sql，无需单独执行本脚本
--
-- 执行方式：
--   mysql -uroot -proot AI-MES < sql/migrations/001_add_sys_coze_config.sql
--
-- 特性：可重复执行（CREATE TABLE IF NOT EXISTS + 种子数据幂等插入）
-- 日期：2026-06-30
-- =============================================================================

USE `AI-MES`;

-- Coze 集成配置（单行表，id 固定为 1）
CREATE TABLE IF NOT EXISTS sys_coze_config (
    id BIGINT PRIMARY KEY COMMENT '固定为 1',
    api_token VARCHAR(512) NULL COMMENT 'Coze API Token / 密钥',
    bot_id VARCHAR(128) NULL COMMENT 'Bot ID',
    api_url VARCHAR(255) NOT NULL DEFAULT 'https://api.coze.cn/v3' COMMENT 'API 基础地址',
    workflow_id VARCHAR(128) NULL COMMENT '排产工作流 ID（可选）',
    welcome_message VARCHAR(500) NULL COMMENT '欢迎语',
    knowledge_base VARCHAR(255) NULL COMMENT '知识库标识',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Coze 集成配置';

-- 初始化默认配置行（已存在则跳过）
INSERT INTO sys_coze_config (id, bot_id, api_url, welcome_message, enabled, update_time)
SELECT 1, '7656623287480991779', 'https://api.coze.cn/v3', '您好，我是 AI-MES 智能助手，可协助查询工单进度、异常处理及 SOP 指导。', 1, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_coze_config WHERE id = 1);
