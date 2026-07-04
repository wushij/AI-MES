package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mdm_operation_param")
public class MdmOperationParam {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operationId;
    private String paramName;
    private String paramValue;
    private String minValue;
    private String maxValue;
    private String unit;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
