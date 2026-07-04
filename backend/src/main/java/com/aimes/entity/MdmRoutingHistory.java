package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mdm_routing_history")
public class MdmRoutingHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routingId;
    private String version;
    private String actionType;
    private String actionDesc;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;
}
