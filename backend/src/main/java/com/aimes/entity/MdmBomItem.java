package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mdm_bom_item")
public class MdmBomItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bomId;
    private Long materialId;
    private BigDecimal qty;
    private String unit;
    private BigDecimal lossRate;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
