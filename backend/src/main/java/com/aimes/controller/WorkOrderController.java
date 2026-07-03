package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.WorkOrderAssignRequest;
import com.aimes.dto.Requests.WorkOrderCreateRequest;
import com.aimes.dto.Requests.WorkOrderProgressRequest;
import com.aimes.dto.Requests.WorkOrderUpdateRequest;
import com.aimes.service.WorkOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "工单管理")
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @GetMapping
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long teamId) {
        return Result.ok(workOrderService.list(page, size, keyword, status, teamId));
    }

    @GetMapping("/process-board")
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<Map<String, Object>> processBoard() {
        return Result.ok(workOrderService.processBoard());
    }

    @GetMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(workOrderService.detail(id));
    }

    @PostMapping
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> create(@Valid @RequestBody WorkOrderCreateRequest request) {
        return Result.ok(workOrderService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody WorkOrderUpdateRequest request) {
        return Result.ok(workOrderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Void> delete(@PathVariable Long id) {
        workOrderService.delete(id);
        return Result.ok("删除成功", null);
    }

    @PostMapping("/{id}/assign")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> assign(@PathVariable Long id, @Valid @RequestBody WorkOrderAssignRequest request) {
        return Result.ok(workOrderService.assign(id, request));
    }

    @PostMapping("/{id}/claim")
    @SaCheckRole(value = {"admin", "worker"}, mode = SaMode.OR)
    public Result<Map<String, Object>> claim(@PathVariable Long id) {
        return Result.ok(workOrderService.claim(id));
    }

    @PutMapping("/{id}/progress")
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<Map<String, Object>> progress(@PathVariable Long id, @Valid @RequestBody WorkOrderProgressRequest request) {
        return Result.ok(workOrderService.updateProgress(id, request));
    }

    @PostMapping("/{id}/complete")
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<Map<String, Object>> complete(@PathVariable Long id) {
        return Result.ok(workOrderService.complete(id));
    }
}
