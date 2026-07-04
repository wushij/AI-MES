package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prod_process_record")
public class ProdProcessRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private Long operationId;
    private Long deviceId;
    private String processName;
    private Integer seqNo;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}


