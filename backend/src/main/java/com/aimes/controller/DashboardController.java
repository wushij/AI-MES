package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.aimes.common.Result;
import com.aimes.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "驾驶舱")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@SaCheckLogin
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(dashboardService.stats());
    }

    @GetMapping("/progress")
    public Result<List<Map<String, Object>>> progress() {
        return Result.ok(dashboardService.progress());
    }

    @GetMapping("/alerts")
    public Result<Map<String, Object>> alerts() {
        return Result.ok(dashboardService.alerts());
    }

    @GetMapping("/device-summary")
    public Result<Map<String, Object>> deviceSummary() {
        return Result.ok(dashboardService.deviceSummary());
    }

    @GetMapping("/workshop-summary")
    public Result<Map<String, Object>> workshopSummary() {
        return Result.ok(dashboardService.workshopSummary());
    }
}

