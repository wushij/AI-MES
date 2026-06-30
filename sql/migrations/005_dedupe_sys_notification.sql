-- =============================================================================
-- Migration 005: Remove duplicate notifications (same user + content)
-- Keep the row with the highest id (usually the latest).
-- =============================================================================

DELETE n1
FROM `sys_notification` n1
INNER JOIN `sys_notification` n2
  ON n1.user_id = n2.user_id
 AND n1.content = n2.content
 AND n1.id < n2.id;
