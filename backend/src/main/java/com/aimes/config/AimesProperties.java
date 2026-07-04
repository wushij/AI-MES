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
    /** 工单完工时按 BOM 写领料流水 */
    private boolean bomPickOnComplete = true;
}
