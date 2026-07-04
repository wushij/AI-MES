package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mdm_routing")
public class MdmRouting {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String routeCode;
    private String routeName;
    private Long productId;
    private String productName;
    private String version;
    private String status;
    private String rejectedReason;
    private Integer isDefault;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
