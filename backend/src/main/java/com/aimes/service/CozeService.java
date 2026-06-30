package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.CozeChatRequest;
import com.aimes.dto.Requests.CozeSchedulingRequest;
import com.aimes.entity.AiChatLog;
import com.aimes.entity.MatMaterial;
import com.aimes.entity.ProdTeam;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.AiChatLogMapper;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @org.springframework.beans.factory.annotation.Value("${coze.chat-poll-timeout-seconds:90}")
    private int chatPollTimeoutSeconds;

    private final AiChatLogMapper aiChatLogMapper;
    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdPlanMapper prodPlanMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final MatMaterialMapper matMaterialMapper;
    private final AuthService authService;
    private final CozeConfigService cozeConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public Map<String, Object> chat(CozeChatRequest request) {
        SysUser user = authService.currentUser();
        String sessionId = StringUtils.hasText(request.getSessionId()) ? request.getSessionId() : UUID.randomUUID().toString();
        List<ProdWorkOrder> referencedOrders = resolveReferencedOrders(request.getMessage());
        String prompt = buildChatPrompt(user, request.getMessage(), referencedOrders);

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
                "contextOrders", referencedOrders.stream().map(ProdWorkOrder::getOrderNo).toList()
        );
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

        try {
            if (cozeConfigService.isConfigured()) {
                String workflowId = cozeConfigService.getEffectiveWorkflowId();
                if (!StringUtils.hasText(workflowId)) {
                    return mockSchedulingResult(workOrders, warningMaterials,
                            "未配置排产工作流 ID，请在 Coze 配置中填写");
                }
                Map<String, Object> parsed = runSchedulingWorkflow(
                        workOrders, warningMaterials, request.getPlanDate());
                return Map.of("mode", "live", "result", parsed);
            }
        } catch (Exception ex) {
            return mockSchedulingResult(workOrders, warningMaterials,
                    "Coze 工作流调用失败：" + ex.getMessage()
                            + "。请确认工作流「开始节点」入参名为 plan_date、work_orders_json、material_status");
        }
        return mockSchedulingResult(workOrders, warningMaterials, "Coze 未启用或未配置，当前展示演示排产结果");
    }

    private Map<String, Object> mockSchedulingResult(
            List<ProdWorkOrder> workOrders,
            List<MatMaterial> warningMaterials,
            String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "mock");
        result.put("message", message);
        result.put("result", buildMockScheduling(workOrders, warningMaterials));
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
            Map<String, Object> parsed = runSchedulingWorkflow(workOrders, warningMaterials, LocalDate.now());
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
            LocalDate planDate) throws IOException, InterruptedException {
        String workflowId = cozeConfigService.getEffectiveWorkflowId();
        if (!StringUtils.hasText(workflowId)) {
            throw new IOException("未配置排产工作流 ID");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflow_id", workflowId.trim());
        payload.put("bot_id", botId());
        payload.put("parameters", Map.of(
                "plan_date", String.valueOf(planDate),
                "work_orders_json", objectMapper.writeValueAsString(workOrders),
                "material_status", objectMapper.writeValueAsString(warningMaterials)
        ));
        JsonNode root = invokeJson(workflowApiUrl() + "/workflow/run", payload);
        Map<String, Object> parsed = parseWorkflowSchedulingResult(root);
        if (parsed == null || parsed.isEmpty()) {
            throw new IOException("工作流返回结果无法解析，请检查 Coze 结束节点是否输出 JSON（priorities / bottlenecks / dispatchSuggestions）");
        }
        return parsed;
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
                "content_type", "text"
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

    private List<ProdWorkOrder> resolveReferencedOrders(String message) {
        List<ProdWorkOrder> result = new ArrayList<>();
        Matcher matcher = ORDER_NO_PATTERN.matcher(message.toUpperCase());
        while (matcher.find()) {
            String orderNo = matcher.group();
            ProdWorkOrder order = prodWorkOrderMapper.selectOne(new LambdaQueryWrapper<ProdWorkOrder>()
                    .eq(ProdWorkOrder::getOrderNo, orderNo)
                    .last("limit 1"));
            if (order != null) {
                result.add(order);
            }
        }
        if (result.isEmpty()) {
            result = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                    .orderByAsc(ProdWorkOrder::getPriority)
                    .orderByDesc(ProdWorkOrder::getDeadline)
                    .last("limit 3"));
        }
        return result;
    }

    private String buildChatPrompt(SysUser user, String message, List<ProdWorkOrder> orders) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是 AI-MES 车间生产助手。请结合以下【本地实时数据上下文】来回答用户的问题。\n");
        builder.append("【重要指令】请【务必】优先使用此处的本地实时数据。云端工具/插件查询到的数据库数据是过时的静态演示数据，请忽略云端工具的结果，以本上下文为准！不要编造数据，也不要使用云端的“精密组件C”等硬编码测试数据。\n");
        builder.append("当前系统时间：").append(LocalDateTime.now()).append("\n");
        builder.append("当前用户：").append(user.getRealName()).append("，角色：").append(user.getRole()).append("\n");
        
        // Fetch all active/released plans
        List<com.aimes.entity.ProdPlan> activePlans = prodPlanMapper.selectList(new LambdaQueryWrapper<com.aimes.entity.ProdPlan>()
                .eq(com.aimes.entity.ProdPlan::getStatus, "released")
                .orderByDesc(com.aimes.entity.ProdPlan::getPlanDate)
                .last("limit 10"));
        if (!activePlans.isEmpty()) {
            builder.append("\n本地实时生产计划（最新 10 个已下发计划）：\n");
            for (com.aimes.entity.ProdPlan plan : activePlans) {
                builder.append("- 计划号=").append(plan.getPlanNo())
                        .append(" 产品=").append(plan.getProductName())
                        .append(" 数量=").append(plan.getPlanQty())
                        .append(" 计划日期=").append(plan.getPlanDate())
                        .append(" 状态=已下发\n");
            }
        }
        
        // Fetch all active/pending work orders
        List<ProdWorkOrder> activeOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                .orderByDesc(ProdWorkOrder::getId)
                .last("limit 20"));
        if (!activeOrders.isEmpty()) {
            builder.append("\n本地实时工单列表（最新 20 个工单）：\n");
            for (ProdWorkOrder order : activeOrders) {
                ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
                builder.append("- 工单号=").append(order.getOrderNo())
                        .append(" 关联计划号=").append(order.getPlanId())
                        .append(" 产品=").append(order.getProductName())
                        .append(" 进度=").append(order.getProgress()).append("%")
                        .append(" 当前工序=").append(order.getProcessName())
                        .append(" 班组=").append(team == null ? "未分配" : team.getTeamName())
                        .append(" 状态=").append(translateWorkOrderStatus(order.getStatus()))
                        .append("\n");
            }
        }
        
        // Fetch warning materials
        List<MatMaterial> warningMaterials = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                .eq(MatMaterial::getAlertStatus, "warning"));
        if (!warningMaterials.isEmpty()) {
            builder.append("\n本地库存预警物料：\n");
            for (MatMaterial mat : warningMaterials) {
                builder.append("- 物料编码=").append(mat.getMaterialCode())
                        .append(" 名称=").append(mat.getMaterialName())
                        .append(" 当前库存=").append(mat.getStockQty())
                        .append(" 安全库存=").append(mat.getSafetyStock())
                        .append(" 单位=").append(mat.getUnit())
                        .append("\n");
            }
        }
        
        builder.append("\n回答要求：使用自然中文直接回答，不要返回 JSON、键值对或代码块。请严格按照上面的本地数据统计并回答。\n");
        builder.append("用户问题：").append(message);
        return builder.toString();
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
        if (!orders.isEmpty() && (message.contains("进度") || message.toUpperCase().contains("WO-"))) {
            ProdWorkOrder order = orders.get(0);
            ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
            return String.format("%s 当前进度 %d%%，处于%s工序，负责班组：%s，状态：%s。",
                    order.getOrderNo(),
                    order.getProgress() == null ? 0 : order.getProgress(),
                    order.getProcessName(),
                    team == null ? "未分配" : team.getTeamName(),
                    order.getStatus());
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
        return "当前为演示模式。我可以帮助你查询工单进度、班组任务、异常处理建议或物料预警信息。";
    }

    private Map<String, Object> buildMockScheduling(List<ProdWorkOrder> workOrders, List<MatMaterial> warningMaterials) {
        List<Map<String, Object>> priorities = new ArrayList<>();
        List<Map<String, Object>> dispatch = new ArrayList<>();
        for (int i = 0; i < workOrders.size(); i++) {
            ProdWorkOrder order = workOrders.get(i);
            ProdTeam team = order.getTeamId() == null
                    ? prodTeamMapper.selectById((i % 2) + 1L)
                    : prodTeamMapper.selectById(order.getTeamId());
            Map<String, Object> priorityRow = new LinkedHashMap<>();
            priorityRow.put("workOrderCode", order.getOrderNo());
            priorityRow.put("rank", i + 1);
            priorityRow.put("priorityLabel", schedulingPriorityLabel(order.getPriority()));
            priorityRow.put("reason", i == 0 ? "交期较近且优先级更高" : "建议按当前队列顺序执行");
            priorities.add(priorityRow);

            Map<String, Object> dispatchRow = new LinkedHashMap<>();
            dispatchRow.put("workOrderCode", order.getOrderNo());
            dispatchRow.put("teamName", team == null ? "待定" : team.getTeamName());
            dispatchRow.put("startTime", requestPlanStartTime(i));
            dispatchRow.put("hours", String.valueOf(4 + i));
            dispatch.add(dispatchRow);
        }
        List<Map<String, Object>> bottlenecks = List.of(Map.of(
                "processName", "装配工序",
                "loadRate", warningMaterials.isEmpty() ? 78 : 95,
                "suggestion", warningMaterials.isEmpty() ? "当前负载可接受" : "请优先处理缺料后再集中排装配任务"
        ));
        return Map.of(
                "priorities", priorities,
                "bottlenecks", bottlenecks,
                "dispatches", dispatch
        );
    }

    private String schedulingPriorityLabel(Integer priority) {
        if (priority == null || priority == 2) {
            return "中";
        }
        return priority == 1 ? "高" : "低";
    }

    private String requestPlanStartTime(int index) {
        return LocalDate.now() + " " + String.format("%02d:00", 8 + index);
    }
}

