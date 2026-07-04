package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.CozeChatRequest;
import com.aimes.dto.Requests.CozeSchedulingRequest;
import com.aimes.entity.AiChatLog;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.MatMaterial;
import com.aimes.entity.ProdTeam;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.AiChatLogMapper;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.MatMaterialMapper;
import com.aimes.mapper.ProdPlanMapper;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CozeService {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("WO-\\d{4}-\\d{3}");
    /** 单会话内带入 Prompt 的最近轮数（不含当前正在发送的这一轮） */
    private static final int SESSION_HISTORY_TURNS = 5;
    /** 单条历史消息最大字符，避免 Prompt 过长 */
    private static final int SESSION_HISTORY_MAX_CHARS = 500;

    private static final DateTimeFormatter SCHEDULING_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int DEFAULT_DISPATCH_HOURS = 2;

    @org.springframework.beans.factory.annotation.Value("${coze.chat-poll-timeout-seconds:90}")
    private int chatPollTimeoutSeconds;

    private final AiChatLogMapper aiChatLogMapper;
    private final ExcEventMapper excEventMapper;
    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdPlanMapper prodPlanMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final MatMaterialMapper matMaterialMapper;
    private final AuthService authService;
    private final CozeConfigService cozeConfigService;
    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public Map<String, Object> chat(CozeChatRequest request) {
        SysUser user = authService.currentUser();
        String sessionId = StringUtils.hasText(request.getSessionId()) ? request.getSessionId() : UUID.randomUUID().toString();
        List<AiChatLog> sessionHistory = loadSessionHistory(user.getId(), sessionId);
        List<ProdWorkOrder> referencedOrders = resolveReferencedOrdersFromText(request.getMessage());
        if (referencedOrders.isEmpty()) {
            referencedOrders = resolveReferencedOrdersFromHistory(sessionHistory);
        }
        ChatPromptMode promptMode = resolvePromptMode(request.getMessage(), referencedOrders, sessionHistory);
        String prompt = buildChatPrompt(user, request.getMessage(), referencedOrders, promptMode, sessionHistory);

        String reply;
        String mode;
        try {
            if (isConfigured()) {
                reply = invokeLiveChat(user, sessionId, prompt);
                mode = "live";
            } else {
                reply = buildMockReply(request.getMessage(), referencedOrders, user);
                mode = "mock";
            }
        } catch (Exception ex) {
            if (isConfigured()) {
                String detail = ex.getMessage() == null ? "" : ex.getMessage();
                if (detail.contains("timeout")) {
                    throw new BusinessException("AI 响应超时，请稍后重试");
                }
                throw new BusinessException("Coze 对话失败：" + detail);
            }
            reply = buildMockReply(request.getMessage(), referencedOrders, user);
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
        List<ProdWorkOrder> referencedOrders = resolveReferencedOrdersFromText(request.getMessage());
        if (referencedOrders.isEmpty()) {
            referencedOrders = resolveReferencedOrdersFromHistory(sessionHistory);
        }
        ChatPromptMode promptMode = resolvePromptMode(request.getMessage(), referencedOrders, sessionHistory);
        String prompt = buildChatPrompt(user, request.getMessage(), referencedOrders, promptMode, sessionHistory);

        PrintWriter writer;
        try {
            writer = response.getWriter();
        } catch (IOException e) {
            throw new BusinessException("获取输出流失败: " + e.getMessage());
        }

        if (!isConfigured()) {
            // Mock streaming
            String reply = buildMockReply(request.getMessage(), referencedOrders, user);
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
            ChatPromptMode promptMode,
            PrintWriter writer,
            StringBuilder replyBuilder) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bot_id", botId());
        payload.put("user_id", String.valueOf(user.getId()));
        payload.put("stream", true);
        payload.put("auto_save_history", true);
        
        // Coze conversation_id can only be passed if it's a valid remote one (non sess_)
        if (StringUtils.hasText(sessionId) && !sessionId.startsWith("sess_")) {
            payload.put("conversation_id", sessionId);
        }
        
        payload.put("additional_messages", List.of(Map.of(
                "role", "user",
                "content", prompt,
                "content_type", "text",
                "type", "question"
        )));

        // Send metadata event first
        Map<String, Object> metadata = Map.of(
                "sessionId", sessionId,
                "mode", "live",
                "promptMode", promptMode.wireValue()
        );
        writer.write("event: metadata\n");
        writer.write("data: " + objectMapper.writeValueAsString(metadata) + "\n\n");
        writer.flush();

        try (InputStream is = invokeStream(apiUrl() + "/chat", payload)) {
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

    private InputStream invokeStream(String url, Map<String, Object> payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream is = response.body()) {
                String errBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException("Coze API 请求失败: HTTP " + response.statusCode() + " - " + errBody);
            }
        }
        return response.body();
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

    /** 客服 Prompt 分流：知识库问答 vs 数据库实时数据 */
    private enum ChatPromptMode {
        /** 原样发送用户问题，由 Bot 检索知识库 */
        KNOWLEDGE("knowledge"),
        /** 注入 MySQL 实时数据后作答 */
        REALTIME("realtime");

        private final String wireValue;

        ChatPromptMode(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
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

    public Map<String, Object> scheduling(CozeSchedulingRequest request) {
        List<ProdWorkOrder> workOrders = request.getWorkOrderIds().stream()
                .map(prodWorkOrderMapper::selectById)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<MatMaterial> warningMaterials = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                .eq(MatMaterial::getAlertStatus, "warning"));
        Map<String, Boolean> constraints = resolveSchedulingConstraints(request);
        LocalDate planDate = request.getPlanDate() != null ? request.getPlanDate() : LocalDate.now();

        try {
            if (cozeConfigService.isConfigured()) {
                String workflowId = cozeConfigService.getEffectiveWorkflowId();
                if (!StringUtils.hasText(workflowId)) {
                    return mockSchedulingResult(workOrders, warningMaterials, constraints, planDate,
                            "未配置排产工作流 ID，请在 Coze 配置中填写");
                }
                Map<String, Object> parsed = finalizeSchedulingResult(
                        runSchedulingWorkflow(workOrders, warningMaterials, planDate, constraints),
                        workOrders,
                        constraints,
                        planDate);
                return Map.of(
                        "mode", "live",
                        "result", parsed,
                        "constraints", constraints
                );
            }
        } catch (Exception ex) {
            return mockSchedulingResult(workOrders, warningMaterials, constraints, planDate,
                    "Coze 工作流调用失败：" + ex.getMessage()
                            + "。请确认工作流「开始节点」入参名为 plan_date、work_orders_json、material_status、constraints_json、current_time");
        }
        return mockSchedulingResult(workOrders, warningMaterials, constraints, planDate,
                "Coze 未启用或未配置，当前展示演示排产结果");
    }

    private Map<String, Boolean> resolveSchedulingConstraints(CozeSchedulingRequest request) {
        Map<String, Boolean> constraints = new LinkedHashMap<>();
        constraints.put("materialAvailability", request.getMaterialConstraint() == null || request.getMaterialConstraint());
        constraints.put("deviceLoad", request.getDeviceConstraint() == null || request.getDeviceConstraint());
        constraints.put("teamHours", request.getTeamConstraint() == null || request.getTeamConstraint());
        return constraints;
    }

    private Map<String, Object> mockSchedulingResult(
            List<ProdWorkOrder> workOrders,
            List<MatMaterial> warningMaterials,
            Map<String, Boolean> constraints,
            LocalDate planDate,
            String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "mock");
        result.put("message", message);
        result.put("constraints", constraints);
        result.put("result", finalizeSchedulingResult(
                buildMockScheduling(workOrders, warningMaterials, constraints, planDate),
                workOrders,
                constraints,
                planDate));
        return result;
    }

    private String workflowApiUrl() {
        String base = apiUrl().replaceAll("/v3/?$", "");
        if (base.endsWith("/v1")) {
            return base;
        }
        return "https://api.coze.cn/v1";
    }

    private Map<String, Object> parseWorkflowSchedulingResult(JsonNode root) throws IOException {
        JsonNode dataNode = root.path("data");
        if (dataNode.isTextual()) {
            JsonNode parsedData = objectMapper.readTree(dataNode.asText());
            Map<String, Object> fromData = extractSchedulingPayload(parsedData);
            if (fromData != null) {
                return fromData;
            }
        } else if (dataNode.isObject()) {
            Map<String, Object> fromData = extractSchedulingPayload(dataNode);
            if (fromData != null) {
                return fromData;
            }
        }
        Map<String, Object> fromRoot = extractSchedulingPayload(root);
        return fromRoot;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSchedulingPayload(JsonNode node) throws IOException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String text = stripMarkdownJson(node.asText());
            if (!StringUtils.hasText(text)) {
                return null;
            }
            return extractSchedulingPayload(objectMapper.readTree(text));
        }
        if (node.isObject()) {
            if (hasSchedulingKeys(node)) {
                return objectMapper.convertValue(node, Map.class);
            }
            for (String key : List.of("Output", "output", "result", "data", "content")) {
                Map<String, Object> nested = extractSchedulingPayload(node.path(key));
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean hasSchedulingKeys(JsonNode node) {
        return node.has("priorities") || node.has("bottlenecks")
                || node.has("dispatchSuggestions") || node.has("dispatches");
    }

    private String stripMarkdownJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (start >= 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }
        return text;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", cozeConfigService.isConfigured());
        result.put("enabled", cozeConfigService.isEnabled());
        result.put("apiUrl", apiUrl());
        result.put("botId", StringUtils.hasText(botId()) ? botId() : null);
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
                    CompletableFuture.supplyAsync(this::testChatHealth);
            CompletableFuture<Map<String, Object>> workflowFuture =
                    CompletableFuture.supplyAsync(this::testWorkflowHealth);
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

    public Map<String, Object> healthChat() {
        if (!cozeConfigService.isConfigured()) {
            return Map.of("status", "skipped", "message", "未配置 API Token 或 Bot ID");
        }
        return testChatHealth();
    }

    public Map<String, Object> healthWorkflow() {
        if (!cozeConfigService.isConfigured()) {
            return Map.of("status", "skipped", "message", "未配置 API Token 或 Bot ID");
        }
        return testWorkflowHealth();
    }

    private Map<String, Object> testChatHealth() {
        Map<String, Object> chat = new LinkedHashMap<>();
        try {
            JsonNode root = createChat("health-check", null, "ping");
            String chatId = root.path("data").path("id").asText("");
            String conversationId = root.path("data").path("conversation_id").asText("");
            String status = pollChatStatus(conversationId, chatId);
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

    private Map<String, Object> testWorkflowHealth() {
        Map<String, Object> workflow = new LinkedHashMap<>();
        String workflowId = cozeConfigService.getEffectiveWorkflowId();
        workflow.put("workflowId", StringUtils.hasText(workflowId) ? workflowId : null);
        if (!StringUtils.hasText(workflowId)) {
            workflow.put("status", "skipped");
            workflow.put("message", "未配置排产工作流 ID，跳过工作流测试");
            return workflow;
        }

        try {
            List<ProdWorkOrder> workOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                    .in(ProdWorkOrder::getStatus, List.of("pending", "assigned"))
                    .orderByAsc(ProdWorkOrder::getPriority)
                    .last("limit 1"));
            if (workOrders.isEmpty()) {
                workOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                        .orderByDesc(ProdWorkOrder::getUpdatedTime)
                        .last("limit 1"));
            }
            if (workOrders.isEmpty()) {
                workOrders = List.of(buildSampleWorkOrder());
            }

            List<MatMaterial> warningMaterials = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                    .eq(MatMaterial::getAlertStatus, "warning")
                    .last("limit 5"));
            ProdWorkOrder sampleOrder = workOrders.get(0);
            Map<String, Object> parsed = runSchedulingWorkflow(
                    workOrders, warningMaterials, LocalDate.now(), resolveSchedulingConstraints(new CozeSchedulingRequest()));
            Map<String, Object> summary = summarizeSchedulingResult(parsed);

            workflow.put("status", "ok");
            workflow.put("mode", "live");
            workflow.put("sampleWorkOrderNo", sampleOrder.getOrderNo());
            workflow.put("summary", summary);
            workflow.put("message", "排产工作流调用成功，样例工单 "
                    + sampleOrder.getOrderNo()
                    + "，返回 priorities=" + summary.get("priorities")
                    + "、bottlenecks=" + summary.get("bottlenecks")
                    + "、dispatches=" + summary.get("dispatches"));
        } catch (Exception ex) {
            workflow.put("status", "error");
            workflow.put("message", "排产工作流测试失败：" + ex.getMessage());
        }
        return workflow;
    }

    private ProdWorkOrder buildSampleWorkOrder() {
        ProdWorkOrder sample = new ProdWorkOrder();
        sample.setOrderNo("WO-TEST-001");
        sample.setProductName("连通性测试产品");
        sample.setProcessName("装配工序");
        sample.setPriority(2);
        sample.setStatus("pending");
        sample.setProgress(0);
        return sample;
    }

    private Map<String, Object> runSchedulingWorkflow(
            List<ProdWorkOrder> workOrders,
            List<MatMaterial> warningMaterials,
            LocalDate planDate,
            Map<String, Boolean> constraints) throws IOException, InterruptedException {
        String workflowId = cozeConfigService.getEffectiveWorkflowId();
        if (!StringUtils.hasText(workflowId)) {
            throw new IOException("未配置排产工作流 ID");
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("plan_date", String.valueOf(planDate));
        parameters.put("work_orders_json", objectMapper.writeValueAsString(buildSchedulingWorkOrderViews(workOrders)));
        if (Boolean.TRUE.equals(constraints.get("materialAvailability"))) {
            parameters.put("material_status", objectMapper.writeValueAsString(warningMaterials));
        } else {
            parameters.put("material_status", "[]");
        }
        parameters.put("constraints_json", objectMapper.writeValueAsString(constraints));
        parameters.put("current_time", LocalDateTime.now().format(SCHEDULING_TIME_FORMAT));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflow_id", workflowId.trim());
        payload.put("bot_id", botId());
        payload.put("parameters", parameters);
        JsonNode root = invokeJson(workflowApiUrl() + "/workflow/run", payload);
        Map<String, Object> parsed = parseWorkflowSchedulingResult(root);
        if (parsed == null || parsed.isEmpty()) {
            throw new IOException("工作流返回结果无法解析，请检查 Coze 结束节点是否输出 JSON（priorities / bottlenecks / dispatchSuggestions）");
        }
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildSchedulingWorkOrderViews(List<ProdWorkOrder> workOrders) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (ProdWorkOrder order : workOrders) {
            Map<String, Object> view = objectMapper.convertValue(order, Map.class);
            ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
            view.put("workOrderCode", order.getOrderNo());
            view.put("teamName", team == null ? null : team.getTeamName());
            views.add(view);
        }
        return views;
    }

    private Map<String, String> resolveAssignedTeamNames(List<ProdWorkOrder> workOrders) {
        Map<String, String> assignedTeams = new LinkedHashMap<>();
        for (ProdWorkOrder order : workOrders) {
            if (order.getTeamId() == null || !StringUtils.hasText(order.getOrderNo())) {
                continue;
            }
            ProdTeam team = prodTeamMapper.selectById(order.getTeamId());
            if (team != null && StringUtils.hasText(team.getTeamName())) {
                assignedTeams.put(order.getOrderNo(), team.getTeamName());
            }
        }
        return assignedTeams;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> finalizeSchedulingResult(
            Map<String, Object> parsed,
            List<ProdWorkOrder> workOrders,
            Map<String, Boolean> constraints,
            LocalDate planDate) {
        if (parsed == null || parsed.isEmpty()) {
            return parsed;
        }
        Map<String, Object> result = applySchedulingConstraintsToResult(parsed, constraints, planDate);
        normalizeDispatchTeams(result, workOrders);
        normalizeDispatchStartTimes(result, planDate, Boolean.TRUE.equals(constraints.get("teamHours")));
        result.put("summary", buildSchedulingSummary(result, workOrders, constraints, planDate));
        return result;
    }

    public Map<String, Object> schedulingContext(List<Long> workOrderIds) {
        List<ProdWorkOrder> workOrders = workOrderIds.stream()
                .map(prodWorkOrderMapper::selectById)
                .filter(java.util.Objects::nonNull)
                .toList();

        List<Map<String, Object>> workOrderViews = workOrders.stream()
                .map(this::toSchedulingContextWorkOrder)
                .toList();

        List<Map<String, Object>> materialAlerts = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                        .eq(MatMaterial::getAlertStatus, "warning")
                        .orderByAsc(MatMaterial::getStockQty))
                .stream()
                .map(this::toSchedulingMaterialAlert)
                .toList();

        List<Long> ids = workOrders.stream().map(ProdWorkOrder::getId).toList();
        List<Map<String, Object>> exceptions = ids.isEmpty()
                ? List.of()
                : excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                        .in(ExcEvent::getWorkOrderId, ids)
                        .in(ExcEvent::getStatus, List.of("open", "processing"))
                        .orderByDesc(ExcEvent::getCreateTime))
                .stream()
                .map(this::toSchedulingExceptionView)
                .toList();

        List<Map<String, Object>> teams = buildSchedulingTeamLoads(workOrders);

        long overdueCount = workOrderViews.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("overdue")))
                .count();

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("selectedCount", workOrders.size());
        kpi.put("overdueCount", overdueCount);
        kpi.put("exceptionCount", exceptions.size());
        kpi.put("materialWarningCount", materialAlerts.size());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("workOrders", workOrderViews);
        context.put("materialAlerts", materialAlerts);
        context.put("exceptions", exceptions);
        context.put("teams", teams);
        context.put("kpi", kpi);
        return context;
    }

    private Map<String, Object> toSchedulingContextWorkOrder(ProdWorkOrder order) {
        ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
        long exceptionCount = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getWorkOrderId, order.getId())
                .in(ExcEvent::getStatus, List.of("open", "processing")));

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", order.getId());
        view.put("orderNo", order.getOrderNo());
        view.put("productName", order.getProductName());
        view.put("processName", order.getProcessName());
        view.put("status", order.getStatus());
        view.put("progress", order.getProgress() == null ? 0 : order.getProgress());
        view.put("priority", order.getPriority());
        view.put("priorityLabel", schedulingPriorityLabel(order.getPriority()));
        view.put("teamId", order.getTeamId());
        view.put("teamName", team == null ? null : team.getTeamName());
        view.put("deadline", order.getDeadline());
        view.put("exceptionCount", exceptionCount);

        LocalDate deadlineDate = order.getDeadline() == null ? null : order.getDeadline().toLocalDate();
        LocalDate today = LocalDate.now();
        boolean overdue = deadlineDate != null && deadlineDate.isBefore(today);
        Long daysToDeadline = deadlineDate == null ? null : java.time.temporal.ChronoUnit.DAYS.between(today, deadlineDate);
        view.put("overdue", overdue);
        view.put("daysToDeadline", daysToDeadline);
        view.put("deadlineLabel", buildDeadlineLabel(deadlineDate, overdue, daysToDeadline));
        view.put("materialRisk", materialAlertsContainProduct(order.getProductName()) ? "warning" : "normal");
        return view;
    }

    private boolean materialAlertsContainProduct(String productName) {
        if (!StringUtils.hasText(productName)) {
            return false;
        }
        long count = matMaterialMapper.selectCount(new LambdaQueryWrapper<MatMaterial>()
                .eq(MatMaterial::getAlertStatus, "warning")
                .like(MatMaterial::getMaterialName, productName.trim()));
        return count > 0;
    }

    private String buildDeadlineLabel(LocalDate deadlineDate, boolean overdue, Long daysToDeadline) {
        if (deadlineDate == null) {
            return "未设交期";
        }
        if (overdue) {
            return "已逾期 " + Math.abs(daysToDeadline) + " 天";
        }
        if (daysToDeadline != null && daysToDeadline == 0) {
            return "今日交期";
        }
        if (daysToDeadline != null && daysToDeadline <= 3) {
            return "距交期 " + daysToDeadline + " 天";
        }
        return deadlineDate.toString();
    }

    private Map<String, Object> toSchedulingMaterialAlert(MatMaterial material) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", material.getId());
        row.put("materialCode", material.getMaterialCode());
        row.put("materialName", material.getMaterialName());
        row.put("stockQty", material.getStockQty());
        row.put("safetyStock", material.getSafetyStock());
        row.put("unit", material.getUnit());
        row.put("alertStatus", material.getAlertStatus());
        return row;
    }

    private Map<String, Object> toSchedulingExceptionView(ExcEvent event) {
        ProdWorkOrder order = event.getWorkOrderId() == null
                ? null
                : prodWorkOrderMapper.selectById(event.getWorkOrderId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.getId());
        row.put("eventNo", event.getEventNo());
        row.put("eventType", event.getEventType());
        row.put("workOrderId", event.getWorkOrderId());
        row.put("workOrderNo", order == null ? null : order.getOrderNo());
        row.put("description", event.getDescription());
        row.put("status", event.getStatus());
        row.put("occurTime", event.getOccurTime());
        return row;
    }

    private List<Map<String, Object>> buildSchedulingTeamLoads(List<ProdWorkOrder> selectedOrders) {
        List<ProdTeam> teams = prodTeamMapper.selectList(new LambdaQueryWrapper<ProdTeam>().orderByAsc(ProdTeam::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProdTeam team : teams) {
            List<ProdWorkOrder> teamOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                    .eq(ProdWorkOrder::getTeamId, team.getId())
                    .in(ProdWorkOrder::getStatus, List.of("assigned", "producing", "exception")));
            long pending = teamOrders.stream().filter(o -> "assigned".equals(o.getStatus())).count();
            long producing = teamOrders.stream().filter(o -> "producing".equals(o.getStatus())).count();
            long selectedHere = selectedOrders.stream()
                    .filter(o -> team.getId().equals(o.getTeamId()))
                    .count();
            int activeTasks = teamOrders.size();
            int loadRate = Math.min(100, activeTasks * 20 + (int) producing * 15);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", team.getId());
            row.put("teamName", team.getTeamName());
            row.put("memberCount", team.getMemberCount());
            row.put("pendingCount", pending);
            row.put("producingCount", producing);
            row.put("activeTaskCount", activeTasks);
            row.put("selectedCount", selectedHere);
            row.put("proposedHours", 0);
            row.put("loadRate", loadRate);
            result.add(row);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String buildSchedulingSummary(
            Map<String, Object> result,
            List<ProdWorkOrder> workOrders,
            Map<String, Boolean> constraints,
            LocalDate planDate) {
        List<Map<String, Object>> priorities = copyMapList(firstPresent(result, "priorities", "prioritySuggestions"));
        List<Map<String, Object>> bottlenecks = copyMapList(firstPresent(result, "bottlenecks", "bottleneckWarnings"));
        List<Map<String, Object>> dispatches = copyMapList(firstPresent(result, "dispatches", "dispatchSuggestions"));

        StringBuilder summary = new StringBuilder();
        summary.append("计划日期 ").append(planDate).append("，共 ").append(workOrders.size()).append(" 个待排产工单。");

        if (!priorities.isEmpty()) {
            Map<String, Object> top = priorities.get(0);
            summary.append(" 建议优先排产 ")
                    .append(top.getOrDefault("workOrderCode", "--"))
                    .append("（").append(top.getOrDefault("priorityLabel", "--")).append("）");
            Object reason = top.get("reason");
            if (reason != null && StringUtils.hasText(String.valueOf(reason))) {
                summary.append("，理由：").append(String.valueOf(reason));
            }
            summary.append("。");
        }

        if (Boolean.TRUE.equals(constraints.get("deviceLoad")) && !bottlenecks.isEmpty()) {
            Map<String, Object> bottleneck = bottlenecks.stream()
                    .filter(row -> {
                        Object rate = row.get("loadRate");
                        return rate instanceof Number number && number.intValue() > 0;
                    })
                    .findFirst()
                    .orElse(bottlenecks.get(0));
            summary.append(" 瓶颈工序 ")
                    .append(bottleneck.getOrDefault("processName", "--"))
                    .append("，预计负荷 ")
                    .append(bottleneck.getOrDefault("loadRate", 0))
                    .append("%。");
        }

        if (!dispatches.isEmpty()) {
            Map<String, Object> last = dispatches.get(dispatches.size() - 1);
            summary.append(" 末单预计 ")
                    .append(last.getOrDefault("startTime", "--"))
                    .append(" 启动，耗时 ")
                    .append(last.getOrDefault("hours", "--"))
                    .append(" 小时。");
        }

        if (!Boolean.TRUE.equals(constraints.get("materialAvailability"))) {
            summary.append(" 本次未纳入物料可用性约束。");
        }
        if (!Boolean.TRUE.equals(constraints.get("teamHours"))) {
            summary.append(" 本次未纳入班组工时约束。");
        }
        return summary.toString();
    }

    private void normalizeDispatchTeams(Map<String, Object> result, List<ProdWorkOrder> workOrders) {
        Map<String, String> assignedTeams = resolveAssignedTeamNames(workOrders);
        if (assignedTeams.isEmpty()) {
            return;
        }
        for (String dispatchKey : List.of("dispatches", "dispatchSuggestions")) {
            if (!result.containsKey(dispatchKey)) {
                continue;
            }
            List<Map<String, Object>> dispatches = copyMapList(result.get(dispatchKey));
            for (Map<String, Object> row : dispatches) {
                String workOrderCode = readDispatchWorkOrderCode(row);
                String assignedTeam = assignedTeams.get(workOrderCode);
                if (!StringUtils.hasText(assignedTeam)) {
                    continue;
                }
                row.put("teamName", assignedTeam);
                row.put("team", assignedTeam);
                row.put("suggestedTeam", assignedTeam);
            }
            result.put(dispatchKey, dispatches);
        }
    }

    private String readDispatchWorkOrderCode(Map<String, Object> row) {
        for (String key : List.of("workOrderCode", "workOrderNo", "orderNo", "code")) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.hasText(text) && !"null".equals(text) && !"--".equals(text)) {
                return text;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applySchedulingConstraintsToResult(
            Map<String, Object> parsed,
            Map<String, Boolean> constraints,
            LocalDate planDate) {
        if (parsed == null || parsed.isEmpty()) {
            return parsed;
        }
        boolean materialEnabled = Boolean.TRUE.equals(constraints.get("materialAvailability"));
        boolean deviceEnabled = Boolean.TRUE.equals(constraints.get("deviceLoad"));
        boolean teamEnabled = Boolean.TRUE.equals(constraints.get("teamHours"));

        Map<String, Object> result = new LinkedHashMap<>(parsed);
        List<Map<String, Object>> priorities = reshapePriorities(
                firstPresent(result, "priorities", "prioritySuggestions"),
                materialEnabled,
                teamEnabled);
        putIfPresent(result, "priorities", priorities);
        putIfPresent(result, "prioritySuggestions", priorities);

        List<Map<String, Object>> bottlenecks = reshapeBottlenecks(
                firstPresent(result, "bottlenecks", "bottleneckWarnings"),
                materialEnabled,
                deviceEnabled,
                teamEnabled);
        putIfPresent(result, "bottlenecks", bottlenecks);
        putIfPresent(result, "bottleneckWarnings", bottlenecks);

        for (String dispatchKey : List.of("dispatches", "dispatchSuggestions")) {
            if (result.containsKey(dispatchKey)) {
                result.put(dispatchKey, reshapeDispatches(result.get(dispatchKey), teamEnabled, planDate));
            }
        }
        return result;
    }

    private Object firstPresent(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key) && source.get(key) != null) {
                return source.get(key);
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (target.containsKey(key)) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> copyMapList(Object source) {
        if (!(source instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                copied.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return copied;
    }

    private List<Map<String, Object>> reshapePriorities(
            Object source,
            boolean materialEnabled,
            boolean teamEnabled) {
        List<Map<String, Object>> priorities = copyMapList(source);
        for (Map<String, Object> row : priorities) {
            for (String key : List.of("reason", "comment", "rationale")) {
                if (!row.containsKey(key)) {
                    continue;
                }
                String text = String.valueOf(row.get(key));
                if (!materialEnabled) {
                    text = stripMaterialReferences(text);
                }
                if (!teamEnabled) {
                    text = appendTeamConstraintNote(text);
                }
                row.put(key, text);
            }
        }
        return priorities;
    }

    private List<Map<String, Object>> reshapeBottlenecks(
            Object source,
            boolean materialEnabled,
            boolean deviceEnabled,
            boolean teamEnabled) {
        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        if (!deviceEnabled) {
            bottlenecks.add(constraintNoticeBottleneck("—", "未启用设备负荷约束，已跳过瓶颈分析"));
        } else {
            for (Map<String, Object> row : copyMapList(source)) {
                if (!materialEnabled && isMaterialBottleneck(row)) {
                    continue;
                }
                if (!materialEnabled) {
                    row.put("suggestion", stripMaterialReferences(String.valueOf(
                            row.getOrDefault("suggestion", row.getOrDefault("advice", row.get("reason"))))));
                    row.put("advice", row.get("suggestion"));
                }
                bottlenecks.add(row);
            }
        }
        if (!materialEnabled) {
            bottlenecks.add(constraintNoticeBottleneck("物料约束", "未启用物料可用性约束，排产未纳入缺料风险"));
        }
        if (!teamEnabled) {
            bottlenecks.add(constraintNoticeBottleneck("班组约束", "未启用班组工时约束，派工时间仅按默认班次估算"));
        }
        return bottlenecks;
    }

    private List<Map<String, Object>> reshapeDispatches(
            Object source,
            boolean teamEnabled,
            LocalDate planDate) {
        List<Map<String, Object>> dispatches = copyMapList(source);
        if (teamEnabled || dispatches.isEmpty()) {
            return dispatches;
        }
        String baselineStart = formatStartTime(computeEarliestDispatchStart(planDate, 0));
        for (Map<String, Object> row : dispatches) {
            row.put("hours", "待定");
            row.put("estimatedHours", "待定");
            row.put("startTime", baselineStart);
            row.put("suggestedStart", baselineStart);
            row.put("suggestedStartTime", baselineStart);
        }
        return dispatches;
    }

    private void normalizeDispatchStartTimes(
            Map<String, Object> result,
            LocalDate planDate,
            boolean teamEnabled) {
        LocalDate effectivePlanDate = planDate != null ? planDate : LocalDate.now();
        for (String dispatchKey : List.of("dispatches", "dispatchSuggestions")) {
            if (!result.containsKey(dispatchKey)) {
                continue;
            }
            List<Map<String, Object>> dispatches = copyMapList(result.get(dispatchKey));
            if (dispatches.isEmpty()) {
                continue;
            }
            LocalDateTime cursor = computeEarliestDispatchStart(effectivePlanDate, 0);
            for (Map<String, Object> row : dispatches) {
                String formatted = formatStartTime(cursor);
                row.put("startTime", formatted);
                row.put("suggestedStart", formatted);
                row.put("suggestedStartTime", formatted);
                if (teamEnabled) {
                    cursor = cursor.plusHours(parseDispatchHours(row.get("hours")));
                }
            }
            result.put(dispatchKey, dispatches);
        }
    }

    private int parseDispatchHours(Object raw) {
        if (raw == null) {
            return DEFAULT_DISPATCH_HOURS;
        }
        String text = String.valueOf(raw).trim();
        if (!StringUtils.hasText(text) || "待定".equals(text) || "null".equals(text)) {
            return DEFAULT_DISPATCH_HOURS;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (!StringUtils.hasText(digits)) {
            return DEFAULT_DISPATCH_HOURS;
        }
        try {
            return Math.max(1, Integer.parseInt(digits));
        } catch (NumberFormatException ex) {
            return DEFAULT_DISPATCH_HOURS;
        }
    }

    private LocalDateTime computeEarliestDispatchStart(LocalDate planDate, int staggerIndex) {
        LocalDate effectiveDate = planDate != null ? planDate : LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayShiftStart = effectiveDate.atTime(8, 0).plusHours(staggerIndex * (long) DEFAULT_DISPATCH_HOURS);

        if (effectiveDate.isAfter(now.toLocalDate())) {
            return dayShiftStart;
        }
        if (effectiveDate.isBefore(now.toLocalDate())) {
            return roundUpToNextHalfHour(now).plusHours(staggerIndex * (long) DEFAULT_DISPATCH_HOURS);
        }
        LocalDateTime earliestToday = roundUpToNextHalfHour(now);
        if (staggerIndex == 0) {
            return earliestToday.isAfter(dayShiftStart) ? earliestToday : dayShiftStart;
        }
        return earliestToday.plusHours(staggerIndex * (long) DEFAULT_DISPATCH_HOURS);
    }

    private LocalDateTime roundUpToNextHalfHour(LocalDateTime time) {
        int minute = time.getMinute();
        if (minute % 30 == 0 && time.getSecond() == 0 && time.getNano() == 0) {
            return time;
        }
        int minutesToAdd = minute % 30 == 0 ? 30 : (30 - minute % 30);
        return time.plusMinutes(minutesToAdd).withSecond(0).withNano(0);
    }

    private String formatStartTime(LocalDateTime time) {
        return time.format(SCHEDULING_TIME_FORMAT);
    }

    private Map<String, Object> constraintNoticeBottleneck(String processName, String suggestion) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("processName", processName);
        row.put("loadRate", 0);
        row.put("suggestion", suggestion);
        row.put("advice", suggestion);
        return row;
    }

    private boolean isMaterialBottleneck(Map<String, Object> row) {
        String text = String.join(" ",
                String.valueOf(row.getOrDefault("processName", "")),
                String.valueOf(row.getOrDefault("process", "")),
                String.valueOf(row.getOrDefault("name", "")),
                String.valueOf(row.getOrDefault("suggestion", "")),
                String.valueOf(row.getOrDefault("advice", "")),
                String.valueOf(row.getOrDefault("reason", "")));
        return containsMaterialReference(text);
    }

    private boolean containsMaterialReference(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("物料")
                || text.contains("缺料")
                || text.contains("库存")
                || text.contains("预警")
                || text.contains("安全库存")
                || text.contains("备料");
    }

    private String stripMaterialReferences(String text) {
        if (!StringUtils.hasText(text) || "null".equals(text)) {
            return "按交期与优先级排序（未纳入物料可用性约束）";
        }
        String[] segments = text.split("[。；;\\n]");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (!StringUtils.hasText(trimmed) || containsMaterialReference(trimmed)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('。');
            }
            builder.append(trimmed);
        }
        if (builder.length() == 0) {
            return "按交期与优先级排序（未纳入物料可用性约束）";
        }
        return builder.toString();
    }

    private String appendTeamConstraintNote(String text) {
        if (!StringUtils.hasText(text) || "null".equals(text)) {
            return "按交期与优先级排序（未考虑班组工时）";
        }
        if (text.contains("班组工时") || text.contains("未考虑班组")) {
            return text;
        }
        return text + "（未考虑班组工时）";
    }

    private Map<String, Object> summarizeSchedulingResult(Map<String, Object> parsed) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("priorities", listSize(parsed.get("priorities")));
        summary.put("bottlenecks", listSize(parsed.get("bottlenecks")));
        Object dispatches = parsed.containsKey("dispatchSuggestions")
                ? parsed.get("dispatchSuggestions")
                : parsed.get("dispatches");
        summary.put("dispatches", listSize(dispatches));
        return summary;
    }

    private int listSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
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

    private boolean isConfigured() {
        return cozeConfigService.isConfigured();
    }

    private String apiToken() {
        return cozeConfigService.getEffectiveApiToken();
    }

    private String botId() {
        return cozeConfigService.getEffectiveBotId();
    }

    private String apiUrl() {
        return cozeConfigService.getEffectiveApiUrl();
    }

    private String invokeLiveChat(SysUser user, String sessionId, String prompt) throws IOException, InterruptedException {
        JsonNode root = createChat(String.valueOf(user.getId()), sessionId, prompt);
        JsonNode data = root.path("data");
        String chatId = data.path("id").asText("");
        String conversationId = data.path("conversation_id").asText("");
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(conversationId)) {
            throw new IOException("Coze 未返回 chat_id 或 conversation_id");
        }
        String status = pollChatStatus(conversationId, chatId);
        if (!"completed".equals(status)) {
            throw new IOException("Coze 对话未完成，status=" + status);
        }
        return fetchAnswerContent(conversationId, chatId);
    }

    private JsonNode createChat(String userId, String conversationId, String prompt) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bot_id", botId());
        payload.put("user_id", userId);
        payload.put("stream", false);
        payload.put("auto_save_history", true);
        if (StringUtils.hasText(conversationId)) {
            payload.put("conversation_id", conversationId);
        }
        payload.put("additional_messages", List.of(Map.of(
                "role", "user",
                "content", prompt,
                "content_type", "text",
                "type", "question"
        )));
        return invokeJson(apiUrl() + "/chat", payload);
    }

    private String pollChatStatus(String conversationId, String chatId) throws IOException, InterruptedException {
        for (int i = 0; i < chatPollTimeoutSeconds; i++) {
            JsonNode root = invokeGetJson(apiUrl() + "/chat/retrieve?conversation_id="
                    + encode(conversationId) + "&chat_id=" + encode(chatId));
            String status = root.path("data").path("status").asText("");
            if ("completed".equals(status) || "failed".equals(status) || "canceled".equals(status)) {
                return status;
            }
            Thread.sleep(1000);
        }
        return "timeout";
    }

    private String fetchAnswerContent(String conversationId, String chatId) throws IOException, InterruptedException {
        JsonNode root = invokeGetJson(apiUrl() + "/chat/message/list?conversation_id="
                + encode(conversationId) + "&chat_id=" + encode(chatId));
        JsonNode messages = root.path("data");
        if (!messages.isArray()) {
            return "AI 服务暂无回复内容";
        }
        StringBuilder answers = new StringBuilder();
        for (JsonNode message : messages) {
            if ("answer".equals(message.path("type").asText())) {
                if (answers.length() > 0) {
                    answers.append("\n");
                }
                answers.append(message.path("content").asText(""));
            }
        }
        return answers.length() > 0 ? prettifyReply(answers.toString()) : "AI 服务暂无回复内容";
    }

    private String prettifyReply(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "AI 服务暂无回复内容";
        }
        String text = raw.trim();
        try {
            JsonNode node = objectMapper.readTree(text);
            if (!node.isObject()) {
                return raw;
            }

            StringBuilder builder = new StringBuilder();
            appendSection(builder, "生产概况", node, "daily_summary", "summary", "overview");
            appendSection(builder, "问题分析", node, "problem_analysis", "analysis");
            appendSection(builder, "明日建议", node, "tomorrow_suggestion", "suggestion", "next_action");
            appendSection(builder, "处理建议", node, "recommendation", "recommendations");

            if (builder.length() > 0) {
                return builder.toString().trim();
            }
        } catch (Exception ignored) {
        }
        return raw;
    }

    private void appendSection(StringBuilder builder, String title, JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }
            String text = value.asText("").trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("【").append(title).append("】").append("\n").append(text);
            return;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonNode invokeGetJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiToken())
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Coze API 请求失败: HTTP " + response.statusCode() + " - " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    private JsonNode invokeJson(String url, Map<String, Object> payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Coze API 请求失败: HTTP " + response.statusCode() + " - " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    private List<AiChatLog> loadSessionHistory(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return List.of();
        }
        List<AiChatLog> recent = aiChatLogMapper.selectList(new LambdaQueryWrapper<AiChatLog>()
                .eq(AiChatLog::getUserId, userId)
                .eq(AiChatLog::getSessionId, sessionId)
                .orderByDesc(AiChatLog::getCreateTime)
                .last("limit " + SESSION_HISTORY_TURNS));
        if (recent.isEmpty()) {
            return List.of();
        }
        List<AiChatLog> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);
        return chronological;
    }

    private void appendSessionHistorySection(StringBuilder builder, List<AiChatLog> sessionHistory) {
        if (sessionHistory.isEmpty()) {
            return;
        }
        builder.append("\n【本会话上下文】\n");
        builder.append("以下为当前对话 session 内的前轮问答，仅供理解追问（如「它」「刚才那个工单」）；");
        builder.append("实时业务数据以下文最新注入为准，不得编造。\n");
        for (AiChatLog log : sessionHistory) {
            builder.append("用户：").append(truncateHistoryText(log.getUserMessage())).append("\n");
            builder.append("助手：").append(truncateHistoryText(log.getAiResponse())).append("\n");
        }
    }

    private String truncateHistoryText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.length() <= SESSION_HISTORY_MAX_CHARS) {
            return normalized;
        }
        return normalized.substring(0, SESSION_HISTORY_MAX_CHARS) + "…";
    }

    private List<ProdWorkOrder> resolveReferencedOrdersFromText(String message) {
        if (!StringUtils.hasText(message)) {
            return List.of();
        }
        List<ProdWorkOrder> result = new ArrayList<>();
        Matcher matcher = ORDER_NO_PATTERN.matcher(message.toUpperCase());
        while (matcher.find()) {
            ProdWorkOrder order = findWorkOrderByNo(matcher.group());
            if (order != null && result.stream().noneMatch(item -> item.getId().equals(order.getId()))) {
                result.add(order);
            }
        }
        return result;
    }

    private List<ProdWorkOrder> resolveReferencedOrdersFromHistory(List<AiChatLog> sessionHistory) {
        ProdWorkOrder lastMatch = null;
        for (AiChatLog log : sessionHistory) {
            for (String text : List.of(log.getUserMessage(), log.getAiResponse())) {
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                Matcher matcher = ORDER_NO_PATTERN.matcher(text.toUpperCase());
                while (matcher.find()) {
                    ProdWorkOrder order = findWorkOrderByNo(matcher.group());
                    if (order != null) {
                        lastMatch = order;
                    }
                }
            }
        }
        return lastMatch == null ? List.of() : List.of(lastMatch);
    }

    private ProdWorkOrder findWorkOrderByNo(String orderNo) {
        return prodWorkOrderMapper.selectOne(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getOrderNo, orderNo)
                .last("limit 1"));
    }

    private boolean isFollowUpQuery(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() > 48) {
            return false;
        }
        return containsAny(trimmed,
                "它", "这个", "那个", "上面", "刚才", "刚刚", "继续", "还有", "然后呢", "再说说")
                || trimmed.endsWith("呢") || trimmed.endsWith("吗") || trimmed.endsWith("？") || trimmed.endsWith("?");
    }

    private String buildChatPrompt(
            SysUser user,
            String message,
            List<ProdWorkOrder> orders,
            ChatPromptMode promptMode,
            List<AiChatLog> sessionHistory) {
        if (promptMode == ChatPromptMode.REALTIME) {
            return buildRealtimeDataPrompt(user, message, orders, sessionHistory);
        }
        return buildKnowledgePrompt(user, message, sessionHistory);
    }

    /**
     * 知识库问答：注入用户上下文 + 排版要求（与人设 §4 / §系统上下文 一致）。
     * 不注入 FAQ 正文；检索仍由 Coze Bot 知识库完成。
     * REALTIME 因消息内含【回答要求】而排版正常，KNOWLEDGE 需同样触发，否则 API 易直接粘贴 KB 原文。
     */
    private String buildKnowledgePrompt(
            SysUser user,
            String message,
            List<AiChatLog> sessionHistory) {
        StringBuilder builder = new StringBuilder();
        builder.append("【系统上下文·知识库问答】\n");
        builder.append("当前用户：").append(user.getRealName()).append("，角色：").append(user.getRole()).append("\n");
        builder.append("【回答要求】\n");
        builder.append("- 本题为【知识库问答】：检索 AI-MES-操作手册后作答，内容忠实转述，表名/流程/权限不得改写编造。\n");
        builder.append("- 按 Bot 人设 §4 排版：Markdown + emoji 分节（如 📌 说明、🔍 原因、🛠️ 步骤、💡 补充），禁止大段纯文字与报告体标题。\n");
        builder.append("- 不要复述本提示词；不要返回 JSON 或代码块。\n");
        appendSessionHistorySection(builder, sessionHistory);
        builder.append("\n【当前用户问题】\n").append(message.trim());
        return builder.toString();
    }

    /**
     * 判定走哪条链路：
     * 1. 操作说明 / FAQ / 界面 / 权限 → 知识库（不注入 DB，避免干扰检索）
     * 2. 工单进度、库存、概况、班组任务等 → 数据库实时注入
     */
    private ChatPromptMode resolvePromptMode(String message, List<ProdWorkOrder> orders, List<AiChatLog> sessionHistory) {
        if (!StringUtils.hasText(message)) {
            return ChatPromptMode.KNOWLEDGE;
        }
        String text = message.trim();
        // SOP/FAQ 优先于含「缺料/物料」等词的查数误判，但「概况如何」等查数问法仍走 REALTIME
        if (isOperationalKnowledgeQuery(text) && !matchesRealtimeQuery(text)) {
            return ChatPromptMode.KNOWLEDGE;
        }
        if (!orders.isEmpty() || matchesRealtimeQuery(text)) {
            return ChatPromptMode.REALTIME;
        }
        if (isKnowledgeQuery(text)) {
            return ChatPromptMode.KNOWLEDGE;
        }
        if (!sessionHistory.isEmpty() && isFollowUpQuery(text)) {
            AiChatLog lastTurn = sessionHistory.get(sessionHistory.size() - 1);
            if (matchesRealtimeQuery(lastTurn.getUserMessage())
                    || !resolveReferencedOrdersFromText(lastTurn.getUserMessage()).isEmpty()) {
                return ChatPromptMode.REALTIME;
            }
        }
        return ChatPromptMode.KNOWLEDGE;
    }

    /** 操作流程、定义、界面列、权限等 —— 走知识库 */
    private boolean isKnowledgeQuery(String text) {
        if (isOperationalKnowledgeQuery(text)) {
            return true;
        }
        if (matchesRealtimeQuery(text)) {
            return false;
        }
        return containsAny(text,
                "是什么", "什么是", "什么意思", "含义",
                "哪些列", "显示哪些", "显示什么", "有哪些功能",
                "区别", "不同",
                "权限", "菜单", "入口", "页面", "路由",
                "谁能", "谁可以", "能否", "可以吗",
                "标准工序", "操作规范");
    }

    /**
     * SOP / 怎么处理类问法：即使含「缺料」「物料」「异常」等词，也优先走知识库。
     * 例：发现缺料后应该怎么处理？→ 04-异常处理手册
     */
    private boolean isOperationalKnowledgeQuery(String text) {
        if (containsAny(text, "怎么", "如何", "怎样", "怎么办")
                && containsAny(text, "处理", "操作", "上报", "应对", "办")) {
            return true;
        }
        if (containsAny(text, "步骤", "流程", "SOP", "手册")) {
            return true;
        }
        if (text.contains("应该") && containsAny(text, "处理", "操作")) {
            return true;
        }
        return isMaterialModuleKnowledgeQuery(text);
    }

    /** 查数、查状态、查进度 —— 走数据库注入 */
    private boolean matchesRealtimeQuery(String text) {
        if (text.toUpperCase().contains("WO-") || text.toUpperCase().contains("MAT-")) {
            return true;
        }
        if (containsAny(text, "概况", "预警物料", "交期", "在制", "待派工", "待认领", "已派工")) {
            return true;
        }
        if (text.contains("库存") || text.contains("缺货")) {
            return true;
        }
        if (text.contains("今日") && containsAny(text, "生产", "完工", "任务", "概况")) {
            return true;
        }
        if (text.contains("进度") && containsAny(text, "多少", "几", "百分比", "%")) {
            return true;
        }
        if (text.contains("进度") && text.toUpperCase().contains("WO-")) {
            return true;
        }
        if (containsAny(text, "甲班", "乙班", "丙班")
                && containsAny(text, "任务", "工单", "哪些", "多少")) {
            return true;
        }
        if (text.contains("计划") && containsAny(text, "状态", "多少", "几个", "几条", "哪些")) {
            return true;
        }
        // 模块说明 / 页面结构 / 权限 / SOP 走知识库，勿因含「物料」「预警」「缺料」误判为查数
        if (isMaterialModuleKnowledgeQuery(text)) {
            return false;
        }
        if (text.contains("缺料") && containsAny(text, "多少", "几个", "哪些", "当前", "触发", "涉及", "预警")) {
            return true;
        }
        if (text.contains("物料") && containsAny(text, "多少", "库存", "缺口")) {
            return true;
        }
        if (text.contains("物料") && containsAny(text, "预警", "缺料")
                && containsAny(text, "多少", "几个", "哪些", "列表", "当前", "触发", "涉及")) {
            return true;
        }
        if (text.contains("工单") && containsAny(text, "多少", "几个", "几条", "列表", "状态")) {
            return true;
        }
        if (text.contains("异常") && containsAny(text,
                "多少", "几个", "哪些", "列表", "当前", "最新",
                "待处理", "未处理", "open", "processing")) {
            return true;
        }
        return false;
    }

    /** 物料/缺料相关的功能、页面、权限说明 —— 知识库问答，非实时查数 */
    private boolean isMaterialModuleKnowledgeQuery(String text) {
        return containsAny(text,
                "模块", "页面", "做什么", "谁能", "谁可以", "访问", "功能", "菜单", "入口",
                "有哪些内容", "显示什么", "展示什么", "显示哪些", "有哪些功能",
                "步骤", "流程", "SOP", "手册", "上报")
                || containsAny(text, "是什么", "什么是");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String buildRealtimeDataPrompt(
            SysUser user,
            String message,
            List<ProdWorkOrder> orders,
            List<AiChatLog> sessionHistory) {
        List<com.aimes.entity.ProdPlan> activePlans = prodPlanMapper.selectList(new LambdaQueryWrapper<com.aimes.entity.ProdPlan>()
                .eq(com.aimes.entity.ProdPlan::getStatus, "released")
                .orderByDesc(com.aimes.entity.ProdPlan::getPlanDate)
                .last("limit 10"));
        List<ProdWorkOrder> activeOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                .orderByDesc(ProdWorkOrder::getId)
                .last("limit 20"));
        List<MatMaterial> warningMaterials = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                .eq(MatMaterial::getAlertStatus, "warning"));
        List<ExcEvent> openExceptions = excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                .in(ExcEvent::getStatus, List.of("open", "processing"))
                .last("ORDER BY FIELD(status, 'open', 'processing'), occur_time DESC limit 20"));

        StringBuilder builder = new StringBuilder();
        builder.append("你是 AI-MES 车间生产助手。请结合以下【本地实时数据】回答用户问题。\n");
        builder.append("【实时数据说明】以下由后端从 MySQL 实时查询注入，工单进度、库存、计划状态以此为准；");
        builder.append("勿调用 Bot 插件/云端数据库；知识库中的演示数字不得覆盖此处数据。\n");
        builder.append("当前系统时间：").append(LocalDateTime.now()).append("\n");
        builder.append("当前用户：").append(user.getRealName()).append("，角色：").append(user.getRole()).append("\n");

        if (!activePlans.isEmpty()) {
            builder.append("\n【本地实时数据·已下发计划（最新10条）】\n");
            for (com.aimes.entity.ProdPlan plan : activePlans) {
                builder.append("- 计划号=").append(plan.getPlanNo())
                        .append(" 产品=").append(plan.getProductName())
                        .append(" 数量=").append(plan.getPlanQty())
                        .append(" 计划日期=").append(plan.getPlanDate())
                        .append(" 状态=已下发\n");
            }
        }

        if (!activeOrders.isEmpty()) {
            builder.append("\n【本地实时数据·工单（最新20条）】\n");
            for (ProdWorkOrder order : activeOrders) {
                ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
                builder.append("- 工单号=").append(order.getOrderNo())
                        .append(" 产品=").append(order.getProductName())
                        .append(" 进度=").append(order.getProgress()).append("%")
                        .append(" 工序=").append(order.getProcessName())
                        .append(" 班组=").append(team == null ? "未分配" : team.getTeamName())
                        .append(" 状态=").append(translateWorkOrderStatus(order.getStatus()))
                        .append("\n");
            }
        }

        if (!warningMaterials.isEmpty()) {
            builder.append("\n【本地实时数据·预警物料（全部）】\n");
            for (MatMaterial mat : warningMaterials) {
                builder.append("- 物料编码=").append(mat.getMaterialCode())
                        .append(" 名称=").append(mat.getMaterialName())
                        .append(" 库存=").append(mat.getStockQty()).append(mat.getUnit())
                        .append(" 安全库存=").append(mat.getSafetyStock()).append(mat.getUnit())
                        .append("\n");
            }
        }

        builder.append("\n【本地实时数据·未处理异常（open/processing，最新20条）】\n");
        if (openExceptions.isEmpty()) {
            builder.append("- 当前无未处理异常\n");
        } else {
            for (ExcEvent event : openExceptions) {
                ProdWorkOrder relatedOrder = event.getWorkOrderId() == null
                        ? null
                        : prodWorkOrderMapper.selectById(event.getWorkOrderId());
                builder.append("- 异常号=").append(event.getEventNo())
                        .append(" 类型=").append(translateExceptionType(event.getEventType()))
                        .append(" 状态=").append(translateExceptionStatus(event.getStatus()))
                        .append(" 关联工单=").append(relatedOrder == null ? "无" : relatedOrder.getOrderNo())
                        .append(" 发生时间=").append(event.getOccurTime())
                        .append(" 描述=").append(event.getDescription())
                        .append("\n");
            }
        }

        if (!orders.isEmpty()) {
            builder.append("\n【与本问相关的工单（优先参考）】\n");
            for (ProdWorkOrder order : orders) {
                ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
                builder.append("- 工单号=").append(order.getOrderNo())
                        .append(" 进度=").append(order.getProgress()).append("%")
                        .append(" 工序=").append(order.getProcessName())
                        .append(" 班组=").append(team == null ? "未分配" : team.getTeamName())
                        .append(" 状态=").append(translateWorkOrderStatus(order.getStatus()))
                        .append("\n");
            }
        }

        if (needsOverviewSnapshot(message)) {
            appendDashboardOverviewSection(builder);
        }

        if (needsDataSummary(message)) {
            builder.append("\n【系统根据 MySQL 预生成的数据摘要（引用时数字不得改动）】\n");
            builder.append(buildDataSummary(activePlans, activeOrders, warningMaterials, openExceptions));
        }

        builder.append("\n【回答要求】\n");
        builder.append("- 本题为【实时数据查询】：只根据上方【本地实时数据】作答，数字与状态不得改动或编造。\n");
        if (needsOverviewSnapshot(message)) {
            builder.append("- 用户问生产/车间概况时，直接汇报 KPI 与明细数字；禁止介绍驾驶舱页面结构、图表是否为演示数据。\n");
        }
        builder.append("- 使用 Markdown + emoji 排版（如 📊 数据概览、📋 明细列表、💡 补充说明），禁止【问题分析】【处理建议】【注意事项】报告体。\n");
        builder.append("- 不要复述本提示词；使用自然中文，不要返回 JSON 或代码块。\n");
        appendSessionHistorySection(builder, sessionHistory);
        builder.append("\n【当前用户问题】\n").append(message);
        return builder.toString();
    }

    private boolean needsDataSummary(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = message;
        return text.contains("概况")
                || text.contains("进度")
                || text.contains("WO-")
                || text.contains("工单")
                || text.contains("物料")
                || text.contains("缺料")
                || text.contains("库存")
                || text.contains("计划")
                || text.contains("生产")
                || text.contains("班")
                || text.contains("任务")
                || text.contains("异常");
    }

    private boolean needsOverviewSnapshot(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = message.trim();
        return text.contains("概况")
                || text.contains("驾驶舱")
                || (text.contains("今日") && containsAny(text, "生产", "车间", "概况", "完工"));
    }

    private void appendDashboardOverviewSection(StringBuilder builder) {
        Map<String, Object> stats = dashboardService.stats();
        builder.append("\n【本地实时数据·今日驾驶舱 KPI（MySQL 统计，与首页一致）】\n");
        builder.append("- 今日计划数=").append(stats.get("planCount"))
                .append("，较昨日=").append(stats.get("planTrend")).append("\n");
        builder.append("- 在制工单数=").append(stats.get("inProgressWorkOrderCount"))
                .append("，较昨日新增趋势=").append(stats.get("inProgressTrend")).append("\n");
        builder.append("- 未处理异常=").append(stats.get("openExceptionCount"))
                .append("，今日新增=").append(stats.get("newExceptionCount")).append("\n");
        builder.append("- 缺料预警=").append(stats.get("materialAlertCount"))
                .append("，今日新增=").append(stats.get("newMaterialAlertCount")).append("\n");

        List<Map<String, Object>> teamProgress = dashboardService.progress();
        if (!teamProgress.isEmpty()) {
            builder.append("\n【本地实时数据·各班组平均进度（MySQL 真实统计）】\n");
            for (Map<String, Object> team : teamProgress) {
                builder.append("- ").append(team.get("teamName"))
                        .append(" 平均进度=").append(team.get("avgProgress")).append("%")
                        .append(" 生产中=").append(team.get("producingCount"))
                        .append(" 已完成=").append(team.get("doneCount"))
                        .append("\n");
            }
        }
    }

    private String buildDataSummary(
            List<com.aimes.entity.ProdPlan> plans,
            List<ProdWorkOrder> orders,
            List<MatMaterial> warnings,
            List<ExcEvent> openExceptions) {
        StringBuilder summary = new StringBuilder();
        summary.append("今日/当前计划数：").append(plans.size()).append(" 个已下发计划。");
        if (!plans.isEmpty()) {
            summary.append(" 例如 ").append(plans.get(0).getPlanNo())
                    .append("（").append(plans.get(0).getProductName())
                    .append("，").append(plans.get(0).getPlanQty()).append("件）。");
        }
        long inProgress = orders.stream()
                .filter(o -> List.of("assigned", "producing", "exception").contains(o.getStatus()))
                .count();
        summary.append(" 在制工单 ").append(inProgress).append(" 个。");
        if (!orders.isEmpty()) {
            summary.append(" 工单明细：");
            int limit = Math.min(orders.size(), 5);
            for (int i = 0; i < limit; i++) {
                ProdWorkOrder order = orders.get(i);
                ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
                if (i > 0) {
                    summary.append("；");
                }
                summary.append(order.getOrderNo())
                        .append(" 进度").append(order.getProgress()).append("%")
                        .append(" 工序").append(order.getProcessName())
                        .append(" 班组").append(team == null ? "未分配" : team.getTeamName())
                        .append(" 状态").append(translateWorkOrderStatus(order.getStatus()));
            }
            summary.append("。");
        }
        summary.append(" 预警物料 ").append(warnings.size()).append(" 项。");
        if (!warnings.isEmpty()) {
            summary.append(" 明细：");
            for (int i = 0; i < warnings.size(); i++) {
                MatMaterial mat = warnings.get(i);
                if (i > 0) {
                    summary.append("；");
                }
                summary.append(mat.getMaterialCode())
                        .append(" ").append(mat.getMaterialName())
                        .append(" 库存").append(mat.getStockQty()).append(mat.getUnit())
                        .append(" 安全库存").append(mat.getSafetyStock()).append(mat.getUnit());
            }
            summary.append("。");
        }
        summary.append(" 未处理异常 ").append(openExceptions.size()).append(" 项。");
        if (!openExceptions.isEmpty()) {
            summary.append(" 明细：");
            int limit = Math.min(openExceptions.size(), 5);
            for (int i = 0; i < limit; i++) {
                ExcEvent event = openExceptions.get(i);
                ProdWorkOrder relatedOrder = event.getWorkOrderId() == null
                        ? null
                        : prodWorkOrderMapper.selectById(event.getWorkOrderId());
                if (i > 0) {
                    summary.append("；");
                }
                summary.append(event.getEventNo())
                        .append(" ").append(translateExceptionType(event.getEventType()))
                        .append(" ").append(translateExceptionStatus(event.getStatus()))
                        .append(" 工单").append(relatedOrder == null ? "无" : relatedOrder.getOrderNo());
            }
            summary.append("。");
        }
        return summary.toString();
    }

    private String translateExceptionStatus(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "open" -> "待处理";
            case "processing" -> "处理中";
            case "closed" -> "已关闭";
            default -> status;
        };
    }

    private String translateExceptionType(String type) {
        if (type == null) {
            return "其他";
        }
        return switch (type) {
            case "device" -> "设备停机";
            case "material", "shortage" -> "缺料";
            case "quality" -> "质量异常";
            default -> "其他";
        };
    }

    private String translateWorkOrderStatus(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "pending" -> "已排产/待领工";
            case "assigned" -> "已指派/待开工";
            case "producing" -> "进行中";
            case "completed", "done" -> "已完成";
            case "exception" -> "异常";
            default -> status;
        };
    }

    private String buildMockReply(String message, List<ProdWorkOrder> orders, SysUser user) {
        if (!orders.isEmpty()) {
            ProdWorkOrder order = orders.get(0);
            ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
            if (message.contains("交期") || message.contains("截止")) {
                return String.format("%s 计划交期为 %s。",
                        order.getOrderNo(),
                        order.getDeadline() == null ? "未设置" : order.getDeadline());
            }
            if (message.contains("进度") || message.toUpperCase().contains("WO-") || isFollowUpQuery(message)) {
                return String.format("%s 当前进度 %d%%，处于%s工序，负责班组：%s，状态：%s。",
                        order.getOrderNo(),
                        order.getProgress() == null ? 0 : order.getProgress(),
                        order.getProcessName(),
                        team == null ? "未分配" : team.getTeamName(),
                        order.getStatus());
            }
        }
        if (message.contains("班") || message.contains("任务")) {
            List<ProdWorkOrder> teamOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                    .eq(user.getTeamId() != null, ProdWorkOrder::getTeamId, user.getTeamId())
                    .in(ProdWorkOrder::getStatus, List.of("assigned", "producing", "exception"))
                    .orderByAsc(ProdWorkOrder::getPriority)
                    .last("limit 5"));
            if (!teamOrders.isEmpty()) {
                return "当前班组重点任务：" + teamOrders.stream()
                        .map(order -> order.getOrderNo() + "（" + order.getProcessName() + "，" + order.getStatus() + "）")
                        .reduce((a, b) -> a + "；" + b)
                        .orElse("暂无任务");
            }
        }
        if (needsOverviewSnapshot(message) || (message.contains("概况") && matchesRealtimeQuery(message))) {
            Map<String, Object> stats = dashboardService.stats();
            return String.format(
                    "📊 今日车间生产概况：今日计划 %s 个（%s），在制工单 %s 个（%s），未处理异常 %s 项（今日新增 %s），缺料预警 %s 项（今日新增 %s）。",
                    stats.get("planCount"), stats.get("planTrend"),
                    stats.get("inProgressWorkOrderCount"), stats.get("inProgressTrend"),
                    stats.get("openExceptionCount"), stats.get("newExceptionCount"),
                    stats.get("materialAlertCount"), stats.get("newMaterialAlertCount"));
        }
        if (message.contains("物料") || message.contains("缺料")) {
            List<MatMaterial> warnings = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                    .eq(MatMaterial::getAlertStatus, "warning"));
            if (!warnings.isEmpty()) {
                MatMaterial material = warnings.get(0);
                return String.format("当前有 %d 项缺料预警。示例：%s 库存 %s%s，安全库存 %s%s，建议优先补料。",
                        warnings.size(),
                        material.getMaterialName(),
                        material.getStockQty(),
                        material.getUnit(),
                        material.getSafetyStock(),
                        material.getUnit());
            }
        }
        if (message.contains("异常") && matchesRealtimeQuery(message)) {
            List<ExcEvent> openExceptions = excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                    .in(ExcEvent::getStatus, List.of("open", "processing"))
                    .last("ORDER BY FIELD(status, 'open', 'processing'), occur_time DESC limit 10"));
            if (openExceptions.isEmpty()) {
                return "当前没有未处理异常（待处理/处理中均为 0）。";
            }
            StringBuilder reply = new StringBuilder("当前未处理异常共 ")
                    .append(openExceptions.size())
                    .append(" 项：");
            for (int i = 0; i < openExceptions.size(); i++) {
                ExcEvent event = openExceptions.get(i);
                ProdWorkOrder relatedOrder = event.getWorkOrderId() == null
                        ? null
                        : prodWorkOrderMapper.selectById(event.getWorkOrderId());
                if (i > 0) {
                    reply.append("；");
                }
                reply.append(event.getEventNo())
                        .append("（").append(translateExceptionType(event.getEventType()))
                        .append("，").append(translateExceptionStatus(event.getStatus()))
                        .append("，工单 ")
                        .append(relatedOrder == null ? "无" : relatedOrder.getOrderNo())
                        .append("）");
            }
            reply.append("。");
            return reply.toString();
        }
        return "当前为演示模式。我可以帮助你查询工单进度、班组任务、异常处理建议或物料预警信息。";
    }

    private Map<String, Object> buildMockScheduling(
            List<ProdWorkOrder> workOrders,
            List<MatMaterial> warningMaterials,
            Map<String, Boolean> constraints,
            LocalDate planDate) {
        boolean materialEnabled = Boolean.TRUE.equals(constraints.get("materialAvailability"));
        boolean deviceEnabled = Boolean.TRUE.equals(constraints.get("deviceLoad"));
        boolean teamEnabled = Boolean.TRUE.equals(constraints.get("teamHours"));

        List<Map<String, Object>> priorities = new ArrayList<>();
        List<Map<String, Object>> dispatch = new ArrayList<>();
        for (int i = 0; i < workOrders.size(); i++) {
            ProdWorkOrder order = workOrders.get(i);
            ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
            Map<String, Object> priorityRow = new LinkedHashMap<>();
            priorityRow.put("workOrderCode", order.getOrderNo());
            priorityRow.put("rank", i + 1);
            priorityRow.put("priorityLabel", schedulingPriorityLabel(order.getPriority()));
            priorityRow.put("reason", buildMockPriorityReason(i, materialEnabled, teamEnabled, warningMaterials));
            priorities.add(priorityRow);

            Map<String, Object> dispatchRow = new LinkedHashMap<>();
            dispatchRow.put("workOrderCode", order.getOrderNo());
            dispatchRow.put("teamName", team == null ? "待定" : team.getTeamName());
            dispatchRow.put("startTime", formatStartTime(computeEarliestDispatchStart(planDate, i)));
            dispatchRow.put("hours", teamEnabled ? String.valueOf(4 + i) : "待定");
            dispatch.add(dispatchRow);
        }

        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        if (deviceEnabled) {
            bottlenecks.add(Map.of(
                    "processName", "装配工序",
                    "loadRate", materialEnabled && !warningMaterials.isEmpty() ? 95 : 78,
                    "suggestion", materialEnabled && !warningMaterials.isEmpty()
                            ? "请优先处理缺料后再集中排装配任务"
                            : "当前负载可接受"
            ));
        } else {
            bottlenecks.add(Map.of(
                    "processName", "—",
                    "loadRate", 0,
                    "suggestion", "未启用设备负荷约束，已跳过瓶颈分析"
            ));
        }
        if (!materialEnabled) {
            bottlenecks.add(Map.of(
                    "processName", "物料约束",
                    "loadRate", 0,
                    "suggestion", "未启用物料可用性约束，排产未纳入缺料风险"
            ));
        }
        if (!teamEnabled) {
            bottlenecks.add(Map.of(
                    "processName", "班组约束",
                    "loadRate", 0,
                    "suggestion", "未启用班组工时约束，派工时间仅按默认班次估算"
            ));
        }

        return Map.of(
                "priorities", priorities,
                "bottlenecks", bottlenecks,
                "dispatches", dispatch
        );
    }

    private String buildMockPriorityReason(
            int index,
            boolean materialEnabled,
            boolean teamEnabled,
            List<MatMaterial> warningMaterials) {
        if (index == 0) {
            StringBuilder reason = new StringBuilder("交期较近且优先级更高");
            if (materialEnabled && !warningMaterials.isEmpty()) {
                reason.append("，并受物料预警影响");
            }
            if (!teamEnabled) {
                reason.append("（未考虑班组工时）");
            }
            return reason.toString();
        }
        if (!teamEnabled) {
            return "建议按当前队列顺序执行（未考虑班组工时）";
        }
        return "建议按当前队列顺序执行";
    }

    private String schedulingPriorityLabel(Integer priority) {
        if (priority == null || priority == 2) {
            return "中";
        }
        return priority == 1 ? "高" : "低";
    }
}

