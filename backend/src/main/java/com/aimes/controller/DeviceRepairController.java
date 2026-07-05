package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aimes.common.Result;
import com.aimes.dto.Requests.DeviceRepairCreateRequest;
import com.aimes.dto.Requests.DeviceRepairHandleRequest;
import com.aimes.service.DeviceRepairService;
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

@Tag(name = "设备维修")
@RestController
@RequestMapping("/api/device-repairs")
@RequiredArgsConstructor
public class DeviceRepairController {

    private final DeviceRepairService deviceRepairService;

    @GetMapping
    @SaCheckPermission("设备")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String status) {
        return Result.ok(deviceRepairService.list(deviceId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(deviceRepairService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> create(@Valid @RequestBody DeviceRepairCreateRequest request) {
        return Result.ok(deviceRepairService.create(request));
    }

    @PutMapping("/{id}/start")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> start(@PathVariable Long id) {
        return Result.ok(deviceRepairService.start(id));
    }

    @PutMapping("/{id}/complete")
    @SaCheckPermission("设备")
    public Result<Map<String, Object>> complete(@PathVariable Long id, @Valid @RequestBody DeviceRepairHandleRequest request) {
        return Result.ok(deviceRepairService.complete(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("设备")
    public Result<Void> delete(@PathVariable Long id) {
        deviceRepairService.delete(id);
        return Result.ok("删除成功", null);
    }
}
