package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mdm_operation_material")
public class MdmOperationMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operationId;
    private Long materialId;
    private java.math.BigDecimal qty;
    private String unit;
    private String materialType;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
