package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("prod_work_order")
public class ProdWorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long planId;
    private String productName;
    private Long teamId;
    private Long routingId;
    private String routeVersion;
    private String processName;
    private Integer progress;
    private String status;
    private Integer priority;
    private LocalDateTime deadline;
    private LocalDateTime scheduledStartTime;
    private BigDecimal estimatedHours;
    private Integer schedulingRank;
    private String schedulingReason;
    private Long claimUserId;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
