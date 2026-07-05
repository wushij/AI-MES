package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deviceId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
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
