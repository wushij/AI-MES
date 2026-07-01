package com.aimes.service;

import com.aimes.dto.Requests.CozeConfigSaveRequest;
import com.aimes.entity.SysCozeConfig;
import com.aimes.mapper.SysCozeConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CozeConfigService {

    private static final long CONFIG_ID = 1L;

    private final SysCozeConfigMapper sysCozeConfigMapper;

    @Value("${coze.api-token:}")
    private String envApiToken;

    @Value("${coze.bot-id:}")
    private String envBotId;

    @Value("${coze.api-url:https://api.coze.cn/v3}")
    private String envApiUrl;

    @Value("${coze.workflow-id:}")
    private String envWorkflowId;

    /** 启动时将 .env 中的 Coze 配置写入数据库（仅当库内为空时） */
    @jakarta.annotation.PostConstruct
    public void syncFromEnvIfEmpty() {
        SysCozeConfig row = loadRow();
        boolean changed = false;
        if (!StringUtils.hasText(row.getApiToken()) && StringUtils.hasText(envApiToken)) {
            row.setApiToken(envApiToken.trim());
            changed = true;
        }
        if (!StringUtils.hasText(row.getBotId()) && StringUtils.hasText(envBotId)) {
            row.setBotId(envBotId.trim());
            changed = true;
        }
        if (!StringUtils.hasText(row.getApiUrl()) && StringUtils.hasText(envApiUrl)) {
            row.setApiUrl(envApiUrl.trim());
            changed = true;
        }
        if (!StringUtils.hasText(row.getWorkflowId()) && StringUtils.hasText(envWorkflowId)) {
            row.setWorkflowId(envWorkflowId.trim());
            changed = true;
        }
        if (changed) {
            row.setUpdateTime(LocalDateTime.now());
            sysCozeConfigMapper.updateById(row);
        }
    }

    public String getEffectiveApiToken() {
        SysCozeConfig row = loadRow();
        if (StringUtils.hasText(row.getApiToken())) {
            return row.getApiToken().trim();
        }
        return StringUtils.hasText(envApiToken) ? envApiToken.trim() : "";
    }

    public String getEffectiveBotId() {
        SysCozeConfig row = loadRow();
        if (StringUtils.hasText(row.getBotId())) {
            return row.getBotId().trim();
        }
        return StringUtils.hasText(envBotId) ? envBotId.trim() : "";
    }

    public String getEffectiveApiUrl() {
        SysCozeConfig row = loadRow();
        if (StringUtils.hasText(row.getApiUrl())) {
            return row.getApiUrl().trim();
        }
        return StringUtils.hasText(envApiUrl) ? envApiUrl.trim() : "https://api.coze.cn/v3";
    }

    public String getEffectiveWorkflowId() {
        SysCozeConfig row = loadRow();
        return StringUtils.hasText(row.getWorkflowId()) ? row.getWorkflowId().trim() : "";
    }

    public String getWelcomeMessage() {
        SysCozeConfig row = loadRow();
        return StringUtils.hasText(row.getWelcomeMessage())
                ? row.getWelcomeMessage()
                : "您好，我是 AI-MES 智能助手，可协助查询工单进度、异常处理及 SOP 指导。";
    }

    public boolean isEnabled() {
        SysCozeConfig row = loadRow();
        return row.getEnabled() == null || row.getEnabled() == 1;
    }

    public boolean isConfigured() {
        return isEnabled()
                && StringUtils.hasText(getEffectiveApiToken())
                && StringUtils.hasText(getEffectiveBotId());
    }

    public Map<String, Object> getConfigView() {
        SysCozeConfig row = loadRow();
        String effectiveToken = getEffectiveApiToken();
        String effectiveBotId = getEffectiveBotId();
        String effectiveApiUrl = getEffectiveApiUrl();

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("botId", effectiveBotId);
        view.put("apiUrl", effectiveApiUrl);
        view.put("workflowId", StringUtils.hasText(row.getWorkflowId()) ? row.getWorkflowId() : "");
        view.put("welcomeMessage", getWelcomeMessage());
        view.put("enabled", isEnabled());
        view.put("hasApiToken", StringUtils.hasText(effectiveToken));
        view.put("apiTokenMasked", maskToken(effectiveToken));
        view.put("configured", isConfigured());
        view.put("tokenSource", StringUtils.hasText(row.getApiToken()) ? "database" : (StringUtils.hasText(envApiToken) ? "env" : "none"));
        view.put("botSource", StringUtils.hasText(row.getBotId()) ? "database" : (StringUtils.hasText(envBotId) ? "env" : "none"));
        view.put("updateTime", row.getUpdateTime());
        return view;
    }

    public Map<String, Object> saveConfig(CozeConfigSaveRequest request) {
        SysCozeConfig row = loadRow();
        if (StringUtils.hasText(request.getApiToken()) && !request.getApiToken().contains("*")) {
            row.setApiToken(request.getApiToken().trim());
        }
        row.setBotId(request.getBotId().trim());
        row.setApiUrl(StringUtils.hasText(request.getApiUrl()) ? request.getApiUrl().trim() : "https://api.coze.cn/v3");
        row.setWorkflowId(StringUtils.hasText(request.getWorkflowId()) ? request.getWorkflowId().trim() : null);
        row.setWelcomeMessage(StringUtils.hasText(request.getWelcomeMessage()) ? request.getWelcomeMessage().trim() : null);
        row.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        row.setUpdateTime(LocalDateTime.now());
        sysCozeConfigMapper.updateById(row);
        return getConfigView();
    }

    private SysCozeConfig loadRow() {
        SysCozeConfig row = sysCozeConfigMapper.selectById(CONFIG_ID);
        if (row != null) {
            return row;
        }
        row = new SysCozeConfig();
        row.setId(CONFIG_ID);
        row.setApiUrl("https://api.coze.cn/v3");
        row.setEnabled(1);
        row.setUpdateTime(LocalDateTime.now());
        sysCozeConfigMapper.insert(row);
        return row;
    }

    private String maskToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        if (token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}
