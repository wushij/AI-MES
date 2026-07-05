package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aimes.common.Result;
import com.aimes.dto.Requests.DeviceMaintenancePlanSaveRequest;
import com.aimes.dto.Requests.DeviceMaintenanceSubmitRequest;
import com.aimes.service.DeviceMaintenanceService;
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

@Tag(name = "设备保养")
@RestController
@RequestMapping("/api/device-maintenances")
@RequiredArgsConstructor
public class DeviceMaintenanceController {

    private final DeviceMaintenanceService deviceMaintenanceService;

    @GetMapping("/plans")
    @SaCheckPermission("设备")
    public Result<List<Map<String, Object>>> listPlans(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(deviceMaintenanceService.listPlans(deviceId, categoryId, keyword));
    }

    @GetMapping("/plans/for-device/{deviceId}")
    @SaCheckPermission("设备")
    public Result<List<Map<String, Object>>> listPlansForDevice(@PathVariable Long deviceId) {
        return Result.ok(deviceMaintenanceService.listPlansForDevice(deviceId));
    }

    @GetMapping("/plans/due")
    @SaCheckPermission("设备")
    public Result<List<Map<String, Object>>> listDuePlans(@RequestParam(required = false) Long deviceId) {
        return Result.ok(deviceMaintenanceService.listDuePlans(deviceId));
    }

    @GetMapping("/plans/{id}")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> getPlan(@PathVariable Long id) {
        return Result.ok(deviceMaintenanceService.getPlan(id));
    }

    @PostMapping("/plans")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> createPlan(@Valid @RequestBody DeviceMaintenancePlanSaveRequest request) {
        return Result.ok(deviceMaintenanceService.createPlan(request));
    }

    @PutMapping("/plans/{id}")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> updatePlan(@PathVariable Long id, @Valid @RequestBody DeviceMaintenancePlanSaveRequest request) {
        return Result.ok(deviceMaintenanceService.updatePlan(id, request));
    }

    @DeleteMapping("/plans/{id}")
    @SaCheckPermission("设备")
    public Result<Void> deletePlan(@PathVariable Long id) {
        deviceMaintenanceService.deletePlan(id);
        return Result.ok("删除成功", null);
    }

    @GetMapping("/records")
    @SaCheckPermission("设备")
    public Result<List<Map<String, Object>>> listRecords(@RequestParam Long deviceId) {
        return Result.ok(deviceMaintenanceService.listRecords(deviceId));
    }

    @PostMapping("/records")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> submitMaintenance(@Valid @RequestBody DeviceMaintenanceSubmitRequest request) {
        return Result.ok(deviceMaintenanceService.submitMaintenance(request));
    }

    @DeleteMapping("/records/{id}")
    @SaCheckPermission("设备")
    public Result<Void> deleteRecord(@PathVariable Long id) {
        deviceMaintenanceService.deleteRecord(id);
        return Result.ok("删除成功", null);
    }
}
