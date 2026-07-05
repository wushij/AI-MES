-- 产品成品库存 + 库存流水支持产品维度
ALTER TABLE mdm_product
    ADD COLUMN stock_qty DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '成品库存' AFTER unit;

ALTER TABLE inv_transaction
    MODIFY COLUMN material_id BIGINT NULL COMMENT '物料ID（物料流水时填写）';

ALTER TABLE inv_transaction
    ADD COLUMN product_id BIGINT NULL COMMENT '产品ID（成品流水时填写）' AFTER material_id;

ALTER TABLE inv_transaction
    ADD KEY idx_inv_txn_product (product_id);

ALTER TABLE inv_transaction
    ADD CONSTRAINT fk_inv_txn_product FOREIGN KEY (product_id) REFERENCES mdm_product(id) ON DELETE CASCADE;
