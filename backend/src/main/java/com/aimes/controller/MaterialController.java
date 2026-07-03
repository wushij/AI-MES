package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.MaterialCreateRequest;
import com.aimes.dto.Requests.MaterialUpdateRequest;
import com.aimes.service.MaterialService;
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

@Tag(name = "物料预警")
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        return Result.ok(materialService.list(keyword, status));
    }

    @GetMapping("/alerts")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> alerts() {
        return Result.ok(materialService.alerts());
    }

    @PostMapping
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> create(@Valid @RequestBody MaterialCreateRequest request) {
        return Result.ok(materialService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody MaterialUpdateRequest request) {
        return Result.ok(materialService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return Result.ok("删除成功", null);
    }
}
