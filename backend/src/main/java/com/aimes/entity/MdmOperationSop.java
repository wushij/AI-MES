package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mdm_operation_sop")
public class MdmOperationSop {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operationId;
    private String fileName;
    private String fileType;
    private String filePath;
    private Long fileSize;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
