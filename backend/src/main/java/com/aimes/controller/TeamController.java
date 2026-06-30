package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.TeamSaveRequest;
import com.aimes.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(teamService.list());
    }

    @GetMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(teamService.detail(id));
    }

    @PostMapping
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> create(@Valid @RequestBody TeamSaveRequest request) {
        return Result.ok(teamService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody TeamSaveRequest request) {
        return Result.ok(teamService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(value = {"admin", "supervisor"}, mode = SaMode.OR)
    public Result<Void> delete(@PathVariable Long id) {
        teamService.delete(id);
        return Result.ok("删除成功", null);
    }
}
