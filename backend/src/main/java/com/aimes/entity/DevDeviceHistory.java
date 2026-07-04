package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_device_history")
public class DevDeviceHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String actionType;
    private String actionDesc;
    private Long operatorId;
    private String operatorName;
    private Long relatedEventId;
    private String beforeValue;
    private String afterValue;
    private LocalDateTime createTime;
}
