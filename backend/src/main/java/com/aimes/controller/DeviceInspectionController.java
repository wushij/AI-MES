package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aimes.common.Result;
import com.aimes.dto.Requests.DeviceInspectionPlanSaveRequest;
import com.aimes.dto.Requests.DeviceInspectionSubmitRequest;
import com.aimes.service.DeviceInspectionService;
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

@Tag(name = "设备点检")
@RestController
@RequestMapping("/api/device-inspections")
@RequiredArgsConstructor
public class DeviceInspectionController {

    private final DeviceInspectionService deviceInspectionService;

    @GetMapping("/plans")
    @SaCheckPermission("设备")
    public Result<List<Map<String, Object>>> listPlans(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(deviceInspectionService.listPlans(deviceId, categoryId, keyword));
    }

    @GetMapping("/plans/for-device/{deviceId}")
    @SaCheckPermission("设备")
    public Result<List<Map<String, Object>>> listPlansForDevice(@PathVariable Long deviceId) {
        return Result.ok(deviceInspectionService.listPlansForDevice(deviceId));
    }

    @GetMapping("/plans/{id}")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> getPlan(@PathVariable Long id) {
        return Result.ok(deviceInspectionService.getPlan(id));
    }

    @PostMapping("/plans")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> createPlan(@Valid @RequestBody DeviceInspectionPlanSaveRequest request) {
        return Result.ok(deviceInspectionService.createPlan(request));
    }

    @PutMapping("/plans/{id}")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> updatePlan(@PathVariable Long id, @Valid @RequestBody DeviceInspectionPlanSaveRequest request) {
        return Result.ok(deviceInspectionService.updatePlan(id, request));
    }

    @DeleteMapping("/plans/{id}")
    @SaCheckPermission("设备")
    public Result<Void> deletePlan(@PathVariable Long id) {
        deviceInspectionService.deletePlan(id);
        return Result.ok("删除成功", null);
    }

    @GetMapping("/records")
    @SaCheckPermission("设备")
    public Result<List<Map<String, Object>>> listRecords(@RequestParam Long deviceId) {
        return Result.ok(deviceInspectionService.listRecords(deviceId));
    }

    @PostMapping("/records")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> submitInspection(@Valid @RequestBody DeviceInspectionSubmitRequest request) {
        return Result.ok(deviceInspectionService.submitInspection(request));
    }

    @DeleteMapping("/records/{id}")
    @SaCheckPermission("设备")
    public Result<Void> deleteRecord(@PathVariable Long id) {
        deviceInspectionService.deleteRecord(id);
        return Result.ok("删除成功", null);
    }
}
