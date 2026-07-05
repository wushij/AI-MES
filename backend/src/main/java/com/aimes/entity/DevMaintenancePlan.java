package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("dev_maintenance_plan")
public class DevMaintenancePlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String planCode;
    private String planName;
    private Long deviceId;
    private Long categoryId;
    private String cycleType;
    private String maintenanceItems;
    private LocalDate nextDueDate;
    private LocalDate lastMaintenanceDate;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
