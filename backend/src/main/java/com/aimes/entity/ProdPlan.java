package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("prod_plan")
public class ProdPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String planNo;
    private Long productId;
    private String productName;
    private Integer planQty;
    private LocalDate planDate;
    private String status;
    private Long createdBy;
    private String remark;
    private LocalDateTime releaseTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
