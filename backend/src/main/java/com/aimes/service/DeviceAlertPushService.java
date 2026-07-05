package com.aimes.service;

import com.aimes.entity.DevDevice;
import com.aimes.entity.DevInspectionRecord;
import com.aimes.entity.DevRepairOrder;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.SysUser;
import com.aimes.mapper.DevDeviceMapper;
import com.aimes.mapper.SysUserMapper;
import com.aimes.websocket.NotificationWebSocketManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceAlertPushService {

    private final DevDeviceMapper devDeviceMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationWebSocketManager notificationWebSocketManager;
    private final DeviceRuntimeService deviceRuntimeService;

    public void pushExceptionAlert(ExcEvent event) {
        if (event.getDeviceId() == null || !"device".equals(event.getEventType())) {
            return;
        }
        DevDevice device = devDeviceMapper.selectById(event.getDeviceId());
        if (device == null) {
            return;
        }

        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("id", event.getId());
        alert.put("alertType", "exception");
        alert.put("alertTypeLabel", "设备异常");
        alert.put("refNo", event.getEventNo());
        alert.put("title", event.getDescription());
        alert.put("status", event.getStatus());
        alert.put("statusLabel", exceptionStatusLabel(event.getStatus()));
        alert.put("occurTime", event.getOccurTime());
        alert.put("source", "exc_event");
        alert.put("sourceId", event.getId());
        alert.put("targetUrl", "/exceptions");

        String content = device.getDeviceCode() + " " + device.getDeviceName() + "：" + event.getDescription();
        pushToRecipients(device, alert, content);
    }

    public void pushRepairAlert(DevRepairOrder order) {
        DevDevice device = devDeviceMapper.selectById(order.getDeviceId());
        if (device == null) {
            return;
        }

        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("id", order.getId());
        alert.put("alertType", "repair");
        alert.put("alertTypeLabel", "维修待处理");
        alert.put("refNo", order.getRepairNo());
        alert.put("title", order.getFaultReason());
        alert.put("status", order.getStatus());
        alert.put("statusLabel", repairStatusLabel(order.getStatus()));
        alert.put("occurTime", order.getReportTime());
        alert.put("source", "dev_repair_order");
        alert.put("sourceId", order.getId());
        alert.put("targetUrl", "/devices");

        String content = device.getDeviceCode() + " " + device.getDeviceName() + " 报修：" + order.getFaultReason();
        pushToRecipients(device, alert, content);
    }

    public void pushInspectionAlert(DevInspectionRecord record) {
        if (record.getIsNormal() != null && record.getIsNormal() == 1) {
            return;
        }
        DevDevice device = devDeviceMapper.selectById(record.getDeviceId());
        if (device == null) {
            return;
        }

        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("id", record.getId());
        alert.put("alertType", "inspection");
        alert.put("alertTypeLabel", "点检异常");
        alert.put("refNo", record.getRecordNo());
        alert.put("title", record.getPlanName() != null ? record.getPlanName() + " 点检异常" : "点检发现异常");
        alert.put("status", "abnormal");
        alert.put("statusLabel", "异常");
        alert.put("occurTime", record.getInspectTime());
        alert.put("source", "dev_inspection_record");
        alert.put("sourceId", record.getId());
        alert.put("targetUrl", "/devices");

        String content = device.getDeviceCode() + " " + device.getDeviceName() + " 点检异常";
        pushToRecipients(device, alert, content);
    }

    private void pushToRecipients(DevDevice device, Map<String, Object> alert, String content) {
        List<Map<String, Object>> todayAlerts = deviceRuntimeService.listTodayAlerts(device.getId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceId", device.getId());
        payload.put("deviceCode", device.getDeviceCode());
        payload.put("deviceName", device.getDeviceName());
        payload.put("alert", alert);
        payload.put("todayAlertCount", todayAlerts.size());
        payload.put("message", content);

        for (Long userId : resolveRecipients(device)) {
            notificationWebSocketManager.pushDeviceAlert(userId, payload);
        }
    }

    private Set<Long> resolveRecipients(DevDevice device) {
        Set<Long> ids = new LinkedHashSet<>();
        sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getRole, List.of("admin", "supervisor")))
                .forEach(user -> ids.add(user.getId()));
        if (device.getManagerId() != null) {
            ids.add(device.getManagerId());
        }
        return ids;
    }

    private String exceptionStatusLabel(String status) {
        return switch (status) {
            case "open" -> "待处理";
            case "processing" -> "处理中";
            case "closed" -> "已关闭";
            default -> status;
        };
    }

    private String repairStatusLabel(String status) {
        return switch (status) {
            case "open" -> "待维修";
            case "processing" -> "维修中";
            case "completed" -> "已完成";
            default -> status;
        };
    }
}
