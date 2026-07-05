package com.aimes.service.coze;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.CozeChatRequest;
import com.aimes.entity.AiChatLog;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.AiChatLogMapper;
import com.aimes.service.AuthService;
import com.aimes.service.CozeConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CozeChatService {

    private final AiChatLogMapper aiChatLogMapper;
    private final AuthService authService;
    private final CozeConfigService cozeConfigService;
    private final CozeApiClient cozeApiClient;
    private final CozeChatPromptService cozeChatPromptService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> chat(CozeChatRequest request) {
        SysUser user = authService.currentUser();
        String sessionId = StringUtils.hasText(request.getSessionId()) ? request.getSessionId() : UUID.randomUUID().toString();
        List<AiChatLog> sessionHistory = loadSessionHistory(user.getId(), sessionId);
        List<ProdWorkOrder> referencedOrders = cozeChatPromptService.resolveReferencedOrdersFromText(request.getMessage());
        if (referencedOrders.isEmpty()) {
            referencedOrders = cozeChatPromptService.resolveReferencedOrdersFromHistory(sessionHistory);
        }
        CozeChatPromptMode promptMode = cozeChatPromptService.resolvePromptMode(request.getMessage(), referencedOrders, sessionHistory);
        String prompt = cozeChatPromptService.buildChatPrompt(user, request.getMessage(), referencedOrders, promptMode, sessionHistory);

        String reply;
        String mode;
        try {
            if (cozeApiClient.isConfigured()) {
                reply = invokeLiveChat(user, sessionId, prompt);
                mode = "live";
            } else {
                reply = cozeChatPromptService.buildMockReply(request.getMessage(), referencedOrders, user);
                mode = "mock";
            }
        } catch (Exception ex) {
            if (cozeApiClient.isConfigured()) {
                String detail = ex.getMessage() == null ? "" : ex.getMessage();
                if (detail.contains("timeout")) {
                    throw new BusinessException("AI 响应超时，请稍后重试");
                }
                throw new BusinessException("Coze 对话失败：" + detail);
            }
            reply = cozeChatPromptService.buildMockReply(request.getMessage(), referencedOrders, user);
            mode = "mock-fallback";
        }

        AiChatLog log = new AiChatLog();
        log.setUserId(user.getId());
        log.setSessionId(sessionId);
        log.setUserMessage(request.getMessage());
        log.setAiResponse(reply);
        log.setBotId(StringUtils.hasText(cozeConfigService.getEffectiveBotId()) ? cozeConfigService.getEffectiveBotId() : "demo-bot");
        log.setCreateTime(LocalDateTime.now());
        aiChatLogMapper.insert(log);

        return Map.of(
                "reply", reply,
                "sessionId", sessionId,
                "mode", mode,
                "promptMode", promptMode.wireValue(),
                "contextOrders", referencedOrders.stream().map(ProdWorkOrder::getOrderNo).toList()
        );
    }

    public void chatStream(CozeChatRequest request, HttpServletResponse response) {
        SysUser user = authService.currentUser();
        String sessionId = StringUtils.hasText(request.getSessionId()) ? request.getSessionId() : UUID.randomUUID().toString();
        List<AiChatLog> sessionHistory = loadSessionHistory(user.getId(), sessionId);
        List<ProdWorkOrder> referencedOrders = cozeChatPromptService.resolveReferencedOrdersFromText(request.getMessage());
        if (referencedOrders.isEmpty()) {
            referencedOrders = cozeChatPromptService.resolveReferencedOrdersFromHistory(sessionHistory);
        }
        CozeChatPromptMode promptMode = cozeChatPromptService.resolvePromptMode(request.getMessage(), referencedOrders, sessionHistory);
        String prompt = cozeChatPromptService.buildChatPrompt(user, request.getMessage(), referencedOrders, promptMode, sessionHistory);

        PrintWriter writer;
        try {
            writer = response.getWriter();
        } catch (IOException e) {
            throw new BusinessException("获取输出流失败: " + e.getMessage());
        }

        if (!cozeApiClient.isConfigured()) {
            // Mock streaming
            String reply = cozeChatPromptService.buildMockReply(request.getMessage(), referencedOrders, user);
            streamMockReply(writer, reply, sessionId, promptMode.wireValue());
            saveChatLog(user.getId(), sessionId, request.getMessage(), reply);
            return;
        }

        // Live streaming
        StringBuilder replyBuilder = new StringBuilder();
        try {
            invokeLiveChatStream(user, sessionId, prompt, promptMode, writer, replyBuilder);
            saveChatLog(user.getId(), sessionId, request.getMessage(), replyBuilder.toString());
        } catch (Exception ex) {
            String detail = ex.getMessage() == null ? "" : ex.getMessage();
            String errorMsg;
            if (detail.contains("timeout")) {
                errorMsg = "AI 响应超时，请稍后重试";
            } else {
                errorMsg = "Coze 对话失败：" + detail;
            }
            try {
                // Send error event
                writer.write("event: error\n");
                writer.write("data: " + objectMapper.writeValueAsString(Map.of("message", errorMsg)) + "\n\n");
                writer.flush();
            } catch (Exception ignored) {}
        }
    }

    private void saveChatLog(Long userId, String sessionId, String userMessage, String aiResponse) {
        AiChatLog log = new AiChatLog();
        log.setUserId(userId);
        log.setSessionId(sessionId);
        log.setUserMessage(userMessage);
        log.setAiResponse(aiResponse);
        log.setBotId(StringUtils.hasText(cozeConfigService.getEffectiveBotId()) ? cozeConfigService.getEffectiveBotId() : "demo-bot");
        log.setCreateTime(LocalDateTime.now());
        aiChatLogMapper.insert(log);
    }

    private void invokeLiveChatStream(
            SysUser user,
            String sessionId,
            String prompt,
            CozeChatPromptMode promptMode,
            PrintWriter writer,
            StringBuilder replyBuilder) throws IOException, InterruptedException {
        Map<String, Object> payload = cozeApiClient.buildChatPayload(
                String.valueOf(user.getId()),
                resolveStreamConversationId(sessionId),
                prompt,
                true);

        // Send metadata event first
        Map<String, Object> metadata = Map.of(
                "sessionId", sessionId,
                "mode", "live",
                "promptMode", promptMode.wireValue()
        );
        writer.write("event: metadata\n");
        writer.write("data: " + objectMapper.writeValueAsString(metadata) + "\n\n");
        writer.flush();

        try (InputStream is = cozeApiClient.invokeStream(cozeApiClient.apiUrl() + "/chat", payload)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            String currentEvent = null;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
                writer.flush();

                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("event:")) {
                    currentEvent = trimmedLine.substring(6).trim();
                } else if (trimmedLine.startsWith("data:")) {
                    String dataStr = trimmedLine.substring(5).trim();
                    if ("conversation.message.delta".equals(currentEvent)) {
                        try {
                            JsonNode node = objectMapper.readTree(dataStr);
                            if ("answer".equals(node.path("type").asText())) {
                                replyBuilder.append(node.path("content").asText(""));
                            }
                        } catch (Exception ignored) {}
                    }
                } else if (trimmedLine.isEmpty()) {
                    currentEvent = null;
                }
            }
        }
    }

    private void streamMockReply(PrintWriter writer, String reply, String sessionId, String promptMode) {
        Map<String, Object> metadata = Map.of(
                "sessionId", sessionId,
                "mode", "mock",
                "promptMode", promptMode
        );
        try {
            writer.write("event: metadata\n");
            writer.write("data: " + objectMapper.writeValueAsString(metadata) + "\n\n");
            writer.flush();
        } catch (Exception ignored) {}

        if (reply != null) {
            String msgId = "mock-msg-" + UUID.randomUUID().toString();
            int chunkSize = 2;
            for (int i = 0; i < reply.length(); i += chunkSize) {
                int end = Math.min(i + chunkSize, reply.length());
                String chunk = reply.substring(i, end);
                
                Map<String, Object> delta = Map.of(
                        "id", msgId,
                        "role", "assistant",
                        "type", "answer",
                        "content", chunk,
                        "content_type", "text"
                );
                
                try {
                    writer.write("event: conversation.message.delta\n");
                    writer.write("data: " + objectMapper.writeValueAsString(delta) + "\n\n");
                    writer.flush();
                    Thread.sleep(40);
                } catch (Exception e) {
                    break;
                }
            }
            
            Map<String, Object> completed = Map.of(
                    "id", msgId,
                    "role", "assistant",
                    "type", "answer",
                    "content", reply,
                    "content_type", "text"
            );
            try {
                writer.write("event: conversation.message.completed\n");
                writer.write("data: " + objectMapper.writeValueAsString(completed) + "\n\n");
                
                writer.write("event: conversation.chat.completed\n");
                writer.write("data: {}\n\n");
                writer.flush();
            } catch (Exception ignored) {}
        }
    }

    public List<Map<String, Object>> history(String sessionId) {
        SysUser user = authService.currentUser();
        return aiChatLogMapper.selectList(new LambdaQueryWrapper<AiChatLog>()
                        .eq(AiChatLog::getUserId, user.getId())
                        .eq(StringUtils.hasText(sessionId), AiChatLog::getSessionId, sessionId)
                        .orderByDesc(AiChatLog::getCreateTime))
                .stream()
                .map(log -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", log.getId());
                    row.put("sessionId", log.getSessionId());
                    row.put("userMessage", log.getUserMessage());
                    row.put("aiResponse", log.getAiResponse());
                    row.put("botId", log.getBotId());
                    row.put("createTime", log.getCreateTime());
                    return row;
                })
                .toList();
    }

    public int deleteSession(String sessionId) {
        SysUser user = authService.currentUser();
        if (!StringUtils.hasText(sessionId)) {
            return 0;
        }
        return aiChatLogMapper.delete(new LambdaQueryWrapper<AiChatLog>()
                .eq(AiChatLog::getUserId, user.getId())
                .eq(AiChatLog::getSessionId, sessionId));
    }

    private String invokeLiveChat(SysUser user, String sessionId, String prompt) throws IOException, InterruptedException {
        JsonNode root = cozeApiClient.createChat(String.valueOf(user.getId()), sessionId, prompt);
        JsonNode data = root.path("data");
        String chatId = data.path("id").asText("");
        String conversationId = data.path("conversation_id").asText("");
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(conversationId)) {
            throw new IOException("Coze 未返回 chat_id 或 conversation_id");
        }
        String status = cozeApiClient.pollChatStatus(conversationId, chatId);
        if (!"completed".equals(status)) {
            throw new IOException("Coze 对话未完成，status=" + status);
        }
        return cozeApiClient.fetchAnswerContent(conversationId, chatId);
    }

    private String resolveStreamConversationId(String sessionId) {
        if (StringUtils.hasText(sessionId) && !sessionId.startsWith("sess_")) {
            return sessionId;
        }
        return null;
    }

    private List<AiChatLog> loadSessionHistory(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return List.of();
        }
        List<AiChatLog> recent = aiChatLogMapper.selectList(new LambdaQueryWrapper<AiChatLog>()
                .eq(AiChatLog::getUserId, userId)
                .eq(AiChatLog::getSessionId, sessionId)
                .orderByDesc(AiChatLog::getCreateTime)
                .last("limit " + CozeConstants.SESSION_HISTORY_TURNS));
        if (recent.isEmpty()) {
            return List.of();
        }
        List<AiChatLog> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);
        return chronological;
    }

    public Map<String, Object> testChatHealth() {
        Map<String, Object> chat = new LinkedHashMap<>();
        try {
            JsonNode root = cozeApiClient.createChat("health-check", null, "ping");
            String chatId = root.path("data").path("id").asText("");
            String conversationId = root.path("data").path("conversation_id").asText("");
            String status = cozeApiClient.pollChatStatus(conversationId, chatId);
            boolean ok = "completed".equals(status);
            chat.put("status", ok ? "ok" : status);
            chat.put("chatId", chatId);
            chat.put("message", "Bot 对话已创建，chat_id=" + chatId + "，status=" + status);
        } catch (Exception ex) {
            chat.put("status", "error");
            chat.put("message", "Bot 对话测试失败：" + ex.getMessage());
        }
        return chat;
    }

    public Map<String, Object> healthChat() {
        if (!cozeConfigService.isConfigured()) {
            return Map.of("status", "skipped", "message", "未配置 API Token 或 Bot ID");
        }
        return testChatHealth();
    }
}

