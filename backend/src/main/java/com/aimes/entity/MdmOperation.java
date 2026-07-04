package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mdm_operation")
public class MdmOperation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routingId;
    private String operationCode;
    private Integer seqNo;
    private String operationName;
    private Double standardHours;
    private Double prepHours;
    private Double changeoverHours;
    private Integer needReport;
    private Integer needCheck;
    private Integer needScan;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
