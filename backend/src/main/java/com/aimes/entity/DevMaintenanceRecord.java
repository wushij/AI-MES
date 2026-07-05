package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_maintenance_record")
public class DevMaintenanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String recordNo;
    private Long deviceId;
    private Long planId;
    private String planName;
    private Long maintainerId;
    private String maintainerName;
    private LocalDateTime maintenanceTime;
    private String maintenanceItems;
    private Integer isCompleted;
    private String remark;
    private LocalDateTime createdTime;
}
