package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mat_material")
public class MatMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialCode;
    private String materialName;
    private BigDecimal stockQty;
    private BigDecimal safetyStock;
    private String unit;
    private String alertStatus;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
