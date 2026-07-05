package com.aimes.service.coze;

import com.aimes.service.CozeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class CozeHealthService {

    private final CozeConfigService cozeConfigService;
    private final CozeApiClient cozeApiClient;
    private final CozeChatService cozeChatService;
    private final CozeSchedulingService cozeSchedulingService;

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", cozeConfigService.isConfigured());
        result.put("enabled", cozeConfigService.isEnabled());
        result.put("apiUrl", cozeApiClient.apiUrl());
        result.put("botId", StringUtils.hasText(cozeApiClient.botId()) ? cozeApiClient.botId() : null);
        result.put("tokenSource", cozeConfigService.getConfigView().get("tokenSource"));
        if (!cozeConfigService.isConfigured()) {
            result.put("status", "mock");
            result.put("message", "未配置 API Token 或 Bot ID，或已关闭 Coze，当前返回演示数据");
            result.put("chat", Map.of("status", "skipped", "message", "未配置 Bot 连接"));
            result.put("workflow", Map.of("status", "skipped", "message", "未配置工作流"));
            return result;
        }

        Map<String, Object> chatResult;
        Map<String, Object> workflowResult;
        try {
            CompletableFuture<Map<String, Object>> chatFuture =
                    CompletableFuture.supplyAsync(cozeChatService::testChatHealth);
            CompletableFuture<Map<String, Object>> workflowFuture =
                    CompletableFuture.supplyAsync(cozeSchedulingService::testWorkflowHealth);
            chatResult = chatFuture.join();
            workflowResult = workflowFuture.join();
        } catch (Exception ex) {
            chatResult = Map.of("status", "error", "message", "Bot 对话测试失败：" + ex.getMessage());
            workflowResult = Map.of("status", "error", "message", "排产工作流测试失败：" + ex.getMessage());
        }
        result.put("chat", chatResult);
        result.put("workflow", workflowResult);
        result.put("status", resolveOverallHealthStatus(chatResult, workflowResult));
        result.put("message", buildOverallHealthMessage(chatResult, workflowResult));
        return result;
    }

    private String resolveOverallHealthStatus(Map<String, Object> chat, Map<String, Object> workflow) {
        String chatStatus = String.valueOf(chat.get("status"));
        String workflowStatus = String.valueOf(workflow.get("status"));
        boolean chatOk = "ok".equals(chatStatus);
        boolean workflowOk = "ok".equals(workflowStatus);
        boolean workflowSkipped = "skipped".equals(workflowStatus);

        if (chatOk && workflowOk) {
            return "ok";
        }
        if (chatOk && workflowSkipped) {
            return "partial";
        }
        if (chatOk || workflowOk) {
            return "partial";
        }
        return "error";
    }

    private String buildOverallHealthMessage(Map<String, Object> chat, Map<String, Object> workflow) {
        return "Bot 对话：" + chat.get("status") + "；排产工作流：" + workflow.get("status");
    }
}
