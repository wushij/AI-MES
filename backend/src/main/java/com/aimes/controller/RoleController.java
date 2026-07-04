package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aimes.common.Result;
import com.aimes.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "角色权限")
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@SaCheckPermission("角色管理")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(roleService.list());
    }

    @PutMapping("/{roleKey}/permissions")
    public Result<String> updatePermissions(@PathVariable String roleKey, @RequestBody List<String> permissions) {
        roleService.updatePermissions(roleKey, permissions);
        return Result.ok("权限更新成功");
    }
}
