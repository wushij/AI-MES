-- 工序报工支持多台设备
ALTER TABLE prod_process_record
    ADD COLUMN device_ids VARCHAR(255) NULL COMMENT '报工使用设备ID列表，逗号分隔' AFTER device_id;
