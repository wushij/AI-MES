package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.entity.SysNotification;
import com.aimes.service.SysNotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "消息通知")
@RestController
@RequestMapping("/api/notifications")
@SaCheckRole(value = {"admin", "supervisor", "worker"}, mode = SaMode.OR)
public class SysNotificationController {

    private final SysNotificationService sysNotificationService;

    public SysNotificationController(SysNotificationService sysNotificationService) {
        this.sysNotificationService = sysNotificationService;
    }

    @GetMapping
    public Result<List<SysNotification>> list(@RequestParam(required = false) Integer isRead) {
        return Result.ok(sysNotificationService.list(isRead));
    }

    @GetMapping("/unread")
    public Result<List<SysNotification>> listUnread() {
        return Result.ok(sysNotificationService.listUnread());
    }

    @PostMapping("/read-all")
    public Result<String> markAllAsRead() {
        sysNotificationService.markAllAsRead();
        return Result.ok("所有通知已标记为已读");
    }

    @PutMapping("/{id}/read")
    public Result<String> markAsRead(@PathVariable Long id) {
        sysNotificationService.markAsRead(id);
        return Result.ok("通知已读");
    }

    @DeleteMapping("/read")
    public Result<String> clearRead() {
        sysNotificationService.clearRead();
        return Result.ok("已清除全部已读通知");
    }
}
