package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long productId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String productName;

    private String version;
    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejectedReason;

    private Integer isDefault;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
