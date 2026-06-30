-- =============================================================================
-- Migration 004: Add avatar column to sys_user table
-- =============================================================================

ALTER TABLE sys_user ADD COLUMN avatar MEDIUMTEXT NULL COMMENT '头像Base64' AFTER real_name;
