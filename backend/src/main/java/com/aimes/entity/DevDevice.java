package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_device")
public class DevDevice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private String deviceName;
    private Long categoryId;
    private String deviceType;
    private String brand;
    private String model;
    private String serialNumber;
    private String workshop;
    private String lineName;
    private String station;
    private Long managerId;
    private java.time.LocalDate purchaseDate;
    private java.time.LocalDate installDate;
    private java.time.LocalDate enableDate;
    private java.time.LocalDate warrantyDate;
    private String status;
    private Long teamId;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
