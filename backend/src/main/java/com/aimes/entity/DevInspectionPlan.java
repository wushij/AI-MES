package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_inspection_plan")
public class DevInspectionPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String planCode;
    private String planName;
    private Long deviceId;
    private Long categoryId;
    private String cycleType;
    private String checkItems;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
