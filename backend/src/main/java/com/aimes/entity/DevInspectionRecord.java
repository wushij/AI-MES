package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_inspection_record")
public class DevInspectionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String recordNo;
    private Long deviceId;
    private Long planId;
    private String planName;
    private Long inspectorId;
    private String inspectorName;
    private LocalDateTime inspectTime;
    private String checkItems;
    private Integer isNormal;
    private String remark;
    private LocalDateTime createdTime;
}
