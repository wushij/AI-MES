package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
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

    @GetMapping("/workshop-summary")
    public Result<Map<String, Object>> workshopSummary() {
        return Result.ok(dashboardService.workshopSummary());
    }
}
