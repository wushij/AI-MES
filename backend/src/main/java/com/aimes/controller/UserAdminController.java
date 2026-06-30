package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.aimes.common.Result;
import com.aimes.dto.Requests.ResetPasswordRequest;
import com.aimes.dto.Requests.UserSaveRequest;
import com.aimes.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(userAdminService.list());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(userAdminService.detail(id));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody UserSaveRequest request) {
        return Result.ok(userAdminService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        return Result.ok(userAdminService.update(id, request));
    }

    @PostMapping("/{id}/reset-password")
    public Result<Map<String, Object>> resetPassword(@PathVariable Long id,
                                                       @Valid @RequestBody ResetPasswordRequest request) {
        return Result.ok(userAdminService.resetPassword(id, request.getPassword()));
    }

    @PostMapping("/{id}/toggle-status")
    public Result<Map<String, Object>> toggleStatus(@PathVariable Long id) {
        return Result.ok(userAdminService.toggleStatus(id));
    }
}
