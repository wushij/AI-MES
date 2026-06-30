package com.aimes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prod_team")
public class ProdTeam {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String teamCode;
    private String teamName;
    private Long leaderId;
    private Integer memberCount;
    private String lineName;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
