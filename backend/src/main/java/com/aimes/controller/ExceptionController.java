package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.ExceptionCreateRequest;
import com.aimes.dto.Requests.ExceptionHandleRequest;
import com.aimes.service.ExceptionService;
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

@Tag(name = "异常管理")
@RestController
@RequestMapping("/api/exceptions")
@RequiredArgsConstructor
public class ExceptionController {

    private final ExceptionService exceptionService;

    @GetMapping
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String status) {
        return Result.ok(exceptionService.list(page, size, keyword, type, status));
    }

    @GetMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(exceptionService.detail(id));
    }

    @PostMapping
    @SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
    public Result<Map<String, Object>> create(@Valid @RequestBody ExceptionCreateRequest request) {
        return Result.ok(exceptionService.create(request));
    }

    @PutMapping("/{id}/handle")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> handle(@PathVariable Long id, @Valid @RequestBody ExceptionHandleRequest request) {
        return Result.ok(exceptionService.handle(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole("admin")
    public Result<Void> delete(@PathVariable Long id) {
        exceptionService.delete(id);
        return Result.ok("异常记录已删除", null);
    }
}
