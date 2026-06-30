package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.CozeChatRequest;
import com.aimes.dto.Requests.CozeConfigSaveRequest;
import com.aimes.dto.Requests.CozeSchedulingRequest;
import com.aimes.dto.Requests.SchedulingApplyRequest;
import com.aimes.service.CozeConfigService;
import com.aimes.service.CozeService;
import com.aimes.service.WorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/coze")
@RequiredArgsConstructor
public class CozeController {

    private final CozeService cozeService;
    private final CozeConfigService cozeConfigService;
    private final WorkOrderService workOrderService;

    @GetMapping("/config")
    @SaCheckRole("admin")
    public Result<Map<String, Object>> config() {
        return Result.ok(cozeConfigService.getConfigView());
    }

    @PutMapping("/config")
    @SaCheckRole("admin")
    public Result<Map<String, Object>> saveConfig(@Valid @RequestBody CozeConfigSaveRequest request) {
        return Result.ok("保存成功", cozeConfigService.saveConfig(request));
    }

    @PostMapping("/chat")
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<Map<String, Object>> chat(@Valid @RequestBody CozeChatRequest request) {
        return Result.ok(cozeService.chat(request));
    }

    @GetMapping("/chat/history")
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> history(@RequestParam(required = false) String sessionId) {
        return Result.ok(cozeService.history(sessionId));
    }

    @DeleteMapping("/chat/history")
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<Integer> deleteHistory(@RequestParam String sessionId) {
        return Result.ok("删除成功", cozeService.deleteSession(sessionId));
    }

    @PostMapping("/scheduling")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> scheduling(@Valid @RequestBody CozeSchedulingRequest request) {
        return Result.ok(cozeService.scheduling(request));
    }

    @PostMapping("/scheduling/apply")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> applyScheduling(@Valid @RequestBody SchedulingApplyRequest request) {
        return Result.ok("排产建议已应用到工单", workOrderService.applySchedulingSuggestions(request));
    }

    @GetMapping("/health")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> health() {
        return Result.ok(cozeService.health());
    }

    @GetMapping("/health/chat")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> healthChat() {
        return Result.ok(cozeService.healthChat());
    }

    @GetMapping("/health/workflow")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> healthWorkflow() {
        return Result.ok(cozeService.healthWorkflow());
    }
}
