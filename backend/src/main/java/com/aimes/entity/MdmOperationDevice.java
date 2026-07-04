package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mdm_operation_device")
public class MdmOperationDevice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operationId;
    private Long deviceId;
    private Long categoryId;
    private String bindType;
    private LocalDateTime createdTime;
}
