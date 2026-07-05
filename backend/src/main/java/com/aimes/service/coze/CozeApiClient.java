package com.aimes.service.coze;

import com.aimes.service.CozeConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CozeApiClient {

    @Value("${coze.chat-poll-timeout-seconds:90}")
    private int chatPollTimeoutSeconds;

    private final CozeConfigService cozeConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CozeApiClient(CozeConfigService cozeConfigService, ObjectMapper objectMapper) {
        this.cozeConfigService = cozeConfigService;
        this.objectMapper = objectMapper;
    }

    public String workflowApiUrl() {
        String base = apiUrl().replaceAll("/v3/?$", "");
        if (base.endsWith("/v1")) {
            return base;
        }
        return "https://api.coze.cn/v1";
    }

    public String stripMarkdownJson(String raw) {
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

    public String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public JsonNode invokeGetJson(String url) throws IOException, InterruptedException {
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

    public JsonNode invokeJson(String url, Map<String, Object> payload) throws IOException, InterruptedException {
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

    public InputStream invokeStream(String url, Map<String, Object> payload) throws IOException, InterruptedException {
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

    public Map<String, Object> buildChatPayload(String userId, String conversationId, String prompt, boolean stream) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bot_id", botId());
        payload.put("user_id", userId);
        payload.put("stream", stream);
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
        return payload;
    }

    public JsonNode createChat(String userId, String conversationId, String prompt) throws IOException, InterruptedException {
        return invokeJson(apiUrl() + "/chat", buildChatPayload(userId, conversationId, prompt, false));
    }

    public String pollChatStatus(String conversationId, String chatId) throws IOException, InterruptedException {
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

    public String fetchAnswerContent(String conversationId, String chatId) throws IOException, InterruptedException {
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

    public String prettifyReply(String raw) {
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

    public boolean isConfigured() {
        return cozeConfigService.isConfigured();
    }

    public String apiToken() {
        return cozeConfigService.getEffectiveApiToken();
    }

    public String botId() {
        return cozeConfigService.getEffectiveBotId();
    }

    public String apiUrl() {
        return cozeConfigService.getEffectiveApiUrl();
    }
}
