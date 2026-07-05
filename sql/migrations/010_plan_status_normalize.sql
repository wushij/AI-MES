-- 统一生产计划已完成状态：历史数据 completed -> done
UPDATE prod_plan SET status = 'done' WHERE status = 'completed';
