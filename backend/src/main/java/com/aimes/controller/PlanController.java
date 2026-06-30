package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.PlanSaveRequest;
import com.aimes.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        return Result.ok(planService.list(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(planService.detail(id));
    }

    @PostMapping
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> create(@Valid @RequestBody PlanSaveRequest request) {
        return Result.ok(planService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody PlanSaveRequest request) {
        return Result.ok(planService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return Result.ok("删除成功", null);
    }

    @PostMapping("/{id}/release")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> release(@PathVariable Long id) {
        return Result.ok(planService.release(id));
    }
}
