package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inv_transaction")
public class InvTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private String txnType;
    private BigDecimal qty;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private String refType;
    private Long refId;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdTime;
}
