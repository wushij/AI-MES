package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.CozeChatRequest;
import com.aimes.dto.Requests.CozeConfigSaveRequest;
import com.aimes.dto.Requests.CozeSchedulingRequest;
import com.aimes.dto.Requests.SchedulingApplyRequest;
import com.aimes.service.CozeConfigService;
import com.aimes.service.CozeService;
import com.aimes.service.WorkOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "AI / Coze")
@RestController
@RequestMapping("/api/coze")
@RequiredArgsConstructor
public class CozeController {

    private final CozeService cozeService;
    private final CozeConfigService cozeConfigService;
    private final WorkOrderService workOrderService;

    @GetMapping("/config")
    @SaCheckPermission("Coze 配置")
    public Result<Map<String, Object>> config() {
        return Result.ok(cozeConfigService.getConfigView());
    }

    @GetMapping("/welcome")
    @SaCheckPermission("AI 客服")
    public Result<Map<String, Object>> welcome() {
        return Result.ok(Map.of("welcomeMessage", cozeConfigService.getWelcomeMessage()));
    }

    @PutMapping("/config")
    @SaCheckPermission("Coze 配置")
    public Result<Map<String, Object>> saveConfig(@Valid @RequestBody CozeConfigSaveRequest request) {
        return Result.ok("保存成功", cozeConfigService.saveConfig(request));
    }

    @PostMapping("/chat")
    @SaCheckPermission("AI 客服")
    public Result<Map<String, Object>> chat(@Valid @RequestBody CozeChatRequest request) {
        return Result.ok(cozeService.chat(request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("AI 客服")
    public void chatStream(@Valid @RequestBody CozeChatRequest request, HttpServletResponse response) {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        cozeService.chatStream(request, response);
    }

    @GetMapping("/chat/history")
    @SaCheckPermission("AI 客服")
    public Result<List<Map<String, Object>>> history(@RequestParam(required = false) String sessionId) {
        return Result.ok(cozeService.history(sessionId));
    }

    @DeleteMapping("/chat/history")
    @SaCheckPermission("AI 客服")
    public Result<Integer> deleteHistory(@RequestParam String sessionId) {
        return Result.ok("删除成功", cozeService.deleteSession(sessionId));
    }

    @PostMapping("/scheduling")
    @SaCheckPermission("排产")
    public Result<Map<String, Object>> scheduling(@Valid @RequestBody CozeSchedulingRequest request) {
        return Result.ok(cozeService.scheduling(request));
    }

    @GetMapping("/scheduling/context")
    @SaCheckPermission("排产")
    public Result<Map<String, Object>> schedulingContext(@RequestParam String workOrderIds) {
        List<Long> ids = java.util.Arrays.stream(workOrderIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
        return Result.ok(cozeService.schedulingContext(ids));
    }

    @PostMapping("/scheduling/apply")
    @SaCheckPermission("排产")
    public Result<Map<String, Object>> applyScheduling(@Valid @RequestBody SchedulingApplyRequest request) {
        return Result.ok("排产建议已应用到工单", workOrderService.applySchedulingSuggestions(request));
    }

    @GetMapping("/health")
    @SaCheckPermission(value = {"Coze 配置", "排产"}, mode = SaMode.OR)
    public Result<Map<String, Object>> health() {
        return Result.ok(cozeService.health());
    }

    @GetMapping("/health/chat")
    @SaCheckPermission("Coze 配置")
    public Result<Map<String, Object>> healthChat() {
        return Result.ok(cozeService.healthChat());
    }

    @GetMapping("/health/workflow")
    @SaCheckPermission("Coze 配置")
    public Result<Map<String, Object>> healthWorkflow() {
        return Result.ok(cozeService.healthWorkflow());
    }
}
