package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qms_inspection_record")
public class QmsInspectionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private Long processRecordId;
    private Long operationId;
    private Long planId;
    private String itemName;
    private String measuredValue;
    private String result;
    private Long inspectorId;
    private String remark;
    private LocalDateTime createdTime;
}
