package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exc_event")
public class ExcEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventNo;
    private String eventType;
    private Long workOrderId;
    private Long deviceId;
    private String description;
    private String status;
    private Long reporterId;
    private Long handlerId;
    private LocalDateTime occurTime;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
    private String handleAction;
    private String handleResult;
}
