package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_repair_order")
public class DevRepairOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String repairNo;
    private Long deviceId;
    private Long eventId;
    private String faultReason;
    private String faultCode;
    private String description;
    private String status;
    private Long reporterId;
    private String reporterName;
    private Long repairerId;
    private String repairerName;
    private LocalDateTime reportTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer repairMinutes;
    private String repairAction;
    private String repairResult;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
