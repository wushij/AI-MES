package com.aimes.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aimes")
public class AimesProperties {

    private String uploadDir = "uploads";
    private int planSplitQty = 100;
    /** 生产执行时按工序领料；无工序领料记录时工单完工回退按 BOM */
    private boolean bomPickOnComplete = true;
}
