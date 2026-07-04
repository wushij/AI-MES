package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qms_inspection_plan")
public class QmsInspectionPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operationId;
    private String itemName;
    private String standard;
    private String minValue;
    private String maxValue;
    private String unit;
    private Integer sortNo;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
