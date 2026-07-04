package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mdm_bom")
public class MdmBom {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String version;
    private String status;
    private Integer isDefault;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
