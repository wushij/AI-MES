SET NAMES utf8mb4;
-- P3-T9 今日运行统计演示数据（报工记录）
-- 若已初始化可单独执行以刷新今日演示报工

USE `AI-MES`;

UPDATE prod_process_record
SET start_time = '2026-07-05 08:00:00', end_time = NULL, status = 'running', remark = '今日报工运行', updated_time = NOW()
WHERE device_id = 1 AND process_name = '装配' AND seq_no = 2
LIMIT 1;

INSERT INTO prod_process_record (work_order_id, operation_id, device_id, process_name, seq_no, status, start_time, end_time, remark, created_time, updated_time)
SELECT 2, 7, 2, '检测', 3, 'done', '2026-07-05 09:00:00', '2026-07-05 10:30:00', '今日检测完成', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM prod_process_record WHERE device_id = 2 AND start_time >= '2026-07-05 00:00:00'
);
