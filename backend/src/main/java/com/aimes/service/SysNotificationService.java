package com.aimes.service;

import com.aimes.entity.SysNotification;
import com.aimes.entity.SysUser;
import com.aimes.mapper.SysNotificationMapper;
import com.aimes.websocket.NotificationWebSocketManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysNotificationService {

    private final SysNotificationMapper sysNotificationMapper;
    private final AuthService authService;
    private final NotificationWebSocketManager notificationWebSocketManager;

    public List<SysNotification> listUnread() {
        return list(0);
    }

    public List<SysNotification> list(Integer isRead) {
        SysUser user = authService.currentUser();
        return sysNotificationMapper.selectList(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, user.getId())
                .eq(isRead != null, SysNotification::getIsRead, isRead)
                .orderByDesc(SysNotification::getCreateTime));
    }

    @Transactional
    public void markAllAsRead() {
        SysUser user = authService.currentUser();
        sysNotificationMapper.update(null, new LambdaUpdateWrapper<SysNotification>()
                .set(SysNotification::getIsRead, 1)
                .eq(SysNotification::getUserId, user.getId())
                .eq(SysNotification::getIsRead, 0));
    }

    @Transactional
    public void markAsRead(Long id) {
        SysUser user = authService.currentUser();
        sysNotificationMapper.update(null, new LambdaUpdateWrapper<SysNotification>()
                .set(SysNotification::getIsRead, 1)
                .eq(SysNotification::getId, id)
                .eq(SysNotification::getUserId, user.getId())
                .eq(SysNotification::getIsRead, 0));
    }

    @Transactional
    public void createNotification(Long userId, String title, String content, String type, String targetUrl) {
        long duplicateCount = sysNotificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getContent, content));
        if (duplicateCount > 0) {
            return;
        }

        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setTargetUrl(targetUrl);
        notification.setIsRead(0);
        notification.setCreateTime(java.time.LocalDateTime.now());
        sysNotificationMapper.insert(notification);
        notificationWebSocketManager.pushToUser(userId, notification);
    }
}
