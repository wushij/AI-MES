package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.DeviceCategorySaveRequest;
import com.aimes.dto.Requests.DeviceSaveRequest;
import com.aimes.dto.Requests.DeviceStatusRequest;
import com.aimes.service.DeviceCategoryService;
import com.aimes.service.DeviceService;
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

import java.util.List;
import java.util.Map;

@Tag(name = "设备管理")
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceCategoryService deviceCategoryService;

    @GetMapping
    @SaCheckPermission(value = {"设备", "异常上报"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId) {
        return Result.ok(deviceService.list(keyword, status, categoryId));
    }

    @GetMapping("/options")
    @SaCheckPermission(value = {"设备", "异常上报"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> options() {
        return Result.ok(deviceService.options());
    }

    @GetMapping("/form-options")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> formOptions() {
        return Result.ok(deviceService.formOptions());
    }

    @GetMapping("/summary")
    @SaCheckPermission(value = {"设备", "生产计划", "工单管理"}, mode = SaMode.OR)
    public Result<Map<String, Object>> summary() {
        return Result.ok(deviceService.summary());
    }

    @GetMapping("/categories")
    @SaCheckPermission(value = {"设备", "异常上报"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> categories() {
        return Result.ok(deviceCategoryService.listTree());
    }

    @PostMapping("/categories")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> createCategory(@Valid @RequestBody DeviceCategorySaveRequest request) {
        return Result.ok(deviceCategoryService.create(request));
    }

    @PutMapping("/categories/{id}")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> updateCategory(@PathVariable Long id, @Valid @RequestBody DeviceCategorySaveRequest request) {
        return Result.ok(deviceCategoryService.update(id, request));
    }

    @DeleteMapping("/categories/{id}")
    @SaCheckPermission("设备")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        deviceCategoryService.delete(id);
        return Result.ok("删除成功", null);
    }

    @GetMapping("/{id}/today-alerts")
    @SaCheckPermission("设备")
    public Result<List<Map<String, Object>>> todayAlerts(@PathVariable Long id) {
        return Result.ok(deviceService.listTodayAlerts(id));
    }

    @GetMapping("/{id}/process-records")
    @SaCheckPermission("设备")
    public Result<List<Map<String, Object>>> processRecords(@PathVariable Long id) {
        return Result.ok(deviceService.listProcessRecords(id));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(deviceService.detail(id));
    }

    @GetMapping("/{id}/full")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> detailFull(@PathVariable Long id) {
        return Result.ok(deviceService.detailFull(id));
    }

    @PostMapping
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> create(@Valid @RequestBody DeviceSaveRequest request) {
        return Result.ok(deviceService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody DeviceSaveRequest request) {
        return Result.ok(deviceService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @Valid @RequestBody DeviceStatusRequest request) {
        return Result.ok(deviceService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("设备")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.ok("删除成功", null);
    }
}

