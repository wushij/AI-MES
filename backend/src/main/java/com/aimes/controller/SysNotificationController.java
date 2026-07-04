package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.aimes.common.Result;
import com.aimes.entity.SysNotification;
import com.aimes.service.SysNotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "消息通知")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@SaCheckLogin
public class SysNotificationController {

    private final SysNotificationService sysNotificationService;

    @GetMapping
    public Result<List<SysNotification>> list(@RequestParam(required = false) Integer isRead) {
        return Result.ok(sysNotificationService.list(isRead));
    }

    @GetMapping("/unread")
    public Result<List<SysNotification>> unread() {
        return Result.ok(sysNotificationService.listUnread());
    }

    @PostMapping("/read-all")
    public Result<String> readAll() {
        sysNotificationService.markAllAsRead();
        return Result.ok("已全部标记为已读", null);
    }

    @PutMapping("/{id}/read")
    public Result<String> read(@PathVariable Long id) {
        sysNotificationService.markAsRead(id);
        return Result.ok("已标记为已读", null);
    }

    @DeleteMapping("/read")
    public Result<String> clearRead() {
        sysNotificationService.clearRead();
        return Result.ok("已清空已读通知", null);
    }
}
